/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.markdown.IncomingMarkdownBlock
import io.element.android.features.messages.impl.timeline.markdown.IncomingMarkdownDocument
import io.element.android.features.messages.impl.timeline.markdown.IncomingMarkdownInlineContent
import io.element.android.features.messages.impl.timeline.markdown.IncomingMarkdownInlineStyle
import io.element.android.features.messages.impl.timeline.markdown.IncomingMarkdownTableAlignment
import io.element.android.features.messages.impl.timeline.markdown.IncomingMarkdownTableCell
import io.element.android.features.messages.impl.timeline.markdown.IncomingMarkdownTableRow
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.wysiwyg.link.Link
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal const val INCOMING_MARKDOWN_TABLE_TAG = "incoming_markdown_table"

@Composable
fun IncomingMarkdownView(
    document: IncomingMarkdownDocument,
    rawBody: String,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit = {},
) {
    MarkdownBlocks(
        blocks = document.blocks,
        onLinkClick = onLinkClick,
        onLinkLongClick = onLinkLongClick,
        onLongClick = onLongClick,
        modifier = modifier
            .onSizeChanged { size ->
                onContentLayoutChange(
                    ContentAvoidingLayoutData(
                        contentWidth = size.width,
                        contentHeight = size.height,
                    )
                )
            }
            .clearAndSetSemantics { contentDescription = rawBody },
    )
}

