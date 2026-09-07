package wiki.kivo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import wiki.kivo.core.data.ContentRepository
import wiki.kivo.core.data.local.SettingsRepository
import wiki.kivo.core.model.*

data class HomeUiState(
    val server: GameServer = GameServer.JP,
    val news: LoadState<List<ContentCard>> = LoadState(),
    val articles: LoadState<List<ContentCard>> = LoadState(),
    val bulletin: LoadState<Page<ContentCard>> = LoadState(),
    val recent: LoadState<List<Student>> = LoadState(),
    val birthdays: LoadState<List<Student>> = LoadState(),
    val lucky: LoadState<ContentCard> = LoadState(),
    val anniversary: LoadState<List<ContentCard>> = LoadState(),
    val schedules: Map<String, LoadState<Schedule>> = emptyMap(),
    val pickupStudents: List<Student> = emptyList(),
)

@HiltViewModel
class HomeViewModel
@Inject
constructor(val repository: ContentRepository, private val preferences: SettingsRepository) :
    ViewModel() {
    private val mutable = MutableStateFlow(HomeUiState())
    val state = mutable.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private var settings = AppSettings()
    private var calendarDay = LocalDate.now(ZoneId.of("Asia/Shanghai"))

    init {
        refresh()
        viewModelScope.launch {
            preferences.settings.collect { next ->
                val previous = settings
                settings = next
                if (mutable.value.schedules.isEmpty() || previous.server != next.server)
                    loadSchedule(next.server)
                if (previous.showBirthdays != next.showBirthdays) {
                    if (next.showBirthdays) loadBirthdays() else jobs.remove("birthdays")?.cancel()
                }
                if (previous.showHistory != next.showHistory) {
                    if (next.showHistory) loadAnniversary()
                    else jobs.remove("anniversary")?.cancel()
                }
            }
        }
    }

    private fun launchPart(
        key: String,
        force: Boolean = false,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        if (!force && jobs[key]?.isActive == true) return
        jobs.remove(key)?.cancel()
        jobs[key] = viewModelScope.launch(block = block)
    }

    fun setServer(server: GameServer) {
        viewModelScope.launch { preferences.setServer(server) }
    }

    /** 只由前台首页的分钟时钟调用；后台不轮询，跨天刷新生日、幸运物与那年今日。 */
    fun onClockTick() {
        val today =
            java.time.Instant.ofEpochSecond(repository.clock.now())
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDate()
        if (today != calendarDay) {
            calendarDay = today
            refresh(true)
        } else if (
            jobs["schedule"]?.isActive != true &&
                mutable.value.schedules.values.any { it.isStale(300) }
        )
            loadSchedule(settings.server)
    }

    fun refresh(force: Boolean = false) {
        launchPart("news", force) {
            repository.news(force).collect { s -> mutable.update { it.copy(news = s) } }
        }
        launchPart("articles", force) {
            repository.articles(force).collect { s -> mutable.update { it.copy(articles = s) } }
        }
        launchPart("bulletin", force) {
            repository.bulletins(force = force).collect { s ->
                mutable.update { it.copy(bulletin = s) }
            }
        }
        launchPart("recent", force) {
            repository.recent(force).collect { s -> mutable.update { it.copy(recent = s) } }
        }
        loadLucky(force)
        if (settings.showBirthdays) loadBirthdays(force)
        if (settings.showHistory) loadAnniversary(force)
        if (mutable.value.schedules.isNotEmpty()) loadSchedule(settings.server, force)
    }

    private fun loadSchedule(server: GameServer, force: Boolean = false) {
        jobs.remove("schedule")?.cancel()
        mutable.update {
            it.copy(server = server, schedules = emptyMap(), pickupStudents = emptyList())
        }
        if (!server.hasScheduleApi) return
        launchPart("schedule", true) {
            supervisorScope {
                listOf("卡池", "活动", "总力战").forEach { kind ->
                    launch {
                        repository.schedule(server, kind, force).collectLatest { result ->
                            mutable.update { it.copy(schedules = it.schedules + (kind to result)) }
                            if (kind == "卡池" && result.value != null) {
                                val students = supervisorScope {
                                    requireNotNull(result.value)
                                        .students
                                        .take(12)
                                        .map { id -> async { repository.student(id).last().value } }
                                        .awaitAll()
                                        .filterNotNull()
                                }
                                ensureActive()
                                mutable.update { it.copy(pickupStudents = students) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadBirthdays(force: Boolean = false) =
        launchPart("birthdays", force) {
            repository.birthdays(force).collectLatest { ids ->
                if (ids.value == null)
                    mutable.update {
                        it.copy(birthdays = LoadState(loading = ids.loading, error = ids.error))
                    }
                else {
                    val results = supervisorScope {
                        requireNotNull(ids.value)
                            .map { id -> async { repository.student(id, force).last() } }
                            .awaitAll()
                    }
                    mutable.update {
                        it.copy(
                            birthdays =
                                LoadState(
                                    results
                                        .mapNotNull { s -> s.value }
                                        .sortedBy { s -> s.birthday },
                                    ids.loading,
                                    ids.fetchedAt,
                                    ids.fromCache,
                                    ids.error
                                        ?: if (results.any { s -> s.error != null }) "部分生日资料未能更新"
                                        else null,
                                )
                        )
                    }
                }
            }
        }

    private fun loadLucky(force: Boolean = false) =
        launchPart("lucky", force) {
            repository.lucky(force).collectLatest { result ->
                val item = result.value
                val type =
                    when (item?.type) {
                        "item" -> EntityType.ITEM
                        "equipment" -> EntityType.EQUIPMENT
                        "student" -> EntityType.STUDENT
                        else -> null
                    }
                if (item != null && item.id > 0 && type != null) {
                    repository.detail(EntityKey(type, item.id), force).collect { detail ->
                        mutable.update {
                            it.copy(
                                lucky =
                                    LoadState(
                                        detail.value?.card,
                                        detail.loading,
                                        result.fetchedAt,
                                        result.fromCache,
                                        result.error ?: detail.error,
                                    )
                            )
                        }
                    }
                } else
                    mutable.update {
                        it.copy(
                            lucky =
                                LoadState(
                                    loading = result.loading,
                                    error =
                                        result.error
                                            ?: if (item != null) "今天的幸运物类型还未支持，可在网站查看" else null,
                                )
                        )
                    }
            }
        }

    private fun loadAnniversary(force: Boolean = false) =
        launchPart("anniversary", force) {
            val date = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            // 首页是历年选读：只取近五年每年最多五条，不扫描或全量同步史书。
            val parts = supervisorScope {
                ((date.year - 5).coerceAtLeast(2021) until date.year)
                    .map { year -> async { repository.historyYear(date, year, force).last() } }
                    .awaitAll()
            }
            val entries =
                parts
                    .flatMap { it.value.orEmpty() }
                    .distinctBy { it.key }
                    .sortedByDescending { it.timestamp }
            mutable.update {
                it.copy(
                    anniversary =
                        LoadState(
                            entries,
                            false,
                            parts.mapNotNull { p -> p.fetchedAt }.minOrNull(),
                            parts.any { p -> p.fromCache },
                            if (parts.any { p -> p.error != null }) "部分年份暂时无法读取，已保留可用记录" else null,
                        )
                )
            }
        }
}
