package wiki.kivo.core.data

import java.io.IOException
import kotlinx.serialization.json.*
import wiki.kivo.core.data.network.*
import wiki.kivo.core.model.*

/** 线上 DTO 在这里结束；界面只接收稳定的领域模型。 */
object ContentMapper {
    private fun id(data: JsonObject): Int =
        data.number("id")?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
            ?: throw IOException("资料缺少有效编号")

    fun student(data: JsonObject, fallbackId: Int? = null): Student =
        Student(
            id =
                data.number("id")?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
                    ?: fallbackId?.takeIf { it > 0 }
                    ?: throw IOException("角色缺少有效编号"),
            name =
                data.text("given_name").ifBlank { data.text("given_name_jp") }.ifBlank { "姓名待补充" },
            familyName = data.text("family_name"),
            skin = data.text("skin"),
            avatar = UrlPolicy.resource(data.text("avatar")),
            birthday = data.text("birthday"),
            nameCn = data.text("given_name_cn"),
            skinCn = data.text("skin_cn"),
        )

    fun card(data: JsonObject, type: EntityType, fallbackId: Int? = null): ContentCard {
        if (type == EntityType.STUDENT) return student(data, fallbackId).card()
        return ContentCard(
            key =
                EntityKey(
                    type,
                    data.number("id")?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
                        ?: fallbackId
                        ?: id(data),
                ),
            title = data.text("title").ifBlank { data.text("name") }.ifBlank { "名称待补充" },
            image =
                UrlPolicy.resource(
                    data
                        .text("image")
                        .ifBlank { data.text("cover") }
                        .ifBlank { data.text("icon") }
                        .ifBlank {
                            (data["info"] as? JsonArray)
                                ?.firstOrNull()
                                ?.let { (it as? JsonObject)?.text("icon") }
                                .orEmpty()
                        }
                ),
            summary = summary(data.text("summary").ifBlank { data.text("body_summary") }),
            timestamp = data.number("start_time") ?: data.number("created_at"),
            sourceUrl = UrlPolicy.link(data.text("url")),
            subtitle =
                when (data.text("line_type").lowercase()) {
                    "jp" -> "日服"
                    "cn" -> "国服"
                    "global" -> "国际服"
                    else -> data.text("line_type")
                },
        )
    }

    fun cards(data: JsonObject, key: String, type: EntityType): Page<ContentCard> {
        // 线上“没有历史记录”返回 max_page=0 + timeline=null；仅接受这一明确的空页组合。
        if (data[key] == JsonNull && data.number("max_page") == 0L) return Page(emptyList(), 1)
        if (data[key] !is JsonArray) throw IOException("列表格式发生变化，请稍后重试")
        return Page(
            data.array(key).map { card(it.asObject(), type) }.distinctBy { it.key },
            (data.number("max_page") ?: 1).coerceIn(1, 100_000).toInt(),
        )
    }

    fun schedule(data: JsonObject, kind: String) =
        Schedule(
            kind,
            data.number("start_date")?.takeIf { it > 0 },
            data.number("end_date")?.takeIf { it > 0 },
            UrlPolicy.resource(data.text("banner")),
            data.array("students").mapNotNull {
                (it as? JsonPrimitive)?.intOrNull?.takeIf { n -> n > 0 }
            },
        )

    fun detail(data: JsonObject, key: EntityKey): ContentDetail {
        if (
            data.keys.none {
                it in
                    setOf(
                        "title",
                        "name",
                        "given_name",
                        "given_name_jp",
                        "body",
                        "info",
                        "introduction",
                    )
            }
        )
            throw IOException("资料格式发生变化，请稍后重试")
        val body =
            when (key.type) {
                EntityType.STUDENT -> data.text("introduction")
                EntityType.ITEM,
                EntityType.EQUIPMENT ->
                    data
                        .text("description")
                        .ifBlank { data.text("introduction") }
                        .ifBlank {
                            (data["info"] as? JsonArray)
                                ?.mapNotNull { (it as? JsonObject)?.text("description") }
                                ?.joinToString("\n\n")
                                .orEmpty()
                        }
                else -> data.text("body")
            }
        val skills =
            if (key.type == EntityType.STUDENT)
                data
                    .array("character_datas")
                    .flatMap { variant ->
                        val skill = (variant as? JsonObject)?.get("skill") as? JsonObject
                        listOf("ex_skill", "passive_skill").flatMap { kind ->
                            skill?.array(kind).orEmpty().mapNotNull { raw ->
                                val value = raw as? JsonObject ?: return@mapNotNull null
                                val levels =
                                    value.array("info").map {
                                        (it as? JsonObject)?.text("describe").orEmpty()
                                    }
                                if (levels.isEmpty()) null
                                else
                                    SkillPreview(
                                        value.text("title"),
                                        value.text("title_cn"),
                                        levels,
                                    )
                            }
                        }
                    }
                    .distinctBy { it.title }
            else emptyList()
        return ContentDetail(
            card(data, key.type, key.id),
            body,
            data.number("updated_at"),
            student = if (key.type == EntityType.STUDENT) student(data, key.id) else null,
            skills = skills,
        )
    }

    fun summary(raw: String): String =
        raw.replace(Regex("\\[\\[toc]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("[*#|>`]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(150)
}
