package wiki.kivo.core.media

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.*
import java.io.File
import kotlin.math.*
import kotlinx.coroutines.*

/** 长图按当前视口解码，初始缩略图有像素上限；放大不会解码整张超大原画。 */
class ArchiveImageView(context: Context) : View(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var decoder: BitmapRegionDecoder? = null
    private var preview: Bitmap? = null
    private var tile: Bitmap? = null
    private var region = Rect()
    private var sourceWidth = 1
    private var sourceHeight = 1
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var oldX = 0f
    private var oldY = 0f
    private var tileJob: Job? = null
    private var loadJob: Job? = null
    private var animation: Drawable? = null
    @Suppress("DEPRECATION") private var legacyMovie: Movie? = null
    private var movieTime = 0L
    private var movieLastFrame = 0L
    var onReady: ((Boolean) -> Unit)? = null
    var playing = true
        set(value) {
            field = value
            updateAnimation()
        }

    var active = true
        set(value) {
            field = value
            updateAnimation()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var onFailure: ((String) -> Unit)? = null
    private val pinch =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(d: ScaleGestureDetector): Boolean {
                    zoomBy(d.scaleFactor)
                    return true
                }
            },
        )
    private val taps =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (zoom > 1.1f) resetCamera() else zoomBy(3f)
                    return true
                }
            },
        )

    init {
        contentDescription = "图片预览，双击放大或还原，双指缩放，单指移动"
    }

    fun load(file: File) {
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                // 正文中的 GIF 在普通列表只显示封面，全屏后才解码动画。
                // Android 9+ 使用系统逐帧解码器；8.x 使用 Movie，先限制源帧像素防止大 GIF 撑满堆。
                val header =
                    withContext(Dispatchers.IO) {
                        file.inputStream().use { input -> ByteArray(6).also { input.read(it) } }
                    }
                if (String(header, Charsets.US_ASCII).startsWith("GIF8")) {
                    loadAnimation(file)
                    return@launch
                }
                var next: BitmapRegionDecoder? = null
                var bitmap: Bitmap? = null
                try {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        val d =
                            BitmapRegionDecoder.newInstance(file.path, false) ?: error("图片格式暂不支持")
                        next = d
                        var sample = 1
                        while (max(d.width, d.height) / sample > 1600) sample *= 2
                        bitmap =
                            d.decodeRegion(
                                Rect(0, 0, d.width, d.height),
                                BitmapFactory.Options().apply { inSampleSize = sample },
                            ) ?: error("无法解码图片")
                    }
                    decoder?.recycle()
                    preview?.recycle()
                    tile?.recycle()
                    tile = null
                    decoder = next
                    next = null
                    preview = bitmap
                    bitmap = null
                    sourceWidth = decoder!!.width
                    sourceHeight = decoder!!.height
                    resetCamera()
                    onReady?.invoke(false)
                } finally {
                    next?.recycle()
                    bitmap?.recycle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure?.invoke("图片加载失败：${e.message}")
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun loadAnimation(file: File) {
        var next: Drawable? = null
        var movie: Movie? = null
        var originalWidth = 1
        var originalHeight = 1
        withContext(Dispatchers.IO) {
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, dimensions)
            require(dimensions.outWidth > 0 && dimensions.outHeight > 0) { "动图尺寸无效" }
            require(file.length() <= 32L * 1024 * 1024) { "动图文件超出移动预览预算" }
            if (Build.VERSION.SDK_INT >= 28) {
                next =
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _
                        ->
                        val ratio =
                            (1600f / max(info.size.width, info.size.height)).coerceAtMost(1f)
                        decoder.setTargetSize(
                            (info.size.width * ratio).toInt().coerceAtLeast(1),
                            (info.size.height * ratio).toInt().coerceAtLeast(1),
                        )
                    }
                originalWidth = next!!.intrinsicWidth
                originalHeight = next!!.intrinsicHeight
            } else {
                require(
                    dimensions.outWidth.toLong() * dimensions.outHeight * 4 <= 16L * 1024 * 1024
                ) {
                    "此动图超出当前设备的解码预算"
                }
                movie = Movie.decodeFile(file.path) ?: error("无法解码动图")
                originalWidth = movie!!.width()
                originalHeight = movie!!.height()
            }
        }
        releaseAnimation()
        decoder?.let { synchronized(it) { it.recycle() } }
        decoder = null
        preview?.recycle()
        preview = null
        tile?.recycle()
        tile = null
        animation = next
        legacyMovie = movie
        sourceWidth = originalWidth
        sourceHeight = originalHeight
        next?.apply {
            callback = this@ArchiveImageView
            setBounds(0, 0, sourceWidth, sourceHeight)
        }
        resetCamera()
        updateAnimation()
        onReady?.invoke(true)
    }

    private fun updateAnimation() {
        movieLastFrame = 0L
        if (Build.VERSION.SDK_INT >= 28)
            (animation as? AnimatedImageDrawable)?.let {
                if (active && playing) it.start() else it.stop()
            }
        invalidate()
    }

    private fun releaseAnimation() {
        if (Build.VERSION.SDK_INT >= 28) (animation as? AnimatedImageDrawable)?.stop()
        animation?.let {
            unscheduleDrawable(it)
            it.callback = null
        }
        animation = null
        legacyMovie = null
        movieTime = 0
        movieLastFrame = 0
    }

    override fun verifyDrawable(who: Drawable) = who === animation || super.verifyDrawable(who)

    private fun scale() = min(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight) * zoom

    private fun imageRect(): RectF {
        val s = scale()
        return RectF(
            (width - sourceWidth * s) / 2 + panX,
            (height - sourceHeight * s) / 2 + panY,
            (width + sourceWidth * s) / 2 + panX,
            (height + sourceHeight * s) / 2 + panY,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val checkpoint = canvas.save()
        canvas.clipRect(0, 0, width, height)
        val destination = imageRect()
        if (animation != null || legacyMovie != null) {
            canvas.save()
            canvas.translate(destination.left, destination.top)
            canvas.scale(scale(), scale())
            animation?.draw(canvas)
            legacyMovie?.let { movie ->
                val now = SystemClock.uptimeMillis()
                if (active && playing && movieLastFrame != 0L)
                    movieTime += (now - movieLastFrame).coerceAtMost(100)
                movieLastFrame = now
                val duration = movie.duration().takeIf { it > 0 } ?: 1000
                movie.setTime((movieTime % duration).toInt())
                movie.draw(canvas, 0f, 0f)
                if (active && playing) postInvalidateDelayed(33)
            }
            canvas.restore()
        }
        preview?.let { canvas.drawBitmap(it, null, destination, paint) }
        tile?.let {
            val s = scale()
            canvas.drawBitmap(
                it,
                null,
                RectF(
                    destination.left + region.left * s,
                    destination.top + region.top * s,
                    destination.left + region.right * s,
                    destination.top + region.bottom * s,
                ),
                paint,
            )
        }
        canvas.restoreToCount(checkpoint)
    }

    fun resetCamera() {
        zoom = 1f
        panX = 0f
        panY = 0f
        invalidate()
        updateTile()
    }

    fun zoomBy(factor: Float) {
        zoom = (zoom * factor).coerceIn(1f, 32f)
        invalidate()
        updateTile()
    }

    private fun updateTile() {
        tileJob?.cancel()
        if (decoder == null || width == 0 || height == 0) return
        tileJob = scope.launch {
            delay(70)
            val d = decoder ?: return@launch
            val rect = imageRect()
            val s = scale()
            if (s <= 0) return@launch
            val area =
                Rect(
                    ((-rect.left) / s).toInt().coerceIn(0, sourceWidth - 1),
                    ((-rect.top) / s).toInt().coerceIn(0, sourceHeight - 1),
                    ceil((width - rect.left) / s).toInt().coerceIn(1, sourceWidth),
                    ceil((height - rect.top) / s).toInt().coerceIn(1, sourceHeight),
                )
            if (area.isEmpty) return@launch
            var sample = 1
            while (
                area.width().toLong() * area.height() / (sample.toLong() * sample) > 3_000_000 ||
                    1f / (sample * 2) >= s
            ) sample *= 2
            var next: Bitmap? = null
            try {
                withContext(Dispatchers.IO) {
                    synchronized(d) {
                        if (!d.isRecycled)
                            next =
                                d.decodeRegion(
                                    area,
                                    BitmapFactory.Options().apply { inSampleSize = sample },
                                )
                    }
                }
                if (next != null) {
                    tile?.recycle()
                    tile = next
                    next = null
                    region = area
                    invalidate()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure?.invoke("此区域暂时无法放大：${e.message}")
            } finally {
                next?.recycle()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTile()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        taps.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                oldX = event.x
                oldY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pinch.isInProgress && event.pointerCount == 1) {
                    panX += event.x - oldX
                    panY += event.y - oldY
                    invalidate()
                    updateTile()
                }
                oldX = event.x
                oldY = event.y
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        active = false
        releaseAnimation()
        scope.cancel()
        decoder?.let { synchronized(it) { it.recycle() } }
        decoder = null
        preview?.recycle()
        preview = null
        tile?.recycle()
        tile = null
        super.onDetachedFromWindow()
    }
}
