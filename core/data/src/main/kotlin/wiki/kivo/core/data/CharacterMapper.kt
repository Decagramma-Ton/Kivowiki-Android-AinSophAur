package wiki.kivo.core.data

import java.io.IOException
import kotlinx.serialization.json.*
import wiki.kivo.core.data.network.*
import wiki.kivo.core.model.*

/** API 的 null 列表是“暂无资料”；非列表值是格式错误，不能静默吃掉一整组内容。 */
object CharacterMapper {
    private fun JsonObject.translated(key: String) = WikiText(text(key), text("${key}_cn"))

    private fun JsonObject.boolean(key: String) =
        (get(key) as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.int(key: String) = number(key)?.toInt() ?: 0

    private fun JsonObject.resource(key: String) = UrlPolicy.resource(text(key))

    private fun JsonObject.obj(key: String) = get(key) as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonObject.ids(key: String) =
        array(key)
            .mapNotNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { id -> id > 0 } }
            .distinct()

    private fun JsonObject.rows(key: String) = array(key).map { it.asObject() }

    fun catalog(data: JsonObject) =
        Page(
            data
                .rows("students")
                .map {
                    CatalogEntry(
                        ContentMapper.student(it),
                        it.int("school"),
                        it.int("main_relation"),
                    )
                }
                .distinctBy { it.student.id },
            data.int("max_page").coerceAtLeast(1),
        )

    fun reference(row: JsonObject) =
        NamedReference(
            row.int("id"),
            row.translated("name"),
            row.resource("logo") ?: row.resource("icon") ?: row.resource("image"),
        )

    fun profile(data: JsonObject, id: Int): CharacterProfile {
        val info = buildList {
            fun field(label: String, key: String) {
                data.text(key).takeIf { it.isNotBlank() }?.let { add(InfoField(label, it)) }
            }
            field("年级", "grade")
            field("别名", "nick_name")
            // 网站使用 1 作为未知年龄/身高的占位。显示“未公开”，不推导真实数值。
            add(InfoField("年龄", data.int("age").takeIf { it > 1 }?.let { "$it 岁" } ?: "未公开"))
            add(InfoField("身高", data.int("height").takeIf { it > 1 }?.let { "$it cm" } ?: "未公开"))
            add(InfoField("生日", data.text("birthday").ifBlank { "未公开" }))
            field("爱好", "hobby")
            field("日语配音", "character_voice")
            field("中文配音", "character_voice_cn")
            field("角色设计", "designer")
            field("角色原画", "illustrator")
            for ((suffix, label) in
                listOf(
                    "jp" to "日文名",
                    "en" to "英文名",
                    "kr" to "韩文名",
                    "zh_tw" to "繁中译名",
                    "cn" to "国服译名",
                )) {
                val name =
                    listOf(data.text("family_name_$suffix"), data.text("given_name_$suffix"))
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                if (name.isNotBlank()) add(InfoField(label, name))
            }
            if (data.text("weapon_type").isNotBlank())
                add(
                    InfoField(
                        "武器类型",
                        CharacterFilters.label("weapon_type", data.text("weapon_type")),
                    )
                )
            if (data.text("body_shape").isNotBlank())
                add(InfoField("身材", CharacterFilters.label("body_shape", data.text("body_shape"))))
            if (data.boolean("special_appearance")) add(InfoField("特殊外观", "有"))
        }
        val name =
            listOf(data.text("family_name"), data.text("given_name"))
                .filter { it.isNotBlank() }
                .joinToString(" ")
        val cnName =
            listOf(data.text("family_name_cn"), data.text("given_name_cn"))
                .filter { it.isNotBlank() }
                .joinToString(" ")
        fun credits(key: String) =
            data.rows(key).map {
                SourceCredit(
                    it.text("text").ifBlank { it.text("name") },
                    UrlPolicy.link(it.text("url")),
                )
            }
        return CharacterProfile(
            ContentMapper.student(data, id),
            WikiText(name, cnName),
            data.translated("introduction"),
            data.text("momo_talk_signature"),
            info,
            data.int("school"),
            data.int("main_relation"),
            data.ids("relation"),
            data.boolean("is_npc"),
            data
                .rows("skin_list")
                .map { Costume(it.int("id"), it.translated("skin"), it.resource("avatar")) }
                .filter { it.id > 0 },
            listOf(
                    Triple("日服", "is_install", "release_date"),
                    Triple("国际服", "is_install_global", "release_date_global"),
                    Triple("国服", "is_install_cn", "release_date_cn"),
                )
                .map { (label, flag, date) ->
                    ReleaseState(label, data.boolean(flag), data.text(date))
                },
            data.rows("character_datas").map(::battle),
            data.text("more"),
            data.rows("gallery").map { row ->
                CharacterGallery(
                    row.text("title"),
                    row.array("images").mapNotNull {
                        UrlPolicy.resource((it as? JsonPrimitive)?.contentOrNull.orEmpty())
                    },
                )
            },
            data.ids("spine"),
            data.ids("model"),
            linkedMapOf("日语" to "voice", "国语" to "voice_cn", "韩语" to "voice_kr").mapValues {
                (_, key) ->
                data.rows(key).map {
                    CharacterVoice(
                        it.text("description"),
                        it.text("category"),
                        it.text("text"),
                        it.text("text_original"),
                        it.resource("file"),
                    )
                }
            },
            data.resource("sd_model_image"),
            data.resource("recollection_lobby_image"),
            data.rows("gift_data").map { GiftPreference(it.int("id"), it.int("favorability")) },
            data.ids("furniture"),
            credits("source"),
            credits("contributor"),
            data.text("info_declare_uuid"),
            data.text("skill_declare_uuid"),
            data.text("supplementary_declare_uuid"),
            data.text("supplementary_uuid"),
            data.number("updated_at"),
        )
    }

