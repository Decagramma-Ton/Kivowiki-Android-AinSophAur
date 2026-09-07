package wiki.kivo.core.data

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import wiki.kivo.core.data.network.*
import wiki.kivo.core.model.*

/** 只读取公开资料，沿用公共缓存、请求并发上限与断网回退。 */
@Singleton
class OrganizationRepository @Inject constructor(private val content: ContentRepository) {
    private val relationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // 保留最后一次已解析状态；列表回收再进入时同步恢复，不能先把成员条清空。
    private val relationStates =
        object : LinkedHashMap<Int, StateFlow<LoadState<RelationProfile>>>(32, .75f, true) {}

    @Synchronized
    fun relationState(id: Int, force: Boolean = false): StateFlow<LoadState<RelationProfile>> {
        if (force || id !in relationStates) {
            val previous = relationStates[id]?.value ?: LoadState()
            relationStates[id] =
                relation(id, force)
                    .stateIn(relationScope, SharingStarted.WhileSubscribed(5000), previous)
            if (relationStates.size > 64) relationStates.remove(relationStates.keys.first())
        }
        return relationStates.getValue(id)
    }

    fun catalog(page: Int, force: Boolean = false) =
        content.observe(
            "data/schools",
            mapOf("page" to "$page", "page_size" to "40"),
            86400,
            force,
        ) { data ->
            if (
                data["school"] !is JsonArray &&
                    !(data["school"] == JsonNull && data.number("max_page") == 0L)
            )
                throw IOException("组织目录格式发生变化")
            Page(
                data
                    .array("school")
                    .map { OrganizationMapper.school(it.asObject()) }
                    .distinctBy { it.id },
                (data.number("max_page") ?: 1).coerceIn(1, 100000).toInt(),
            )
        }

    fun school(id: Int, force: Boolean = false) =
        content.observe("data/schools/$id", ttl = 86400, force = force) {
            OrganizationMapper.school(it, id)
        }

    fun relation(id: Int, force: Boolean = false) =
        content.observe("data/relations/$id", ttl = 86400, force = force) {
            OrganizationMapper.relation(it, id)
        }
}

object OrganizationMapper {
    private fun id(data: JsonObject, fallback: Int?): Int =
        data.number("id")?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
            ?: fallback
            ?: throw IOException("组织资料缺少编号")

    private fun students(data: JsonObject, key: String) =
        data.array(key).map { ContentMapper.student(it.asObject()) }.distinctBy { it.id }

    fun school(data: JsonObject, fallback: Int? = null) =
        OrganizationProfile(
            id(data, fallback),
            WikiText(data.text("name"), data.text("name_cn")),
            UrlPolicy.resource(data.text("logo")),
            UrlPolicy.resource(data.text("preview_image")),
            data.text("description"),
            data.array("map").map { entry ->
                val map = entry.asObject()
                OrganizationMap(
                    map.text("name"),
                    UrlPolicy.resource(map.text("image")),
                    map.array("mark").map {
                        val mark = it.asObject()
                        // 地标坐标相对于原始图片。缺失或超界时保留文字入口，不能伪造地图位置。
                        fun coordinate(key: String) =
                            (mark[key] as? JsonPrimitive)?.floatOrNull?.takeIf { v ->
                                v.isFinite() && v in 0f..1f
                            }
                        Landmark(
                            mark.text("name"),
                            mark.text("description"),
                            coordinate("x"),
                            coordinate("y"),
                        )
                    },
                )
            },
            students(data, "students"),
            data
                .array("related")
                .mapNotNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { v -> v > 0 } }
                .distinct(),
        )

    fun relation(data: JsonObject, fallback: Int? = null) =
        RelationProfile(
            id(data, fallback),
            WikiText(data.text("name"), data.text("name_cn")),
            UrlPolicy.resource(data.text("image")),
            data.text("description"),
            students(data, "main_students"),
            students(data, "secondary_students"),
        )
}
