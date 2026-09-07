package wiki.kivo.core.content

import org.junit.Assert.*
import org.junit.Test

/** 2026-09-07 从妃咲 61、圣三一 2 公开资料最小化的语法样本。 */
class MobileMarkdownRegressionTest {
    @Test
    fun inlineCodeAndHtmlImagesAreNotLost() {
        val code = ContentParser.parse("`![图片](files/a.png =50x)`").single() as ContentBlock.Text
        assertEquals("![图片](files/a.png =50x)", code.spans.single().text)
        assertTrue(code.spans.single().code)
        val mixed =
            ContentParser.parse("正文 <img src='files/a.png' alt='图'> 后文").single()
                as ContentBlock.Text
        assertEquals(1, mixed.spans.count { it.image != null })
    }

    @Test
    fun liveDocumentsKeepEveryImageWithoutLeakingDialectMarkers() {
        fun children(block: ContentBlock): List<ContentBlock> =
            when (block) {
                is ContentBlock.Aligned -> block.blocks
                is ContentBlock.Admonition -> block.blocks
                is ContentBlock.Quote -> block.blocks
                is ContentBlock.Collapse -> block.blocks
                is ContentBlock.Tabs -> block.tabs.flatMap { it.second }
                is ContentBlock.Table -> block.richCells.flatten().flatten()
                else -> emptyList()
            }
        fun walk(blocks: List<ContentBlock>): List<ContentBlock> = blocks.flatMap {
            listOf(it) + walk(children(it))
        }
        for (name in listOf("student-61", "school-2")) {
            val source = requireNotNull(javaClass.getResource("/mobile/$name.md")).readText()
            val blocks = walk(ContentParser.parse(source))
            val text =
                blocks
                    .filterIsInstance<ContentBlock.Text>()
                    .flatMap { it.spans }
                    .joinToString("") { it.text }
            assertFalse("$name 不应泄漏容器边界", text.contains(":::"))
            assertFalse("$name 不应泄漏占位符", Regex("KIVO[0-9a-f]{32}").containsMatchIn(text))
            assertFalse("$name 不应泄漏图片尺寸", Regex("=\\d+x").containsMatchIn(text))
            val images =
                blocks.filterIsInstance<ContentBlock.Image>() +
                    blocks
                        .filterIsInstance<ContentBlock.Text>()
                        .flatMap { it.spans }
                        .mapNotNull { it.image }
            assertEquals("$name 必须保留所有图片", Regex("!\\[").findAll(source).count(), images.size)
        }
    }

    @Test
    fun linkedCaptionAndDimensionsSurviveNestedParentheses() {
        val block =
            ContentParser.parse(
                    """![design.jpg](files/1348/design.jpg "[原推（点击跳转）](https://x.com/yutokamizu/status/1617855677271244800)" =350x)"""
                )
                .single() as ContentBlock.Image
        assertEquals(350, block.width)
        assertNull(block.height)
        assertEquals("https://static.kivo.wiki/files/1348/design.jpg", block.url)
        assertTrue(
            ContentParser.parse(block.caption)
                .toString()
                .contains("https://x.com/yutokamizu/status/1617855677271244800")
        )
        assertEquals("design.jpg", block.description)
    }

    @Test
    fun medalsKeepTheirOwnInlinePositionsAndDoNotPrintFilenames() {
        val block =
            ContentParser.parse(
                    "【学生】**第2名**![rank2.png](files/19083/rank2.png =50x)；【剧情】**第3名**![rank3.png](files/19083/rank3.png =50x)"
                )
                .single() as ContentBlock.Text
        val images = block.spans.mapNotNull { it.image }
        assertEquals(listOf(50, 50), images.map { it.width })
        assertEquals(listOf("rank2.png", "rank3.png"), images.map { it.description })
        assertFalse(block.spans.joinToString("") { it.text }.contains(".png"))
        assertTrue(
            block.spans.indexOfFirst { it.image != null } <
                block.spans.indexOfFirst { it.text.contains("剧情") }
        )
    }

    @Test
    fun trinityUnclosedOuterAlignmentKeepsNestedContentAndTail() {
        val block =
            ContentParser.parse(
                    "::: center\n# 简介\n\n正文\n::: center\n![校图](files/3/school.jpg =450x)\n:::\n\n# 历史沿革\n尾段"
                )
                .single() as ContentBlock.Aligned
        assertEquals("center", block.alignment)
        assertTrue(block.toString().contains("尾段"))
        assertFalse(block.toString().contains(":::"))
    }

    @Test
    fun nestedNoticeTitleIsTextAndSizedImageIsPreserved() {
        val root =
            ContentParser.parse(
                    ":::: center\n!!! question <font size=\"4\">**荣 誉 勋 章**</font>\n![coconut.png](files/19083/coconut.png =100x)\n!!!\n::::"
                )
                .single() as ContentBlock.Aligned
        val note = root.blocks.single() as ContentBlock.Admonition
        assertEquals("荣 誉 勋 章", note.title)
        assertEquals(100, (note.blocks.single() as ContentBlock.Image).width)
    }

    @Test
    fun sameImageCanHaveDifferentSizesAndFencedExamplesStayLiteral() {
        val result = ContentParser.parse("![a](files/a.png =50x)\n\n![b](files/a.png =300x200)")
        assertEquals(
            listOf(50, 300),
            result.filterIsInstance<ContentBlock.Image>().map { it.width },
        )
        assertEquals(200, (result.last() as ContentBlock.Image).height)
        val code =
            ContentParser.parse("```md\n![a](files/a.png =50x)\n```").single() as ContentBlock.Code
        assertTrue(code.source.contains("=50x"))
    }
}
