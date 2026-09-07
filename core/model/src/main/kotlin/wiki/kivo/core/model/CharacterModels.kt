package wiki.kivo.core.model

import kotlinx.serialization.Serializable

/** 译名逐字段回退，国服缺译文时仍保留原资料；切换语言不改变实体身份。 */
data class WikiText(val original: String = "", val cn: String = "") {
    fun display(mode: TranslationMode) =
        if (mode == TranslationMode.CN) cn.ifBlank { original } else original
}

data class CatalogEntry(val student: Student, val school: Int, val relation: Int)

data class NamedReference(val id: Int, val name: WikiText, val image: String? = null)

data class InfoField(val label: String, val value: String)

data class Costume(val id: Int, val title: WikiText, val avatar: String?)

data class ReleaseState(val server: String, val installed: Boolean, val date: String)

data class SkillLevel(val cost: Int?, val description: WikiText)

data class CharacterSkill(
    val title: WikiText,
    val icon: String?,
    val preview: String?,
    val maxLevel: Int,
    val passive: Boolean,
    val linkedStudent: Int,
    val levels: List<SkillLevel>,
    val derived: List<CharacterSkill>,
)

data class WeaponStage(val title: String, val description: String)

data class CharacterWeapon(
    val name: WikiText,
    val description: WikiText,
    val icon: String?,
    val stages: List<WeaponStage>,
    val skills: List<CharacterSkill>,
)

data class BattleProfile(
    val characterId: Int,
    val devName: String,
    val style: String,
    val type: String,
    val attack: String,
    val defense: String,
    val position: String,
    val team: String,
    val rarity: Int,
    val limited: Boolean,
    val groupControl: Boolean,
    val terrains: List<InfoField>,
    val stats: List<List<InfoField>>,
    val ex: List<CharacterSkill>,
    val passive: List<CharacterSkill>,
    val equipment: List<Int>,
    val favoriteEquipment: Int,
    val materials: List<Int>,
    val weapon: CharacterWeapon,
) {
    // NPC 和卫星角色也返回全零模板。以业务数据判断是否存在战斗档案。
    val hasBattleData
        get() = rarity > 0 || ex.isNotEmpty() || passive.isNotEmpty() || type.isNotBlank()
}

data class CharacterVoice(
    val description: String,
    val category: String,
    val text: String,
    val original: String,
    val file: String?,
)

data class CharacterGallery(val title: String, val images: List<String>)

data class GiftPreference(val id: Int, val favorability: Int)

data class SourceCredit(val text: String, val url: String?)

data class CharacterProfile(
    val student: Student,
    val fullName: WikiText,
    val introduction: WikiText,
    val signature: String,
    val info: List<InfoField>,
    val school: Int,
    val mainRelation: Int,
    val relations: List<Int>,
    val isNpc: Boolean,
    val costumes: List<Costume>,
    val releases: List<ReleaseState>,
    val battles: List<BattleProfile>,
    val more: String,
    val gallery: List<CharacterGallery>,
    val spine: List<Int>,
    val models: List<Int>,
    val voices: Map<String, List<CharacterVoice>>,
    val sdImage: String?,
    val lobbyImage: String?,
    val gifts: List<GiftPreference>,
    val furniture: List<Int>,
    val sources: List<SourceCredit>,
    val contributors: List<SourceCredit>,
    val infoDeclare: String,
    val skillDeclare: String,
    val supplementaryDeclare: String,
    val supplementary: String,
    val updatedAt: Long?,
)

data class CharacterEquipment(
    val id: Int,
    val name: WikiText,
    val favorite: Boolean,
    val levels: List<EquipmentLevel>,
)

data class EquipmentLevel(val description: WikiText, val icon: String?)

data class CharacterItem(
    val id: Int,
    val name: WikiText,
    val description: WikiText,
    val icon: String?,
    val rarity: Int,
)

data class Supplement(val id: Int, val author: String, val avatar: String?, val content: String)

data class Reaction(
    val id: Int,
    val icon: String?,
    val count: Int = 0,
    val selected: Boolean = false,
)

