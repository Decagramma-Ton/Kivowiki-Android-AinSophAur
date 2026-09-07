package wiki.kivo.app

import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import wiki.kivo.core.model.UrlPolicy

/** 对真实公开头像和正文格式使用正式 Coil 管线按缩略图尺寸解码，不以“能打开原图”代替。 */
class ThumbnailReliabilityTest {
    @Test
    fun publicCatalogAndMixedFormatsDecodeAtThumbnailSize() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw =
            withContext(Dispatchers.IO) {
                OkHttpClient()
                    .newCall(
                        Request.Builder()
                            .url("https://api.kivo.wiki/api/v1/data/students?page=1&page_size=12")
                            .build()
                    )
                    .execute()
                    .use {
                        check(it.isSuccessful)
                        requireNotNull(it.body).string()
                    }
            }
        val rows = JSONObject(raw).getJSONObject("data").getJSONArray("students")
        val urls =
            (0 until rows.length()).mapNotNull {
                UrlPolicy.resource(rows.getJSONObject(it).optString("avatar"))
            } +
                listOf(
                    "https://static.kivo.wiki/files/19083/lgj4cn7FML4efZeQXT3qrdc0b6jy61g0.png",
                    "https://static.kivo.wiki/files/1348/MfplMF2UQ7cXIODteikD4o7FJKfM3GAo.jpg",
                    "https://static.kivo.wiki/images/students/龙华%20妃咲/original/gallery/相关图像/kisaki_ex.gif",
                )
        assertTrue(urls.size >= 12)
        val permits = Semaphore(4)
        val records =
            urls
                .map { url ->
                    async {
                        permits.withPermit {
                            val result =
                                SingletonImageLoader.get(context)
                                    .execute(
                                        ImageRequest.Builder(context).data(url).size(96, 96).build()
                                    )
                            if (result is SuccessResult) "OK $url"
                            else "FAIL $url ${(result as ErrorResult).throwable}"
                        }
                    }
                }
                .awaitAll()
        File(context.getExternalFilesDir(null), "mobile-regression")
            .apply { mkdirs() }
            .resolve("thumbnails.txt")
            .writeText(records.joinToString("\n"))
        assertTrue(
            records.filter { it.startsWith("FAIL") }.joinToString("\n"),
            records.none { it.startsWith("FAIL") },
        )
    }
}
