package wiki.kivo.core.content

import org.junit.Assert.*
import org.junit.Test

class ContentParserTest {
    @Test
    fun alignmentContainersKeepAllInlineAndImageContent() {
        for (direction in listOf("left", "center", "right", "justify")) {
            for (fence in listOf(":::", "::::", ":::::")) {
                for (gap in listOf("", " ")) {
                    val source =
                        "$fence$gap$direction  \r\n\r\n## 标题\r\n\r\n**正文** [角色](/data/character/86)\r\n\r\n![图片](//static.kivo.wiki/alignment.png)\r\n\r\n$fence\r\n\r\n容器之后"
                    val blocks = ContentParser.parse(source)
                    val container = blocks.first() as ContentBlock.Aligned
                    assertEquals(direction, container.alignment)
                    assertEquals(1, container.blocks.filterIsInstance<ContentBlock.Image>().size)
                    assertEquals(
                        2,
                        container.blocks.filterIsInstance<ContentBlock.Text>().first().heading,
                    )
                    assertTrue(
                        container.blocks
                            .filterIsInstance<ContentBlock.Text>()
                            .flatMap { it.spans }
                            .any { it.bold }
                    )
                    assertTrue(
                        container.blocks.toString().contains("https://kivo.wiki/data/character/86")
                    )
                    assertEquals("容器之后", (blocks.last() as ContentBlock.Text).spans.single().text)
                    assertFalse(blocks.toString().contains(fence))
                }
            }
        }
    }

    @Test
    fun nestedAlignmentAndTabsDoNotConsumeSiblingContainers() {
        val source =
            """
            ::: center
            外层第一段
            :::: right
            内层靠右
            :::: left
            更深层靠左
            ::::${' '}
            ::::${' '}
            外层第二段
            :::

            :::: tabs
            @tab 第一页
            ::: center
            ::: tabs
            @tab 内页一
            一
            @tab 内页二
            二
            :::
            :::
            @tab 第二页
            完整尾段
            ::::${' '}
            """
                .trimIndent()
        val blocks = ContentParser.parse(source)
        val outer = blocks.first() as ContentBlock.Aligned
        val right = outer.blocks.filterIsInstance<ContentBlock.Aligned>().single()
        assertEquals("right", right.alignment)
        assertEquals(
            "left",
            right.blocks.filterIsInstance<ContentBlock.Aligned>().single().alignment,
        )
        assertTrue(outer.blocks.last().toString().contains("外层第二段"))
        val tabs = blocks.last() as ContentBlock.Tabs
        assertEquals(listOf("第一页", "第二页"), tabs.tabs.map { it.first })
        val inner =
            (tabs.tabs.first().second.single() as ContentBlock.Aligned).blocks.single()
                as ContentBlock.Tabs
        assertEquals(listOf("内页一", "内页二"), inner.tabs.map { it.first })
        assertTrue(tabs.tabs.last().second.toString().contains("完整尾段"))
    }

    @Test
    fun alignmentExamplesStayLiteralInsideCodeAndUnclosedInputIsNotLost() {
        val example = "::: center\n图片\n:::"
        val blocks = ContentParser.parse("```makefile\n$example\n```\n\n::: right\n\n保留尾段")
        assertEquals(example + "\n", (blocks.first() as ContentBlock.Code).source)
        assertEquals("right", (blocks.last() as ContentBlock.Aligned).alignment)
        assertTrue(blocks.last().toString().contains("保留尾段"))
        val nested =
            ContentParser.parse("::: center\n\n~~~\n::: right\n~~~\n\n:::").single()
                as ContentBlock.Aligned
        assertTrue(nested.blocks.single() is ContentBlock.Code)
    }

