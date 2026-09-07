package wiki.kivo.core.content

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.*
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.jsoup.Jsoup
import wiki.kivo.core.model.UrlPolicy

data class RichText(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
    val image: ContentBlock.Image? = null,
)

sealed interface ContentBlock {
    data class Text(val spans: List<RichText>, val heading: Int = 0, val bullet: String? = null) :
        ContentBlock

    data class Image(
        val url: String,
        val description: String,
        val width: Int? = null,
        val height: Int? = null,
        val caption: String = "",
    ) : ContentBlock

    data class Quote(val blocks: List<ContentBlock>) : ContentBlock

    data class Collapse(val title: String, val blocks: List<ContentBlock>) : ContentBlock

    data class Admonition(val kind: String, val title: String, val blocks: List<ContentBlock>) :
        ContentBlock

    data class Tabs(val tabs: List<Pair<String, List<ContentBlock>>>) : ContentBlock

    data class Aligned(val alignment: String, val blocks: List<ContentBlock>) : ContentBlock

    data class Table(
        val rows: List<List<String>>,
        val richCells: List<List<List<ContentBlock>>> = emptyList(),
    ) : ContentBlock

    data class Code(val source: String) : ContentBlock

    data class Mermaid(val source: String) : ContentBlock

    data object Rule : ContentBlock
}

/** 将站点正文解析为不含脚本的原生内容块。第三方 AST 不进入业务层；解析深度和输入长度均设上限。 */
object ContentParser {
    private val containerOpening =
        Regex("^ {0,3}(:{3,})[ \\t]*(tabs|left|center|right|justify)[ \\t]*$")
    private val noteOpening = Regex("^ {0,3}!!![ \\t]+([a-zA-Z]+)(?:[ \\t]+(.*))?$")

    /** 用栈匹配容器，兼容三/四个及更多冒号；长围栏不会误关闭内层容器。 */
    private fun updateContainers(line: String, markers: MutableList<String>) {
        val opening = containerOpening.matchEntire(line)
        when {
            opening != null -> markers.add(opening.groupValues[1])
            noteOpening.matches(line) -> markers.add("!!!")
            markers.lastOrNull() == "!!!" && line.trim() == "!!!" ->
                markers.removeAt(markers.lastIndex)
            markers.lastOrNull()?.startsWith(':') == true &&
                line.trim().all { it == ':' } &&
                line.trim().length >= markers.last().length -> markers.removeAt(markers.lastIndex)
        }
    }

    private val parser =
        Parser.builder()
            .extensions(listOf(TablesExtension.create(), AutolinkExtension.create()))
            .build()

    fun parse(source: String): List<ContentBlock> =
        if (source.length <= 1_000_000) parseInternal(source, 0)
        else
            listOf(ContentBlock.Admonition("note", "此篇资料较长，以下保留完整原文", emptyList())) +
                source.chunked(32_000).map { ContentBlock.Code(it) }

