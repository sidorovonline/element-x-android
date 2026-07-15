/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.markdown

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IncomingMarkdownParserTest {
    @Test
    fun `parse activates for headings and GFM tables`() {
        val result = parser().parse(
            """
            # Build matrix

            | Component | Status |
            | :--- | ---: |
            | Parser | Ready |

            Details
            -------
            """.trimIndent()
        )

        assertThat(result).isNotNull()
        assertThat(result!!.blocks.filterIsInstance<IncomingMarkdownBlock.Heading>().map { it.level }).containsExactly(1, 2).inOrder()
        val table = result.blocks.filterIsInstance<IncomingMarkdownBlock.Table>().single()
        assertThat(table.rows).hasSize(2)
        assertThat(table.rows.first().cells.map { it.content.text }).containsExactly("Component", "Status").inOrder()
        assertThat(table.rows.first().cells.map { it.alignment })
            .containsExactly(IncomingMarkdownTableAlignment.START, IncomingMarkdownTableAlignment.END)
            .inOrder()
    }

    @Test
    fun `parse does not activate from text patterns without heading or table nodes`() {
        val parser = parser()

        assertThat(parser.parse("Ordinary text\nwith another line")).isNull()
        assertThat(parser.parse("`# code, not a heading`")).isNull()
        assertThat(parser.parse("| not | a table |\n| missing | delimiter | ")).isNull()
    }

    @Test
    fun `parse keeps raw HTML literal and inert`() {
        val source = """
            # <span>Visible heading</span>

            <script>alert('not executed')</script>
            <img src="https://example.org/tracker.png">
        """.trimIndent()

        val result = parser().parse(source)!!

        val heading = result.blocks.first() as IncomingMarkdownBlock.Heading
        assertThat(heading.content.text).isEqualTo("<span>Visible heading</span>")
        assertThat(result.blocks.filterIsInstance<IncomingMarkdownBlock.Literal>().joinToString("\n") { it.text })
            .contains("<script>alert('not executed')</script>")
        assertThat(result.blocks.filterIsInstance<IncomingMarkdownBlock.Literal>().joinToString("\n") { it.text })
            .contains("<img src=\"https://example.org/tracker.png\">")
    }

    @Test
    fun `parse exposes only supported link schemes as interactive spans`() {
        val result = parser().parse(
            "# Links\n\n[Web](https://example.org) [Matrix](matrix:r/example.org) " +
                "[Script](javascript:alert(1)) [Data](data:text/plain,bad) [Custom](custom:value)"
        )!!
        val paragraph = result.blocks.filterIsInstance<IncomingMarkdownBlock.Paragraph>().single()
        val destinations = paragraph.content.spans.mapNotNull { (it.style as? IncomingMarkdownInlineStyle.Link)?.destination }

        assertThat(destinations).containsExactly("https://example.org", "matrix:r/example.org").inOrder()
        assertThat(paragraph.content.text).contains("Script")
        assertThat(paragraph.content.text).contains("Data")
        assertThat(paragraph.content.text).contains("Custom")
    }

    @Test
    fun `parse supports escaped pipes inside table cells`() {
        val result = parser().parse(
            """
            | Value | Result |
            | --- | --- |
            | a\|b | kept |
            """.trimIndent()
        )!!

        val table = result.blocks.single() as IncomingMarkdownBlock.Table
        assertThat(table.rows.last().cells.first().content.text).isEqualTo("a|b")
    }

    @Test
    fun `parse accepts exact resource limits and rejects one over`() {
        assertThat(parser(IncomingMarkdownLimits(maxInputLength = 9)).parse("# 1234567")).isNotNull()
        assertThat(parser(IncomingMarkdownLimits(maxInputLength = 8)).parse("# 1234567")).isNull()

        val twoRowTable = "| A | B |\n|---|---|\n| 1 | 2 |"
        assertThat(parser(IncomingMarkdownLimits(maxRowsPerTable = 2)).parse(twoRowTable)).isNotNull()
        assertThat(parser(IncomingMarkdownLimits(maxRowsPerTable = 1)).parse(twoRowTable)).isNull()

        val threeColumnTable = "| A | B | C |\n|---|---|---|\n| 1 | 2 | 3 |"
        assertThat(parser(IncomingMarkdownLimits(maxColumnsPerTable = 3)).parse(threeColumnTable)).isNotNull()
        assertThat(parser(IncomingMarkdownLimits(maxColumnsPerTable = 2)).parse(threeColumnTable)).isNull()

        val longCellTable = "| Name |\n|---|\n| 12345 |"
        assertThat(parser(IncomingMarkdownLimits(maxCellLength = 5)).parse(longCellTable)).isNotNull()
        assertThat(parser(IncomingMarkdownLimits(maxCellLength = 4)).parse(longCellTable)).isNull()

        val fourCellTable = "| A | B |\n|---|---|\n| 1 | 2 |"
        assertThat(parser(IncomingMarkdownLimits(maxTotalTableCells = 4)).parse(fourCellTable)).isNotNull()
        assertThat(parser(IncomingMarkdownLimits(maxTotalTableCells = 3)).parse(fourCellTable)).isNull()
    }

    @Test
    fun `parse rejects excessive AST nodes and nesting`() {
        val heading = "# Heading"
        assertThat(parser(IncomingMarkdownLimits(maxAstNodes = 3)).parse(heading)).isNotNull()
        assertThat(parser(IncomingMarkdownLimits(maxAstNodes = 2)).parse(heading)).isNull()

        val nestedHeading = "> > # Heading"
        assertThat(parser(IncomingMarkdownLimits(maxNestingDepth = 4)).parse(nestedHeading)).isNotNull()
        assertThat(parser(IncomingMarkdownLimits(maxNestingDepth = 3)).parse(nestedHeading)).isNull()
    }

    @Test
    fun `default limits match the untrusted input contract`() {
        assertThat(IncomingMarkdownLimits()).isEqualTo(
            IncomingMarkdownLimits(
                maxInputLength = 65_536,
                maxRowsPerTable = 100,
                maxColumnsPerTable = 20,
                maxCellLength = 4_096,
                maxTotalTableCells = 1_000,
                maxAstNodes = 4_096,
                maxNestingDepth = 32,
            )
        )
    }

    private fun parser(limits: IncomingMarkdownLimits = IncomingMarkdownLimits()) = DefaultIncomingMarkdownParser(limits)
}