    @Test
    fun tableImagesAndLinksSurviveNativeRendering() {
        val table =
            ContentParser.parse(
                    "| 资料 |\n| --- |\n| [联动角色](/data/character/38) ![图](//static.kivo.wiki/test.png) |"
                )
                .filterIsInstance<ContentBlock.Table>()
                .single()
        val blocks = table.richCells.last().single()
        assertTrue(
            blocks
                .filterIsInstance<ContentBlock.Text>()
                .flatMap { it.spans }
                .any { it.link == "https://kivo.wiki/data/character/38" }
        )
        assertEquals(
            1,
            blocks
                .filterIsInstance<ContentBlock.Text>()
                .flatMap { it.spans }
                .count { it.image != null },
        )
        val html =
            ContentParser.parse(
                    "<table><tr><td><a href='/article/55'>已知问题</a><img src='//static.kivo.wiki/test.png'></td></tr></table>"
                )
                .filterIsInstance<ContentBlock.Table>()
                .single()
        assertTrue(
            html.richCells
                .flatten()
                .flatten()
                .filterIsInstance<ContentBlock.Text>()
                .flatMap { it.spans }
                .any { it.link == "https://kivo.wiki/article/55" }
        )
    }

    @Test
    fun lengthyArticleNeverLosesItsTail() {
        val source = "正文".repeat(500_001) + "最后一段"
        val parsed = ContentParser.parse(source)
        assertTrue(parsed.last().toString().contains("最后一段"))
    }

    @Test
    fun markdownKeepsHeadingsLinksTablesAndLists() {
        val blocks =
            ContentParser.parse(
                "# 标题\n\n**正文**与[文章](/article/83)\n\n- 第一项\n- 第二项\n\n| 列一 | 列二 |\n| --- | --- |\n| A | B |"
            )
        assertEquals(1, (blocks.first() as ContentBlock.Text).heading)
        assertTrue(
            blocks
                .filterIsInstance<ContentBlock.Text>()
                .flatMap { it.spans }
                .any { it.bold && it.text == "正文" }
        )
        assertTrue(
            blocks
                .filterIsInstance<ContentBlock.Text>()
                .flatMap { it.spans }
                .any { it.link == "https://kivo.wiki/article/83" }
        )
        assertEquals(2, blocks.filterIsInstance<ContentBlock.Text>().count { it.bullet != null })
        assertEquals(
            listOf("A", "B"),
            blocks.filterIsInstance<ContentBlock.Table>().single().rows.last(),
        )
    }

    @Test
    fun nestedDetailsPreserveStructureAndReadableMarkdown() {
        val blocks =
            ContentParser.parse(
                "<details><summary>外层</summary>\n\n**说明**\n\n<details><summary>内层</summary>\n\n深层正文\n\n</details>\n</details>"
            )
        val outer = blocks.single() as ContentBlock.Collapse
        assertEquals("外层", outer.title)
        assertEquals("内层", outer.blocks.filterIsInstance<ContentBlock.Collapse>().single().title)
        assertTrue(outer.blocks.filterIsInstance<ContentBlock.Text>().first().spans.any { it.bold })
    }

    @Test
    fun executableContentIsRemovedAndUnsafeLinksStayUnclickable() {
        val blocks =
            ContentParser.parse(
                "<script>secretScript()</script>\n\n[危险](javascript:alert) ![危险](file:///x)\n\n![古书馆](//static.kivo.wiki/a.png)"
            )
        assertFalse(blocks.toString().contains("secretScript"))
        assertTrue(
            blocks
                .filterIsInstance<ContentBlock.Text>()
                .flatMap { it.spans }
                .all { it.link == null }
        )
        assertEquals(
            "https://static.kivo.wiki/a.png",
            blocks.filterIsInstance<ContentBlock.Image>().single().url,
        )
    }

    @Test
    fun malformedHtmlAndEmptyContentRemainReadable() {
        assertTrue(ContentParser.parse("").isEmpty())
        assertTrue(ContentParser.parse("<div>没有闭合的容器\n\n仍然可读").toString().contains("仍然可读"))
        assertFalse(ContentParser.parse("[[toc]]\n\n正文").toString().contains("[[toc]]"))
    }
}
