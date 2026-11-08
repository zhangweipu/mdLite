package com.example.mviapp.core.md

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mviapp.core.ui.config.LocalAppDimens
import com.example.mviapp.core.ui.display.MdReaderDisplay

/**
 * Markdown 渲染组件。
 *
 * 将解析后的块级元素在 Compose 中渲染。样式统一由 [rememberMarkdownStyle] 提供：
 * 普通模式沿用 MaterialTheme；电子墨水屏模式切换为纯黑白 + 黑色线框结构
 * （见 [MdReaderDisplay]），让文字落在彩墨屏的高分辨率黑白层上。
 *
 * @param blocks        解析后的 Markdown 块级元素
 * @param listState      外部传入的列表状态（用于保存/恢复滚动位置）
 * @param restoreIndex   恢复的滚动位置（块索引），<=0 表示不恢复
 * @param restoreOffset  恢复的滚动偏移
 * @param highlights     已标记（高亮）的内容块索引
 * @param onToggleHighlight 长按段落时回调（块索引 + 摘要文本）
 */
@Composable
fun MarkdownContent(
    blocks: List<MarkdownParser.MdBlock>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
    restoreIndex: Int = 0,
    restoreOffset: Int = 0,
    highlights: Set<Int> = emptySet(),
    onToggleHighlight: (Int, String) -> Unit = { _, _ -> },
) {
    val dimens = LocalAppDimens.current
    val style = rememberMarkdownStyle()

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

    // 彩墨屏刷新慢：松手后的惯性滚动会产生大量中间帧，形成明显拖影。
    // 关掉 fling 后只在手指按住时滚动，画面稳定且省电。
    val defaultFling = ScrollableDefaults.flingBehavior()
    val flingBehavior = if (style.params.flingEnabled) defaultFling else NoFlingBehavior

    val listContent: @Composable () -> Unit = {
        LazyColumn(
            modifier = modifier,
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        ) {
            itemsIndexed(blocks) { index, block ->
                HighlightableBlock(
                    block = block,
                    highlighted = index in highlights,
                    style = style,
                    onLongPress = {
                        onToggleHighlight(index, MarkdownParser.blockPreview(block))
                    },
                )
            }
        }
    }
    if (style.params.overscrollEnabled) {
        listContent()
    } else {
        WithoutOverscroll { listContent() }
    }
}

/**
 * 松手即停的滚动行为。普通模式不用它（会有「滑不动」的突兀感），
 * 只在电子墨水屏模式启用。返回 0 表示没有剩余速度需要传给父级嵌套滚动。
 */
private object NoFlingBehavior : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float = 0f
}

