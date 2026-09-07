package wiki.kivo.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import wiki.kivo.core.content.ContentBlockView
import wiki.kivo.core.content.ContentParser
import wiki.kivo.core.designsystem.KivoTheme
import wiki.kivo.core.model.AppSettings

/** 检查实际排版结果，避免仅证明解析器产生了 Aligned，却没有真正改变视觉对齐。 */
class ContentAlignmentTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun alignmentReachesHeadingsCaptionsAndRestoresAfterNestedContainer() {
        val blocks =
            ContentParser.parse(
                """
                :::: center
                # 居中标题
                居中正文

                ![文件名.png](https://static.kivo.wiki/local-test.png "居中图注")

                ::: right
                靠右正文
                :::
                ::: left
                靠左正文
                :::
                恢复居中
                :::: 
                容器外靠左
                """
                    .trimIndent()
            )
        ui.setContent {
            KivoTheme(AppSettings(loadImages = false)) {
                Column(Modifier.width(320.dp).testTag("fixture")) {
                    blocks.forEach { ContentBlockView(it, {}, {}) }
                }
            }
        }
        fun alignment(text: String, expected: TextAlign) {
            val layouts = mutableListOf<TextLayoutResult>()
            ui.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            assertEquals(expected, layouts.single().layoutInput.style.textAlign)
            if (expected == TextAlign.Center)
                assertEquals(
                    layouts.single().size.width / 2f,
                    (layouts.single().getLineLeft(0) + layouts.single().getLineRight(0)) / 2f,
                    1f,
                )
        }
        listOf("居中标题", "居中正文", "居中图注", "恢复居中").forEach { alignment(it, TextAlign.Center) }
        alignment("靠右正文", TextAlign.End)
        alignment("靠左正文", TextAlign.Start)
        alignment("容器外靠左", TextAlign.Start)
        val button = ui.onNodeWithContentDescription("文件名.png").fetchSemanticsNode().boundsInRoot
        val parent = ui.onNodeWithTag("fixture").fetchSemanticsNode().boundsInRoot
        assertEquals(parent.center.x, button.center.x, 1f)
        ui.onAllNodesWithText("::::", substring = true).assertCountEquals(0)
    }
}
