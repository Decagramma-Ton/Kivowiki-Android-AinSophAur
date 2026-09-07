package wiki.kivo.core.content

import java.util.Base64

/**
 * 只扫描图片节点的边界，不用正则重写整篇正文。标题内的 Markdown 链接允许括号， URL 也允许配对括号；反斜杠转义和引号必须先于右括号处理。 尺寸通过私有标题载体交给
 * CommonMark AST，避免以 URL 为键导致同图不同尺寸互相覆盖。
 */
internal object WikiImages {
    private val size = Regex("[ \\t]+=(\\d*)x(\\d*)[ \\t]*$")

    fun normalize(source: String): String {
        val out = StringBuilder()
        var cursor = 0
        while (cursor < source.length) {
            val start = source.indexOf("![", cursor)
            if (start < 0) break
            // 行内代码与围栏代码一样必须保留字面内容；匹配相同长度的反引号再继续。
            val code = source.indexOf('`', cursor)
            if (code in cursor until start) {
                var codeEnd = code
                while (codeEnd < source.length && source[codeEnd] == '`') codeEnd++
                val marker = source.substring(code, codeEnd)
                val closing = source.indexOf(marker, codeEnd)
                if (closing >= 0) {
                    cursor = (closing + marker.length).also { out.append(source, cursor, it) }
                    continue
                }
            }
            out.append(source, cursor, start)
            var endAlt = start + 2
            var brackets = 1
            while (endAlt < source.length && brackets > 0) {
                when (source[endAlt]) {
                    '\\' -> endAlt++
                    '[' -> brackets++
                    ']' -> brackets--
                }
                endAlt++
            }
            if (endAlt >= source.length || source[endAlt] != '(') {
                out.append("![")
                cursor = start + 2
                continue
            }
            var end = endAlt + 1
            var parentheses = 1
            var quote: Char? = null
            while (end < source.length && parentheses > 0 && source[end] != '\n') {
                val c = source[end]
                when {
                    c == '\\' -> end++
                    quote != null -> if (c == quote) quote = null
                    c == '"' || c == '\'' -> quote = c
                    c == '(' -> parentheses++
                    c == ')' -> parentheses--
                }
                end++
            }
            if (parentheses != 0) {
                out.append("![")
                cursor = start + 2
                continue
            }
            val inside = source.substring(endAlt + 1, end - 1)
            val match = size.find(inside)
            if (match == null) out.append(source, start, end)
            else {
                val ordinary = inside.substring(0, match.range.first).trim()
                val titleStart = Regex("\\s+[\"']").find(ordinary)?.range?.first
                val url = (if (titleStart == null) ordinary else ordinary.take(titleStart)).trim()
                val title =
                    if (titleStart == null) ""
                    else ordinary.substring(titleStart).trim().drop(1).dropLast(1)
                val encoded = Base64.getEncoder().encodeToString(title.toByteArray(Charsets.UTF_8))
                out.append(source, start, endAlt + 1)
                    .append(url)
                    .append(" \"kivo-size:")
                    .append(match.groupValues[1])
                    .append(':')
                    .append(match.groupValues[2])
                    .append(':')
                    .append(encoded)
                    .append("\")")
            }
            cursor = end
        }
        return out.append(source, cursor, source.length).toString()
    }

    fun image(url: String, alt: String, title: String?): ContentBlock.Image {
        val fields = title.orEmpty().split(':', limit = 4)
        if (fields.size != 4 || fields[0] != "kivo-size")
            return ContentBlock.Image(url, alt, caption = title.orEmpty())
        fun dimension(value: String) = value.toIntOrNull()?.takeIf { it in 1..16384 }
        val caption = runCatching {
            String(Base64.getDecoder().decode(fields[3]), Charsets.UTF_8)
        }.getOrDefault("")
        return ContentBlock.Image(url, alt, dimension(fields[1]), dimension(fields[2]), caption)
    }
}
