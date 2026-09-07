package wiki.kivo.core.media

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.android.filament.*
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer
import kotlin.math.*
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 独立离屏画布复用已加载的场景，不放大手机截图，也不改用户预览镜头。 一次录像复用 GPU 对象；像素回读完成才创建 Bitmap，取消后回调不再交付帧。 宿主必须在主线程、Engine
 * 销毁前关闭会话。
 */
internal class ModelFrameExport(private val viewer: ModelViewer, val edge: Int) : AutoCloseable {
    private val engine = viewer.engine
    private val entity = EntityManager.get().create()
    private val camera = engine.createCamera(entity)
    private val view = engine.createView()
    private val renderer = engine.createRenderer()
    private val swapChain = engine.createSwapChain(edge, edge, 0)
    // 帧按回读完成顺序串行交付，整段录像复用缓冲，避免每帧申请数 MB 堆外内存。
    private val pixels = ByteBuffer.allocateDirect(edge * edge * 4)
    private var closed = false

    init {
        require(edge in 256..4096)
        view.scene = viewer.scene
        view.camera = camera
        view.viewport = Viewport(0, 0, edge, edge)
        view.colorGrading = viewer.view.colorGrading
        view.blendMode = viewer.view.blendMode
        camera.setProjection(40.0, 1.0, .1, 100.0, Camera.Fov.VERTICAL)
        // 包围盒按每个可渲染实体的世界矩阵变换，包含独立绑定的光环/附件。
        // 将全部角点投影到当前观察方向后求取景距离，既避免裁切，也避免固定远镜头留下大片空白。
        val corners = mutableListOf<FloatArray>()
        val renderables = engine.renderableManager
        val transforms = engine.transformManager
        viewer.asset?.renderableEntities?.forEach { id ->
            val instance = renderables.getInstance(id)
            if (instance != 0) {
                val box = renderables.getAxisAlignedBoundingBox(instance, null)
                val matrix =
                    transforms.getWorldTransform(transforms.getInstance(id), null as FloatArray?)
                for (x in listOf(-1, 1)) for (y in listOf(-1, 1)) for (z in listOf(-1, 1)) {
                    val local =
                        floatArrayOf(
                            box.center[0] + x * box.halfExtent[0],
                            box.center[1] + y * box.halfExtent[1],
                            box.center[2] + z * box.halfExtent[2],
                            1f,
                        )
                    val world = FloatArray(4)
                    android.opengl.Matrix.multiplyMV(world, 0, matrix, 0, local, 0)
                    if (world.all { it.isFinite() }) corners += world
                }
            }
        }
        check(corners.isNotEmpty()) { "模型没有可导出的几何体" }
        val center =
            FloatArray(3) { axis -> (corners.minOf { it[axis] } + corners.maxOf { it[axis] }) / 2 }
        val direction = viewer.camera.getForwardVector(null)
        val up = viewer.camera.getUpVector(null)
        val left = viewer.camera.getLeftVector(null)
        val tangent = tan(Math.toRadians(20.0))
        val distance =
            corners
                .maxOf { corner ->
                    val delta = FloatArray(3) { corner[it] - center[it] }
                    fun project(axis: FloatArray) = (0..2).sumOf { delta[it].toDouble() * axis[it] }
                    max(abs(project(left)), abs(project(up))) / tangent - project(direction)
                }
                .coerceAtLeast(.1) * 1.12
        camera.lookAt(
            center[0] - direction[0] * distance,
            center[1] - direction[1] * distance,
            center[2] - direction[2] * distance,
            center[0].toDouble(),
            center[1].toDouble(),
            center[2].toDouble(),
            up[0].toDouble(),
            up[1].toDouble(),
            up[2].toDouble(),
        )
        camera.setExposure(16f, 1f / 125f, 100f)
        renderer.clearOptions = viewer.renderer.clearOptions
    }

    suspend fun frame(): Bitmap = suspendCancellableCoroutine { continuation ->
        check(!closed) { "导出会话已经关闭" }
        pixels.clear()
        check(renderer.beginFrame(swapChain, System.nanoTime())) { "设备暂时无法创建导出帧" }
        try {
            renderer.render(view)
            renderer.readPixels(
                0,
                0,
                edge,
                edge,
                Texture.PixelBufferDescriptor(
                    pixels,
                    Texture.Format.RGBA,
                    Texture.Type.UBYTE,
                    1,
                    0,
                    0,
                    0,
                    Handler(Looper.getMainLooper()),
                    Runnable {
                        if (!continuation.isActive) return@Runnable
                        pixels.rewind()
                        val raw = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
                        raw.copyPixelsFromBuffer(pixels)
                        // Filament 的 Android 回读已按图片行序返回；不能再按底层 GL 原点额外翻转。
                        continuation.resume(raw) { _, bitmap, _ -> bitmap.recycle() }
                    },
                ),
            )
        } finally {
            renderer.endFrame()
            // 预览暂停后不再有后续屏幕帧替我们推动回读；必须提交并完成本次离屏命令。
            engine.flushAndWait()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        engine.flushAndWait()
        engine.destroySwapChain(swapChain)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyCameraComponent(entity)
        EntityManager.get().destroy(entity)
    }
}
