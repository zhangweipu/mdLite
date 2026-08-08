package com.example.mviapp.core.md

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mviapp.core.ui.config.LocalAppDimens

/**
 * Markdown 渲染组件
 *
 * 将解析后的块级元素在 Compose 中渲染，
 * 样式统一使用 [MaterialTheme] 与全局尺寸配置 [LocalAppDimens]。
 *
 * @param blocks        解析后的 Markdown 块级元素
 * @param listState      外部传入的列表状态（用于保存/恢复滚动位置）
 * @param restoreIndex   恢复的滚动位置（块索引），<=0 表示不恢复
 * @param restoreOffset  恢复的滚动偏移
 */
@Composable
fun MarkdownContent(
    blocks: List<MarkdownParser.MdBlock>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    restoreIndex: Int = 0,
    restoreOffset: Int = 0,
) {
    val dimens = LocalAppDimens.current

    // 内容解析完成后恢复上次阅读位置（索引越界时自动收敛到末尾）
    LaunchedEffect(blocks) {
        if (restoreIndex > 0) {
            val maxIndex = (blocks.size - 1).coerceAtLeast(0)
            listState.scrollToItem(
                index = restoreIndex.coerceAtMost(maxIndex),
                scrollOffset = restoreOffset,
            )
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
    ) {
        itemsIndexed(blocks) { _, block ->
            when (block) {
                is MarkdownParser.MdBlock.Heading -> HeadingBlock(block)
                is MarkdownParser.MdBlock.Paragraph -> Text(
                    text = inlineText(block.content),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is MarkdownParser.MdBlock.CodeBlock -> CodeBlock(block)
                is MarkdownParser.MdBlock.Blockquote -> BlockquoteBlock(block)
                is MarkdownParser.MdBlock.UnorderedList -> BulletListBlock(block)
                is MarkdownParser.MdBlock.OrderedList -> NumberedListBlock(block)
                is MarkdownParser.MdBlock.Table -> TableBlock(block)
                MarkdownParser.MdBlock.HorizontalRule -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun TableBlock(block: MarkdownParser.MdBlock.Table) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellStyle = MaterialTheme.typography.bodyMedium
    val headerStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    // 先测量所有单元格文本，得到统一的列宽，保证各行列严格对齐
    val columnWidths = remember(block, textMeasurer, cellStyle, headerStyle, scheme, density) {
        computeColumnWidths(
            block = block,
            textMeasurer = textMeasurer,
            scheme = scheme,
            cellStyle = cellStyle,
            headerStyle = headerStyle,
            density = density,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .clip(shape)
                .border(width = 1.dp, color = scheme.outlineVariant, shape = shape),
        ) {
            TableRow(
                cells = block.header,
                alignments = block.alignments,
                columnWidths = columnWidths,
                isHeader = true,
            )
            block.rows.forEachIndexed { rowIndex, row ->
                HorizontalDivider(color = scheme.outlineVariant)
                TableRow(
                    cells = row,
                    alignments = block.alignments,
                    columnWidths = columnWidths,
                    isHeader = false,
                    shaded = rowIndex % 2 == 1,
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<List<MarkdownParser.MdInline>>,
    alignments: List<MarkdownParser.TableAlignment>,
    columnWidths: List<Dp>,
    isHeader: Boolean,
    shaded: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .background(
                color = when {
                    isHeader -> scheme.surfaceVariant
                    shaded -> scheme.surfaceVariant.copy(alpha = 0.4f)
                    else -> Color.Transparent
                },
            ),
    ) {
        cells.forEachIndexed { index, cell ->
            TableCell(
                text = inlineText(cell),
                alignment = alignments.getOrElse(index) { MarkdownParser.TableAlignment.Left },
                width = columnWidths.getOrElse(index) { MIN_COLUMN_WIDTH },
                isHeader = isHeader,
            )
            if (index < cells.lastIndex) {
                VerticalDivider(
                    color = scheme.outlineVariant,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun TableCell(
    text: AnnotatedString,
    alignment: MarkdownParser.TableAlignment,
    width: Dp,
    isHeader: Boolean,
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = CELL_HORIZONTAL_PADDING, vertical = 10.dp),
        textAlign = when (alignment) {
            MarkdownParser.TableAlignment.Left -> TextAlign.Start
            MarkdownParser.TableAlignment.Center -> TextAlign.Center
            MarkdownParser.TableAlignment.Right -> TextAlign.End
        },
        style = if (isHeader) {
            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.bodyMedium
        },
    )
}

/** 按列测量单元格文本，返回统一的列宽（含内边距，限制在最小/最大之间） */
private fun computeColumnWidths(
    block: MarkdownParser.MdBlock.Table,
    textMeasurer: TextMeasurer,
    scheme: ColorScheme,
    cellStyle: TextStyle,
    headerStyle: TextStyle,
    density: Density,
): List<Dp> {
    val columnCount = block.header.size
    val maxWidthPx = IntArray(columnCount)

    block.header.forEachIndexed { index, cell ->
        val width = textMeasurer.measure(
            text = inlineAnnotatedString(cell, scheme),
            style = headerStyle,
        ).size.width
        maxWidthPx[index] = maxOf(maxWidthPx[index], width)
    }
    block.rows.forEach { row ->
        row.forEachIndexed { index, cell ->
            val width = textMeasurer.measure(
                text = inlineAnnotatedString(cell, scheme),
                style = cellStyle,
            ).size.width
            maxWidthPx[index] = maxOf(maxWidthPx[index], width)
        }
    }

    return maxWidthPx.map { px ->
        val contentWidth = with(density) { px.toDp() }
        (contentWidth + CELL_HORIZONTAL_PADDING * 2)
            .coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
    }
}

private val MIN_COLUMN_WIDTH = 96.dp
private val MAX_COLUMN_WIDTH = 240.dp
private val CELL_HORIZONTAL_PADDING = 12.dp

// ==================== 块级渲染 ====================

@Composable
private fun HeadingBlock(block: MarkdownParser.MdBlock.Heading) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineMedium
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    val color = if (block.level == 1) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = inlineText(block.content),
        style = style.copy(color = color),
    )
}

@Composable
private fun CodeBlock(block: MarkdownParser.MdBlock.CodeBlock) {
    val dimens = LocalAppDimens.current
    Card(
        shape = RoundedCornerShape(dimens.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = block.code,
                modifier = Modifier.padding(12.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun BlockquoteBlock(block: MarkdownParser.MdBlock.Blockquote) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        Text(
            text = inlineText(block.content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BulletListBlock(block: MarkdownParser.MdBlock.UnorderedList) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    text = inlineText(item),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun NumberedListBlock(block: MarkdownParser.MdBlock.OrderedList) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    text = inlineText(item),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

// ==================== 行内渲染 ====================

@Composable
private fun inlineText(inlines: List<MarkdownParser.MdInline>): AnnotatedString =
    inlineAnnotatedString(inlines, MaterialTheme.colorScheme)

/** 构建行内 AnnotatedString，供渲染与列宽测量共用 */
private fun inlineAnnotatedString(
    inlines: List<MarkdownParser.MdInline>,
    scheme: ColorScheme,
): AnnotatedString = buildAnnotatedString {
    inlines.forEach { inline ->
        when (inline) {
            is MarkdownParser.MdInline.Plain -> append(inline.text)
            is MarkdownParser.MdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(inline.text)
            }
            is MarkdownParser.MdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(inline.text)
            }
            is MarkdownParser.MdInline.Code -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = scheme.surfaceVariant,
                    color = scheme.primary,
                ),
            ) {
                append(inline.text)
            }
            is MarkdownParser.MdInline.Link -> withStyle(
                SpanStyle(
                    color = scheme.primary,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(inline.text)
            }
        }
    }
}
