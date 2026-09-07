package wiki.kivo.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import wiki.kivo.core.model.*

class OrganizationMapperTest {
    @Test
    fun mapsPreserveNormalizedCoordinatesAndMalformedMarksRemainReadable() {
        val school =
            OrganizationMapper.school(
                Json.parseToJsonElement(
                        """{
            "id":1,"name":"阿比多斯高中","name_cn":"阿拜多斯高等学院",
            "map":[{"name":"现役主校区","image":"//static.kivo.wiki/map.png","mark":[
                {"name":"主楼","x":0.40594566888774986,"y":0.285140562248996},
                {"name":"缺失坐标","x":null,"y":2}]}],
            "related":[19,20,19,0,-1],"students":null
        }"""
                    )
                    .jsonObject
            )
        assertEquals(listOf(19, 20), school.relations)
        assertEquals("阿拜多斯高等学院", school.name.display(TranslationMode.CN))
        assertEquals(.40594567f, school.maps.single().landmarks.first().x!!, .000001f)
        assertNull(school.maps.single().landmarks.last().x)
        assertNull(school.maps.single().landmarks.last().y)
        assertEquals("https://static.kivo.wiki/map.png", school.maps.single().image)
        assertTrue(school.students.isEmpty())
    }

    @Test
    fun relationIdentityAndCostumesRemainDistinctFromSchools() {
        val relation =
            OrganizationMapper.relation(
                Json.parseToJsonElement(
                        """{
            "id":19,"name":"废校对策委员会","name_cn":"", "description":"原文",
            "main_students":[{"id":86,"given_name":"白子"},{"id":87,"given_name":"白子","skin":"骑行服"}],
            "secondary_students":[{"id":86,"given_name":"白子"},{"id":74,"given_name":"日富美"}]
        }"""
                    )
                    .jsonObject
            )
        assertEquals(listOf(86, 87, 74), relation.members().map { it.id })
        assertEquals(listOf(87, 74), relation.members(86).map { it.id })
        assertEquals("废校对策委员会", relation.name.display(TranslationMode.CN))
        assertNotEquals(EntityKey(EntityType.SCHOOL, 19), relation.card(TranslationMode.FAN).key)
    }

    @Test
    fun nullAndMissingOptionalCollectionsAreNotErrors() {
        val relation =
            OrganizationMapper.relation(
                Json.parseToJsonElement("""{"name":"未公开关系","main_students":null}""").jsonObject,
                9,
            )
        assertTrue(relation.members().isEmpty())
        assertEquals(9, relation.id)
    }
}
