package wiki.kivo.core.media

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import wiki.kivo.core.model.CacheLimit

class MediaCacheEvictionTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun asset(path: String, bytes: Int, age: Long): File =
        File(temporary.root, path).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(bytes))
            setLastModified(age)
        }

    @Test
    fun evictsOldestWholePackagesAndIndividualDownloads() {
        val old = asset("spine-old/atlas.png", 40, 1).parentFile.apply { setLastModified(1) }
        val download = asset("downloads/old.png", 30, 2)
        val recent = asset("spine-new/atlas.png", 50, 3).parentFile.apply { setLastModified(3) }
        MediaCacheEviction.trim(temporary.root, 60)
        assertFalse(old.exists())
        assertFalse(download.exists())
        assertTrue(File(recent, "atlas.png").exists())
    }

    @Test
    fun protectsActivePackageThenEnforcesLimitOnRelease() {
        val active = asset("spine-active/atlas.png", 100, 1).parentFile
        val unused = asset("downloads/old.png", 20, 2)
        MediaCacheEviction.trim(temporary.root, 50, setOf(active))
        assertTrue(active.exists())
        assertFalse(unused.exists())
        MediaCacheEviction.trim(temporary.root, 50)
        assertFalse(active.exists())
    }

    @Test
    fun unlimitedRetainsFilesAndFiniteBudgetsReserveOtherCaches() {
        val file = asset("downloads/a.png", 100, 1)
        MediaCacheEviction.trim(temporary.root, CacheLimit.UNLIMITED.mediaBytes)
        assertTrue(file.exists())
        CacheLimit.entries
            .filter { it != CacheLimit.UNLIMITED }
            .forEach {
                assertEquals(
                    it.bytes,
                    it.mediaBytes + CacheLimit.IMAGE_BYTES + CacheLimit.CONTENT_BYTES,
                )
                assertTrue(it.mediaBytes >= 192L * 1024 * 1024)
            }
    }
}