data class CharacterMedia(
    val id: Int,
    val name: String,
    val remark: String,
    val type: String,
    val file: String,
    val companion: String?,
    val textures: List<String>,
) {
    val label
        get() = remark.ifBlank { name }

    val isSpine
        get() = type == "spr" || type == "home"
}

/** 全部参数均来自 2026-09-07 网站前台。未知值可显示，未知参数不会发给服务端。 */
@Serializable
data class CharacterQuery(
    val search: String = "",
    val filters: Map<String, String> = emptyMap(),
    val sort: String = "name_sort",
    val descending: Boolean = false,
) {
    fun parameters(page: Int): Map<String, String> {
        require(page > 0 && search.length <= 120)
        require(sort in CharacterFilters.sorts.map { it.first })
        require(filters.keys.all { it in CharacterFilters.all.map { f -> f.key } })
        return buildMap {
            put("page", page.toString())
            put("page_size", "40")
            // 当前前台同时传这两个字段；线上实测仅 character_data_search 不会过滤结果。
            if (search.isNotBlank()) {
                put("name", search.trim())
                put("character_data_search", search.trim())
            }
            putAll(filters.filterValues { it.isNotBlank() })
            put(sort, if (descending) "desc" else "asc")
        }
    }
}

data class CharacterFilter(
    val key: String,
    val label: String,
    val group: String,
    val options: List<Pair<String, String>> = emptyList(),
)

object CharacterFilters {
    val sorts =
        listOf(
            "name_sort" to "姓名",
            "id_sort" to "上传顺序",
            "height_sort" to "身高",
            "birthday_sort" to "生日",
            "release_date_sort" to "日服发布日期",
            "release_date_global_sort" to "国际服发布日期",
            "release_date_cn_sort" to "国服发布日期",
            "updated_at_sort" to "最近编辑",
        )

    private fun options(vararg values: String) = values.map {
        val p = it.split(':', limit = 2)
        p[0] to p.getOrElse(1) { p[0] }
    }

