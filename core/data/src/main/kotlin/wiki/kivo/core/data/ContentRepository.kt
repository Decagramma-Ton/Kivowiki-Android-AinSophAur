package wiki.kivo.core.data

import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import wiki.kivo.core.data.local.*
import wiki.kivo.core.data.network.*
import wiki.kivo.core.model.*

/** 首页和阅读器共享缓存。UI 不自行请求网络，也不把错误伪装成空列表。 */
@Singleton
class ContentRepository
@Inject
constructor(
    private val api: KivoApi,
    private val database: KivoDatabase,
    private val json: Json,
    val clock: ServerClock,
) {
    private val dao
        get() = database.dao()

    // 固定数量的互斥锁避免任意搜索词不断增加锁对象；同查询同时刷新会合并。
    private val locks = Array(32) { Mutex() }
    private val cacheMaintenance = Mutex()

    fun <T> observe(
        path: String,
        query: Map<String, String> = emptyMap(),
        ttl: Long = 900,
        force: Boolean = false,
        mapper: (JsonObject) -> T,
    ): Flow<LoadState<T>> = flow {
        val key = api.url(path, query)
        val requestedAt = System.currentTimeMillis()
        var previous: LoadState<T> = LoadState()
        val cached = dao.cached(key)
        if (cached != null) {
            runCatching { mapper(json.parseToJsonElement(cached.payload).asObject()) }
                .onSuccess {
                    previous = LoadState(it, false, cached.fetchedAt / 1000, true)
                }
                .onFailure { dao.invalidate(key) }
        }
        val refresh = force || previous.value == null || previous.isStale(ttl)
        emit(previous.copy(loading = refresh))
        if (!refresh) return@flow
        try {
            val latest =
                locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
                    val recent = dao.cached(key)
                    if (recent != null && recent.fetchedAt >= requestedAt) {
                        LoadState(
                            mapper(json.parseToJsonElement(recent.payload).asObject()),
                            false,
                            recent.fetchedAt / 1000,
                            true,
                        )
                    } else {
                        val result = api.get(key)
                        // 先解析再替换缓存，格式漂移不会把可读的旧资料覆盖掉。
                        val mapped = mapper(result.data)
                        val now = System.currentTimeMillis()
                        clock.record(result.serverTime)
                        val payload = result.data.toString()
                        if (payload.toByteArray().size <= 2 * 1024 * 1024) {
                            cacheMaintenance.withLock {
                                database.withTransaction {
                                    dao.cache(
                                        CachedResponse(
                                            key,
                                            payload,
                                            now,
                                            result.serverTime,
                                            result.version,
                                        )
                                    )
                                    while (dao.cacheBytes() > CacheLimit.CONTENT_BYTES) dao
                                        .evictOldest()
                                }
                            }
                        }
                        LoadState(mapped, false, now / 1000)
                    }
                }
            emit(latest)
        } catch (failure: Exception) {
            if (failure is CancellationException && failure !is TimeoutCancellationException)
                throw failure
            emit(previous.copy(loading = false, error = failure.friendlyMessage()))
        }
    }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)

    fun news(force: Boolean = false) =
        observe("news", mapOf("page" to "1", "page_size" to "6"), force = force) {
            ContentMapper.cards(it, "news", EntityType.NEWS).entries
        }

    fun articles(force: Boolean = false) =
        observe(
            "articles",
            mapOf("page" to "1", "page_size" to "4", "summary_size" to "100"),
            force = force,
        ) {
            ContentMapper.cards(it, "article", EntityType.ARTICLE).entries
        }

    fun bulletins(page: Int = 1, force: Boolean = false) =
        observe("bulletins", mapOf("page" to page.toString(), "page_size" to "12"), force = force) {
            ContentMapper.cards(it, "bulletin", EntityType.BULLETIN)
        }

    fun recent(force: Boolean = false) =
        observe(
            "data/students",
            mapOf("page" to "1", "page_size" to "6", "updated_at_sort" to "desc"),
            force = force,
        ) {
            it.array("students").map { item -> ContentMapper.student(item.asObject()) }
        }

    fun students(page: Int = 1, force: Boolean = false) =
        observe(
            "data/students",
            mapOf("page" to page.toString(), "page_size" to "40"),
            ttl = 3600,
            force = force,
        ) { data ->
            Page(
                data
                    .array("students")
                    .map { ContentMapper.student(it.asObject()) }
                    .distinctBy { it.id },
                (data.number("max_page") ?: 1).toInt().coerceIn(1, 10000),
            )
        }

    /** 搜索词仅用于当前请求，不写入磁盘缓存或浏览历史。 */
    suspend fun search(query: String, page: Int = 1): Page<SearchResult> {
        require(query.isNotBlank() && query.length <= 120 && page > 0)
        val result =
            try {
                api.get(
                    api.url(
                        "search/",
                        mapOf(
                            "page" to "$page",
                            "page_size" to "20",
                            "keywords" to query.trim(),
                            "summary_size" to "100",
                        ),
                    )
                )
            } catch (failure: ApiFailure) {
                if (failure.businessCode == 4041) return Page(emptyList()) else throw failure
            }
        return Page(
            result.data
                .array("search_results")
                .map { item ->
                    val row = item.asObject()
                    SearchResult(
                        row.number("id")?.toInt()?.takeIf { it > 0 }
                            ?: throw java.io.IOException("搜索结果缺少编号"),
                        row.text("type"),
                        row.text("title"),
                        ContentMapper.summary(row.text("body")),
                        row.array("tags").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                    )
                }
                .distinctBy { it.key },
            (result.data.number("max_page") ?: 1).toInt().coerceIn(1, 10000),
        )
    }

    fun birthdays(force: Boolean = false) =
        observe("data/students/birthday/week", ttl = 3600, force = force) {
            it.array("students")
                .mapNotNull { id -> (id as? JsonPrimitive)?.intOrNull }
                .filter { id -> id > 0 }
                .distinct()
                .take(24)
        }

    fun student(id: Int, force: Boolean = false) =
        observe("data/students/$id", ttl = 86400, force = force) { ContentMapper.student(it, id) }

    // 凌晨刚打开时也不能把昨天尚未达到 15 分钟 TTL 的幸运物当成今天的数据。
    fun lucky(force: Boolean = false): Flow<LoadState<LuckyItem>> {
        val now = Instant.ofEpochSecond(clock.now()).atZone(ZoneId.of("Asia/Shanghai"))
        return observe(
            "data/lucky_item",
            ttl = now.toLocalTime().toSecondOfDay().toLong().coerceAtMost(900),
            force = force,
        ) {
            LuckyItem(it.text("type"), it.number("id")?.toInt() ?: 0)
        }
    }

    fun schedule(
        server: GameServer,
        kind: String,
        force: Boolean = false,
    ): Flow<LoadState<Schedule>> {
        require(server.hasScheduleApi)
        val path =
            when (kind) {
                "卡池" -> "pick_up"
                "活动" -> "event/now"
                "总力战" -> "raid/now"
                else -> error("Unknown schedule")
            }
        return observe("data/$path", mapOf("server" to server.value), ttl = 300, force = force) {
            ContentMapper.schedule(it, kind)
        }
    }

    fun detail(key: EntityKey, force: Boolean = false) =
        observe("${key.type.apiPath}/${key.id}", ttl = 86400, force = force) {
            ContentMapper.detail(it, key)
        }

    fun historyYear(
        date: LocalDate,
        year: Int,
        force: Boolean = false,
    ): Flow<LoadState<List<ContentCard>>> {
        val start = date.withYear(year).atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond()
        return observe(
            "timeline",
            mapOf(
                "page" to "1",
                "page_size" to "5",
                "start_time_start" to "$start",
                "start_time_end" to "${start + 86399}",
                "start_time_sort" to "desc",
            ),
            ttl = 21600,
            force = force,
        ) {
            ContentMapper.cards(it, "timeline", EntityType.TIMELINE).entries
        }
    }

    suspend fun cacheBytes(): Long = dao.cacheBytes()

    suspend fun clearCache() = cacheMaintenance.withLock { dao.clearCache() }
}

/** 用最近一次服务端秒数 + 单调时钟计算日程，避免设备时间调整导致倒计时跳跃。 */
@Singleton
class ServerClock @Inject constructor() {
    @Volatile private var reference: Pair<Long, Long>? = null

    fun record(seconds: Long?) {
        if (seconds != null && seconds > 0)
            reference = seconds to android.os.SystemClock.elapsedRealtime()
    }

    fun now(): Long =
        reference?.let { (seconds, elapsed) ->
            seconds + (android.os.SystemClock.elapsedRealtime() - elapsed) / 1000
        } ?: Instant.now().epochSecond
}
