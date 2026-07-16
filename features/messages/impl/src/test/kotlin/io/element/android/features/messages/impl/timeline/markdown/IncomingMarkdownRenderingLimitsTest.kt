/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.markdown

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.textcomposer.mentions.MentionType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test

class IncomingMarkdownRenderingLimitsTest {
    @Test
    fun `native mention table cells are bounded at the rendering limit`() {
        assertThat(documentWithMentionCells(16).isWithinRenderingLimits()).isTrue()
        assertThat(documentWithMentionCells(17).isWithinRenderingLimits()).isFalse()
    }

    @Test
    fun `large tables without native mention cells remain accepted`() {
        val cells = List(1_000) {
            IncomingMarkdownTableCell(
                content = IncomingMarkdownInlineContent("value", persistentListOf()),
                header = false,
                alignment = IncomingMarkdownTableAlignment.START,
            )
        }.toImmutableList()
        val document = IncomingMarkdownDocument(
            blocks = persistentListOf(
                IncomingMarkdownBlock.Table(
                    rows = persistentListOf(IncomingMarkdownTableRow(cells))
                )
            )
        )

        assertThat(document.isWithinRenderingLimits()).isTrue()
    }

    private fun documentWithMentionCells(count: Int): IncomingMarkdownDocument {
        val mentionStyle = IncomingMarkdownInlineStyle.Mention(MentionType.User(UserId("@alice:example.org")))
        val cells = List(count) {
            IncomingMarkdownTableCell(
                content = IncomingMarkdownInlineContent(
                    text = "@",
                    spans = persistentListOf(IncomingMarkdownInlineSpan(0, 1, mentionStyle)),
                ),
                header = false,
                alignment = IncomingMarkdownTableAlignment.START,
            )
        }.toImmutableList()
        return IncomingMarkdownDocument(
            blocks = persistentListOf(
                IncomingMarkdownBlock.Table(
                    rows = persistentListOf(IncomingMarkdownTableRow(cells))
                )
            )
        )
    }
}
