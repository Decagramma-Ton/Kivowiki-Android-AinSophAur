package wiki.kivo.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/** 实体类型参与身份，避免文章 1、角色 1 共用缓存或收藏。 */
@Serializable
enum class EntityType(val apiPath: String, val webPath: String, val label: String) {
    NEWS("news", "", "资讯"),
    ARTICLE("articles", "article", "文章"),
    BULLETIN("bulletins", "", "公告"),
    STUDENT("data/students", "data/character", "角色"),
    SCHOOL("data/schools", "data/organize", "组织"),
    RELATION("data/relations", "", "关系"),
    ITEM("data/items", "data/item", "物品"),
    EQUIPMENT("data/equipments", "", "装备"),
    TIMELINE("timeline", "timeline", "史书"),
}

@Serializable
data class EntityKey(val type: EntityType, val id: Int) {
    init {
        require(id > 0) { "资料 ID 必须为正数" }
    }

    val storageKey: String
        get() = "${type.name}/$id"

    val webUrl: String
        get() =
            when (type) {
                EntityType.NEWS,
                EntityType.BULLETIN -> "https://kivo.wiki/"
                EntityType.EQUIPMENT -> "https://kivo.wiki/data/item"
                // 网站关系详情是组织页内弹层，没有独立可分享网页路由。
                EntityType.RELATION -> "https://kivo.wiki/data/organize"
                else -> "https://kivo.wiki/${type.webPath}/$id"
            }
}

@Serializable
enum class GameServer(val value: String, val label: String, val zone: String) {
    JP("jp", "日服", "Asia/Tokyo"),
    CN("cn", "国服", "Asia/Shanghai"),
    GLOBAL("global", "国际服", "UTC");

    val hasScheduleApi
        get() = this != GLOBAL
}

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

@Serializable
enum class TranslationMode(val label: String) {
    FAN("民间翻译"),
    CN("国服翻译"),
}

/** 启动目标使用稳定标识持久化，不能依赖界面文字或列表顺序。 */
@Serializable
enum class StartDestination(val label: String, val tab: Int, val portal: String? = null) {
    HOME("首页", 0),
    DATA("资料", 1),
    CHARACTERS("角色图鉴", 2),
    COLLECTION("典藏", 3),
    PROFILE("我的", 4),
    ARTICLES("报刊亭", 3, "articles"),
    TIMELINE("Kivo 史书", 1, "timeline"),
    GALLERY("画廊", 3, "gallery"),
    MUSIC("留声机", 3, "music"),
    COMICS("漫画屋", 3, "comics"),
}

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val server: GameServer = GameServer.JP,
    val readingScale: Float = 1f,
    val loadImages: Boolean = true,
    val externalImages: Boolean = true,
    val reducedMotion: Boolean = false,
    val rememberHistory: Boolean = true,
    val showBirthdays: Boolean = true,
    val showHistory: Boolean = true,
    val startDestination: StartDestination = StartDestination.HOME,
    val translation: TranslationMode = TranslationMode.FAN,
    val levelMax: Boolean = true,
    val searchAutoRedirect: Boolean = true,
    val festiveEffects: Boolean = true,
    val compactCatalog: Boolean = false,
    val showCharacterBackground: Boolean = true,
    val swipeCategories: Boolean = true,
    val compactOrganization: Boolean = false,
    val cacheLimit: CacheLimit = CacheLimit.GB_1,
    val spineGpu: Boolean = true,
    val spineFixBlend: Boolean = true,
    val spineExperimental: Boolean = false,
)

/** 可重建的磁盘缓存预算。图片和 JSON 保留固定子预算，剩余空间供重媒体使用。 */
enum class CacheLimit(val label: String, val bytes: Long) {
    MB_512("512 MB", 512L * 1024 * 1024),
    GB_1("1 GB（推荐）", 1024L * 1024 * 1024),
    GB_2("2 GB", 2048L * 1024 * 1024),
    GB_4("4 GB", 4096L * 1024 * 1024),
    UNLIMITED("无上限", Long.MAX_VALUE);

