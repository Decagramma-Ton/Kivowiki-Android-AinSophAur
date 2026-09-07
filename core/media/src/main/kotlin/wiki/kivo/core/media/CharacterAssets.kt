package wiki.kivo.core.media

import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import wiki.kivo.core.model.CacheLimit
import wiki.kivo.core.model.CharacterMedia

data class AssetProgress(val completed: Int = 0, val total: Int = 0, val bytes: Long = 0)

data class PreparedCharacterMedia(
    val metadata: CharacterMedia,
    val file: File,
    val companion: File?,
    val textures: List<File>,
    val directory: File,
)

/** 重资源独立于百科 JSON 缓存。只下载所选资源依赖，临时文件校验后才成为有效缓存。 */
@Singleton
class CharacterAssets
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    client: OkHttpClient,
) {
    // 资源都是公开只读 GET。独立调度和较长的整体期限适应纹理下载，不改变账号写入策略。
    private val client =
        client
            .newBuilder()
            .retryOnConnectionFailure(true)
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 4
                    maxRequestsPerHost = 3
                }
            )
            .callTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    private val lock = Mutex()
    private val root
        get() = File(context.cacheDir, "character-media").apply { mkdirs() }

    private val maxFile = 96L * 1024 * 1024
    private var protectedDirectory: File? = null
    private var protectedMedia: PreparedCharacterMedia? = null
    private var budget = CacheLimit.GB_1.mediaBytes
    private var appliedLimit: CacheLimit? = null
    // 清理属于应用级维护，不能随弹窗离开而取消；始终与下载共用同一把锁。
    private val maintenance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun setCacheLimit(limit: CacheLimit) = lock.withLock {
        if (appliedLimit == limit) return@withLock
        withContext(Dispatchers.IO) {
            budget = limit.mediaBytes
            prune(null)
            appliedLimit = limit
        }
    }

    fun release(prepared: PreparedCharacterMedia) {
        maintenance.launch {
            lock.withLock {
                // 同一路径重试时可能已有新预览；旧弹窗的释放不能解除新实例的保护。
                if (protectedMedia === prepared) {
                    protectedDirectory = null
                    protectedMedia = null
                }
                prune(null)
            }
        }
    }

    suspend fun prepare(
        media: CharacterMedia,
        progress: (AssetProgress) -> Unit = {},
    ): PreparedCharacterMedia = lock.withLock {
        withContext(Dispatchers.IO) {
            val fingerprint =
                sha256((media.file + media.companion + media.textures.joinToString()).toByteArray())
                    .take(16)
            val directory =
                File(root, "${if(media.isSpine)"spine" else "model"}-${media.id}-$fingerprint")
                    .apply { mkdirs() }
            protectedDirectory = directory
            var ready = false
            try {
                var completed = 0
                var bytes = 0L
                val total = 2 + media.textures.size
                suspend fun get(url: String, file: File): File {
                    download(url, file, maxFile)
                    completed++
                    bytes += file.length()
                    require(bytes <= 192L * 1024 * 1024) { "此素材依赖总量超出移动预览预算" }
                    progress(AssetProgress(completed, total, bytes))
                    return file
                }
                val main =
                    get(
                        media.file,
                        File(
                            directory,
                            if (media.isSpine) "skeleton.skel"
                            else
                                "model.${URI(media.file).path.substringAfterLast('.').lowercase()}",
                        ),
                    )
                val companion =
                    media.companion?.let {
                        get(
                            it,
                            File(directory, if (media.isSpine) "skeleton.atlas" else "model.mtl"),
                        )
                    }
                val textures = mutableListOf<File>()
                if (media.isSpine) {
                    if (companion == null) throw IOException("立绘缺少纹理图集")
                    val pages = AtlasContract.pages(companion.readText())
                    for (page in pages) {
                        val matches =
                            media.textures.filter { basename(it) == page.substringAfterLast('/') }
                        if (matches.size != 1) throw IOException("纹理依赖无法唯一匹配：$page")
                        val file = safeChild(directory, page)
                        file.parentFile?.mkdirs()
                        textures += get(matches.single(), file)
                    }
                    var decodedBytes = 0L
                    textures.forEach { file ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, options)
                        if (options.outWidth <= 0 || options.outHeight <= 0)
                            throw IOException("纹理图片不可读取")
                        decodedBytes += options.outWidth.toLong() * options.outHeight * 4
                    }
                    val lowMemory =
                        context.getSystemService(ActivityManager::class.java).isLowRamDevice
                    if (decodedBytes > (if (lowMemory) 32 else 64) * 1024L * 1024)
                        throw IOException("此素材的纹理超出当前设备预览预算，请查看静态图片或使用性能更高的设备")
                    val version =
                        AtlasContract.skeletonVersion(
                            main.inputStream().use { input ->
                                ByteArray(256).let {
                                    val n = input.read(it)
                                    it.copyOf(n.coerceAtLeast(0))
                                }
                            }
                        )
                    if (!version.startsWith("4.2."))
                        throw IOException("此素材使用 Spine $version，当前预览支持 4.2 素材")
                } else {
                    for (url in media.textures) textures +=
                        get(url, safeChild(directory, basename(url)))
                }
                directory.setLastModified(System.currentTimeMillis())
                progress(AssetProgress(completed, completed, bytes))
                ready = true
                PreparedCharacterMedia(media, main, companion, textures, directory).also {
                    protectedMedia = it
                }
            } finally {
                // 无效或取消的依赖包也参与清理，避免失败下载绕过全局预算。
                if (!ready) protectedDirectory = null
                prune(if (ready) directory else null)
            }
        }
    }

    /** 供图片保存/GLB 外部依赖复用。只有用户触发的查看或保存会调用，不携带账号凭据。 */
    suspend fun fetch(url: String): File = lock.withLock {
        withContext(Dispatchers.IO) {
            val directory = File(root, "downloads").apply { mkdirs() }
            val file =
                File(
                    directory,
                    sha256(url.toByteArray()) + "." + basename(url).substringAfterLast('.', "bin"),
                )
            download(url, file, maxFile)
            prune(file)
            file
        }
    }

    suspend fun invalidate(media: CharacterMedia) = lock.withLock {
        withContext(Dispatchers.IO) {
            val fingerprint =
                sha256((media.file + media.companion + media.textures.joinToString()).toByteArray())
                    .take(16)
            val folder =
                File(root, "${if(media.isSpine)"spine" else "model"}-${media.id}-$fingerprint")
            if (folder.canonicalFile.parentFile == root.canonicalFile) folder.deleteRecursively()
        }
    }

    /** GLB 外部引用也由同一下载器取得；解析和纹理预算检查均在创建 GPU 场景之前完成。 */
    suspend fun model(
        prepared: PreparedCharacterMedia,
        style: Int = 0,
        cell: Int = 60,
    ): GltfPackage {
        val initial = withContext(Dispatchers.Default) { CharacterGltf.read(prepared) }
        require(initial.document.arr("buffers").size + initial.document.arr("images").size <= 96) {
            "模型资源数量超出预览预算"
        }
        val resources = initial.resources.toMutableMap()
        for (entry in initial.document.arr("buffers") + initial.document.arr("images")) {
            val uri = entry.jsonObject.string("uri")
            if (uri.isBlank() || resources.containsKey(uri)) continue
            if (uri.startsWith("data:")) {
                require(
                    uri.length < 96 * 1024 * 1024 && uri.substringBefore(',').endsWith(";base64")
                ) {
                    "模型内嵌资源格式不支持"
                }
                resources[uri] = java.util.Base64.getDecoder().decode(uri.substringAfter(','))
            } else {
                val matched =
                    prepared.textures.singleOrNull { it.name == uri.substringAfterLast('/') }
                resources[uri] =
                    if (matched != null) withContext(Dispatchers.IO) { matched.readBytes() }
                    else {
                        require(
                            !uri.startsWith('/') &&
                                !uri.contains('\\') &&
                                !uri.split('/').contains("..")
                        ) {
                            "模型依赖路径不合法"
                        }
                        val url = URI(prepared.metadata.file).resolve(uri).toString()
                        withContext(Dispatchers.IO) { fetch(url).readBytes() }
                    }
            }
        }
        return withContext(Dispatchers.Default) {
            require(resources.values.sumOf { it.size.toLong() } <= 128L * 1024 * 1024) {
                "模型解包后超出移动预览预算"
            }
            var pixels = 0L
            for (entry in initial.document.arr("images")) {
                val image = entry.jsonObject
                val bytes =
                    if (image["bufferView"] != null) {
                        val view =
                            initial.document.arr("bufferViews")[image.int("bufferView")].jsonObject
                        val buffer = initial.document.arr("buffers")[view.int("buffer")].jsonObject
                        val source = resources[buffer.string("uri")] ?: error("缺少图片缓冲")
                        val offset = view.int("byteOffset")
                        val end = offset.toLong() + view.int("byteLength")
                        require(offset >= 0 && end <= source.size)
                        source.copyOfRange(offset, end.toInt())
                    } else resources[image.string("uri")] ?: continue
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth > 0 && options.outHeight > 0)
                    pixels += options.outWidth.toLong() * options.outHeight * 4
            }
            require(pixels <= 64L * 1024 * 1024) { "模型纹理超出移动预览预算，请使用静态图片" }
            val base = CharacterGltf.unlit(initial.copy(resources = resources))
            val original =
                context.assets.open("character/mouth/style-${style.coerceIn(0,3)}.png").use {
                    it.readBytes()
                }
            // 网站第四套为 WebP，统一转成 PNG 后交给 glTF，避免依赖未声明的 WebP 扩展。
            val mouth =
                if (style == 3) {
                    val bitmap =
                        BitmapFactory.decodeByteArray(original, 0, original.size)
                            ?: error("嘴型贴图无法读取")
                    try {
                        java.io.ByteArrayOutputStream().use { output ->
                            check(
                                bitmap.compress(
                                    android.graphics.Bitmap.CompressFormat.PNG,
                                    100,
                                    output,
                                )
                            )
                            output.toByteArray()
                        }
                    } finally {
                        bitmap.recycle()
                    }
                } else original
            CharacterGltf.mouth(base, mouth, cell)
        }
    }

    private suspend fun download(url: String, destination: File, limit: Long) {
        repeat(3) { attempt ->
            try {
                downloadOnce(url, destination, limit)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                if (
                    failure is PermanentAssetFailure ||
                        failure is javax.net.ssl.SSLException ||
                        attempt == 2
                )
                    throw failure
                delay(700L shl attempt)
            }
        }
    }

    private class PermanentAssetFailure(message: String) : IOException(message)

    private suspend fun downloadOnce(url: String, destination: File, limit: Long) {
        requireTrusted(url)
        if (destination.exists() && destination.length() in 1..limit) {
            destination.setLastModified(System.currentTimeMillis())
            return
        }
        val temporary =
            File(destination.parentFile, "${destination.name}.${UUID.randomUUID()}.part")
        val call = client.newCall(Request.Builder().url(url).build())
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        temporary.delete()
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use { r ->
                                if (!r.isSuccessful) {
                                    val message = "资源读取失败（${r.code}）"
                                    if (r.code !in listOf(408, 429, 500, 502, 503, 504))
                                        throw PermanentAssetFailure(message)
                                    val retryAfter = r.header("Retry-After")?.toLongOrNull()
                                    if (retryAfter != null && retryAfter > 2)
                                        throw PermanentAssetFailure("资源服务繁忙，请在 ${retryAfter} 秒后重试")
                                    throw IOException(message)
                                }
                                val body = r.body ?: throw IOException("资源为空")
                                val expected = body.contentLength()
                                if (expected > limit) throw PermanentAssetFailure("资源文件超出大小限制")
                                var count = 0L
                                body.byteStream().use { input ->
                                    temporary.outputStream().use { output ->
                                        val buffer = ByteArray(32 * 1024)
                                        while (true) {
                                            if (!continuation.isActive) throw IOException("下载已取消")
                                            val n = input.read(buffer)
                                            if (n < 0) break
                                            count += n
                                            if (count > limit)
                                                throw PermanentAssetFailure("资源文件超出大小限制")
                                            output.write(buffer, 0, n)
                                        }
                                    }
                                }
                                if (count == 0L || (expected >= 0 && count != expected))
                                    throw IOException("资源下载不完整")
                                if (!temporary.renameTo(destination))
                                    throw PermanentAssetFailure("暂存资源无法保存")
                            }
                            if (continuation.isActive) continuation.resume(Unit)
                        } catch (e: Exception) {
                            temporary.delete()
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }
            )
        }
    }

    private fun prune(active: File?) {
        MediaCacheEviction.trim(root, budget, setOfNotNull(active, protectedDirectory))
    }

    suspend fun cacheBytes() =
        withContext(Dispatchers.IO) {
            root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }

    suspend fun clear() = lock.withLock {
        withContext(Dispatchers.IO) {
            protectedDirectory = null
            protectedMedia = null
            root
                .listFiles()
                .orEmpty()
                .filter { it.canonicalFile.parentFile == root.canonicalFile }
                .forEach { it.deleteRecursively() }
        }
    }

    companion object {
        fun sha256(bytes: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }

        fun basename(url: String) =
            URLDecoder.decode(URI(url).rawPath.substringAfterLast('/'), "UTF-8").also {
                require(it.isNotBlank() && !it.contains('/') && !it.contains('\\'))
            }

        fun requireTrusted(url: String) {
            val uri = URI(url)
            require(
                uri.scheme == "https" &&
                    uri.userInfo == null &&
                    (uri.host == "kivo.wiki" || uri.host?.endsWith(".kivo.wiki") == true)
            ) {
                "此资源不在古书馆资源域名内"
            }
        }

        fun safeChild(directory: File, name: String): File {
            require(
                !name.contains('\\') &&
                    !name.contains(':') &&
                    !name.startsWith('/') &&
                    name.split('/').none { it == ".." || it == "." || it.isBlank() }
            ) {
                "资源路径不合法"
            }
            val file = File(directory, name)
            require(file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) {
                "资源路径越界"
            }
            return file
        }
    }
}

/** Atlas 页按文档中的空行分隔，绝不依赖 API images 数组顺序。 */
object AtlasContract {
    fun pages(atlas: String): List<String> {
        val result = mutableListOf<String>()
        var page = true
        for (line in atlas.lineSequence()) {
            if (line.isBlank()) {
                page = true
                continue
            }
            if (page) {
                val name = line.trim()
                require(
                    name.endsWith(".png", true) ||
                        name.endsWith(".webp", true) ||
                        name.endsWith(".jpg", true)
                ) {
                    "纹理图集格式无效"
                }
                require(name !in result) { "纹理页重名" }
                result += name
                page = false
            }
        }
        require(result.isNotEmpty()) { "纹理图集没有图片页" }
        return result
    }

    fun skeletonVersion(header: ByteArray) =
        Regex("[34]\\.[0-9]+\\.[0-9]+").find(String(header, Charsets.ISO_8859_1))?.value ?: "未知版本"
}
