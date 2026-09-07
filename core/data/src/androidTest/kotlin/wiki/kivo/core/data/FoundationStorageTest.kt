package wiki.kivo.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import retrofit2.Response
import wiki.kivo.core.data.account.*
import wiki.kivo.core.data.local.*
import wiki.kivo.core.data.network.*
import wiki.kivo.core.model.*

/** 独立的库测试 APK，所有账号返回来自内存替身；不会向生产服务器发送登录请求。 */
@RunWith(AndroidJUnit4::class)
class FoundationStorageTest {
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var db: KivoDatabase

    @Before
    fun prepare() {
        db = Room.inMemoryDatabaseBuilder(context, KivoDatabase::class.java).build()
    }

    @After
    fun finish() = runBlocking {
        db.close()
        SessionVault(context, json).clear()
    }

    @Test
    fun clearingCachePreservesBookmarksAndReadingPosition() = runBlocking {
        val library = LibraryRepository(db, json)
        val a = ContentCard(EntityKey(EntityType.ARTICLE, 1), "测试文章")
        val b = ContentCard(EntityKey(EntityType.STUDENT, 1), "测试角色")
        library.addBookmark(a)
        library.addBookmark(b)
        library.record(a, 14, 120)
        db.dao().cache(CachedResponse("test", "{}", 1, null, "test"))
        db.dao().clearCache()
        assertEquals(0L, db.dao().cacheBytes())
        assertEquals(2, library.bookmarks.first().size)
        assertEquals(14 to 120, library.progress(a.key))
        library.clearHistory()
        assertEquals(2, library.bookmarks.first().size)
        library.removeBookmark(a.key)
        assertEquals(b.key, library.bookmarks.first().single().card.key)
    }