    private fun parseInternal(raw: String, depth: Int): List<ContentBlock> {
        if (depth > 8) return listOf(ContentBlock.Text(listOf(RichText(Jsoup.parse(raw).text()))))
        // 占位标识每次解析独立生成，正文无法伪造一个序号来引用另一块内容。
        val token = "KIVO" + java.util.UUID.randomUUID().toString().replace("-", "")
        val saved = mutableMapOf<String, ContentBlock>()
        fun save(block: ContentBlock): String {
            val key = token + saved.size
            saved[key] = block
            return "\n\n$key\n\n"
        }
        val lines = raw.replace("\r\n", "\n").split('\n')
        val protected = StringBuilder()
        var i = 0
        // 先保护代码围栏，代码示例中的 !!!、HTML 和 ::: 必须保持字面含义。
        while (i < lines.size) {
            val opening = Regex("^ {0,3}(\\x60{3,}|~{3,})(.*)$").matchEntire(lines[i])
            if (opening == null) {
                protected.append(lines[i++]).append('\n')
                continue
            }
            val fence = opening.groupValues[1]
            val body = StringBuilder()
            i++
            while (
                i < lines.size &&
                    !Regex(
                            "^ {0,3}" +
                                Regex.escape(fence.first().toString()) +
                                "{" +
                                fence.length +
                                ",}\\s*$"
                        )
                        .matches(lines[i])
            ) body.append(lines[i++]).append('\n')
            if (i < lines.size) i++
            protected.append(
                save(
                    if (opening.groupValues[2].trim().equals("mermaid", true))
                        ContentBlock.Mermaid(body.toString())
                    else ContentBlock.Code(body.toString())
                )
            )
        }
        val clean =
            protected
                .toString()
                .replace(
                    Regex("(?is)<(script|style|iframe|object|embed|form)\\b[^>]*>.*?</\\1\\s*>"),
                    "",
                )
                .replace(Regex("(?i)\\[\\[toc]]"), "")
        val extended = StringBuilder()
        val extensionLines = clean.split('\n')
        i = 0
        fun resolve(block: ContentBlock): ContentBlock =
            when (block) {
                is ContentBlock.Text ->
                    saved[block.spans.joinToString("") { it.text }.trim()] ?: block
                is ContentBlock.Quote -> block.copy(blocks = block.blocks.map(::resolve))
                is ContentBlock.Collapse -> block.copy(blocks = block.blocks.map(::resolve))
                is ContentBlock.Admonition -> block.copy(blocks = block.blocks.map(::resolve))
                is ContentBlock.Tabs ->
                    block.copy(tabs = block.tabs.map { it.first to it.second.map(::resolve) })
                is ContentBlock.Aligned -> block.copy(blocks = block.blocks.map(::resolve))
                else -> block
            }
        fun nested(value: String): List<ContentBlock> =
            parseInternal(value, depth + 1).map(::resolve)
        while (i < extensionLines.size) {
            val line = extensionLines[i]
            val note = noteOpening.matchEntire(line)
            val container = containerOpening.matchEntire(line)
            if (note == null && container == null) {
                extended.append(line).append('\n')
                i++
                continue
            }
            val marker = if (note != null) "!!!" else container!!.groupValues[1]
            val markers = mutableListOf(marker)
            var end = i + 1
            while (end < extensionLines.size) {
                updateContainers(extensionLines[end], markers)
                if (markers.isEmpty()) break
                end++
            }
            // 对齐容器允许在文末隐式结束（与网站的 Markdown 容器一致）；未知/标签容器保守降级。
            if (
                end == extensionLines.size &&
                    (container == null || container.groupValues[2] == "tabs")
            ) {
                extended.append(line).append('\n')
                i++
                continue
            }
            val body = extensionLines.subList(i + 1, end).joinToString("\n")
            val block =
                if (note != null) {
                    val kind = note.groupValues[1].lowercase()
                    ContentBlock.Admonition(
                        kind,
                        Jsoup.parseBodyFragment(note.groupValues[2])
                            .text()
                            .replace("**", "")
                            .trim()
                            .ifBlank {
                                when (kind) {
                                    "warning",
                                    "caution",
                                    "attention" -> "注意"
                                    "danger",
                                    "error",
                                    "failure",
                                    "bug" -> "警告"
                                    "tip",
                                    "hint",
                                    "success" -> "小提示"
                                    "question" -> "问题"
                                    else -> "提示"
                                }
                            },
                        nested(body),
                    )
                } else if (container!!.groupValues[2] == "tabs") {
                    val tabs = mutableListOf<Pair<String, List<ContentBlock>>>()
                    var title = "内容"
                    val tabBody = StringBuilder()
                    val tabContainers = mutableListOf<String>()
                    body.lines().forEach { tabLine ->
                        val tab = Regex("^@tab(?:[ \\t]+|$)(.*)$").matchEntire(tabLine.trim())
                        if (tab != null && tabContainers.isEmpty()) {
                            if (tabBody.isNotBlank()) tabs += title to nested(tabBody.toString())
                            title = tab.groupValues[1].trim().ifBlank { "标签 " + (tabs.size + 1) }
                            tabBody.clear()
                        } else {
                            tabBody.append(tabLine).append('\n')
                            updateContainers(tabLine, tabContainers)
                        }
                    }
                    if (tabBody.isNotBlank() || tabs.isEmpty())
                        tabs += title to nested(tabBody.toString())
                    ContentBlock.Tabs(tabs)
                } else ContentBlock.Aligned(container.groupValues[2], nested(body))
            extended.append(save(block))
            i = end + 1
        }
        val prepared = StringBuilder()
        val source = extended.toString()
        var cursor = 0
        var level = 0
        var start = 0
        var bodyStart = 0
        Regex("(?is)</?details\\b[^>]*>").findAll(source).forEach { match ->
            if (!match.value.startsWith("</", true)) {
                if (level++ == 0) {
                    start = match.range.first
                    bodyStart = match.range.last + 1
                }
            } else if (level > 0 && --level == 0) {
                prepared.append(source.substring(cursor, start))
                val inner = source.substring(bodyStart, match.range.first)
                val summary = Regex("(?is)<summary\\b[^>]*>(.*?)</summary\\s*>").find(inner)
                val title = Jsoup.parseBodyFragment(summary?.groupValues?.get(1) ?: "展开阅读").text()
                val remaining = if (summary == null) inner else inner.removeRange(summary.range)
                prepared.append(save(ContentBlock.Collapse(title, nested(remaining))))
                cursor = match.range.last + 1
            }
        }
        prepared.append(source.substring(cursor))
        val markdown = WikiImages.normalize(prepared.toString())
        return blocks(parser.parse(markdown), saved, depth).map(::resolve)
    }

