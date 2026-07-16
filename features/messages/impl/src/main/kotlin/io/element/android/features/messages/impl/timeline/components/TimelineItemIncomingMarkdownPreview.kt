/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.markdown.DefaultIncomingMarkdownCompatibilityFormatter
import io.element.android.features.messages.impl.timeline.markdown.DefaultIncomingMarkdownParser
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.utils.TextPillificationHelper
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.textcomposer.mentions.LocalMentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.MentionSpan
import io.element.android.libraries.textcomposer.mentions.MentionSpanFormatter
import io.element.android.libraries.textcomposer.mentions.MentionSpanTheme
import io.element.android.libraries.textcomposer.mentions.MentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.MentionType
import io.element.android.libraries.textcomposer.mentions.getMentionSpans

@PreviewsDayNight
@Composable
internal fun TimelineItemIncomingMarkdownPreview() = ElementPreview {
    val markdown = remember { checkNotNull(DefaultIncomingMarkdownParser().parse(MARKDOWN_PREVIEW_BODY)) }
    Column {
        ATimelineItemEventRow(
            event = aTimelineItemEvent(
                senderDisplayName = "Markdown sender",
                isMine = false,
                content = aTimelineItemTextContent(body = MARKDOWN_PREVIEW_BODY).copy(incomingMarkdown = markdown),
                groupPosition = TimelineItemGroupPosition.First,
            ),
        )
        Spacer(Modifier.height(24.dp))
        ATimelineItemEventRow(
            event = aTimelineItemEvent(
                isMine = true,
                content = aTimelineItemTextContent(body = MARKDOWN_PREVIEW_BODY).copy(incomingMarkdown = markdown),
                groupPosition = TimelineItemGroupPosition.First,
            ),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemIncomingMarkdownFallbackPreview() = ElementPreview {
    val rejectedMarkdown = remember {
        DefaultIncomingMarkdownParser().parse(OVER_LIMIT_TABLE_PREVIEW_BODY).also { check(it == null) }
    }
    ATimelineItemEventRow(
        event = aTimelineItemEvent(
            senderDisplayName = "Markdown sender",
            isMine = false,
            content = aTimelineItemTextContent(body = OVER_LIMIT_TABLE_PREVIEW_BODY).copy(incomingMarkdown = rejectedMarkdown),
            groupPosition = TimelineItemGroupPosition.First,
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun TimelineItemIncomingMarkdownStrongCompatibilityPreview() = ElementPreview {
    val markdown = remember {
        val parsed = checkNotNull(DefaultIncomingMarkdownParser().parse(STRONG_COMPATIBILITY_PREVIEW_BODY))
        DefaultIncomingMarkdownCompatibilityFormatter(PreviewTextPillificationHelper).format(parsed)
    }
    val mentionSpanTheme = remember { MentionSpanTheme(UserId("@me:example.org")) }
    mentionSpanTheme.updateStyles()
    val mentionSpanUpdater = remember { PreviewMentionSpanUpdater(mentionSpanTheme) }
    CompositionLocalProvider(LocalMentionSpanUpdater provides mentionSpanUpdater) {
        Column {
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    senderDisplayName = "Markdown sender",
                    isMine = false,
                    content = aTimelineItemTextContent(body = STRONG_COMPATIBILITY_PREVIEW_BODY).copy(incomingMarkdown = markdown),
                    groupPosition = TimelineItemGroupPosition.First,
                ),
            )
            Spacer(Modifier.height(24.dp))
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    isMine = true,
                    content = aTimelineItemTextContent(body = STRONG_COMPATIBILITY_PREVIEW_BODY),
                    groupPosition = TimelineItemGroupPosition.First,
                ),
            )
        }
    }
}

private val MARKDOWN_PREVIEW_BODY = """
    # Build matrix

    | Component | Status | Notes for phone layout |
    | :--- | :---: | ---: |
    | Parser | Ready | Incoming messages only |
    | Wide table | Ready | Scroll horizontally without moving the room timeline |

    ## Details

    Copy and Select Text keep the raw source.
""".trimIndent()

private val OVER_LIMIT_TABLE_PREVIEW_BODY = buildString {
    appendLine("# Literal fallback")
    appendLine()
    appendLine((1..21).joinToString(prefix = "| ", separator = " | ", postfix = " |") { "Column $it" })
    appendLine((1..21).joinToString(prefix = "| ", separator = " | ", postfix = " |") { "---" })
    append((1..21).joinToString(prefix = "| ", separator = " | ", postfix = " |") { "Value $it" })
}

private val STRONG_COMPATIBILITY_PREVIEW_BODY = """
    **Strong release ready**
    Visit https://example.org/docs
    Owner @alice:example.org
""".trimIndent()

private object PreviewTextPillificationHelper : TextPillificationHelper {
    override fun pillify(text: CharSequence, pillifyPermalinks: Boolean): CharSequence {
        val result = SpannableStringBuilder(text)
        val start = result.indexOf(PREVIEW_MENTION)
        if (start < 0) return result
        result.replace(start, start + PREVIEW_MENTION.length, "@ ")
        result.setSpan(
            MentionSpan(MentionType.User(UserId(PREVIEW_MENTION))),
            start,
            start + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        result.setSpan(
            URLSpan("https://matrix.to/#/$PREVIEW_MENTION"),
            start,
            start + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return result
    }
}

private class PreviewMentionSpanUpdater(private val theme: MentionSpanTheme) : MentionSpanUpdater {
    override fun updateMentionSpans(text: CharSequence): CharSequence {
        text.getMentionSpans().forEach { span ->
            span.updateTheme(theme)
            span.updateDisplayText(
                object : MentionSpanFormatter {
                    override fun formatDisplayText(mentionType: MentionType): CharSequence = "Alice"
                }
            )
        }
        return text
    }

    @Composable
    override fun rememberMentionSpans(text: CharSequence): CharSequence = remember(text) { updateMentionSpans(text) }
}

private const val PREVIEW_MENTION = "@alice:example.org"
