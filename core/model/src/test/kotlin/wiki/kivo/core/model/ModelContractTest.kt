package wiki.kivo.core.model

import org.junit.Assert.*
import org.junit.Test

/** 资源来源、实体身份和时间边界直接关系到错误导航与过期日程，作为基础回归门槛。 */
class ModelContractTest {
    @Test
    fun resourcesHandleProtocolRelativeUnicodeAndEmpty() {
        assertEquals(
            "https://static.kivo.wiki/cache/a.png",
            UrlPolicy.resource("//static.kivo.wiki/cache/a.png"),
        )
        assertEquals("https://static.kivo.wiki/a%20b.png", UrlPolicy.resource("/a b.png"))
        assertEquals(
            "https://static.kivo.wiki/a.png",
            UrlPolicy.resource("http://static.kivo.wiki/a.png"),
        )
        assertNull(UrlPolicy.resource("  "))
        assertTrue(UrlPolicy.resource("/图片.png")!!.contains("%E5"))
    }

    @Test
    fun untrustedSchemesCredentialsAndPortsAreRejected() {
        listOf(
                "javascript:alert(1)",
                "file:///etc/passwd",
                "data:image/png,a",
                "intent://x",
                "https://user:password@kivo.wiki/x",
                "https://kivo.wiki:8080/x",
                "https://kivo.wiki/\\x",
                "https://kivo.wiki/a\nb",
            )
            .forEach { assertNull(it, UrlPolicy.link(it)) }
        assertFalse(UrlPolicy.isFirstPartyImage("https://static.kivo.wiki.evil.example/a"))
        assertFalse(UrlPolicy.isFirstPartyImage("https://other.example/a"))
        assertTrue(UrlPolicy.isFirstPartyImage("https://static.kivo.wiki/a"))
    }

    @Test
    fun onlyVerifiedEntityRoutesBecomeNative() {
        assertEquals(EntityKey(EntityType.ARTICLE, 83), UrlPolicy.entity("kivo.wiki/article/83"))
        assertEquals(EntityKey(EntityType.STUDENT, 1), UrlPolicy.entity("/data/character/1"))
        listOf(
                "/article/0",
                "/article/-1",
                "/article/9999999999999",
                "/article/1?mode=edit",
                "/article/1#part",
                "https://other.example/article/1",
                "/data/equipment/19",
            )
            .forEach { assertNull(it, UrlPolicy.entity(it)) }
        assertNotEquals(
            EntityKey(EntityType.ARTICLE, 1).storageKey,
            EntityKey(EntityType.STUDENT, 1).storageKey,
        )
    }

    @Test
    fun scheduleNeverClaimsExpiredOrIncompleteDataIsCurrent() {
        val schedule = Schedule("活动", 100, 200, null)
        assertEquals("即将开始", schedule.status(99))
        assertEquals("进行中", schedule.status(100))
        assertEquals("已结束 · 待更新", schedule.status(200))
        assertEquals(0f, schedule.progress(0))
        assertEquals(.5f, schedule.progress(150))
        assertEquals(1f, schedule.progress(250))
        assertEquals("资料待更新", schedule.copy(end = 100).status(120))
        assertEquals("资料待更新", schedule.copy(start = null).status(120))
    }

    @Test
    fun cacheFreshnessHandlesClockRollbackAndTtlBoundary() {
        val state = LoadState("cached", false, 1000, true)
        assertFalse(state.isStale(300, 1299))
        assertTrue(state.isStale(300, 1300))
        assertTrue(state.isStale(300, 900))
        assertFalse(state.isStale(300, 980))
        assertTrue(LoadState<String>().isStale(300, 1000))
    }
}
