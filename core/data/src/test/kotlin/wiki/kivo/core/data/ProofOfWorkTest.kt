package wiki.kivo.core.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import wiki.kivo.core.data.account.ProofOfWork

class ProofOfWorkTest {
    @Test
    fun sha256UsesUtf8AndFullLengthHex() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ProofOfWork.hash("abc"),
        )
    }

    @Test
    fun challengeProofMatchesPublishedWebsiteProtocol() = runBlocking {
        val proof = ProofOfWork.solve("LOCAL_TEST_ONLY", { 1700000000 })
        assertTrue(
            ProofOfWork.hash("LOCAL_TEST_ONLY" + proof.getValue("X-PoW-Nonce")).startsWith("0000")
        )
        assertEquals("1700000000", proof["X-PoW-Client-Time"])
        assertEquals(
            ProofOfWork.signature("LOCAL_TEST_ONLY", proof.getValue("X-PoW-Nonce"), 1700000000),
            proof["X-PoW-Signature"],
        )
        assertEquals(4, proof.size)
    }

    @Test
    fun changedDifficultyAndEmptyChallengeAreRejected() = runBlocking {
        assertNotNull(runCatching { ProofOfWork.solve("x", { 1 }, 5) }.exceptionOrNull())
        assertNotNull(runCatching { ProofOfWork.solve("", { 1 }) }.exceptionOrNull())
    }
}
