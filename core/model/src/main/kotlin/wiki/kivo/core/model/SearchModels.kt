package wiki.kivo.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val id: Int,
    val type: String,
    val title: String,
    val summary: String,
    val tags: List<String> = emptyList(),
) {
    val key
        get() = "$type/$id"

    val label
        get() =
            when (type) {
                "article" -> "文章"
                "data_student" -> "角色"
                "data_item" -> "物品"
                "data_equipment" -> "装备"
                "data_school" -> "组织"
                "comic" -> "漫画"
                "gallery" -> "画廊"
                "music" -> "音乐"
                "video" -> "视频"
                else -> "站内资料"
            }

    val entity
        get() =
            when (type) {
                "article" -> EntityKey(EntityType.ARTICLE, id)
                "data_student" -> EntityKey(EntityType.STUDENT, id)
                "data_school" -> EntityKey(EntityType.SCHOOL, id)
                "data_relation" -> EntityKey(EntityType.RELATION, id)
                "data_item" -> EntityKey(EntityType.ITEM, id)
                "data_equipment" -> EntityKey(EntityType.EQUIPMENT, id)
                else -> null
            }

    fun matchesExactly(query: String): Boolean {
        fun normalize(value: String) =
            value.trim().replace(Regex("\\s+"), "").replace('（', '(').replace('）', ')').lowercase()
        val wanted = normalize(query)
        return wanted.isNotEmpty() &&
            (listOf(title) + tags.flatMap { it.split(',', '，') }).any { normalize(it) == wanted }
    }
}

/** 多个精确结果时保留选择权，不能直接用相关度最高的一条替用户决定。 */
fun uniqueExactMatch(results: List<SearchResult>, query: String): SearchResult? =
    results.filter { it.matchesExactly(query) }.singleOrNull()
