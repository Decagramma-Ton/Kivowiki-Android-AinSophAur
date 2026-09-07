package wiki.kivo.core.content

import org.junit.Assert.*
import org.junit.Test

class WikiDialectTest {
    @Test
    fun noteKeepsTitleAndRichBody() {
        val blocks = ContentParser.parse("!!! note 提示\n这是一段 **重要** 的说明。\n!!!\n\n后续正文")
        val note = blocks.first() as ContentBlock.Admonition
        assertEquals("提示", note.title)
        assertTrue(
            (note.blocks.single() as ContentBlock.Text).spans.any { it.bold && it.text == "重要" }
        )
        assertEquals("后续正文", (blocks.last() as ContentBlock.Text).spans.single().text)
    }

    @Test
    fun fencedExamplesAreNeverExecutedOrConverted() {
        val source = "!!! note 示例\n<script>literal</script>\n!!!\n"
        val blocks = ContentParser.parse("```markdown\n$source```\n\n<script>bad()</script>\n\n正文")
        assertEquals(source, (blocks.first() as ContentBlock.Code).source)
        assertFalse(blocks.toString().contains("bad()"))
    }

    @Test
    fun containersNestWithoutConsumingFollowingParagraphs() {
        val blocks = ContentParser.parse("!!! warning 注意\n!!! tip 提醒\n内部\n!!!\n!!!\n\n外部")
        assertTrue(
            (blocks.first() as ContentBlock.Admonition).blocks.single() is ContentBlock.Admonition
        )
        assertEquals(2, blocks.size)
        assertTrue(ContentParser.parse("!!! note 未闭合\n正文").none { it is ContentBlock.Admonition })
    }

    @Test
    fun tabsAndSizedImagesRemainReadable() {
        val tabs =
            ContentParser.parse("::: tabs\n@tab 第一项\n甲\n@tab 第二项\n乙\n:::").single()
                as ContentBlock.Tabs
        assertEquals(listOf("第一项", "第二项"), tabs.tabs.map { it.first })
        assertTrue(
            ContentParser.parse("![图片](https://static.kivo.wiki/images/a.png =300x80)").single()
                is ContentBlock.Image
        )
    }

    @Test
    fun oldPlaceholderTextCannotImpersonateARealBlock() {
        val blocks =
            ContentParser.parse(
                "KIVO_INTERNAL_COLLAPSE_0\n\n<details><summary>标题</summary>内容</details>"
            )
        assertTrue(blocks.first() is ContentBlock.Text)
        assertTrue(blocks.last() is ContentBlock.Collapse)
    }
}
