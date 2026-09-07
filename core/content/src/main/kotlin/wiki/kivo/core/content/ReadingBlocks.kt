package wiki.kivo.core.content

/** 对齐只改变样式，不应把整篇长文变成一个不可回收的 Lazy item。 在阅读边界拆分其直接子块，保留 AST 容器给预览/嵌套组件，嵌套方向仍由最内层覆盖。 */
fun List<ContentBlock>.readingBlocks(): List<ContentBlock> = flatMap { block ->
    if (block is ContentBlock.Aligned)
        block.blocks.readingBlocks().map {
            if (it is ContentBlock.Aligned) it
            else ContentBlock.Aligned(block.alignment, listOf(it))
        }
    else listOf(block)
}

/** 目录识别包在对齐容器内的标题，但正文仍由原始块渲染以保留对齐语义。 */
fun ContentBlock.readingHeading(): ContentBlock.Text? =
    when (this) {
        is ContentBlock.Text -> takeIf { heading > 0 }
        is ContentBlock.Aligned -> blocks.singleOrNull()?.readingHeading()
        else -> null
    }
