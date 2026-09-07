package wiki.kivo.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.Matrix
import android.view.Choreographer
import android.view.MotionEvent
import android.view.TextureView
import com.google.android.filament.*
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.*
import java.nio.ByteBuffer

/** 单场景 Filament 视图；TextureView 提供手势、原生截图，不引入第二套 3D 引擎。 */
class ModelPreviewView(context: Context) : TextureView(context), Choreographer.FrameCallback {
    private var viewer: ModelViewer? = null
    private var frameExport: ModelFrameExport? = null
    private var pending: GltfPackage? = null
    private var scheduled = false
    private var last = 0L
    private var dirty = 8
    private var elapsed = 0f
    private var selected = 0
    private var manipulator: Manipulator? = null
    private var colorGrading: ColorGrading? = null
    private var animationChosen = false
    private var pendingReady: Pair<List<String>, Boolean>? = null
    var onReady: ((List<String>, Boolean) -> Unit)? = null
    var onFailure: ((String) -> Unit)? = null
    var playing = true
        set(value) {
            field = value
            last = 0
            requestFrame()
        }

    var active = true
        set(value) {
            field = value
            if (!value) stopFrames() else requestFrame()
        }

    var lowPower = false
    var rotate = false
        set(value) {
            field = value
            requestFrame()
        }

    var background = Color.rgb(238, 241, 247)
        set(value) {
            field = value
            viewer?.let { setBackground(it) }
            requestFrame()
        }