    fun skill(row: JsonObject, depth: Int = 0): CharacterSkill {
        if (depth > 24) throw IOException("技能分支层级超出可显示范围，请反馈此角色编号")
        return CharacterSkill(
            row.translated("title"),
            row.resource("icon"),
            row.resource("preview"),
            row.int("max_level"),
            row.boolean("is_passive_skill"),
            row.int("link_student_id"),
            row.rows("info").map {
                SkillLevel(
                    it.number("cost")?.toInt(),
                    WikiText(it.text("describe"), it.text("describe_cn")),
                )
            },
            row.rows("derived_skills").map { skill(it, depth + 1) },
        )
    }

    private val statNames =
        mapOf(
            "max_hp" to "生命值",
            "attack" to "攻击力",
            "defense" to "防御力",
            "healing" to "治疗力",
            "accuracy" to "命中值",
            "evasion" to "闪避值",
            "crit" to "暴击值",
            "crit_res" to "暴击抵抗力",
            "crit_dmg" to "暴击伤害",
            "crit_dmg_res" to "暴击伤害减免",
            "stability" to "稳定值",
            "range" to "普通攻击射程",
            "cc_power" to "群控强化",
            "cc_res" to "群控抵抗",
            "recovery_boost" to "回复强化率",
            "mag_count" to "弹药数",
        )
    private val percentStats = setOf("crit_dmg", "crit_dmg_res", "recovery_boost")

    private fun battle(row: JsonObject): BattleProfile {
        val skills = row.obj("skill")
        val weapon = row.obj("weapons")
        return BattleProfile(
            row.int("character_id"),
            row.text("dev_name"),
            row.text("combat_style"),
            row.text("type"),
            row.text("attack_attribute"),
            row.text("defensive_attributes"),
            row.text("team_position"),
            row.text("battlefield_position"),
            row.int("rarity"),
            row.boolean("limited"),
            row.boolean("is_groupc_control"),
            listOf(
                    "outdoor_adaptability" to "野外",
                    "indoor_adaptability" to "室内",
                    "street_adaptability" to "街区",
                )
                .map { (key, label) -> InfoField(label, row.text(key).ifBlank { "—" }) },
            row.rows("basic").map { stats ->
                stats.entries.map { (key, value) ->
                    val raw = (value as? JsonPrimitive)?.contentOrNull.orEmpty()
                    val display =
                        if (key in percentStats)
                            raw.toBigDecimalOrNull()
                                ?.multiply(100.toBigDecimal())
                                ?.stripTrailingZeros()
                                ?.toPlainString()
                                ?.plus("%") ?: raw
                        else raw
                    InfoField(statNames[key] ?: key, display)
                }
            },
            skills.rows("ex_skill").map { skill(it) },
            skills.rows("passive_skill").map { skill(it) },
            row.ids("equipment"),
            row.int("favorite_equipment"),
            row.ids("cultivate_material"),
            CharacterWeapon(
                weapon.translated("name"),
                weapon.translated("description"),
                weapon.resource("icon"),
                weapon.rows("info").map { WeaponStage(it.text("title"), it.text("description")) },
                weapon.rows("skill").map { skill(it) },
            ),
        )
    }

    fun equipment(row: JsonObject) =
        CharacterEquipment(
            row.int("id"),
            row.translated("name"),
            row.boolean("is_favorite"),
            row.rows("info").map {
                EquipmentLevel(it.translated("description"), it.resource("icon"))
            },
        )

    fun item(row: JsonObject) =
        CharacterItem(
            row.int("id"),
            row.translated("name"),
            row.translated("description"),
            row.resource("icon"),
            row.int("rarity"),
        )

    fun supplements(data: JsonObject) =
        Page(
            data.rows("supplementary").map { row ->
                Supplement(
                    row.int("id"),
                    row.obj("user").text("user_name"),
                    row.obj("user").resource("avatar"),
                    row.text("content"),
                )
            },
            data.int("max_page").coerceAtLeast(1),
        )

    fun icons(data: JsonObject) =
        data.rows("icon").map { Reaction(it.int("id"), it.resource("icon")) }

    fun reactions(data: JsonObject, icons: List<Reaction>) = icons.map { icon ->
        data
            .rows("declare")
            .find { it.int("icon_id") == icon.id }
            ?.let { icon.copy(count = it.int("count"), selected = it.boolean("selected")) }
            ?: icon.copy(count = 0, selected = false)
    }

    fun media(row: JsonObject, spine: Boolean): CharacterMedia =
        CharacterMedia(
            row.int("id"),
            row.text("name"),
            row.text("remark"),
            row.text("type"),
            row.resource(if (spine) "skel_file" else "model_file")
                ?: throw IOException("这份预览尚未提供有效文件"),
            row.resource(if (spine) "atlas_file" else "mtl_file"),
            row.array(if (spine) "images" else "texture").mapNotNull {
                UrlPolicy.resource((it as? JsonPrimitive)?.contentOrNull.orEmpty())
            },
        )
}
