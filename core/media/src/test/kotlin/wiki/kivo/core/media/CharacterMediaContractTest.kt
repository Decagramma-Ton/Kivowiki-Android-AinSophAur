package wiki.kivo.core.media

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CharacterMediaContractTest {
    @Test
    fun atlasUsesDeclaredPagesAndRejectsDuplicateOrUnsafePaths() {
        assertEquals(
            listOf("first.png", "second.png"),
            AtlasContract.pages(
                "first.png\nsize: 2048,2048\nregion\n xy: 2,2\n\nsecond.png\nsize: 2048,2048\nregion2\n xy: 3,3"
            ),
        )
        assertTrue(
            runCatching { AtlasContract.pages("a.png\nsize: 1,1\n\na.png\nsize: 1,1") }.isFailure
        )
        for (path in listOf("../other", "/root", "a/../../b", "C:\\file", "a\\b")) assertTrue(
            runCatching { CharacterAssets.safeChild(File("cache"), path) }.isFailure
        )
        assertEquals("4.2.33", AtlasContract.skeletonVersion("binary\u00004.2.33".toByteArray()))
    }

    @Test
    fun mediaDoesNotAcceptThirdPartyOrCredentialUrls() {
        for (url in
            listOf(
                "http://static.kivo.wiki/a",
                "https://kivo.wiki.evil.test/a",
                "https://user@static.kivo.wiki/a",
                "file:///a",
            )) assertTrue(runCatching { CharacterAssets.requireTrusted(url) }.isFailure)
        CharacterAssets.requireTrusted("https://static.kivo.wiki/a")
    }

    @Test
    fun glbRejectsTruncationAndKeepsBinaryPayload() {
        val json =
            """{"asset":{"version":"2.0"},"buffers":[{"byteLength":4}],"animations":[{"name":"动作"}]}"""
                .toByteArray()
        val padded = json + ByteArray((4 - json.size % 4) % 4) { 32 }
        val b =
            ByteBuffer.allocate(12 + 8 + padded.size + 8 + 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putInt(0x46546c67)
                    putInt(2)
                    putInt(capacity())
                    putInt(padded.size)
                    putInt(0x4e4f534a)
                    put(padded)
                    putInt(4)
                    putInt(0x004e4942)
                    putInt(123)
                }
                .array()
        val result = CharacterGltf.glb(b)
        assertEquals(1, result.document["animations"]!!.jsonArray.size)
        assertEquals(
            123,
            ByteBuffer.wrap(result.resources.values.single()).order(ByteOrder.LITTLE_ENDIAN).int,
        )
        assertTrue(runCatching { CharacterGltf.glb(b.copyOf(b.size - 1)) }.isFailure)
    }

    @Test
    fun objSupportsNegativeIndicesAndPreservesTexture() {
        val scene =
            CharacterObj.convert(
                "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nf -4/1 -3/2 -2/3 -1/4",
                null,
                mapOf("halo.png" to byteArrayOf(1, 2, 3)),
            )
        assertEquals(
            6,
            scene.document["accessors"]!!.jsonArray[0].jsonObject["count"]!!.jsonPrimitive.int,
        )
        assertTrue(scene.resources.values.any { it.contentEquals(byteArrayOf(1, 2, 3)) })
        assertTrue(
            runCatching { CharacterObj.convert("v 0 0 0\nf 1 2 3", null, emptyMap()) }.isFailure
        )
    }

    @Test
    fun mouthConnectedComponentsDoNotMergeIsolatedEyes() {
        val positions =
            listOf(
                doubleArrayOf(0.0, 0.0, 0.0),
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(1.0, 1.0, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(3.0, 3.0, 0.0),
                doubleArrayOf(4.0, 3.0, 0.0),
                doubleArrayOf(4.0, 4.0, 0.0),
            )
        val groups = CharacterGltf.connectedTriangles(positions, listOf(0, 1, 2, 0, 2, 3, 4, 5, 6))
        assertEquals(listOf(6, 3), groups.map { it.size })
    }
}