@Composable
private fun MarkdownBlocks(
    blocks: ImmutableList<IncomingMarkdownBlock>,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            MarkdownBlock(
                block = block,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
private fun MarkdownBlock(
    block: IncomingMarkdownBlock,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    when (block) {
        is IncomingMarkdownBlock.Heading -> MarkdownInlineText(
            content = block.content,
            style = headingStyle(block.level),
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
            onLongClick = onLongClick,
            modifier = Modifier.semantics { heading() },
        )
        is IncomingMarkdownBlock.Paragraph -> MarkdownInlineText(
            content = block.content,
            style = ElementTheme.typography.fontBodyLgRegular,
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
            onLongClick = onLongClick,
        )
        is IncomingMarkdownBlock.Table -> MarkdownTable(
            table = block,
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
            onLongClick = onLongClick,
        )
        is IncomingMarkdownBlock.BlockQuote -> Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(ElementTheme.colors.separatorPrimary)
            )
            MarkdownBlocks(
                blocks = block.blocks,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
                onLongClick = onLongClick,
            )
        }
        is IncomingMarkdownBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (block.ordered) "${block.startNumber + index}." else "\u2022",
                        color = ElementTheme.colors.textSecondary,
                        style = ElementTheme.typography.fontBodyLgRegular,
                    )
                    MarkdownBlocks(
                        blocks = item,
                        onLinkClick = onLinkClick,
                        onLinkLongClick = onLinkLongClick,
                        onLongClick = onLongClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        is IncomingMarkdownBlock.Literal -> MarkdownInlineText(
            content = IncomingMarkdownInlineContent(block.text, persistentListOf()),
            style = ElementTheme.typography.fontBodyLgRegular,
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
            onLongClick = onLongClick,
        )
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> ElementTheme.typography.fontHeadingLgBold
    2 -> ElementTheme.typography.fontHeadingMdBold
    3 -> ElementTheme.typography.fontHeadingSmMedium
    else -> ElementTheme.typography.fontBodyLgMedium
}

@Composable
private fun MarkdownTable(
    table: IncomingMarkdownBlock.Table,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    if (table.rows.isEmpty()) return
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val columnCount = table.rows.maxOf { it.cells.size }
        if (columnCount == 0) return@BoxWithConstraints
        val columnWidths = remember(table, maxWidth) {
            table.columnWidths(columnCount, maxWidth)
        }
        val tableWidth = columnWidths.fold(0.dp, Dp::plus)
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .testTag(INCOMING_MARKDOWN_TABLE_TAG)
        ) {
            Column(modifier = Modifier.width(tableWidth)) {
                table.rows.forEach { row ->
                    MarkdownTableRow(
                        row = row,
                        columnCount = columnCount,
                        columnWidths = columnWidths,
                        onLinkClick = onLinkClick,
                        onLinkLongClick = onLinkLongClick,
                        onLongClick = onLongClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    row: IncomingMarkdownTableRow,
    columnCount: Int,
    columnWidths: List<Dp>,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        repeat(columnCount) { columnIndex ->
            val cell = row.cells.getOrNull(columnIndex)
            MarkdownTableCell(
                cell = cell,
                width = columnWidths[columnIndex],
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
private fun MarkdownTableCell(
    cell: IncomingMarkdownTableCell?,
    width: Dp,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val isHeader = cell?.header == true
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(if (isHeader) ElementTheme.colors.bgSubtleSecondary else ElementTheme.colors.bgCanvasDefault)
            .border(0.5.dp, ElementTheme.colors.separatorPrimary)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        MarkdownInlineText(
            content = cell?.content ?: IncomingMarkdownInlineContent("", persistentListOf()),
            style = if (isHeader) ElementTheme.typography.fontBodyMdMedium else ElementTheme.typography.fontBodyMdRegular,
            textAlign = when (cell?.alignment) {
                IncomingMarkdownTableAlignment.CENTER -> TextAlign.Center
                IncomingMarkdownTableAlignment.END -> TextAlign.End
                IncomingMarkdownTableAlignment.START, null -> TextAlign.Start
            },
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun IncomingMarkdownBlock.Table.columnWidths(columnCount: Int, availableWidth: Dp): List<Dp> {
    val desiredWidths = List(columnCount) { columnIndex ->
        val longestLine = rows.maxOf { row ->
            row.cells.getOrNull(columnIndex)?.content?.text?.lineSequence()?.maxOfOrNull(String::length) ?: 0
        }
        (longestLine * APPROXIMATE_CHARACTER_WIDTH_DP + CELL_HORIZONTAL_PADDING_DP)
            .coerceIn(MIN_COLUMN_WIDTH_DP, MAX_COLUMN_WIDTH_DP)
            .dp
    }
    val desiredTableWidth = desiredWidths.fold(0.dp, Dp::plus)
    if (desiredTableWidth >= availableWidth) return desiredWidths
    val extraPerColumn = (availableWidth - desiredTableWidth) / columnCount
    return desiredWidths.map { it + extraPerColumn }
}

@Composable
private fun MarkdownInlineText(
    content: IncomingMarkdownInlineContent,
    style: TextStyle,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val linkColor = ElementTheme.colors.textLinkExternal
    val codeBackground = ElementTheme.colors.bgSubtleSecondary
    val annotatedText = remember(content, linkColor, codeBackground) {
        buildAnnotatedString {
            append(content.text)
            content.spans.forEach { span ->
                if (span.start !in 0..length || span.endExclusive !in span.start..length) return@forEach
                when (val inlineStyle = span.style) {
                    IncomingMarkdownInlineStyle.Strong -> addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        span.start,
                        span.endExclusive,
                    )
                    IncomingMarkdownInlineStyle.Emphasis -> addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic),
                        span.start,
                        span.endExclusive,
                    )
                    IncomingMarkdownInlineStyle.Code -> addStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                        span.start,
                        span.endExclusive,
                    )
                    is IncomingMarkdownInlineStyle.Link -> {
                        addStyle(
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                            span.start,
                            span.endExclusive,
                        )
                        addStringAnnotation(
                            tag = LINK_ANNOTATION_TAG,
                            annotation = inlineStyle.destination,
                            start = span.start,
                            end = span.endExclusive,
                        )
                    }
                }
            }
        }
    }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val hasLinks = remember(content) { content.spans.any { it.style is IncomingMarkdownInlineStyle.Link } }
    val linkModifier = if (hasLinks) {
        Modifier.pointerInput(content, layoutResult) {
            detectTapGestures(
                onTap = { position ->
                    annotatedText.linkAt(position, layoutResult)?.let(onLinkClick)
                },
                onLongPress = { position ->
                    val link = annotatedText.linkAt(position, layoutResult)
                    if (link == null) onLongClick?.invoke() else onLinkLongClick(link)
                },
            )
        }
    } else {
        Modifier
    }
    Text(
        text = annotatedText,
        modifier = modifier.then(linkModifier),
        color = ElementTheme.colors.textPrimary,
        style = style,
        textAlign = textAlign,
        onTextLayout = { layoutResult = it },
    )
}

private fun AnnotatedString.linkAt(position: Offset, layoutResult: TextLayoutResult?): Link? {
    val layout = layoutResult ?: return null
    if (isEmpty()) return null
    val offset = layout.getOffsetForPosition(position).coerceIn(0, lastIndex)
    val annotation = getStringAnnotations(LINK_ANNOTATION_TAG, offset, offset + 1).firstOrNull() ?: return null
    return Link(
        url = annotation.item,
        text = substring(annotation.start, annotation.end),
    )
}

private const val LINK_ANNOTATION_TAG = "markdown_link"
private const val APPROXIMATE_CHARACTER_WIDTH_DP = 7
private const val CELL_HORIZONTAL_PADDING_DP = 24
private const val MIN_COLUMN_WIDTH_DP = 112
private const val MAX_COLUMN_WIDTH_DP = 220
