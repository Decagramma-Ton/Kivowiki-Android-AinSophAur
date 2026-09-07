package wiki.kivo.core.data.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import wiki.kivo.core.model.UserProfile

@Serializable internal data class PersistedSession(val refresh: String, val profile: UserProfile)

/** 只持久化加密刷新令牌；密码与访问令牌不写入磁盘、日志或备份。 */
@Singleton
class SessionVault
@Inject
constructor(@ApplicationContext context: Context, private val json: Json) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "session.v1"))
    private val alias = "kivo.session.aes.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }
            .generateKey()
    }

    internal suspend fun read(): PersistedSession? =
        withContext(Dispatchers.IO) {
            if (!file.baseFile.exists()) return@withContext null
            val bytes = file.readFully()
            require(bytes.size in 29..65536) { "会话文件不可用" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            json.decodeFromString<PersistedSession>(
                cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
            )
        }

    internal suspend fun write(session: PersistedSession) =
        withContext(Dispatchers.IO) {
            val cipher =
                Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.doFinal(json.encodeToString(session).encodeToByteArray())
            val stream = file.startWrite()
            try {
                stream.write(cipher.iv)
                stream.write(encrypted)
                file.finishWrite(stream)
            } catch (failure: Exception) {
                file.failWrite(stream)
                throw failure
            }
        }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