    private fun children(node: Node): List<Node> = buildList {
        var child = node.firstChild
        while (child != null) {
            add(child)
            child = child.next
        }
    }

    private fun inline(
        node: Node,
        bold: Boolean = false,
        italic: Boolean = false,
        link: String? = null,
    ): List<RichText> =
        when (node) {
            is org.commonmark.node.Text -> listOf(RichText(node.literal, bold, italic, link = link))
            is Code -> listOf(RichText(node.literal, bold, italic, code = true, link = link))
            is Link ->
                children(node).flatMap {
                    inline(it, bold, italic, UrlPolicy.link(node.destination))
                }
            is StrongEmphasis -> children(node).flatMap { inline(it, true, italic, link) }
            is Emphasis -> children(node).flatMap { inline(it, bold, true, link) }
            is SoftLineBreak,
            is HardLineBreak -> listOf(RichText("\n"))
            is HtmlInline ->
                if (images(node).isNotEmpty()) images(node).map { RichText("", image = it) }
                else
                    listOf(
                        RichText(
                            if (node.literal.matches(Regex("(?i)<br\\s*/?>"))) "\n"
                            else Jsoup.parseBodyFragment(node.literal).text(),
                            bold,
                            italic,
                            link = link,
                        )
                    )
            is org.commonmark.node.Image -> images(node).map { RichText("", image = it) }
            else -> children(node).flatMap { inline(it, bold, italic, link) }
        }

    private fun images(node: Node): List<ContentBlock.Image> =
        if (node is org.commonmark.node.Image)
            UrlPolicy.resource(node.destination)
                ?.let {
                    listOf(
                        WikiImages.image(
                            it,
                            children(node).flatMap { inline(it) }.joinToString("") { it.text },
                            node.title,
                        )
                    )
                }
                .orEmpty()
        else if (node is HtmlInline)
            Jsoup.parseBodyFragment(node.literal).select("img").mapNotNull { image ->
                UrlPolicy.resource(image.attr("src"))?.let {
                    ContentBlock.Image(it, image.attr("alt"))
                }
            }
        else children(node).flatMap { images(it) }

