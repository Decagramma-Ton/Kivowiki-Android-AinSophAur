package wiki.kivo.core.content

import org.junit.Assert.*
import org.junit.Test

class MermaidContractTest {
    @Test
    fun nestedDiagramIsNotCodeAndFollowingTextSurvives() {
        val blocks =
            ContentParser.parse(
                ":::: center\r\n```mermaid\r\nflowchart TB\r\nA[白子] --> B[星野]\r\n```\r\n::::\r\n\r\n正文末尾"
            )
        val aligned = blocks.first() as ContentBlock.Aligned
        assertTrue(aligned.blocks.single() is ContentBlock.Mermaid)
        assertTrue((blocks.last() as ContentBlock.Text).spans.any { it.text.contains("正文末尾") })
        assertTrue(ContentParser.parse("```text\nmermaid\n``` ").first() is ContentBlock.Code)
    }

    @Test
    fun hostileSourceCannotEscapeIntoExecutableDocument() {
        val source = "</script><script>alert('bad')</script><img src='https://example.com/private'>"
        val html = MermaidDocument.html(source, false, false)
        assertFalse(html.contains(source))
        assertFalse(html.contains("https://example.com/private"))
        assertTrue(html.contains("securityLevel:'strict'"))
        assertTrue(html.contains("connect-src 'none'"))
        assertTrue(html.contains("img-src 'none'"))
    }
}
