package wiki.kivo.core.data

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import wiki.kivo.core.model.*

/** 六种真实结构的只读快照；测试不请求源站、不依赖源站当日排序或编辑状态。 */
class CharacterMapperTest {
    private fun raw(id: Int) =
        Json.parseToJsonElement(
                requireNotNull(javaClass.getResource("/characters/$id.json")).readText()
            )
            .jsonObject

    private fun profile(id: Int) = CharacterMapper.profile(raw(id), id)

    @Test
    fun allSixProfilesPreserveCollectionsAndLongText() {
        for (id in listOf(86, 373, 346, 136, 619, 593)) {
            val source = raw(id)
            val p = profile(id)
            assertEquals(id, p.student.id)
            assertEquals(source["more"]?.jsonPrimitive?.content.orEmpty(), p.more)
            assertEquals(source["character_datas"]!!.jsonArray.size, p.battles.size)
            assertEquals(
                source["gallery"]!!.jsonArray.sumOf { it.jsonObject["images"]!!.jsonArray.size },
                p.gallery.sumOf { it.images.size },
            )
            for ((label, key) in
                mapOf("日语" to "voice", "国语" to "voice_cn", "韩语" to "voice_kr")) assertEquals(
                source[key]!!.jsonArray.size,
                p.voices[label]!!.size,
            )
            assertEquals(
                source["skin_list"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int },
                p.costumes.map { it.id },
            )
        }
    }

    @Test
    fun hoshinoModesCarryIndependentBattleData() {
        val modes = profile(373).battles
        assertEquals(listOf("防御型", "攻击型"), modes.map { it.style })
        assertEquals(listOf("Tank", "Dealer"), modes.map { it.type })
        assertNotEquals(modes[0].ex, modes[1].ex)
        assertNotEquals(modes[0].stats, modes[1].stats)
    }

    @Test
    fun derivedSkillsKeepTheirOwnLevelsAndIbukiLinksToIroha() {
        val ex = profile(346).battles.first().ex.first()
        assertEquals(5, ex.levels.size)
        assertEquals(3, ex.derived.size)
        // 实际接口把“终演的旋律”标为 10 级，但仅收录 5 条说明；不复制伪造第 6～10 级。
        assertEquals(10, ex.derived.last().maxLevel)
        assertEquals(5, ex.derived.last().levels.size)
        val ibuki = profile(136).battles.first()
        assertEquals(2, ibuki.ex.size)
        assertTrue((ibuki.ex + ibuki.passive).any { it.linkedStudent == 38 })
    }

    @Test
    fun uninstalledAndNpcDoNotFabricateAgeOrBattleStats() {
        assertFalse(profile(619).isNpc)
        assertTrue(profile(593).isNpc)
        for (id in listOf(619, 593)) {
            val p = profile(id)
            assertFalse(p.battles.any { it.hasBattleData })
            assertTrue(p.info.any { it.label == "年龄" && it.value == "未公开" })
            assertFalse(p.releases.any { it.installed })
        }
    }

    @Test
    fun percentagesAndLocalizedEquipmentStayReadable() {
        val p = profile(86)
        assertEquals("200%", p.battles.first().stats.first().single { it.label == "暴击伤害" }.value)
        assertEquals("100%", p.battles.first().stats.first().single { it.label == "回复强化率" }.value)
        assertEquals(listOf(180, 60, 40), p.gifts.map { it.favorability })
    }

    @Test
    fun malformedListIsNotMistakenForEmptyContent() {
        val source = raw(86).toMutableMap()
        source["voice"] = JsonObject(emptyMap())
        assertTrue(runCatching { CharacterMapper.profile(JsonObject(source), 86) }.isFailure)
    }
}