    private fun blocks(
        node: Node,
        collapses: Map<String, ContentBlock>,
        depth: Int,
    ): List<ContentBlock> =
        when (node) {
            is Paragraph -> {
                val spans = inline(node)
                val text = spans.joinToString("") { it.text }
                val saved = collapses[text.trim()]
                if (saved != null) listOf(saved)
                else if (text.isNotBlank()) listOf(ContentBlock.Text(spans)) else images(node)
            }
            is Heading -> listOf(ContentBlock.Text(inline(node), node.level))
            is BlockQuote ->
                listOf(ContentBlock.Quote(children(node).flatMap { blocks(it, collapses, depth) }))
            is BulletList,
            is OrderedList ->
                children(node).flatMapIndexed { index, item ->
                    val marker =
                        if (node is OrderedList) "${node.markerStartNumber + index}." else "•"
                    children(item)
                        .flatMap { blocks(it, collapses, depth) }
                        .mapIndexed { i, block ->
                            if (i == 0 && block is ContentBlock.Text) block.copy(bullet = marker)
                            else block
                        }
                }
            is FencedCodeBlock -> listOf(ContentBlock.Code(node.literal))
            is IndentedCodeBlock -> listOf(ContentBlock.Code(node.literal))
            is ThematicBreak -> listOf(ContentBlock.Rule)
            is TableBlock -> {
                val rows =
                    children(node)
                        .flatMap { children(it) }
                        .filterIsInstance<TableRow>()
                        .map { row ->
                            children(row).map { cell -> inline(cell).joinToString("") { it.text } }
                        }
                val rich =
                    children(node)
                        .flatMap { children(it) }
                        .filterIsInstance<TableRow>()
                        .map { row ->
                            children(row).map { cell ->
                                inline(cell).let { spans ->
                                    if (spans.any { it.text.isNotBlank() })
                                        listOf(ContentBlock.Text(spans))
                                    else images(cell)
                                }
                            }
                        }
                listOf(ContentBlock.Table(rows, rich))
            }
            is HtmlBlock -> htmlBlocks(node.literal, depth)
            else -> children(node).flatMap { blocks(it, collapses, depth) }
        }

    private fun htmlBlocks(source: String, depth: Int): List<ContentBlock> {
        val doc = Jsoup.parseBodyFragment(source)
        doc.select("script,style,iframe,object,embed,form,input,button").remove()
        val result = mutableListOf<ContentBlock>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotBlank()) {
                result += parseInternal(text.toString(), depth + 1)
                text.clear()
            }
        }
        fun walk(node: org.jsoup.nodes.Node) {
            if (node is org.jsoup.nodes.TextNode) {
                text.append(node.wholeText)
                return
            }
            if (node !is org.jsoup.nodes.Element) return
            when (node.normalName()) {
                "table" -> {
                    flush()
                    val rows =
                        node
                            .select("tr")
                            .filter {
                                it.parents().firstOrNull { p -> p.normalName() == "table" } === node
                            }
                            .map {
                                it.children().filter { c -> c.normalName() in listOf("th", "td") }
                            }
                    result +=
                        ContentBlock.Table(
                            rows.map { row -> row.map { it.text() } },
                            rows.map { row -> row.map { htmlBlocks(it.html(), depth + 1) } },
                        )
                }
                "img" -> {
                    flush()
                    UrlPolicy.resource(node.attr("src"))?.let {
                        result += ContentBlock.Image(it, node.attr("alt"))
                    }
                }
                "a" -> {
                    val url = UrlPolicy.link(node.attr("href"))
                    if (url != null)
                        text
                            .append('[')
                            .append(node.text().ifBlank { url })
                            .append("](")
                            .append(url.replace(" ", "%20"))
                            .append(')')
                    else node.childNodes().forEach(::walk)
                    node.select("img").forEach { image ->
                        flush()
                        UrlPolicy.resource(image.attr("src"))?.let {
                            result += ContentBlock.Image(it, image.attr("alt"))
                        }
                    }
                }
                "br" -> text.append('\n')
                "video",
                "audio" -> {
                    flush()
                    val url =
                        UrlPolicy.link(
                            node.attr("src").ifBlank {
                                node.selectFirst("source")?.attr("src").orEmpty()
                            }
                        )
                    if (url != null)
                        result += ContentBlock.Text(listOf(RichText("打开嵌入媒体", link = url)))
                }
                else -> {
                    if (node.isBlock) text.append('\n')
                    node.childNodes().forEach(::walk)
                    if (node.isBlock) text.append('\n')
                }
            }
        }
        doc.body().childNodes().forEach(::walk)
        flush()
        return result
    }
}
