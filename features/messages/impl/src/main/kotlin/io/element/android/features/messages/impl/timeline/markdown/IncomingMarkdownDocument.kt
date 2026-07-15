/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.markdown

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class IncomingMarkdownDocument(
    val blocks: ImmutableList<IncomingMarkdownBlock>,
)

@Immutable
sealed interface IncomingMarkdownBlock {
    data class Heading(
        val level: Int,
        val content: IncomingMarkdownInlineContent,
    ) : IncomingMarkdownBlock

    data class Paragraph(
        val content: IncomingMarkdownInlineContent,
    ) : IncomingMarkdownBlock

    data class Table(
        val rows: ImmutableList<IncomingMarkdownTableRow>,
    ) : IncomingMarkdownBlock

    data class BlockQuote(
        val blocks: ImmutableList<IncomingMarkdownBlock>,
    ) : IncomingMarkdownBlock

    data class ListBlock(
        val ordered: Boolean,
        val startNumber: Int,
        val items: ImmutableList<ImmutableList<IncomingMarkdownBlock>>,
    ) : IncomingMarkdownBlock

    data class Literal(
        val text: String,
    ) : IncomingMarkdownBlock
}

@Immutable
data class IncomingMarkdownTableRow(
    val cells: ImmutableList<IncomingMarkdownTableCell>,
)

@Immutable
data class IncomingMarkdownTableCell(
    val content: IncomingMarkdownInlineContent,
    val header: Boolean,
    val alignment: IncomingMarkdownTableAlignment,
)

enum class IncomingMarkdownTableAlignment {
    START,
    CENTER,
    END,
}

@Immutable
data class IncomingMarkdownInlineContent(
    val text: String,
    val spans: ImmutableList<IncomingMarkdownInlineSpan>,
)

@Immutable
data class IncomingMarkdownInlineSpan(
    val start: Int,
    val endExclusive: Int,
    val style: IncomingMarkdownInlineStyle,
)

@Immutable
sealed interface IncomingMarkdownInlineStyle {
    data object Strong : IncomingMarkdownInlineStyle

    data object Emphasis : IncomingMarkdownInlineStyle

    data object Code : IncomingMarkdownInlineStyle

    data class Link(
        val destination: String,
    ) : IncomingMarkdownInlineStyle
}