/** 可标记内容块：长按任意块级元素切换高亮 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HighlightableBlock(
    block: MarkdownParser.MdBlock,
    highlighted: Boolean,
    style: MarkdownStyle,
    onLongPress: () -> Unit,
) {
    val colors = style.colors
    val shape = RoundedCornerShape(style.cornerRadius)
    // 墨水屏不能用浅色底表示高亮（灰阶抖动成噪点），改用黑边框 + 左侧黑竖条
    val outline = highlighted && style.params.outlineHighlight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            )
            .background(
                color = when {
                    highlighted && outline -> Color.White
                    highlighted -> colors.highlightBg
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .then(
                if (outline) {
                    Modifier
                        .border(width = style.borderWidth, color = colors.border, shape = shape)
                        .drawBehind {
                            val bar = style.highlightBarWidth.toPx()
                            drawLine(
                                color = colors.highlightMark,
                                start = Offset(bar / 2f, 0f),
                                end = Offset(bar / 2f, size.height),
                                strokeWidth = bar,
                            )
                        }
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
    ) {
        when (block) {
            is MarkdownParser.MdBlock.Heading -> HeadingBlock(block, style)
            is MarkdownParser.MdBlock.Paragraph -> Text(
                text = inlineText(block.content, style),
                style = style.bodyLarge,
            )
            is MarkdownParser.MdBlock.CodeBlock -> CodeBlock(block, style)
            is MarkdownParser.MdBlock.Blockquote -> BlockquoteBlock(block, style)
            is MarkdownParser.MdBlock.UnorderedList -> BulletListBlock(block, style)
            is MarkdownParser.MdBlock.OrderedList -> NumberedListBlock(block, style)
            is MarkdownParser.MdBlock.Table -> TableBlock(block, style)
            MarkdownParser.MdBlock.HorizontalRule -> HorizontalDivider(
                thickness = if (style.params.highContrast) 2.dp else 1.dp,
                color = colors.divider,
            )
        }
    }
}

@Composable
private fun TableBlock(
    block: MarkdownParser.MdBlock.Table,
    style: MarkdownStyle,
) {
    val colors = style.colors
    val shape = RoundedCornerShape(style.cornerRadius)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // 先测量所有单元格文本，得到统一的列宽，保证各行列严格对齐
    val columnWidths = remember(block, textMeasurer, style, density) {
        computeColumnWidths(
            block = block,
            textMeasurer = textMeasurer,
            style = style,
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
                .border(width = style.borderWidth, color = colors.border, shape = shape),
        ) {
            TableRow(
                cells = block.header,
                alignments = block.alignments,
                columnWidths = columnWidths,
                isHeader = true,
                shaded = false,
                style = style,
            )
            block.rows.forEachIndexed { rowIndex, row ->
                HorizontalDivider(
                    thickness = style.borderWidth,
                    color = colors.divider,
                )
                TableRow(
                    cells = row,
                    alignments = block.alignments,
                    columnWidths = columnWidths,
                    isHeader = false,
                    shaded = style.params.zebraStripes && rowIndex % 2 == 1,
                    style = style,
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
    shaded: Boolean,
    style: MarkdownStyle,
) {
    val colors = style.colors
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .background(
                color = when {
                    isHeader -> colors.tableHeaderBg
                    shaded -> colors.tableZebraBg
                    else -> Color.Transparent
                },
            ),
    ) {
        cells.forEachIndexed { index, cell ->
            TableCell(
                text = inlineText(cell, style),
                alignment = alignments.getOrElse(index) { MarkdownParser.TableAlignment.Left },
                width = columnWidths.getOrElse(index) { MIN_COLUMN_WIDTH },
                isHeader = isHeader,
                style = style,
            )
            if (index < cells.lastIndex) {
                VerticalDivider(
                    thickness = style.borderWidth,
                    color = colors.divider,
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
    style: MarkdownStyle,
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
            style.bodyLarge.copy(
                fontWeight = if (style.params.highContrast) {
                    FontWeight.Bold
                } else {
                    FontWeight.SemiBold
                },
            )
        } else {
            style.bodyMedium
        },
    )
}

/** 按列测量单元格文本，返回统一的列宽（含内边距，限制在最小/最大之间） */
private fun computeColumnWidths(
    block: MarkdownParser.MdBlock.Table,
    textMeasurer: TextMeasurer,
    style: MarkdownStyle,
    density: Density,
): List<Dp> {
    val columnCount = block.header.size
    val maxWidthPx = IntArray(columnCount)
    val cellStyle: TextStyle = style.bodyMedium
    val headerStyle: TextStyle = style.bodyLarge.copy(fontWeight = FontWeight.SemiBold)

    block.header.forEachIndexed { index, cell ->
        val width = textMeasurer.measure(
            text = inlineAnnotatedString(cell, style),
            style = headerStyle,
        ).size.width
        maxWidthPx[index] = maxOf(maxWidthPx[index], width)
    }
    block.rows.forEach { row ->
        row.forEachIndexed { index, cell ->
            val width = textMeasurer.measure(
                text = inlineAnnotatedString(cell, style),
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
private fun HeadingBlock(
    block: MarkdownParser.MdBlock.Heading,
    style: MarkdownStyle,
) {
    val base = style.heading[block.level.coerceIn(1, 6) - 1]
    val color = if (block.level == 1) style.colors.heading else style.colors.text
    Text(
        text = inlineText(block.content, style),
        style = base.copy(color = color),
    )
}

@Composable
private fun CodeBlock(
    block: MarkdownParser.MdBlock.CodeBlock,
    style: MarkdownStyle,
) {
    val shape = RoundedCornerShape(style.cornerRadius)
    // 墨水屏：白底 + 黑色实线边框取代浅灰卡片与阴影
    val borderModifier = if (style.params.highContrast) {
        Modifier.border(width = style.borderWidth, color = style.colors.border, shape = shape)
    } else {
        Modifier
    }
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = style.colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = style.cardElevation),
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = block.code,
                modifier = Modifier.padding(12.dp),
                style = style.code,
                softWrap = false,
            )
        }
    }
    // 长代码行仍由内部的 horizontalScroll 支持块内横滑，不受整页设置影响
}

