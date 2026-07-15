/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.markdown

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.toImmutableList
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import java.net.URI
import java.util.ArrayDeque

interface IncomingMarkdownParser {
    fun parse(source: String): IncomingMarkdownDocument?
}

internal data class IncomingMarkdownLimits(
    val maxInputLength: Int = 65_536,
    val maxRowsPerTable: Int = 100,
    val maxColumnsPerTable: Int = 20,
    val maxCellLength: Int = 4_096,
    val maxTotalTableCells: Int = 1_000,
    val maxAstNodes: Int = 4_096,
    val maxNestingDepth: Int = 32,
)

@ContributesBinding(AppScope::class)
class DefaultIncomingMarkdownParser internal constructor(
    private val limits: IncomingMarkdownLimits,
) : IncomingMarkdownParser {
    @Inject
    constructor() : this(IncomingMarkdownLimits())

    private val parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        .maxOpenBlockParsers(limits.maxNestingDepth)
        .build()

    override fun parse(source: String): IncomingMarkdownDocument? {
        if (source.length > limits.maxInputLength) return null
        val root = try {
            parser.parse(source)
        } catch (_: Exception) {
            return null
        } as? Document ?: return null
        if (!validate(root, source)) return null
        return IncomingMarkdownDocument(
            blocks = mapBlocks(root, source).toImmutableList(),
        )
    }

    private fun validate(root: Node, source: String): Boolean {
        var nodeCount = 0
        var totalTableCells = 0
        var containsSupportedStructure = false
        val pending = ArrayDeque<NodeAtDepth>().apply { add(NodeAtDepth(root, 0)) }

        while (pending.isNotEmpty()) {
            val (node, depth) = pending.removeLast()
            nodeCount++
            if (nodeCount > limits.maxAstNodes || depth > limits.maxNestingDepth) return false
            when (node) {
                is Heading -> containsSupportedStructure = true
                is TableBlock -> {
                    containsSupportedStructure = true
                    val tableCellCount = validateTable(node, source) ?: return false
                    totalTableCells += tableCellCount
                    if (totalTableCells > limits.maxTotalTableCells) return false
                }
            }
            node.children().forEach { pending.add(NodeAtDepth(it, depth + 1)) }
        }
        return containsSupportedStructure
    }

    private fun validateTable(table: TableBlock, source: String): Int? {
        var rowCount = 0
        var cellCount = 0
        table.sections().forEach { section ->
            section.children().filterIsInstance<TableRow>().forEach { row ->
                rowCount++
                if (rowCount > limits.maxRowsPerTable) return null
                val cells = row.children().filterIsInstance<TableCell>().toList()
                if (cells.size > limits.maxColumnsPerTable) return null
                cellCount += cells.size
                cells.forEach { cell ->
                    if (inlineTextLength(cell, source) > limits.maxCellLength) return null
                }
            }
        }
        return cellCount
    }

    private fun inlineTextLength(parent: Node, source: String): Int {
        var length = 0
        val pending = ArrayDeque<Node>().apply { parent.children().forEach { add(it) } }
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            length += when (node) {
                is Text -> node.literal.length
                is Code -> node.literal.length
                is HtmlInline -> node.literal.length
                is SoftLineBreak, is HardLineBreak -> 1
                is Image -> sourceSlice(node, source).length
                else -> 0
            }
            if (length > limits.maxCellLength) return length
            node.children().forEach { pending.add(it) }
        }
        return length
    }

    private fun mapBlocks(parent: Node, source: String): List<IncomingMarkdownBlock> {
        return parent.children().map { node ->
            when (node) {
                is Heading -> IncomingMarkdownBlock.Heading(node.level, mapInline(node, source))
                is Paragraph -> IncomingMarkdownBlock.Paragraph(mapInline(node, source))
                is TableBlock -> mapTable(node, source)
                is BlockQuote -> IncomingMarkdownBlock.BlockQuote(mapBlocks(node, source).toImmutableList())
                is BulletList -> mapList(node, source, ordered = false, startNumber = 1)
                is OrderedList -> mapList(node, source, ordered = true, startNumber = node.markerStartNumber ?: 1)
                else -> IncomingMarkdownBlock.Literal(sourceSlice(node, source))
            }
        }.toList()
    }

    private fun mapList(node: Node, source: String, ordered: Boolean, startNumber: Int): IncomingMarkdownBlock.ListBlock {
        val items = node.children()
            .filterIsInstance<ListItem>()
            .map { mapBlocks(it, source).toImmutableList() }
            .toList()
            .toImmutableList()
        return IncomingMarkdownBlock.ListBlock(
            ordered = ordered,
            startNumber = startNumber,
            items = items,
        )
    }

    private fun mapTable(table: TableBlock, source: String): IncomingMarkdownBlock.Table {
        val rows = buildList {
            table.sections().forEach { section ->
                section.children().filterIsInstance<TableRow>().forEach { row ->
                    add(
                        IncomingMarkdownTableRow(
                            cells = row.children().filterIsInstance<TableCell>().map { cell ->
                                IncomingMarkdownTableCell(
                                    content = mapInline(cell, source),
                                    header = cell.isHeader,
                                    alignment = when (cell.alignment) {
                                        TableCell.Alignment.CENTER -> IncomingMarkdownTableAlignment.CENTER
                                        TableCell.Alignment.RIGHT -> IncomingMarkdownTableAlignment.END
                                        TableCell.Alignment.LEFT, null -> IncomingMarkdownTableAlignment.START
                                    },
                                )
                            }.toList().toImmutableList()
                        )
                    )
                }
            }
        }
        return IncomingMarkdownBlock.Table(rows.toImmutableList())
    }

    private fun mapInline(parent: Node, source: String): IncomingMarkdownInlineContent {
        val builder = InlineBuilder(source)
        parent.children().forEach(builder::append)
        return builder.build()
    }

    private fun sourceSlice(node: Node, source: String): String {
        val spans = node.sourceSpans
        if (spans.isEmpty()) return ""
        val start = spans.minOf { it.inputIndex }.coerceIn(0, source.length)
        val end = spans.maxOf { it.inputIndex + it.length }.coerceIn(start, source.length)
        return source.substring(start, end)
    }

    private inner class InlineBuilder(
        private val source: String,
    ) {
        private val text = StringBuilder()
        private val spans = mutableListOf<IncomingMarkdownInlineSpan>()

        fun append(node: Node) {
            when (node) {
                is Text -> text.append(node.literal)
                is Code -> appendStyled(node, IncomingMarkdownInlineStyle.Code) { text.append(node.literal) }
                is SoftLineBreak, is HardLineBreak -> text.append('\n')
                is HtmlInline -> text.append(node.literal)
                is Image -> text.append(sourceSlice(node, source))
                is Emphasis -> appendStyled(node, IncomingMarkdownInlineStyle.Emphasis)
                is StrongEmphasis -> appendStyled(node, IncomingMarkdownInlineStyle.Strong)
                is Link -> {
                    val destination = node.destination.takeIf(::isSafeLinkDestination)
                    if (destination == null) {
                        appendChildren(node)
                    } else {
                        appendStyled(node, IncomingMarkdownInlineStyle.Link(destination))
                    }
                }
                else -> {
                    if (node.firstChild == null) {
                        text.append(sourceSlice(node, source))
                    } else {
                        appendChildren(node)
                    }
                }
            }
        }

        private fun appendStyled(node: Node, style: IncomingMarkdownInlineStyle, content: (() -> Unit)? = null) {
            val start = text.length
            if (content == null) appendChildren(node) else content()
            if (text.length > start) {
                spans += IncomingMarkdownInlineSpan(start, text.length, style)
            }
        }

        private fun appendChildren(node: Node) = node.children().forEach(::append)

        fun build() = IncomingMarkdownInlineContent(text.toString(), spans.toImmutableList())
    }

    private data class NodeAtDepth(val node: Node, val depth: Int)

    private fun isSafeLinkDestination(destination: String): Boolean {
        val scheme = runCatching { URI(destination).scheme?.lowercase() }.getOrNull() ?: return false
        return scheme in SAFE_LINK_SCHEMES
    }

    private companion object {
        val SAFE_LINK_SCHEMES = setOf("https", "http", "matrix", "mailto", "tel")
    }
}

private fun Node.children(): Sequence<Node> = generateSequence(firstChild) { it.next }

private fun TableBlock.sections(): Sequence<Node> = children().filter { it is TableHead || it is TableBody }
