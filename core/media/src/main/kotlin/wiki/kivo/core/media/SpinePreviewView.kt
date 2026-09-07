package wiki.kivo.core.media

import android.content.Context
import android.graphics.*
import android.view.*
import com.esotericsoftware.spine.BlendMode
import com.esotericsoftware.spine.android.AndroidSkeletonDrawable
import com.esotericsoftware.spine.android.SkeletonRenderer
import com.esotericsoftware.spine.android.bounds.Bounds
import kotlin.math.*
import kotlinx.coroutines.*

/** Canvas 原生骨架视图。调度由宿主可见性驱动，暂停时只在手势或控制变动后重绘。 */
class SpinePreviewView(context: Context) : View(context), Choreographer.FrameCallback {
    private var drawable: AndroidSkeletonDrawable? = null
    private val renderer = SkeletonRenderer()
    private var bounds = Bounds(0.0, 0.0, 1.0, 1.0)
    var onReady: ((List<String>, List<String>) -> Unit)? = null
    var onFailure: ((String) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var scheduled = false
    private var last = 0L
    private var alive = false
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var oldX = 0f
    private var oldY = 0f
    private var originalBlends = emptyList<BlendMode>()
    private var isPortrait = false

    /** Android 9 及以下的硬件 Canvas 不支持本运行时的三角网格，保持软件回退。 */
    var gpuRendering = true
        set(value) {
            field = value
            val layer =
                if (value && android.os.Build.VERSION.SDK_INT >= 29) LAYER_TYPE_NONE
                else LAYER_TYPE_SOFTWARE
            if (layerType != layer) setLayerType(layer, null)
        }

    var fixBlendMode = true
        set(value) {
            if (field == value) return
            field = value
            applyBlendModes()
            invalidate()
        }

    /** 与网站实验选项一致：立绘使用骨架单位原始比例，默认关闭；大厅仍按视口适配。 */
    var experimentalRendering = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private fun applyBlendModes() {
        drawable?.skeletonData?.slots?.forEachIndexed { index, slot ->
            val original = originalBlends[index]
            // 与前台 fixBlendMode 对齐：仅 Additive → Screen，其他混合保持素材原值。
            slot.blendMode =
                if (fixBlendMode && original == BlendMode.additive) BlendMode.screen else original
        }
    }

    var playing = true
        set(value) {
            field = value
            schedule()
        }

    var active = true
        set(value) {
            field = value
            if (!value) stopFrames() else schedule()
        }

    var fill = false
        set(value) {
            field = value
            invalidate()
        }

    var background = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }

