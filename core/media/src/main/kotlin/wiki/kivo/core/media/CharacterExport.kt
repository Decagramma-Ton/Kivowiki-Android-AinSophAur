package wiki.kivo.core.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import java.io.File
import kotlinx.coroutines.*

/** 导出走系统文件选择器，不申请整盘读写权限，不进入共享相册扫描其他文件。 */
object CharacterExport {
    private data class EncodingPlan(val codec: String, val width: Int, val height: Int)

    /** 先按设备宣告的尺寸、帧率与 YUV 输入能力选编码器；窄能力机型逐档回退，绝不拉伸画面。 */
    private fun encodingPlan(width: Int, height: Int): EncodingPlan {
        val encoders =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter {
                it.isEncoder && MediaFormat.MIMETYPE_VIDEO_AVC in it.supportedTypes
            }
        for (edge in listOf(1920, 1080, 720, 480)) {
            val scale = (edge.toFloat() / maxOf(width, height)).coerceAtMost(1f)
            for (encoder in encoders) {
                val caps =
                    runCatching { encoder.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) }
                        .getOrNull() ?: continue
                if (
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible !in
                        caps.colorFormats
                )
                    continue
                val video = caps.videoCapabilities ?: continue
                val wAlign = maxOf(2, video.widthAlignment)
                val hAlign = maxOf(2, video.heightAlignment)
                val w = ((width * scale).toInt() / wAlign * wAlign).coerceAtLeast(wAlign)
                val h = ((height * scale).toInt() / hAlign * hAlign).coerceAtLeast(hAlign)
                if (video.areSizeAndRateSupported(w, h, 24.0))
                    return EncodingPlan(encoder.name, w, h)
            }
        }
        error("此设备没有兼容的 AVC 视频编码器，请保存 PNG 图片")
    }

    suspend fun videoDimensions(file: File): String =
        withContext(Dispatchers.IO) {
            MediaMetadataRetriever().use { reader ->
                reader.setDataSource(file.path)
                "${reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)} × ${reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)}"
            }
        }

    suspend fun copy(resolver: ContentResolver, destination: Uri, source: File) =
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(destination, "w")?.use { output ->
                source.inputStream().use { input ->
                    val bytes = ByteArray(32768)
                    while (true) {
                        ensureActive()
                        val n = input.read(bytes)
                        if (n < 0) break
                        output.write(bytes, 0, n)
                    }
                }
            } ?: error("无法写入所选位置")
        }

    suspend fun png(resolver: ContentResolver, destination: Uri, bitmap: Bitmap) =
        withContext(Dispatchers.IO) {
            try {
                resolver.openOutputStream(destination, "w")?.use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "图片编码失败" }
                } ?: error("无法写入所选位置")
            } finally {
                bitmap.recycle()
            }
        }

    /**
     * 有界的无声预览录像：最长 10 秒、24fps、原始渲染尺寸（设备编码器范围内）。取消或失败必删本应用临时文件。 Canvas / TextureView 取帧在主线程，编码及写盘在
     * IO 线程；不把编码工作压到 UI。
     */
    suspend fun video(
        file: File,
        seconds: Int = 5,
        snapshot: suspend () -> Bitmap,
        progress: (Float) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            require(seconds in 1..10)
            var codec: MediaCodec? = null
            var muxer: MediaMuxer? = null
            var started = false
            var success = false
            var firstFrame: Bitmap? = null
            try {
                val first =
                    withContext(Dispatchers.Main.immediate) { snapshot() }.also { firstFrame = it }
                val plan = encodingPlan(first.width, first.height)
                val width = plan.width
                val height = plan.height

                val format =
                    MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
                        .apply {
                            setInteger(
                                MediaFormat.KEY_COLOR_FORMAT,
                                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                            )
                            setInteger(
                                MediaFormat.KEY_BIT_RATE,
                                (width * height * 6).coerceIn(3_000_000, 20_000_000),
                            )
                            setInteger(MediaFormat.KEY_FRAME_RATE, 24)
                            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                        }
                val encoder =
                    MediaCodec.createByCodecName(plan.codec).also {
                        codec = it
                        it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        it.start()
                    }
                val output =
                    MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also {
                        muxer = it
                    }
                val info = MediaCodec.BufferInfo()
                var track = -1
                var eos = false
                fun drain(wait: Boolean = false) {
                    while (true) {
                        val index = encoder.dequeueOutputBuffer(info, if (wait) 10_000 else 0)
                        when {
                            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                check(!started)
                                track = output.addTrack(encoder.outputFormat)
                                output.start()
                                started = true
                            }
                            index >= 0 -> {
                                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0)
                                    info.size = 0
                                if (info.size > 0) {
                                    check(started)
                                    val buffer = requireNotNull(encoder.getOutputBuffer(index))
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    output.writeSampleData(track, buffer, info)
                                }
                                eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                encoder.releaseOutputBuffer(index, false)
                                if (eos) return
                            }
                            else -> return
                        }
                    }
                }
                val frames = seconds * 24
                val pixels = IntArray(width * height)
                for (frame in 0 until frames) {
                    ensureActive()
                    val start = System.nanoTime()
                    var index = encoder.dequeueInputBuffer(10_000)
                    while (index < 0) {
                        ensureActive()
                        drain()
                        index = encoder.dequeueInputBuffer(10_000)
                    }
                    val image = requireNotNull(encoder.getInputImage(index)) { "设备编码器不提供兼容的图像输入" }
                    val original =
                        if (frame == 0) requireNotNull(firstFrame).also { firstFrame = null }
                        else withContext(Dispatchers.Main.immediate) { snapshot() }
                    val bitmap = Bitmap.createScaledBitmap(original, width, height, true)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    if (bitmap !== original) bitmap.recycle()
                    original.recycle()
                    val planes = image.planes
                    for (y in 0 until height) for (x in 0 until width) {
                        val c = pixels[y * width + x]
                        val r = c shr 16 and 255
                        val g = c shr 8 and 255
                        val b = c and 255
                        planes[0]
                            .buffer
                            .put(
                                y * planes[0].rowStride + x * planes[0].pixelStride,
                                (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
                                    .coerceIn(0, 255)
                                    .toByte(),
                            )
                        if (x % 2 == 0 && y % 2 == 0) {
                            planes[1]
                                .buffer
                                .put(
                                    y / 2 * planes[1].rowStride + x / 2 * planes[1].pixelStride,
                                    (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)
                                        .coerceIn(0, 255)
                                        .toByte(),
                                )
                            planes[2]
                                .buffer
                                .put(
                                    y / 2 * planes[2].rowStride + x / 2 * planes[2].pixelStride,
                                    (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)
                                        .coerceIn(0, 255)
                                        .toByte(),
                                )
                        }
                    }
                    image.close()
                    encoder.queueInputBuffer(
                        index,
                        0,
                        width * height * 3 / 2,
                        frame * 1_000_000L / 24,
                        0,
                    )
                    drain()
                    withContext(Dispatchers.Main.immediate) { progress((frame + 1f) / frames) }
                    delay(
                        ((1_000_000_000L / 24 - (System.nanoTime() - start)) / 1_000_000)
                            .coerceAtLeast(0)
                    )
                }
                var index = encoder.dequeueInputBuffer(10_000)
                while (index < 0) {
                    ensureActive()
                    drain()
                    index = encoder.dequeueInputBuffer(10_000)
                }
                encoder.queueInputBuffer(
                    index,
                    0,
                    0,
                    seconds * 1_000_000L,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                withTimeout(10_000) {
                    while (!eos) {
                        ensureActive()
                        drain(true)
                    }
                }
                success = true
                file
            } finally {
                firstFrame?.recycle()
                runCatching { codec?.stop() }
                codec?.release()
                if (started) runCatching { muxer?.stop() }
                muxer?.release()
                if (!success) file.delete()
            }
        }
}
