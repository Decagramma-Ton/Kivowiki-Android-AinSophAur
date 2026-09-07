package wiki.kivo.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlSerializer

/**
 * Release 只读验收桥：运行于独立 Debug 测试进程，不创建 Activity、不改交付 App 数据。 系统 uiautomator dump
 * 强制等待空闲，可能被首页每秒更新的日程阻塞并遗留旧 XML。 这里获取当前可访问窗口；调用方仍须等待目标页面／选中状态，不能把快照当作加载完成。
 */
@RunWith(AndroidJUnit4::class)
class ReleaseUiSnapshotTest {
    @Test
    fun exportActiveReleaseWindow() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("releaseUiDump") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags =
                    flags or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
        // 每次独立 instrumentation 会新连可访问服务；窗口注册可能晚于连接完成。
        var current: AccessibilityNodeInfo? = null
        for (attempt in 0 until 30) {
            current =
                automation.rootInActiveWindow
                    ?: automation.windows.firstOrNull { it.isActive }?.root
            if (current?.packageName?.toString() == "wiki.kivo.app.preview") break
            Thread.sleep(100)
        }
        // 启动过渡或 Splash 尚未退出时不导出；调用方可等待后重试，禁止复用旧快照。
        assumeTrue("Release 窗口尚未就绪", current?.packageName?.toString() == "wiki.kivo.app.preview")
        val root = checkNotNull(current)
        // 不读取桌面或其他 App。源站写入不在这个验收桥中执行。
        assertEquals("wiki.kivo.app.preview", root.packageName?.toString())
        val directory =
            File(instrumentation.targetContext.getExternalFilesDir(null), "release-driver")
        check(directory.isDirectory || directory.mkdirs())
        val pending = File(directory, "current.part")
        val destination = File(directory, "current.xml")
        pending.outputStream().buffered().use { output ->
            val xml = Xml.newSerializer()
            xml.setOutput(output, "UTF-8")
            xml.startDocument("UTF-8", true)
            xml.startTag(null, "hierarchy")
            appendNode(xml, root, 0)
            xml.endTag(null, "hierarchy")
            xml.endDocument()
            xml.flush()
        }
        check(pending.renameTo(destination)) { "界面快照写入失败" }
    }

    private fun appendNode(xml: XmlSerializer, node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 0 && !node.isVisibleToUser) return
        check(depth <= 100) { "可访问树层级异常" }
        val bounds = Rect().also(node::getBoundsInScreen)
        xml.startTag(null, "node")
        mapOf(
                "text" to node.text?.toString().orEmpty(),
                "content-desc" to node.contentDescription?.toString().orEmpty(),
                "package" to node.packageName?.toString().orEmpty(),
                "class" to node.className?.toString().orEmpty(),
                "resource-id" to node.viewIdResourceName.orEmpty(),
                "selected" to node.isSelected.toString(),
                "checked" to node.isChecked.toString(),
                "clickable" to node.isClickable.toString(),
                "enabled" to node.isEnabled.toString(),
                "scrollable" to node.isScrollable.toString(),
                "bounds" to "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]",
            )
            .forEach { (key, value) -> xml.attribute(null, key, value) }
        for (index in 0 until node.childCount) node.getChild(index)?.let {
            appendNode(xml, it, depth + 1)
        }
        xml.endTag(null, "node")
    }
}