    var lowPower = false
    private val pinch =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    zoom = (zoom * detector.scaleFactor).coerceIn(.25f, 8f)
                    invalidate()
                    return true
                }
            },
        )

    init {
        contentDescription = "动态角色预览，双指缩放，单指移动"
        gpuRendering = true
    }

    fun load(prepared: PreparedCharacterMedia) {
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                // 在 IO 线程完成骨架解析与纹理解码；关闭弹窗时取消，晚到结果也必须释放。
                var loaded: AndroidSkeletonDrawable? = null
                try {
                    withContext(Dispatchers.IO) {
                        loaded =
                            AndroidSkeletonDrawable.fromFile(
                                requireNotNull(prepared.companion),
                                prepared.file,
                            )
                    }
                    val value = requireNotNull(loaded)
                    releaseDrawable()
                    drawable = value
                    isPortrait = prepared.metadata.type == "spr"
                    originalBlends = value.skeletonData.slots.map { it.blendMode }
                    applyBlendModes()
                    loaded = null
                    val animations = value.skeletonData.animations.map { it.name }
                    val skins = value.skeletonData.skins.map { it.name }
                    val initial =
                        animations.firstOrNull {
                            it.equals("Idle_01", true) || it.equals("Idle", true)
                        } ?: animations.firstOrNull()
                    if (initial != null) value.animationState.setAnimation(0, initial, true)
                    value.update(0f)
                    fitBounds()
                    onReady?.invoke(animations, skins)
                    schedule()
                    invalidate()
                } finally {
                    loaded?.atlas?.textures?.forEach { it.dispose() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure?.invoke("立绘解析失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun animation(name: String) {
        drawable?.let {
            it.skeleton.setToSetupPose()
            it.animationState.setAnimation(0, name, true)
            it.update(0f)
            // 只更新骨架姿态。bounds 也是镜头的一部分，重算它仍会引发取景跳动。
            // 新素材加载时才适配镜头，用户可通过“复位”主动恢复初始视角。
        }
        last = 0
        invalidate()
        schedule()
    }

    fun skin(name: String) {
        drawable?.let {
            it.skeleton.setSkin(name)
            it.skeleton.setSlotsToSetupPose()
            it.update(0f)
            // 换皮肤沿用同一镜头，避免缩放与主体位置随附件边界改变。
        }
        invalidate()
    }

    private fun fitBounds() {
        drawable?.let {
            bounds = Bounds(it.skeleton)
            if (bounds.width <= 0 || bounds.height <= 0)
                bounds = Bounds(-500.0, -500.0, 1000.0, 1000.0)
        }
        resetCamera()
    }

    fun resetCamera() {
        zoom = 1f
        panX = 0f
        panY = 0f
        invalidate()
    }

    fun zoomBy(factor: Float) {
        zoom = (zoom * factor).coerceIn(.25f, 8f)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        alive = true
        schedule()
    }

    override fun onDetachedFromWindow() {
        alive = false
        stopFrames()
        scope.cancel()
        releaseDrawable()
        super.onDetachedFromWindow()
    }

    private fun releaseDrawable() {
        drawable?.atlas?.textures?.forEach { it.dispose() }
        drawable = null
    }

    private fun stopFrames() {
        Choreographer.getInstance().removeFrameCallback(this)
        scheduled = false
        last = 0
    }

    private fun schedule() {
        if (alive && active && playing && drawable != null && !scheduled) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        } else if (!playing || !active) stopFrames()
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!alive || !active || !playing) return
        val interval = if (lowPower) 33_000_000L else 16_000_000L
        if (last == 0L || frameTimeNanos - last >= interval) {
            drawable?.update(
                if (last == 0L) 0f else ((frameTimeNanos - last) / 1e9f).coerceAtMost(.1f)
            )
            last = frameTimeNanos
            invalidate()
        }
        schedule()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint(canvas, width, height)
    }

    private fun paint(canvas: Canvas, w: Int, h: Int) {
        // Compose 的 AndroidView 不保证裁剪原生 Canvas。先限定视口，再绘制背景和骨架，
        // 防止 drawColor 或大幅动作盖住标题栏与下方控件；导出也使用同一边界。
        val checkpoint = canvas.save()
        canvas.clipRect(0, 0, w, h)
        canvas.drawColor(background)
        val value = drawable
        if (value == null) {
            canvas.restoreToCount(checkpoint)
            return
        }
        val factor =
            (if (experimentalRendering && isPortrait) w.toDouble() / width.coerceAtLeast(1)
                else if (fill) max(w / bounds.width, h / bounds.height)
                else min(w / bounds.width, h / bounds.height))
                .toFloat() * zoom * (if (experimentalRendering && isPortrait) 1f else .94f)
        canvas.save()
        canvas.translate(
            w / 2f + panX * w / width.coerceAtLeast(1),
            h / 2f + panY * h / height.coerceAtLeast(1),
        )
        canvas.scale(factor, -factor)
        canvas.translate(
            (-bounds.x - bounds.width / 2).toFloat(),
            (-bounds.y - bounds.height / 2).toFloat(),
        )
        renderer.renderToCanvas(canvas, renderer.render(value.skeleton))
        canvas.restore()
        canvas.restoreToCount(checkpoint)
    }

    /** 截图与录制复用同一帧渲染路径，不截取应用工具栏，也不需要屏幕录制权限。 */
    fun snapshot(maxEdge: Int = 1440): Bitmap {
        check(drawable != null && width > 0 && height > 0) { "预览尚未就绪" }
        val factor = min(1f, maxEdge.toFloat() / max(width, height))
        return Bitmap.createBitmap(
                max(2, (width * factor).toInt()),
                max(2, (height * factor).toInt()),
                Bitmap.Config.ARGB_8888,
            )
            .also { paint(Canvas(it), it.width, it.height) }
    }

    /** 独立于手机视口、缩放、平移和填充开关的完整骨架导出。 */
    fun fullFrameSource(maxEdge: Int = 4096, seconds: Int = 0): () -> Bitmap {
        val value = requireNotNull(drawable) { "预览尚未就绪" }
        val current = value.animationState.getCurrent(0)
        val skeleton = com.esotericsoftware.spine.Skeleton(value.skeleton)
        val state = com.esotericsoftware.spine.AnimationState(value.animationState.data)
        val start = current?.trackTime ?: 0f
        val entry = current?.let { state.setAnimation(0, it.animation, true) }
        fun pose(time: Float) {
            entry?.trackTime = time
            state.apply(skeleton)
            skeleton.updateWorldTransform(com.esotericsoftware.spine.Skeleton.Physics.pose)
        }
        pose(start)
        var area = Bounds(skeleton)
        // 录像先取所有输出帧边界的并集，再固定镜头；避免动作伸展被裁切或逐帧自动缩放。
        repeat(seconds * 24) { frame ->
            pose(start + frame / 24f)
            val next = Bounds(skeleton)
            val x = min(area.x, next.x)
            val y = min(area.y, next.y)
            area =
                Bounds(
                    x,
                    y,
                    max(area.x + area.width, next.x + next.width) - x,
                    max(area.y + area.height, next.y + next.height) - y,
                )
        }
        check(area.width > 0 && area.height > 0) { "骨架没有可导出的画面" }
        val factor = min(1.0, maxEdge.coerceIn(256, 4096) / (max(area.width, area.height) * 1.04))
        val w = ceil(area.width * factor * 1.04).toInt().coerceAtLeast(2)
        val h = ceil(area.height * factor * 1.04).toInt().coerceAtLeast(2)
        var frame = 0
        return {
            pose(start + if (seconds > 0) frame++ / 24f else 0f)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(background)
                canvas.translate(w / 2f, h / 2f)
                canvas.scale(factor.toFloat(), -factor.toFloat())
                canvas.translate(
                    (-area.x - area.width / 2).toFloat(),
                    (-area.y - area.height / 2).toFloat(),
                )
                renderer.renderToCanvas(canvas, renderer.render(skeleton))
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                oldX = event.x
                oldY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pinch.isInProgress && event.pointerCount == 1) {
                    panX += event.x - oldX
                    panY += event.y - oldY
                    invalidate()
                }
                oldX = event.x
                oldY = event.y
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
