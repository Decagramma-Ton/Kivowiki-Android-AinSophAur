package wiki.kivo.core.content

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wiki.kivo.core.designsystem.*

// 对齐作为正文的局部环境向下传递。内层容器可以覆盖，结束后自动恢复外层；
// 不依赖 Text 的默认 style，防止标题、图注和表格指定字体后丢失对齐。
private val LocalContentTextAlign = compositionLocalOf { TextAlign.Start }

@Composable
fun ContentBlockView(block: ContentBlock, onLink: (String) -> Unit, onImage: (String) -> Unit) {
    val scale = LocalKivoSettings.current.readingScale
    val textAlign = LocalContentTextAlign.current
    val horizontalAlignment =
        when (textAlign) {
            TextAlign.Center -> Alignment.CenterHorizontally
            TextAlign.End -> Alignment.End
            else -> Alignment.Start
        }
    when (block) {
        is ContentBlock.Mermaid -> MermaidBlockView(block.source)
        is ContentBlock.Text ->
            Row(
                Modifier.fillMaxWidth()
                    .then(if (block.heading > 0) Modifier.semantics { heading() } else Modifier)
            ) {
                block.bullet?.let {
                    Text(
                        it,
                        Modifier.width(25.dp),
                        fontSize = (17 * scale).sp,
                        lineHeight = (29 * scale).sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val rich = buildAnnotatedString {
                    block.spans.forEachIndexed { index, span ->
                        if (span.image != null) {
                            appendInlineContent(
                                "image-$index",
                                span.image.description.ifBlank { "图片" },
                            )
                            return@forEachIndexed
                        }
                        val style =
                            SpanStyle(
                                fontWeight =
                                    if (span.bold || block.heading > 0) FontWeight.Bold
                                    else FontWeight.Normal,
                                fontStyle = if (span.italic) FontStyle.Italic else FontStyle.Normal,
                                fontFamily =
                                    if (span.code) FontFamily.Monospace else FontFamily.Default,
                                background =
                                    if (span.code) MaterialTheme.colorScheme.surfaceVariant
                                    else androidx.compose.ui.graphics.Color.Unspecified,
                            )
                        withStyle(style) {
                            if (span.link != null)
                                withLink(
                                    LinkAnnotation.Clickable(
                                        span.link,
                                        TextLinkStyles(
                                            SpanStyle(
                                                color = MaterialTheme.colorScheme.primary,
                                                textDecoration = TextDecoration.Underline,
                                            )
                                        ),
                                    ) {
                                        onLink(span.link)
                                    }
                                ) {
                                    append(span.text)
                                }
                            else append(span.text)
                        }
                    }
                }
                Text(
                    rich,
                    inlineContent =
                        block.spans
                            .mapIndexedNotNull { index, span ->
                                span.image?.let { image ->
                                    // 徽章等行内小图跟随对应文字，不能移到整段末尾或膨胀成全宽卡片。
                                    val width = (image.width ?: 50).coerceAtMost(120).dp
                                    val height =
                                        (image.height ?: image.width ?: 50).coerceAtMost(120).dp
                                    val density = LocalDensity.current
                                    "image-$index" to
                                        InlineTextContent(
                                            Placeholder(
                                                with(density) { width.toSp() },
                                                with(density) { height.toSp() },
                                                PlaceholderVerticalAlign.Center,
                                            )
                                        ) {
                                            KivoImage(
                                                image.url,
                                                image.description,
                                                Modifier.fillMaxSize().clickable {
                                                    onImage(image.url)
                                                },
                                                ContentScale.Fit,
                                            )
                                        }
                                }
                            }
                            .toMap(),
                    textAlign = textAlign,
                    fontSize =
                        ((if (block.heading > 0) (26 - block.heading.coerceAtMost(5)) else 17) *
                                scale)
                            .sp,
                    lineHeight = (29 * scale).sp,
                    modifier =
                        Modifier.weight(1f).padding(top = if (block.heading > 0) 12.dp else 0.dp),
                )
            }
        is ContentBlock.Image -> {
            // 已知宽高按作者尺寸占位；未知比例在同一个 Lazy item 的保存状态中保留，避免回滚时反复跳高。
            var ratio by
                rememberSaveable(block.url, block.width, block.height) {
                    mutableFloatStateOf(
                        if (block.width != null && block.height != null)
                            block.width.toFloat() / block.height
                        else 1.6f
                    )
                }
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KivoImage(
                    block.url,
                    block.description.ifBlank { "正文图片，点击查看" },
                    Modifier.widthIn(max = (block.width ?: 1200).dp)
                        .fillMaxWidth()
                        .then(
                            if (block.height != null && block.width == null)
                                Modifier.heightIn(max = block.height.dp)
                            else Modifier
                        )
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImage(block.url) },
                    ContentScale.Fit,
                    alignment =
                        when (textAlign) {
                            TextAlign.End -> Alignment.CenterEnd
                            TextAlign.Start -> Alignment.CenterStart
                            else -> Alignment.Center
                        },
                    onDimensions = { w, h ->
                        if (w > 0 && h > 0 && (block.width == null || block.height == null))
                            ratio = (w.toFloat() / h).coerceIn(.05f, 20f)
                    },
                )
                // alt 常为文件名，只供无障碍使用；仅作者明确写出的 title 才是可见图注。
                if (block.caption.isNotBlank()) {
                    remember(block.caption) { ContentParser.parse(block.caption) }
                        .forEach { ContentBlockView(it, onLink, onImage) }
                }
            }
        }
        is ContentBlock.Quote ->
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    block.blocks.forEach { ContentBlockView(it, onLink, onImage) }
                }
            }
        is ContentBlock.Admonition -> {
            val warning =
                block.kind in
                    setOf("warning", "caution", "attention", "danger", "error", "failure", "bug")
            val color =
                if (warning) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = .6f))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    block.title,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = textAlign,
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (warning) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                block.blocks.forEach { ContentBlockView(it, onLink, onImage) }
            }
        }
        is ContentBlock.Tabs -> {
            var selected by rememberSaveable { mutableIntStateOf(0) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.tabs.forEachIndexed { index, tab ->
                        FilterChip(
                            selected = selected == index,
                            onClick = { selected = index },
                            label = { Text(tab.first) },
                        )
                    }
                }
                block.tabs
                    .getOrNull(selected.coerceIn(0, (block.tabs.size - 1).coerceAtLeast(0)))
                    ?.second
                    ?.forEach { ContentBlockView(it, onLink, onImage) }
            }
        }
        is ContentBlock.Aligned -> {
            CompositionLocalProvider(
                LocalContentTextAlign provides
                    when (block.alignment) {
                        "center" -> TextAlign.Center
                        "right" -> TextAlign.End
                        "justify" -> TextAlign.Justify
                        else -> TextAlign.Start
                    }
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment =
                        when (block.alignment) {
                            "center" -> Alignment.CenterHorizontally
                            "right" -> Alignment.End
                            else -> Alignment.Start
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    block.blocks.forEach { ContentBlockView(it, onLink, onImage) }
                }
            }
        }
        is ContentBlock.Collapse -> {
            var expanded by rememberSaveable { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (LocalKivoSettings.current.reducedMotion) Modifier
                        else Modifier.animateContentSize()
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp),
                    )
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        block.title,
                        Modifier.weight(1f),
                        textAlign = textAlign,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        if (expanded) "收起" else "展开",
                    )
                }
                if (expanded)
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        block.blocks.forEach { ContentBlockView(it, onLink, onImage) }
                    }
            }
        }
        is ContentBlock.Table ->
            Column(
                Modifier.horizontalScroll(rememberScrollState())
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp),
                    )
            ) {
                block.rows.forEachIndexed { rowIndex, cells ->
                    Row(
                        Modifier.background(
                            if (rowIndex == 0) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        cells.forEachIndexed { columnIndex, text ->
                            val rich = block.richCells.getOrNull(rowIndex)?.getOrNull(columnIndex)
                            if (rich != null)
                                Column(
                                    Modifier.width(180.dp).padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rich.forEach { ContentBlockView(it, onLink, onImage) }
                                }
                            else
                                Text(
                                    text,
                                    Modifier.width(180.dp).padding(12.dp),
                                    textAlign = textAlign,
                                    fontSize = (14 * scale).sp,
                                    lineHeight = (23 * scale).sp,
                                    fontWeight =
                                        if (rowIndex == 0) FontWeight.SemiBold
                                        else FontWeight.Normal,
                                )
                        }
                    }
                }
            }
        is ContentBlock.Code ->
            Text(
                block.source,
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                textAlign = textAlign,
                fontFamily = FontFamily.Monospace,
                fontSize = (14 * scale).sp,
                lineHeight = (23 * scale).sp,
            )
        ContentBlock.Rule ->
            HorizontalDivider(
                Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
    }
}