    private val truth = options("true:是", "false:否")
    val designers =
        listOf(
            "7peach",
            "9ml",
            "CHILD",
            "Crab D",
            "DoReMi",
            "dydldidl",
            "Doremsan2j",
            "Empew",
            "eno",
            "Fame",
            "GULIM",
            "Hwansang",
            "kokosando",
            "koo3473",
            "mery",
            "MISOM150",
            "mona",
            "Mx2J",
            "NAMYO",
            "nemoga",
            "ni02",
            "nino",
            "OSUK2",
            "Owa",
            "Paruru",
            "RONOPU",
            "seicoh",
            "tokki",
            "tonito",
            "Vinoker",
            "whoisshe",
            "YutokaMizu",
            "あやみ",
            "イコモチ",
            "カンザリン",
            "キキ",
            "にぎりうさぎ",
            "ヌードル",
            "はねこと",
            "ビョルチ",
            "ぶくろて",
            "ポップキュン",
            "まきあっと",
            "ミミトケ",
            "ミモザ",
            "やまかわ",
            "春夏冬ゆう",
            "二色こぺ",
            "桧野ひなこ",
            "日下雲",
            "三脚たこ",
        )
    val illustrators =
        listOf(
            "7peach",
            "9ml",
            "CHILD",
            "DoReMi",
            "dydldidl",
            "Empew",
            "Fame",
            "Hwansang",
            "kokosando",
            "koo3473",
            "maruchi",
            "mery",
            "MISOM150",
            "mona",
            "Mx2j",
            "NAMYO",
            "ni02",
            "nino",
            "OSUK2",
            "Paruru",
            "RONOPU",
            "seicoh",
            "tokki",
            "tonito",
            "whoisshe",
            "YutokaMizu",
            "eno",
            "nemoga",
            "Doremsan2j",
            "GULIM",
            "三脚たこ",
            "二色こぺ",
            "日下雲",
            "あやみ",
            "イコモチ",
            "カンザリン",
            "キキ",
            "にぎりうさぎ",
            "ヌードル",
            "はねこと",
            "ビョルチ",
            "ぶくろて",
            "ポップキュン",
            "まきあっと",
            "ミミトケ",
            "やまかわ",
        )
    val all =
        listOf(
            CharacterFilter("school", "所属组织", "身份与实装"),
            CharacterFilter("is_npc", "是否为 NPC", "身份与实装", truth),
            CharacterFilter("is_install", "日服实装", "身份与实装", truth),
            CharacterFilter("is_install_global", "国际服实装", "身份与实装", truth),
            CharacterFilter("is_install_cn", "国服实装", "身份与实装", truth),
            CharacterFilter("is_skin", "换装角色", "身份与实装", truth),
            CharacterFilter("special_appearance", "特殊外观", "身份与实装", truth),
            CharacterFilter("rarity", "稀有度", "战斗与装备", options("3:★★★", "2:★★", "1:★")),
            CharacterFilter("limited", "获取方式", "战斗与装备", options("true:限定", "false:常驻")),
            CharacterFilter("battlefield_position", "部队类型", "战斗与装备", options("STRIKER", "SPECIAL")),
            CharacterFilter(
                "type",
                "职能定位",
                "战斗与装备",
                options("Tank:坦克", "Dealer:输出", "Healer:治疗", "Support:辅助", "T.S.:载具支援"),
            ),
            CharacterFilter(
                "attack_attribute",
                "攻击类型",
                "战斗与装备",
                options("Explosive:爆发", "Piercing:贯穿", "Mystic:神秘", "Vibration:振动", "Chemical:分解"),
            ),
            CharacterFilter(
                "defensive_attributes",
                "防御属性",
                "战斗与装备",
                options(
                    "Light:轻装甲",
                    "Heavy:重装甲",
                    "Special:特殊装甲",
                    "Elastic:弹性装甲",
                    "CompositeArmor:复合装甲",
                ),
            ),
            CharacterFilter(
                "team_position",
                "站位",
                "战斗与装备",
                options("FRONT:前排", "MIDDLE:中排", "BACK:后排"),
            ),
            CharacterFilter("is_group_control", "群控能力", "战斗与装备", truth),
            CharacterFilter(
                "weapon_type",
                "武器类型",
                "战斗与装备",
                options(
                    "SG:霰弹枪",
                    "SMG:冲锋枪",
                    "AR:突击步枪",
                    "GL:榴弹发射器",
                    "HG:手枪",
                    "RL:导弹发射器",
                    "SR:狙击枪",
                    "RG:轨道炮",
                    "MG:重机枪",
                    "MT:迫击炮",
                    "FT:喷火器",
                ),
            ),
            CharacterFilter(
                "equipment",
                "装备",
                "战斗与装备",
                options("2:护符", "3:手表", "4:项链", "5:徽章", "6:发夹", "7:帽子", "8:手套", "9:鞋子", "10:背包"),
            ),
            CharacterFilter(
                "outdoor_adaptability",
                "野外适应性",
                "地形适应性",
                options("SS", "S", "A", "B", "C", "D"),
            ),
            CharacterFilter(
                "indoor_adaptability",
                "室内适应性",
                "地形适应性",
                options("SS", "S", "A", "B", "C", "D"),
            ),
            CharacterFilter(
                "street_adaptability",
                "街区适应性",
                "地形适应性",
                options("SS", "S", "A", "B", "C", "D"),
            ),
            CharacterFilter("birthday", "生日", "个人资料"),
            CharacterFilter(
                "body_shape",
                "身材",
                "个人资料",
                options("Small:娇小", "Medium:普通", "Large:高挑"),
            ),
            CharacterFilter("designer", "角色设计", "个人资料", designers.map { it to it }),
            CharacterFilter("illustrator", "角色原画", "个人资料", illustrators.map { it to it }),
        )

    fun label(key: String, value: String) =
        all.find { it.key == key }?.options?.find { it.first == value }?.second ?: value
}