@Composable
private fun BlockquoteBlock(
    block: MarkdownParser.MdBlock.Blockquote,
    style: MarkdownStyle,
) {
    val shape = RoundedCornerShape(style.cornerRadius)
    val background = if (style.params.highContrast) {
        style.colors.surface
    } else {
        style.colors.surface.copy(alpha = 0.5f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = background, shape = shape)
            .then(
                if (style.params.highContrast) {
                    Modifier.drawBehind {
                        val bar = 3.dp.toPx()
                        drawLine(
                            color = style.colors.border,
                            start = Offset(bar / 2f, 0f),
                            end = Offset(bar / 2f, size.height),
                            strokeWidth = bar,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(12.dp),
    ) {
        Text(
            text = inlineText(block.content, style),
            style = style.bodyMedium.copy(
                color = style.colors.quoteText,
                fontWeight = if (style.params.highContrast) {
                    FontWeight.SemiBold
                } else {
                    style.bodyMedium.fontWeight
                },
            ),
        )
    }
}

@Composable
private fun BulletListBlock(
    block: MarkdownParser.MdBlock.UnorderedList,
    style: MarkdownStyle,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "•",
                    style = style.bullet,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    text = inlineText(item, style),
                    style = style.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun NumberedListBlock(
    block: MarkdownParser.MdBlock.OrderedList,
    style: MarkdownStyle,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${index + 1}.",
                    style = style.bullet,
                    modifier = Modifier.width(28.dp),
                )
                Text(
                    text = inlineText(item, style),
                    style = style.bodyLarge,
                )
            }
        }
    }
}

// ==================== 行内渲染 ====================

@Composable
private fun inlineText(
    inlines: List<MarkdownParser.MdInline>,
    style: MarkdownStyle,
): AnnotatedString = inlineAnnotatedString(inlines, style)

/** 构建行内 AnnotatedString，供渲染与列宽测量共用（纯函数） */
private fun inlineAnnotatedString(
    inlines: List<MarkdownParser.MdInline>,
    style: MarkdownStyle,
): AnnotatedString = buildAnnotatedString {
    val colors = style.colors
    val params = style.params
    inlines.forEach { inline ->
        when (inline) {
            is MarkdownParser.MdInline.Plain -> append(inline.text)
            is MarkdownParser.MdInline.Bold -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
            ) {
                append(inline.text)
            }
            is MarkdownParser.MdInline.Italic -> withStyle(
                if (params.italicAsBold) {
                    // 斜体细笔画在墨水屏上容易断，改用加粗强调
                    SpanStyle(fontStyle = FontStyle.Normal, fontWeight = FontWeight.SemiBold)
                } else {
                    SpanStyle(fontStyle = FontStyle.Italic)
                },
            ) {
                append(inline.text)
            }
            is MarkdownParser.MdInline.Code -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colors.inlineCodeBg,
                    color = colors.inlineCodeText,
                    fontWeight = if (params.inlineCodeInverted) {
                        FontWeight.Bold
                    } else {
                        null
                    },
                ),
            ) {
                append(inline.text)
            }
            is MarkdownParser.MdInline.Link -> withStyle(
                SpanStyle(
                    color = colors.link,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = if (params.highContrast) FontWeight.Bold else null,
                ),
            ) {
                append(inline.text)
            }
        }
    }
}
