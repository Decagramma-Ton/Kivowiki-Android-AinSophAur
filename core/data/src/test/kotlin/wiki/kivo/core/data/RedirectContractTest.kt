package wiki.kivo.core.data

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import wiki.kivo.core.data.network.SameOriginRedirects

class RedirectContractTest {
    private fun client() =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(SameOriginRedirects())
            .build()

    @Test
    fun trailingSlashRedirectKeepsQuery() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(301).setHeader("Location", "/news/?page=1")
            )
            server.enqueue(MockResponse().setBody("ok"))
            client()
                .newCall(Request.Builder().url(server.url("/news?page=1")).build())
                .execute()
                .use { assertEquals("ok", it.body!!.string()) }
            server.takeRequest()
            assertEquals("/news/?page=1", server.takeRequest().path)
        }
    }

    @Test
    fun redirectCannotSendCredentialsToAnotherOrigin() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(307)
                    .setHeader("Location", "https://other.example/auth")
            )
            client()
                .newCall(
                    Request.Builder()
                        .url(server.url("/auth"))
                        .header("Authorization", "Bearer LOCAL_TEST_ONLY")
                        .build()
                )
                .execute()
                .use { assertEquals(307, it.code) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun authRedirectPreservesPostAndDoesNotSilentlyTurnItIntoGet() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "/login/"))
            server.enqueue(MockResponse().setBody("ok"))
            val request =
                Request.Builder()
                    .url(server.url("/login"))
                    .post("LOCAL_TEST_BODY".toRequestBody("application/json".toMediaType()))
                    .build()
            client().newCall(request).execute().close()
            server.takeRequest()
            val redirected = server.takeRequest()
            assertEquals("POST", redirected.method)
            assertEquals("LOCAL_TEST_BODY", redirected.body.readUtf8())
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/login/"))
            client().newCall(request).execute().use { assertEquals(302, it.code) }
            assertEquals(3, server.requestCount)
        }
    }
}
