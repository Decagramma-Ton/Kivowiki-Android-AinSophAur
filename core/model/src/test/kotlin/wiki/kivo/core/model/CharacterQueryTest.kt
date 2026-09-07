package wiki.kivo.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CharacterQueryTest {
    @Test
    fun everyWebsiteFilterHasStableApiKeyAndOptions() {
        val expected =
            setOf(
                "school",
                "is_npc",
                "is_install",
                "is_install_global",
                "is_install_cn",
                "is_skin",
                "special_appearance",
                "rarity",
                "limited",
                "battlefield_position",
                "type",
                "attack_attribute",
                "defensive_attributes",
                "team_position",
                "is_group_control",
                "weapon_type",
                "equipment",
                "outdoor_adaptability",
                "indoor_adaptability",
                "street_adaptability",
                "birthday",
                "body_shape",
                "designer",
                "illustrator",
            )
        assertEquals(expected, CharacterFilters.all.map { it.key }.toSet())
        assertEquals(24, CharacterFilters.all.size)
        assertTrue(
            CharacterFilters.all
                .single { it.key == "attack_attribute" }
                .options
                .any { it.first == "Chemical" }
        )
        assertTrue(
            CharacterFilters.all
                .single { it.key == "defensive_attributes" }
                .options
                .any { it.first == "CompositeArmor" }
        )
        assertEquals(51, CharacterFilters.designers.size)
        assertEquals(46, CharacterFilters.illustrators.size)
    }

    @Test
    fun negativeBooleanFiltersRemainDifferentFromAny() {
        val query =
            CharacterQuery(
                "白子 & 泳装",
                mapOf(
                    "is_npc" to "false",
                    "school" to "1",
                    "equipment" to "7",
                    "special_appearance" to "true",
                ),
                "release_date_cn_sort",
                true,
            )
        val parameters = query.parameters(2)
        assertEquals("false", parameters["is_npc"])
        assertEquals("desc", parameters["release_date_cn_sort"])
        assertEquals("白子 & 泳装", parameters["character_data_search"])
        assertEquals("白子 & 泳装", parameters["name"])
        assertEquals("40", parameters["page_size"])
        assertEquals(query, Json.decodeFromString<CharacterQuery>(Json.encodeToString(query)))
        assertFalse(CharacterQuery().parameters(1).containsKey("is_npc"))
    }

    @Test
    fun invalidSavedOrInjectedKeysDoNotReachTheApi() {
        assertTrue(
            runCatching { CharacterQuery(filters = mapOf("authorization" to "x")).parameters(1) }
                .isFailure
        )
        assertTrue(runCatching { CharacterQuery(sort = "arbitrary_sort").parameters(1) }.isFailure)
        assertTrue(runCatching { CharacterQuery().parameters(0) }.isFailure)
    }

    @Test
    fun translationFallsBackPerField() {
        assertEquals("旧译", WikiText("旧译", "").display(TranslationMode.CN))
        assertEquals("新译", WikiText("旧译", "新译").display(TranslationMode.CN))
    }
}
