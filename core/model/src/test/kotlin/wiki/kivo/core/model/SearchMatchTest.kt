package wiki.kivo.core.model

import org.junit.Assert.*
import org.junit.Test

class SearchMatchTest {
    @Test
    fun exactMatchRespectsCostumesAndAmbiguity() {
        val plain = SearchResult(1, "data_student", "测试 姓名", "", listOf("别名，另名"))
        val skin = SearchResult(2, "data_student", "测试 姓名 (泳装)", "")
        assertEquals(plain, uniqueExactMatch(listOf(plain, skin), "测试姓名"))
        assertEquals(skin, uniqueExactMatch(listOf(plain, skin), "测试姓名（泳装）"))
        assertEquals(plain, uniqueExactMatch(listOf(plain), "另名"))
        assertNull(uniqueExactMatch(listOf(plain, skin), "测试"))
        assertNull(uniqueExactMatch(listOf(plain, plain.copy(id = 3)), "别名"))
    }

    @Test
    fun translationFallsBackAndStartupCatalogHasItsOwnRoot() {
        val student = Student(1, "民间名", skin = "泳装", nameCn = "国服名")
        assertEquals("国服名", student.translated(TranslationMode.CN).name)
        assertEquals("泳装", student.translated(TranslationMode.CN).skin)
        assertEquals(2, StartDestination.CHARACTERS.tab)
        assertNull(StartDestination.CHARACTERS.portal)
    }
}
