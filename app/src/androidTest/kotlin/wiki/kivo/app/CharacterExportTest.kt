package wiki.kivo.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wiki.kivo.core.media.CharacterExport

/** 真正调用设备 AVC 编码器并回读 MP4；只写测试 App 的缓存目录。 */
@RunWith(AndroidJUnit4::class)
class CharacterExportTest {
    @Test
    fun portraitExportKeepsAspectRatioAndCancellationRemovesPartialFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "character-export-contract.mp4")
        var frames = 0
        fun frame() =
            Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (frames++ % 2 == 0) Color.BLUE else Color.GREEN)
            }
        try {
            CharacterExport.video(file, 1, ::frame) {}
            assertTrue(file.length() > 0)
            MediaMetadataRetriever().use { reader ->
                reader.setDataSource(file.path)
                assertEquals(
                    "400",
                    reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                )
                assertEquals(
                    "800",
                    reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                )
                assertTrue(
                    reader
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                        .toLong() in 900..1100
                )
                assertNotNull(reader.getFrameAtTime(300_000)?.also { it.recycle() })
            }
            val cancelled = launch {
                CharacterExport.video(file, 5, ::frame) { if (it > 0.05f) cancel("验证导出取消") }
            }
            cancelled.join()
            assertTrue(cancelled.isCancelled)
            assertFalse(file.exists())
        } finally {
            file.delete()
        }
    }
}
