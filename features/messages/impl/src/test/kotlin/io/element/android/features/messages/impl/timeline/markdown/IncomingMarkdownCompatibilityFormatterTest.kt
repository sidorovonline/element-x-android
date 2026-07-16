/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.markdown

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.utils.FakeTextPillificationHelper
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.textcomposer.mentions.MentionSpan
import io.element.android.libraries.textcomposer.mentions.MentionType
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class IncomingMarkdownCompatibilityFormatterTest : RobolectricTest() {
    @Test
    fun `bare links and mentions retain compatibility spans in every rendered context`() {
        val source = """
            **Strong** https://example.org/paragraph @alice:example.org

            > Quote https://example.org/quote @alice:example.org

            - List https://example.org/list
            - Owner @alice:example.org

            | Kind | Value |
            |---|---|
            | Link | https://example.org/table |
            | Owner | @alice:example.org |
        """.trimIndent()

        val result = formatter().format(checkNotNull(DefaultIncomingMarkdownParser().parse(source)))
        val inlineContents = result.inlineContents()

        assertThat(inlineContents.flatMap { it.linkDestinations() }).containsAtLeast(
            "https://example.org/paragraph",
            "https://example.org/quote",
            "https://example.org/list",
            "https://example.org/table",
        )
        assertThat(inlineContents.flatMap { it.mentionTypes() })
            .containsExactlyElementsIn(List(4) { MentionType.User(UserId("@alice:example.org")) })
        assertThat(inlineContents.any { content ->
            content.spans.any { it.style == IncomingMarkdownInlineStyle.Strong }
        }).isTrue()
    }

    @Test
    fun `overlapping strong and links keep one safe action per destination`() {
        val source = "**https://example.org/bold** [Safe](https://example.org/explicit) [Script](javascript:alert(1))"

        val result = formatter().format(checkNotNull(DefaultIncomingMarkdownParser().parse(source)))
        val content = result.inlineContents().single()
        val links = content.spans.filter { it.style is IncomingMarkdownInlineStyle.Link }
        val strong = content.spans.single { it.style == IncomingMarkdownInlineStyle.Strong }

        assertThat(links.map { (it.style as IncomingMarkdownInlineStyle.Link).destination })
            .containsExactly("https://example.org/bold", "https://example.org/explicit")
        assertThat(links.count { it.start == strong.start && it.endExclusive == strong.endExclusive }).isEqualTo(1)
        assertThat(links.map { (it.style as IncomingMarkdownInlineStyle.Link).destination })
            .doesNotContain("javascript:alert(1)")
    }

    @Test
    fun `inline code keeps compatibility processing outside code only`() {
        val source = "`https://example.org/code @alice:example.org` and https://example.org/plain @alice:example.org"

        val result = formatter().format(checkNotNull(DefaultIncomingMarkdownParser().parse(source)))
        val content = result.inlineContents().single()
        val code = content.spans.single { it.style == IncomingMarkdownInlineStyle.Code }

        assertThat(content.linkDestinations()).containsExactly(
            "https://example.org/plain",
            "https://matrix.to/#/@alice:example.org",
        )
        assertThat(content.mentionTypes()).containsExactly(MentionType.User(UserId("@alice:example.org")))
        assertThat(content.spans.filter { it.style is IncomingMarkdownInlineStyle.Link }.none { link ->
            link.start < code.endExclusive && code.start < link.endExclusive
        }).isTrue()
    }

    private fun formatter(): DefaultIncomingMarkdownCompatibilityFormatter {
        return DefaultIncomingMarkdownCompatibilityFormatter(
            FakeTextPillificationHelper { text, _ -> pillifyMentions(text) }
        )
    }

    private fun pillifyMentions(text: CharSequence): CharSequence {
        val result = SpannableStringBuilder(text)
        MENTION_REGEX.findAll(result).toList().asReversed().forEach { match ->
            val userId = UserId(match.value)
            result.replace(match.range.first, match.range.last + 1, "@ ")
            result.setSpan(
                MentionSpan(MentionType.User(userId)),
                match.range.first,
                match.range.first + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            result.setSpan(
                URLSpan("https://matrix.to/#/${userId.value}"),
                match.range.first,
                match.range.first + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return result
    }

    private fun IncomingMarkdownDocument.inlineContents(): List<IncomingMarkdownInlineContent> {
        fun blocks(blocks: List<IncomingMarkdownBlock>): List<IncomingMarkdownInlineContent> = blocks.flatMap { block ->
            when (block) {
                is IncomingMarkdownBlock.Heading -> listOf(block.content)
                is IncomingMarkdownBlock.Paragraph -> listOf(block.content)
                is IncomingMarkdownBlock.Table -> block.rows.flatMap { row -> row.cells.map { it.content } }
                is IncomingMarkdownBlock.BlockQuote -> blocks(block.blocks)
                is IncomingMarkdownBlock.ListBlock -> block.items.flatMap(::blocks)
                is IncomingMarkdownBlock.Literal -> emptyList()
            }
        }
        return blocks(this.blocks)
    }

    private fun IncomingMarkdownInlineContent.linkDestinations(): List<String> {
        return spans.mapNotNull { (it.style as? IncomingMarkdownInlineStyle.Link)?.destination }
    }

    private fun IncomingMarkdownInlineContent.mentionTypes(): List<MentionType> {
        return spans.mapNotNull { (it.style as? IncomingMarkdownInlineStyle.Mention)?.type }
    }

    private companion object {
        val MENTION_REGEX = Regex("@[A-Za-z0-9._=/-]+:[A-Za-z0-9.-]+")
    }
}
