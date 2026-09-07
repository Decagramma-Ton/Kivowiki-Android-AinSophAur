package wiki.kivo.core.data.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Go 路由会把 /news 重定向至 /news/。只跟随同协议、主机和端口的跳转， 确保 Authorization 不跨域、HTTPS 不降级；写请求只接受保留方法与正文的 307/308。
 */
class SameOriginRedirects : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val origin = request.url
        repeat(4) { step ->
            val response = chain.proceed(request)
            if (response.code !in setOf(301, 302, 303, 307, 308)) return response
            val location =
                response.header("Location")?.let { request.url.resolve(it) } ?: return response
            if (
                location.scheme != origin.scheme ||
                    location.host != origin.host ||
                    location.port != origin.port ||
                    location.username.isNotEmpty() ||
                    location.password.isNotEmpty()
            )
                return response
            if (request.method !in setOf("GET", "HEAD") && response.code !in setOf(307, 308))
                return response
            response.close()
            if (step == 3) throw IOException("服务器跳转次数过多，请稍后重试")
            request = request.newBuilder().url(location).build()
        }
        error("Unreachable redirect state")
    }
}
