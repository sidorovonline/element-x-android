/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.markdown.DefaultIncomingMarkdownParser
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

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
