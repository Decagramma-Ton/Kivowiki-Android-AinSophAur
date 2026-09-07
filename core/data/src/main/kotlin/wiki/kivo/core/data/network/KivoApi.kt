package wiki.kivo.core.data.network

import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Response
import retrofit2.http.*

/** 账号端点显式列出，禁止从内容链接拼接写接口。来源见 开发状态/API差异.md。 */
interface KivoService {
    @GET suspend fun publicGet(@Url url: String): Response<JsonObject>

    @GET("articles/{id}")
    suspend fun article(
        @Path("id") id: Int,
        @Header("Authorization") access: String,
    ): Response<JsonObject>

    @POST("pow/challenge") suspend fun challenge(@Body body: JsonObject): Response<JsonObject>

    @POST("auth/login")
    suspend fun login(
        @Body body: JsonObject,
        @HeaderMap proof: Map<String, String>,
    ): Response<JsonObject>

    @POST("auth/token/access")
    suspend fun accessToken(@Header("Authorization") refresh: String): Response<JsonObject>

    @GET("user/") suspend fun profile(@Header("Authorization") access: String): Response<JsonObject>

    @GET("auth/logout")
    suspend fun logout(@Header("Authorization") refresh: String): Response<JsonObject>

    // 与网站 declare 组件相同的显式契约；表态只由用户点击触发。
    @GET("interactive/declares/{uuid}")
    suspend fun declarations(
        @Path("uuid") uuid: String,
        @Header("Authorization") access: String?,
    ): Response<JsonObject>

    @POST("interactive/declares/{uuid}")
    suspend fun declare(
        @Path("uuid") uuid: String,
        @Header("Authorization") access: String,
        @Body body: JsonObject,
    ): Response<JsonObject>

    @POST("interactive/supplementarys/{uuid}")
    suspend fun supplement(
        @Path("uuid") uuid: String,
        @Header("Authorization") access: String,
        @Body body: JsonObject,
    ): Response<JsonObject>
}

class ApiFailure(val httpCode: Int, val businessCode: Int?, override val message: String) :
    IOException(message)

data class ApiPayload(val data: JsonObject, val serverTime: Long?, val version: String)

fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

fun JsonObject.number(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull

fun JsonObject.array(key: String): JsonArray =
    when (val value = get(key)) {
        null,
        JsonNull -> JsonArray(emptyList())
        is JsonArray -> value
        else -> throw IOException("资料格式发生变化，请稍后重试")
    }

fun JsonElement.asObject(): JsonObject = this as? JsonObject ?: throw IOException("资料格式发生变化，请稍后重试")

/** 同时验证 HTTP 和业务信封。字段缺失不能静默显示成成功空数据。 */
fun checkedPayload(response: Response<JsonObject>, allowEmptyData: Boolean = false): ApiPayload {
    if (!response.isSuccessful)
        throw ApiFailure(
            response.code(),
            null,
            when (response.code()) {
                401 -> "登录已失效，请重新登录"
                403 -> "当前请求暂时无法访问"
                404 -> "这份资料已移除或暂不可用"
                429 -> "访问较频繁，请稍后再试"
                in 500..599 -> "古书馆暂时没有响应，请稍后重试"
                else -> "请求未完成，请稍后重试"
            },
        )
    val body = response.body() ?: throw IOException("没有收到有效资料")
    val code = body.number("code")?.toInt()
    if (body["success"]?.jsonPrimitive?.booleanOrNull != true || code != 2000) {
        throw ApiFailure(
            response.code(),
            code,
            body.text("message").take(160).ifBlank { "服务暂时未能完成请求" },
        )
    }
    return ApiPayload(
        if (allowEmptyData && body["data"] == JsonNull) JsonObject(emptyMap())
        else body["data"]?.asObject() ?: JsonObject(emptyMap()),
        body.number("time"),
        body.text("version"),
    )
}

fun Throwable.friendlyMessage(): String =
    when (this) {
        is ApiFailure -> message
        is java.net.UnknownHostException -> "暂时无法连接网络，可继续阅读已缓存的内容"
        is java.net.SocketTimeoutException,
        is kotlinx.coroutines.TimeoutCancellationException -> "连接有些慢，请稍后重试"
        is javax.net.ssl.SSLException -> "安全连接未能建立，请检查网络后重试"
        is IOException -> message?.takeIf { it.any { c -> c.code > 127 } } ?: "连接中断，可继续阅读已缓存的内容"
        else -> "这份资料暂时无法读取，请稍后重试"
    }

@Singleton
class KivoApi @Inject constructor(@param:Named("publicRead") private val service: KivoService) {
    private val permits = Semaphore(4)

    fun url(path: String, query: Map<String, String> = emptyMap()): String {
        require(Regex("^[a-zA-Z0-9/_-]+$").matches(path))
        val builder =
            "https://api.kivo.wiki/api/v1/".toHttpUrl().newBuilder().addPathSegments(path.trim('/'))
        query.toSortedMap().forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    /** 应用层只重试公开 GET；底层可恢复失效连接，整次调用最多 30 秒且随协程取消。 */
    suspend fun get(url: String): ApiPayload =
        withTimeout(30_000) {
            var attempt = 0
            while (true) {
                try {
                    val response = permits.withPermit { service.publicGet(url) }
                    val retryable = response.code() == 429 || response.code() in 500..599
                    if (retryable && attempt < 2) {
                        val retryAfter = response.headers()["Retry-After"]?.toLongOrNull()
                        if (retryAfter != null && retryAfter > 10)
                            return@withTimeout checkedPayload(response)
                        delay(retryAfter?.coerceAtLeast(1)?.times(1000) ?: (500L shl attempt))
                        attempt++
                        continue
                    }
                    return@withTimeout checkedPayload(response)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: ApiFailure) {
                    throw failure
                } catch (failure: IOException) {
                    if (failure is javax.net.ssl.SSLException || attempt >= 2) throw failure
                    delay(400L shl attempt++)
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
}