    val mediaBytes: Long
        get() = if (this == UNLIMITED) Long.MAX_VALUE else bytes - IMAGE_BYTES - CONTENT_BYTES

    companion object {
        const val IMAGE_BYTES = 256L * 1024 * 1024
        const val CONTENT_BYTES = 64L * 1024 * 1024
    }
}

/** 每个首页区块独立携带内容、刷新状态、新鲜度及失败原因。 */
data class LoadState<T>(
    val value: T? = null,
    val loading: Boolean = true,
    val fetchedAt: Long? = null,
    val fromCache: Boolean = false,
    val error: String? = null,
) {
    fun isStale(ttlSeconds: Long, now: Long = Instant.now().epochSecond): Boolean =
        fetchedAt == null || now - fetchedAt >= ttlSeconds || now < fetchedAt - 60
}

@Serializable
data class ContentCard(
    val key: EntityKey,
    val title: String,
    val image: String? = null,
    val summary: String = "",
    val timestamp: Long? = null,
    val sourceUrl: String? = null,
    val subtitle: String = "",
)

@Serializable
data class ContentDetail(
    val card: ContentCard,
    val body: String,
    val updatedAt: Long? = null,
    val privateContent: Boolean = false,
    val student: Student? = null,
    val skills: List<SkillPreview> = emptyList(),
)

@Serializable
data class SkillPreview(
    val title: String,
    val titleCn: String = "",
    val levels: List<String> = emptyList(),
)

@Serializable
data class Student(
    val id: Int,
    val name: String,
    val familyName: String = "",
    val skin: String = "",
    val avatar: String? = null,
    val birthday: String = "",
    val nameCn: String = "",
    val skinCn: String = "",
) {
    fun translated(mode: TranslationMode): Student =
        if (mode == TranslationMode.CN)
            copy(name = nameCn.ifBlank { name }, skin = skinCn.ifBlank { skin })
        else this

    val displayName
        get() = if (skin.isBlank()) name else "$name（$skin）"

    val key
        get() = EntityKey(EntityType.STUDENT, id)

    fun card() = ContentCard(key, displayName, avatar, subtitle = birthday)
}

@Serializable
data class Schedule(
    val kind: String,
    val start: Long?,
    val end: Long?,
    val banner: String?,
    val students: List<Int> = emptyList(),
) {
    /** 不根据一个过时横幅猜测标题；区间不完整时明确标为待更新。 */
    fun status(now: Long): String =
        when {
            start == null || end == null || end <= start -> "资料待更新"
            now < start -> "即将开始"
            now >= end -> "已结束 · 待更新"
            else -> "进行中"
        }

    fun progress(now: Long): Float =
        if (start != null && end != null && end > start)
            ((now - start).toDouble() / (end - start)).toFloat().coerceIn(0f, 1f)
        else 0f
}

@Serializable data class Page<T>(val entries: List<T>, val maxPage: Int = 1)

@Serializable data class LuckyItem(val type: String, val id: Int, val card: ContentCard? = null)

@Serializable data class UserProfile(val id: String, val name: String, val avatar: String? = null)

data class AccountState(
    val user: UserProfile? = null,
    val busy: Boolean = false,
    val checking: Boolean = false,
    val message: String? = null,
)

/** 个人阅读记录属于本机资料，不与网站账号的云端数据混为一谈。 */
data class SavedEntry(
    val card: ContentCard,
    val savedAt: Long,
    val position: Int = 0,
    val offset: Int = 0,
)

fun shortDate(
    seconds: Long?,
    zone: String = "Asia/Shanghai",
    pattern: String = "MM.dd HH:mm",
): String =
    seconds
        ?.takeIf { it > 0 }
        ?.let {
            runCatching {
                DateTimeFormatter.ofPattern(pattern)
                    .withZone(ZoneId.of(zone))
                    .format(Instant.ofEpochSecond(it))
            }
                .getOrNull()
        } ?: "时间待更新"