    init {
        isOpaque = false
        contentDescription = "角色三维模型，单指旋转，双指缩放或移动"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            Utils.init()
            Gltfio.init()
            manipulator =
                Manipulator.Builder()
                    .targetPosition(0f, 0f, -4f)
                    .viewport(width.coerceAtLeast(1), height.coerceAtLeast(1))
                    .build(Manipulator.Mode.ORBIT)
            viewer =
                ModelViewer(this, manipulator = manipulator).also {
                    it.autoPlayAnimations = false
                    // 游戏已烘焙色彩，线性色调映射保持贴图本色，避免默认电影调色使蓝色偏灰。
                    colorGrading =
                        ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(it.engine)
                    it.view.colorGrading = colorGrading
                    it.cameraFocalLength = 42f
                    setBackground(it)
                }
            pending?.let(::load)
            requestFrame()
        } catch (e: Exception) {
            onFailure?.invoke("当前设备无法创建模型预览：${e.message}")
        } catch (e: LinkageError) {
            onFailure?.invoke("当前设备不支持此原生模型运行时")
        }
    }

    private fun setBackground(v: ModelViewer) {
        val options = v.renderer.clearOptions
        val rgb =
            Colors.toLinear(
                Colors.RgbType.SRGB,
                Color.red(background) / 255f,
                Color.green(background) / 255f,
                Color.blue(background) / 255f,
            )
        options.clear = true
        options.clearColor =
            doubleArrayOf(
                rgb[0].toDouble(),
                rgb[1].toDouble(),
                rgb[2].toDouble(),
                Color.alpha(background) / 255.0,
            )
        v.renderer.clearOptions = options
        v.view.blendMode =
            if (Color.alpha(background) == 255) com.google.android.filament.View.BlendMode.OPAQUE
            else com.google.android.filament.View.BlendMode.TRANSLUCENT
    }

    fun load(scene: GltfPackage) {
        if (pending === scene && viewer?.asset != null) return
        pending = scene
        val value = viewer ?: return
        try {
            value.loadModelGltf(scene.jsonBuffer()) { uri ->
                scene.resources[uri]?.let(ByteBuffer::wrap)
            }
            check(value.asset != null) { "场景或依赖无法读取" }
            value.transformToUnitCube()
            bindHalo(value)
            val animator = value.animator
            val names =
                List(animator?.animationCount ?: 0) {
                    animator!!.getAnimationName(it).ifBlank { "动画 ${it+1}" }
                }
            if (!animationChosen) {
                selected =
                    names.indexOfFirst { it.endsWith("Normal_Idle", true) }.takeIf { it >= 0 }
                        ?: names.indexOfFirst { it.contains("Idle", true) }.coerceAtLeast(0)
                animationChosen = true
            }
            selected = selected.coerceIn(0, (names.size - 1).coerceAtLeast(0))
            pendingReady = names to scene.hasMouth
            dirty = 30
            last = 0
            requestFrame()
        } catch (e: Exception) {
            onFailure?.invoke("模型解析失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** 网页在 HaloRoot 存在时将其绑定 Bip…Head，并保持世界姿态；不存在时保留源结构。 */
    private fun bindHalo(v: ModelViewer) {
        val asset = v.asset ?: return
        val head =
            asset.entities.firstOrNull {
                val name = asset.getName(it).orEmpty().lowercase().replace(' ', '_')
                name.contains("bip") && name.endsWith("_head")
            } ?: return
        val halo = asset.entities.firstOrNull { asset.getName(it) == "HaloRoot" } ?: return
        val tm = v.engine.transformManager
        val h = tm.getInstance(head)
        val child = tm.getInstance(halo)
        if (h == 0 || child == 0) return
        val inverse = FloatArray(16)
        val world = FloatArray(16)
        val local = FloatArray(16)
        tm.getWorldTransform(h, world)
        if (!Matrix.invertM(inverse, 0, world, 0)) return
        tm.getWorldTransform(child, world)
        Matrix.multiplyMM(local, 0, inverse, 0, world, 0)
        tm.setParent(child, h)
        tm.setTransform(child, local)
    }

    fun animation(index: Int) {
        selected = index
        elapsed = 0f
        last = 0
        requestFrame()
    }

    fun resetCamera() {
        viewer?.let {
            it.resetToDefaultState()
            it.cameraFocalLength = 42f
        }
        elapsed = 0f
        requestFrame()
    }

    fun zoomBy(amount: Float) {
        manipulator?.scroll(width / 2, height / 2, amount)
        requestFrame()
    }

    fun turnBy(amount: Int) {
        manipulator?.let {
            it.grabBegin(width / 2, height / 2, false)
            it.grabUpdate(width / 2 + amount, height / 2)
            it.grabEnd()
        }
        requestFrame()
    }

    private fun stopFrames() {
        Choreographer.getInstance().removeFrameCallback(this)
        scheduled = false
        last = 0
    }

    private fun requestFrame() {
        dirty = dirty.coerceAtLeast(3)
        if (isAttachedToWindow && active && viewer != null && !scheduled) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        val v = viewer ?: return
        if (!active || !isAttachedToWindow) return
        if (last == 0L || frameTimeNanos - last >= if (lowPower) 33_000_000L else 16_000_000L) {
            val dt = if (last == 0L) 0f else ((frameTimeNanos - last) / 1e9f).coerceAtMost(.1f)
            last = frameTimeNanos
            if (playing) elapsed += dt
            val animator = v.animator
            if (animator != null && animator.animationCount > 0) {
                animator.applyAnimation(selected.coerceIn(0, animator.animationCount - 1), elapsed)
                animator.updateBoneMatrices()
            }
            if (rotate)
                manipulator?.let {
                    it.grabBegin(width / 2, height / 2, false)
                    it.grabUpdate(width / 2 + 1, height / 2)
                    it.grabEnd()
                }
            v.render(frameTimeNanos)
            dirty--
            if (v.progress >= 1f) {
                pendingReady?.let { onReady?.invoke(it.first, it.second) }
                pendingReady = null
            }
        }
        if (
            (playing && (v.animator?.animationCount ?: 0) > 0) ||
                rotate ||
                v.progress < 1f ||
                dirty > 0
        ) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(
            event.actionMasked != MotionEvent.ACTION_UP &&
                event.actionMasked != MotionEvent.ACTION_CANCEL
        )
        viewer?.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        requestFrame()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun snapshot(maxEdge: Int = 1440): Bitmap {
        check(isAvailable && viewer?.asset != null) { "模型尚未就绪" }
        val scale = (maxEdge.toFloat() / maxOf(width, height)).coerceAtMost(1f)
        return getBitmap(
            (width * scale).toInt().coerceAtLeast(2),
            (height * scale).toInt().coerceAtLeast(2),
        ) ?: error("无法读取模型画面")
    }

    suspend fun fullSnapshot(edge: Int = 2048, animationTime: Float? = null): Bitmap {
        val v = requireNotNull(viewer) { "模型尚未就绪" }
        check(v.asset != null) { "模型尚未就绪" }
        if (frameExport?.edge != edge) {
            finishExport()
            frameExport = ModelFrameExport(v, edge)
        }
        if (animationTime != null) {
            v.animator
                ?.takeIf { it.animationCount > 0 }
                ?.let {
                    it.applyAnimation(
                        selected.coerceIn(0, it.animationCount - 1),
                        elapsed + animationTime,
                    )
                    it.updateBoneMatrices()
                }
        }
        return kotlinx.coroutines.withTimeout(15_000) { requireNotNull(frameExport).frame() }
    }

    fun finishExport() {
        frameExport?.close()
        frameExport = null
        requestFrame()
    }

    override fun onDetachedFromWindow() {
        finishExport()
        stopFrames()
        viewer?.let { v ->
            colorGrading?.let {
                v.view.colorGrading = null
                v.engine.destroyColorGrading(it)
            }
        }
        colorGrading = null
        pending = null
        viewer = null
        manipulator = null
        super.onDetachedFromWindow() /* ModelViewer 自身的 detach listener 唯一负责销毁 engine。 */
    }
}
