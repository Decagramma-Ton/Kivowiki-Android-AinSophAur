package wiki.kivo.core.data.account

import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** 按网站公开登录协议完成校验；只在老师主动提交登录时工作，后台不循环计算。 */
object ProofOfWork {
    // 这是网页公开协议的校验盐，不是服务器密钥或用户凭据；变更须同步契约测试。
    private const val PUBLIC_PROTOCOL_SALT =
        "gi7BVezxvvc818vGlF84PCi2bnVks04Lcfp83FHx7TnmuihJqb2pxzIlhoTci40t"

    fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(
            ""
        ) {
            "%02x".format(it)
        }

    fun signature(challenge: String, nonce: String, seconds: Long): String =
        hash(challenge + nonce + seconds + PUBLIC_PROTOCOL_SALT)

    suspend fun solve(
        challenge: String,
        seconds: () -> Long,
        difficulty: Int = 4,
    ): Map<String, String> =
        withTimeout(12_000) {
            withContext(Dispatchers.Default) {
                require(challenge.length in 1..2048 && difficulty in 1..4) { "登录校验格式已变化" }
                val digest = MessageDigest.getInstance("SHA-256")
                var nonce = 0L
                while (true) {
                    if (nonce % 256 == 0L) coroutineContext.ensureActive()
                    val result = digest.digest((challenge + nonce).toByteArray(Charsets.UTF_8))
                    val zeroBytes = difficulty / 2
                    val valid =
                        (0 until zeroBytes).all { result[it].toInt() == 0 } &&
                            (difficulty % 2 == 0 || (result[zeroBytes].toInt() and 0xf0) == 0)
                    if (valid) break
                    check(++nonce <= 10_000_000) { "登录校验用时过长，请重试" }
                }
                val time = seconds()
                mapOf(
                    "X-PoW-Challenge" to challenge,
                    "X-PoW-Nonce" to nonce.toString(),
                    "X-PoW-Client-Time" to time.toString(),
                    "X-PoW-Signature" to signature(challenge, nonce.toString(), time),
                )
            }
        }
}
