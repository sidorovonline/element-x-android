/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.markdown

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import androidx.core.text.getSpans
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.features.messages.impl.utils.TextPillificationHelper
import io.element.android.libraries.androidutils.text.safeLinkify
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.textcomposer.mentions.MentionSpan
import io.element.android.libraries.textcomposer.mentions.getMentionSpans
import io.element.android.wysiwyg.view.spans.InlineCodeSpan
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.max
import kotlin.math.min

interface IncomingMarkdownCompatibilityFormatter {
    fun format(document: IncomingMarkdownDocument): IncomingMarkdownDocument
}

@ContributesBinding(RoomScope::class)
@Inject
class DefaultIncomingMarkdownCompatibilityFormatter(
    private val textPillificationHelper: TextPillificationHelper,
) : IncomingMarkdownCompatibilityFormatter {
    override fun format(document: IncomingMarkdownDocument): IncomingMarkdownDocument {
        return document.copy(blocks = formatBlocks(document.blocks))
    }

    private fun formatBlocks(blocks: ImmutableList<IncomingMarkdownBlock>): ImmutableList<IncomingMarkdownBlock> {
        return blocks.map { block ->
            when (block) {
                is IncomingMarkdownBlock.Heading -> block.copy(content = format(block.content))
                is IncomingMarkdownBlock.Paragraph -> block.copy(content = format(block.content))
                is IncomingMarkdownBlock.Table -> block.copy(
                    rows = block.rows.map { row ->
                        row.copy(cells = row.cells.map { cell -> cell.copy(content = format(cell.content)) }.toImmutableList())
                    }.toImmutableList()
                )
                is IncomingMarkdownBlock.BlockQuote -> block.copy(blocks = formatBlocks(block.blocks))
                is IncomingMarkdownBlock.ListBlock -> block.copy(
                    items = block.items.map(::formatBlocks).toImmutableList()
                )
                is IncomingMarkdownBlock.Literal -> block
            }
        }.toImmutableList()
    }

    private fun format(content: IncomingMarkdownInlineContent): IncomingMarkdownInlineContent {
        val codeRanges = content.spans
            .filter { it.style == IncomingMarkdownInlineStyle.Code }
            .mapNotNull { span ->
                if (span.start < 0 || span.endExclusive <= span.start || span.endExclusive > content.text.length) null
                else SourceRange(span.start, span.endExclusive)
            }
            .sortedBy(SourceRange::start)
        val spanned = SpannableStringBuilder()
        var cursor = 0
        codeRanges.forEach { codeRange ->
            if (cursor < codeRange.start) spanned.append(content.segment(cursor, codeRange.start, applyCompatibility = true))
            spanned.append(content.segment(codeRange.start, codeRange.endExclusive, applyCompatibility = false))
            cursor = codeRange.endExclusive
        }
        if (cursor < content.text.length) spanned.append(content.segment(cursor, content.text.length, applyCompatibility = true))
        val spans = buildList {
            spanned.getSpans<StyleSpan>().forEach { span ->
                if (span.style and Typeface.BOLD != 0) add(spanned.rangeOf(span, IncomingMarkdownInlineStyle.Strong))
                if (span.style and Typeface.ITALIC != 0) add(spanned.rangeOf(span, IncomingMarkdownInlineStyle.Emphasis))
            }
            spanned.getSpans<InlineCodeSpan>().forEach { span ->
                add(spanned.rangeOf(span, IncomingMarkdownInlineStyle.Code))
            }
            spanned.getSpans<URLSpan>().forEach { span ->
                add(spanned.rangeOf(span, IncomingMarkdownInlineStyle.Link(span.url)))
            }
            spanned.getMentionSpans().forEach { span ->
                add(spanned.rangeOf(span, IncomingMarkdownInlineStyle.Mention(span.type)))
            }
        }
            .filter { it.start >= 0 && it.endExclusive > it.start && it.endExclusive <= spanned.length }
            .distinct()
            .mergeAdjacentStyles()
            .toImmutableList()
        return IncomingMarkdownInlineContent(
            text = spanned.toString(),
            spans = spans,
        )
    }

    private fun IncomingMarkdownInlineContent.segment(
        start: Int,
        endExclusive: Int,
        applyCompatibility: Boolean,
    ): CharSequence {
        val styledSegment = SpannableStringBuilder(text.substring(start, endExclusive)).apply {
            spans.forEach { span ->
                val localStart = max(span.start, start) - start
                val localEnd = min(span.endExclusive, endExclusive) - start
                if (localStart < 0 || localEnd <= localStart || localEnd > length) return@forEach
                val androidSpan = when (val style = span.style) {
                    IncomingMarkdownInlineStyle.Strong -> StyleSpan(Typeface.BOLD)
                    IncomingMarkdownInlineStyle.Emphasis -> StyleSpan(Typeface.ITALIC)
                    IncomingMarkdownInlineStyle.Code -> InlineCodeSpan()
                    is IncomingMarkdownInlineStyle.Link -> URLSpan(style.destination)
                    is IncomingMarkdownInlineStyle.Mention -> MentionSpan(style.type)
                }
                setSpan(androidSpan, localStart, localEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (!applyCompatibility) {
            styledSegment.getSpans<URLSpan>().forEach(styledSegment::removeSpan)
            styledSegment.getMentionSpans().forEach(styledSegment::removeSpan)
            return styledSegment
        }
        val compatibleText = textPillificationHelper.pillify(styledSegment).safeLinkify()
        return if (compatibleText is Spanned) compatibleText else styledSegment
    }

    private fun Spanned.rangeOf(span: Any, style: IncomingMarkdownInlineStyle): IncomingMarkdownInlineSpan {
        return IncomingMarkdownInlineSpan(
            start = getSpanStart(span),
            endExclusive = getSpanEnd(span),
            style = style,
        )
    }

    private val IncomingMarkdownInlineStyle.sortOrder: Int
        get() = when (this) {
            IncomingMarkdownInlineStyle.Strong -> 0
            IncomingMarkdownInlineStyle.Emphasis -> 1
            IncomingMarkdownInlineStyle.Code -> 2
            is IncomingMarkdownInlineStyle.Link -> 3
            is IncomingMarkdownInlineStyle.Mention -> 4
        }

    private fun List<IncomingMarkdownInlineSpan>.mergeAdjacentStyles(): List<IncomingMarkdownInlineSpan> {
        return groupBy(IncomingMarkdownInlineSpan::style)
            .flatMap { (style, styleSpans) ->
                buildList {
                    var current: IncomingMarkdownInlineSpan? = null
                    styleSpans.sortedBy(IncomingMarkdownInlineSpan::start).forEach { next ->
                        val previous = current
                        if (previous == null) {
                            current = next
                        } else if (next.start <= previous.endExclusive) {
                            current = previous.copy(endExclusive = max(previous.endExclusive, next.endExclusive))
                        } else {
                            add(previous)
                            current = next
                        }
                    }
                    current?.let(::add)
                }.map { it.copy(style = style) }
            }
            .sortedWith(compareBy(IncomingMarkdownInlineSpan::start, IncomingMarkdownInlineSpan::endExclusive, { it.style.sortOrder }))
    }

    private data class SourceRange(val start: Int, val endExclusive: Int)
}
