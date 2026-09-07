package wiki.kivo.core.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import wiki.kivo.core.data.network.*
import wiki.kivo.core.model.*

class NetworkContractTest {
    @Test
    fun publicClientRecoversConnectionsWithoutEnablingAccountWriteReplay() = runBlocking {
        val base = DataModule.http()
        assertFalse("账号客户端必须保留不自动补发策略", base.retryOnConnectionFailure)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
            server.enqueue(
                MockResponse().setBody("""{"success":true,"code":2000,"data":{"name":"恢复成功"}}""")
            )
            val api = KivoApi(DataModule.publicService(base, Json { ignoreUnknownKeys = true }))
            assertEquals("恢复成功", api.get(server.url("/read").toString()).data.text("name"))
            assertEquals(2, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))
            try {
                api.get(server.url("/missing").toString())
                fail("不存在的内容不能伪装为成功")
            } catch (expected: ApiFailure) {
                assertEquals(404, expected.httpCode)
            }
            assertEquals("永久错误不应反复请求", 3, server.requestCount)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun payload(raw: String) = json.parseToJsonElement(raw).jsonObject

    @Test
    fun characterWritesKeepExplicitEndpointAndNeverLeakAuthenticationToPublicReads() = runBlocking {
        MockWebServer().use { server ->
            val service =
                Retrofit.Builder()
                    .baseUrl(server.url("/api/v1/"))
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(KivoService::class.java)
            val uuid = "3c0d38ac-d9d1-4ca7-9703-f9650c6f6e66"
            repeat(3) {
                server.enqueue(
                    MockResponse().setBody("""{"success":true,"code":2000,"data":null}""")
                )
            }
            assertTrue(
                checkedPayload(
                        service.declare(
                            uuid,
                            "Bearer LOCAL_TEST_ONLY",
                            buildJsonObject { put("icon_id", 3) },
                        ),
                        true,
                    )
                    .data
                    .isEmpty()
            )
            checkedPayload(
                service.supplement(
                    uuid,
                    "Bearer LOCAL_TEST_ONLY",
                    buildJsonObject { put("content", "::: center\n本地测试\n::: ") },
                ),
                true,
            )
            service.declarations(uuid, null)
            val declare = server.takeRequest()
            assertEquals("POST", declare.method)
            assertEquals("/api/v1/interactive/declares/$uuid", declare.path)
            assertEquals("Bearer LOCAL_TEST_ONLY", declare.getHeader("Authorization"))
            assertEquals(3, payload(declare.body.readUtf8())["icon_id"]!!.jsonPrimitive.int)
            val supplement = server.takeRequest()
            assertEquals("POST", supplement.method)
            assertEquals("/api/v1/interactive/supplementarys/$uuid", supplement.path)
            assertTrue(payload(supplement.body.readUtf8()).text("content").startsWith("::: center"))
            val read = server.takeRequest()
            assertEquals("GET", read.method)
            assertNull(read.getHeader("Authorization"))
        }
    }

    @Test
    fun envelopeRejectsBusinessFailureEvenWithHttp200() {
        val failure = runCatching {
            checkedPayload(
                Response.success(
                    payload("""{"success":false,"code":4010,"message":"过期","data":{}}""")
                )
            )
        }
            .exceptionOrNull()
        assertTrue(failure is ApiFailure)
        assertEquals(4010, (failure as ApiFailure).businessCode)
        assertNotNull(
            runCatching { checkedPayload(Response.success(payload("""{"data":{}}"""))) }
                .exceptionOrNull()
        )
    }

    @Test
    fun publicGetRetriesServerErrorsButNotForbidden() = runBlocking {
        MockWebServer().use { server ->
            val service =
                Retrofit.Builder()
                    .baseUrl(server.url("/"))
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(KivoService::class.java)
            val api = KivoApi(service)
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(
                MockResponse().setBody("""{"success":true,"code":2000,"data":{"ok":1}}""")
            )
            assertEquals(1L, api.get(server.url("/news").toString()).data.number("ok"))
            assertEquals(2, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(403))
            val failure = runCatching { api.get(server.url("/news").toString()) }.exceptionOrNull()
            assertEquals(403, (failure as ApiFailure).httpCode)
            assertEquals(3, server.requestCount)
            repeat(3) { assertNull(server.takeRequest().getHeader("Authorization")) }
        }
    }

    @Test
    fun queryKeysAreCanonicalAndInvalidPathsRejected() {
        val unused = object : KivoService by NoNetworkService() {}
        val api = KivoApi(unused)
        assertEquals(
            api.url("news", linkedMapOf("q" to "白子 & 星野", "page" to "1")),
            api.url("news", linkedMapOf("page" to "1", "q" to "白子 & 星野")),
        )
        assertNotNull(runCatching { api.url("../auth/login") }.exceptionOrNull())
    }

    @Test
    fun actualScheduleAndEquipmentShapesAreSupported() {
        val schedule =
            ContentMapper.schedule(
                payload(
                    """{"start_date":1787634000,"end_date":1788919200,"students":[621,620],"future_field":true}"""
                ),
                "卡池",
            )
        assertEquals(listOf(621, 620), schedule.students)
        assertNull(schedule.banner)
        val detail =
            ContentMapper.detail(
                payload(
                    """{"id":19,"name":"雅典娜3号","info":[{"description":"装备说明","icon":"//static.kivo.wiki/a.png"}]}"""
                ),
                EntityKey(EntityType.EQUIPMENT, 19),
            )
        assertEquals("装备说明", detail.body)
        assertEquals("https://static.kivo.wiki/a.png", detail.card.image)
        assertNotNull(
            runCatching { ContentMapper.cards(payload("""{"news":{}}"""), "news", EntityType.NEWS) }
                .exceptionOrNull()
        )
        assertTrue(
            ContentMapper.cards(
                    payload("""{"max_page":0,"timeline":null}"""),
                    "timeline",
                    EntityType.TIMELINE,
                )
                .entries
                .isEmpty()
        )
        assertNotNull(
            runCatching {
                ContentMapper.cards(payload("""{"max_page":1}"""), "news", EntityType.NEWS)
            }
                .exceptionOrNull()
        )
    }
}

/** 测试里未声明的调用立即失败，任何测试均不会意外访问古书馆生产账号接口。 */
open class NoNetworkService : KivoService {
    override suspend fun supplement(
        uuid: String,
        access: String,
        body: JsonObject,
    ): Response<JsonObject> = error("Unexpected supplement")

    override suspend fun declarations(uuid: String, access: String?): Response<JsonObject> =
        error("Unexpected declarations")

    override suspend fun declare(
        uuid: String,
        access: String,
        body: JsonObject,
    ): Response<JsonObject> = error("Unexpected declare")

    override suspend fun article(id: Int, access: String): Response<JsonObject> =
        error("Unexpected article")

    override suspend fun publicGet(url: String): Response<JsonObject> =
        error("Unexpected network call")

    override suspend fun challenge(body: JsonObject): Response<JsonObject> =
        error("Unexpected challenge")

    override suspend fun login(body: JsonObject, proof: Map<String, String>): Response<JsonObject> =
        error("Unexpected login")

    override suspend fun accessToken(refresh: String): Response<JsonObject> =
        error("Unexpected token refresh")

    override suspend fun profile(access: String): Response<JsonObject> = error("Unexpected profile")

    override suspend fun logout(refresh: String): Response<JsonObject> = error("Unexpected logout")
}