    @Test
    fun databaseSurvivesCloseAndReopen() = runBlocking {
        val name = "foundation-persistence-test.db"
        val card = ContentCard(EntityKey(EntityType.BULLETIN, 39), "本地测试公告")
        val first = Room.databaseBuilder(context, KivoDatabase::class.java, name).build()
        try {
            LibraryRepository(first, json).addBookmark(card)
        } finally {
            first.close()
        }
        val second = Room.databaseBuilder(context, KivoDatabase::class.java, name).build()
        try {
            assertEquals(card, LibraryRepository(second, json).bookmarks.first().single().card)
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun encryptedSessionRoundTripContainsNoPlaintextToken() = runBlocking {
        val vault = SessionVault(context, json)
        val stored = PersistedSession("LOCAL_TEST_REFRESH_NEVER_VALID", UserProfile("test", "测试老师"))
        vault.write(stored)
        assertFalse(
            File(context.noBackupFilesDir, "session.v1")
                .readBytes()
                .decodeToString()
                .contains(stored.refresh)
        )
        assertEquals(stored, SessionVault(context, json).read())
        vault.clear()
        assertNull(vault.read())
    }

    @Test
    fun loginRefreshProfileAndExpiredSessionContract() = runBlocking {
        val service = LocalService()
        val vault = SessionVault(context, json)
        val account = AccountRepository(service, vault, ServerClock())
        account.restore()
        account.login("LOCAL_TEST_ACCOUNT", "LOCAL_TEST_PASSWORD")
        assertEquals("测试老师", account.state.value.user?.name)
        assertNotNull(vault.read())
        assertTrue(service.proofWasValid)
        val restored = AccountRepository(service, vault, ServerClock())
        restored.restore()
        assertEquals("test", restored.state.value.user?.id)
        service.expired = true
        restored.revalidate()
        assertNull(restored.state.value.user)
        assertNull(vault.read())
        assertTrue(restored.state.value.message.orEmpty().contains("过期"))
    }

    @Test
    fun rejectedLoginNeverPersistsAndOfflineLogoutStillClearsLocal() = runBlocking {
        val service = LocalService()
        val vault = SessionVault(context, json)
        val account = AccountRepository(service, vault, ServerClock())
        service.rejectLogin = true
        account.login("LOCAL_TEST_ACCOUNT", "LOCAL_TEST_PASSWORD")
        assertNull(account.state.value.user)
        assertNull(vault.read())
        assertFalse(account.state.value.busy)
        service.rejectLogin = false
        account.login("LOCAL_TEST_ACCOUNT", "LOCAL_TEST_PASSWORD")
        service.offlineLogout = true
        account.logout()
        assertNull(account.state.value.user)
        assertNull(vault.read())
        assertTrue(account.state.value.message.orEmpty().contains("本机已退出"))
    }

    @Test
    fun repositoryCoalescesRefreshAndKeepsLastGoodDataOnFailure() = runBlocking {
        val service = LocalService()
        val repository = ContentRepository(KivoApi(service), db, json, ServerClock())
        val results = coroutineScope {
            List(8) { async(Dispatchers.Default) { repository.news(true).last() } }.awaitAll()
        }
        assertEquals(1, service.publicCalls.get())
        assertTrue(results.all { it.value?.single()?.title == "本地测试资讯" })
        service.failPublic = true
        val offline = repository.news(true).toList()
        assertEquals("本地测试资讯", offline.last().value?.single()?.title)
        assertNotNull(offline.last().error)
        assertTrue(offline.last().fromCache)
        assertEquals(2, service.publicCalls.get())
    }

    @Test
    fun characterWritesRequireSessionAndDoNotRepeatUncertainMutation() = runBlocking {
        val service = LocalService()
        val account = AccountRepository(service, SessionVault(context, json), ServerClock())
        val uuid = "3c0d38ac-d9d1-4ca7-9703-f9650c6f6e66"
        assertEquals(
            401,
            (runCatching { account.declare(uuid, 3) }.exceptionOrNull() as ApiFailure).httpCode,
        )
        assertEquals(0, service.declareCalls)
        account.login("LOCAL_TEST_ACCOUNT", "LOCAL_TEST_PASSWORD")
        service.uncertainWrite = true
        assertTrue(runCatching { account.declare(uuid, 3) }.exceptionOrNull() is IOException)
        assertEquals(1, service.declareCalls)
        assertTrue(account.declarations(uuid)["selected"]!!.jsonPrimitive.boolean)
        service.uncertainWrite = false
        account.declare(uuid, 3)
        assertEquals(2, service.declareCalls)
        assertFalse(account.declarations(uuid)["selected"]!!.jsonPrimitive.boolean)
        account.supplement(uuid, "::: center\n\n本地测试\n\n:::")
        assertEquals(1, service.supplementCalls)
        service.expired = true
        assertTrue(runCatching { account.declare(uuid, 3) }.exceptionOrNull() is ApiFailure)
        assertNull(account.state.value.user)
        assertNull(SessionVault(context, json).read())
    }

    @Test
    fun settingsPersistAndInvalidValuesAreGuarded() = runBlocking {
        val settings = SettingsRepository(context)
        settings.setTheme(ThemeMode.DARK)
        settings.setServer(GameServer.CN)
        settings.setScale(1.3f)
        settings.setFlag("compact_catalog", true)
        settings.setFlag("character_background", false)
        val restored = SettingsRepository(context).settings.first()
        assertEquals(ThemeMode.DARK, restored.theme)
        assertEquals(GameServer.CN, restored.server)
        assertEquals(1.3f, restored.readingScale)
        assertTrue(restored.compactCatalog)
        assertFalse(restored.showCharacterBackground)
        assertNotNull(runCatching { settings.setScale(Float.NaN) }.exceptionOrNull())
        assertNotNull(runCatching { settings.setFlag("unexpected", true) }.exceptionOrNull())
        settings.setTheme(ThemeMode.SYSTEM)
        settings.setServer(GameServer.JP)
        settings.setScale(1f)
        settings.setFlag("compact_catalog", false)
        settings.setFlag("character_background", true)
    }
}

private class LocalService : KivoService {
    var declareCalls = 0
    var supplementCalls = 0
    var uncertainWrite = false
    var selected = false

    override suspend fun supplement(
        uuid: String,
        access: String,
        body: JsonObject,
    ): Response<JsonObject> {
        supplementCalls++
        assertEquals("Bearer LOCAL_TEST_ACCESS_NEVER_VALID", access)
        assertTrue(body.text("content").contains("::: center"))
        return ok("null")
    }

    override suspend fun declarations(uuid: String, access: String?): Response<JsonObject> =
        ok("{\"selected\":$selected}")

    override suspend fun declare(
        uuid: String,
        access: String,
        body: JsonObject,
    ): Response<JsonObject> {
        declareCalls++
        assertEquals("Bearer LOCAL_TEST_ACCESS_NEVER_VALID", access)
        assertEquals(3, body["icon_id"]!!.jsonPrimitive.int)
        if (expired) throw ApiFailure(401, null, "本地测试过期")
        selected = !selected
        if (uncertainWrite) throw IOException("本地测试：服务端已处理但响应丢失")
        return ok("null")
    }

    override suspend fun article(id: Int, access: String): Response<JsonObject> =
        error("Unexpected article")

    var expired = false
    var rejectLogin = false
    var offlineLogout = false
    var failPublic = false
    var proofWasValid = false
    val publicCalls = AtomicInteger()

    private fun ok(data: String) =
        Response.success(
            Json.parseToJsonElement(
                    """{"success":true,"code":2000,"time":1700000000,"data":$data}"""
                )
                .jsonObject
        )

    override suspend fun publicGet(url: String): Response<JsonObject> {
        publicCalls.incrementAndGet()
        delay(200)
        if (failPublic) throw ApiFailure(403, null, "本地测试网络错误")
        return ok("""{"news":[{"id":1,"title":"本地测试资讯"}],"max_page":1}""")
    }

    override suspend fun challenge(body: JsonObject) = ok("""{"challenge":"LOCAL_TEST_ONLY"}""")

    override suspend fun login(body: JsonObject, proof: Map<String, String>): Response<JsonObject> {
        assertEquals("LOCAL_TEST_ACCOUNT", body.text("account"))
        assertEquals("LOCAL_TEST_PASSWORD", body.text("password"))
        proofWasValid =
            ProofOfWork.hash("LOCAL_TEST_ONLY" + proof["X-PoW-Nonce"]).startsWith("0000")
        if (rejectLogin) throw ApiFailure(401, null, "本地测试拒绝")
        return ok("""{"refresh_token":"LOCAL_TEST_REFRESH_NEVER_VALID"}""")
    }

    override suspend fun accessToken(refresh: String): Response<JsonObject> {
        assertEquals("Bearer LOCAL_TEST_REFRESH_NEVER_VALID", refresh)
        if (expired) throw ApiFailure(200, 4010, "本地测试过期")
        return ok("""{"access_token":"LOCAL_TEST_ACCESS_NEVER_VALID"}""")
    }

    override suspend fun profile(access: String): Response<JsonObject> {
        assertEquals("Bearer LOCAL_TEST_ACCESS_NEVER_VALID", access)
        return ok("""{"id":"test","nickname":"测试老师","avatar":""}""")
    }

    override suspend fun logout(refresh: String): Response<JsonObject> {
        if (offlineLogout) throw IOException("本地测试离线")
        return ok("{}")
    }
}
