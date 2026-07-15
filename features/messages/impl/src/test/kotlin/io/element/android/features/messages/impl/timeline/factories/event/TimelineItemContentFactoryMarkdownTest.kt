/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.fixtures.aTimelineItemContentFactory
import io.element.android.features.messages.impl.timeline.markdown.DefaultIncomingMarkdownParser
import io.element.android.features.messages.impl.timeline.markdown.IncomingMarkdownDocument
import io.element.android.features.messages.impl.timeline.markdown.IncomingMarkdownParser
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemNoticeContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.libraries.matrix.api.timeline.item.event.EmoteMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.FormattedBody
import io.element.android.libraries.matrix.api.timeline.item.event.InReplyTo
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.MessageFormat
import io.element.android.libraries.matrix.api.timeline.item.event.NoticeMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.timeline.aProfileDetails
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimelineItemContentFactoryMarkdownTest {
    private val markdown = "# Build matrix\n\n| Component | Status |\n|---|---|\n| Parser | Ready |"

    @Test
    fun `incoming ordinary plain text receives parsed Markdown`() = runTest {
        val parser = CountingParser()
        val result = factory(parser).create(
            itemContent = message(TextMessageType(markdown, formatted = null)),
            eventId = AN_EVENT_ID,
            isEditable = false,
            sender = A_USER_ID_2,
            senderProfile = aProfileDetails(),
        ) as TimelineItemTextContent

        assertThat(result.incomingMarkdown).isNotNull()
        assertThat(parser.inputs).containsExactly(markdown)
    }

    @Test
    fun `outgoing ordinary text never reaches Markdown parser`() = runTest {
        val parser = CountingParser()
        val result = factory(parser).create(
            itemContent = message(TextMessageType(markdown, formatted = null)),
            eventId = AN_EVENT_ID,
            isEditable = true,
            sender = A_USER_ID,
            senderProfile = aProfileDetails(),
        ) as TimelineItemTextContent

        assertThat(result.incomingMarkdown).isNull()
        assertThat(parser.inputs).isEmpty()
    }

    @Test
    fun `Matrix HTML text never reaches Markdown parser`() = runTest {
        val parser = CountingParser()
        val result = factory(parser).create(
            itemContent = message(
                TextMessageType(
                    body = markdown,
                    formatted = FormattedBody(MessageFormat.HTML, "<h1>Build matrix</h1>")
                )
            ),
            eventId = AN_EVENT_ID,
            isEditable = false,
            sender = A_USER_ID_2,
            senderProfile = aProfileDetails(),
        ) as TimelineItemTextContent

        assertThat(result.incomingMarkdown).isNull()
        assertThat(parser.inputs).isEmpty()
    }

    @Test
    fun `reply text never reaches Markdown parser`() = runTest {
        val parser = CountingParser()
        val result = factory(parser).create(
            itemContent = message(
                type = TextMessageType(markdown, formatted = null),
                inReplyTo = InReplyTo.NotLoaded(AN_EVENT_ID),
            ),
            eventId = AN_EVENT_ID,
            isEditable = false,
            sender = A_USER_ID_2,
            senderProfile = aProfileDetails(),
        ) as TimelineItemTextContent

        assertThat(result.incomingMarkdown).isNull()
        assertThat(parser.inputs).isEmpty()
    }

    @Test
    fun `notice text never reaches Markdown parser`() = runTest {
        val parser = CountingParser()
        val result = factory(parser).create(
            itemContent = message(NoticeMessageType(markdown, formatted = null)),
            eventId = AN_EVENT_ID,
            isEditable = false,
            sender = A_USER_ID_2,
            senderProfile = aProfileDetails(),
        )

        assertThat(result).isInstanceOf(TimelineItemNoticeContent::class.java)
        assertThat(parser.inputs).isEmpty()
    }

    @Test
    fun `emote text never reaches Markdown parser`() = runTest {
        val parser = CountingParser()
        factory(parser).create(
            itemContent = MessageContent(
                body = markdown,
                inReplyTo = null,
                isEdited = false,
                threadInfo = null,
                type = EmoteMessageType(markdown, formatted = null),
            ),
            eventId = AN_EVENT_ID,
            isEditable = false,
            sender = A_USER_ID_2,
            senderProfile = aProfileDetails(),
        )

        assertThat(parser.inputs).isEmpty()
    }

    @Test
    fun `ordinary incoming text without heading or table stays on literal path`() = runTest {
        val parser = CountingParser()
        val result = factory(parser).create(
            itemContent = message(TextMessageType("Ordinary message", formatted = null)),
            eventId = AN_EVENT_ID,
            isEditable = false,
            sender = A_USER_ID_2,
            senderProfile = aProfileDetails(),
        ) as TimelineItemTextContent

        assertThat(result.incomingMarkdown).isNull()
        assertThat(result.body).isEqualTo("Ordinary message")
        assertThat(parser.inputs).containsExactly("Ordinary message")
    }

    @Test
    fun `oversized incoming Markdown falls back without changing the raw body`() = runTest {
        val body = "# Heading\n" + "x".repeat(65_537)
        val result = factory(DefaultIncomingMarkdownParser()).create(
            itemContent = message(TextMessageType(body, formatted = null)),
            eventId = AN_EVENT_ID,
            isEditable = false,
            sender = A_USER_ID_2,
            senderProfile = aProfileDetails(),
        ) as TimelineItemTextContent

        assertThat(result.incomingMarkdown).isNull()
        assertThat(result.body).isEqualTo(body)
    }

    private fun factory(parser: IncomingMarkdownParser) = aTimelineItemContentFactory(
        matrixClient = FakeMatrixClient(),
        incomingMarkdownParser = parser,
    )

    private fun message(
        type: io.element.android.libraries.matrix.api.timeline.item.event.MessageType,
        inReplyTo: InReplyTo? = null,
    ) = MessageContent(
        body = when (type) {
            is TextMessageType -> type.body
            is NoticeMessageType -> type.body
            else -> error("Unsupported test message type: $type")
        },
        inReplyTo = inReplyTo,
        isEdited = false,
        threadInfo = null,
        type = type,
    )

    private class CountingParser : IncomingMarkdownParser {
        private val delegate = DefaultIncomingMarkdownParser()
        val inputs = mutableListOf<String>()

        override fun parse(source: String): IncomingMarkdownDocument? {
            inputs += source
            return delegate.parse(source)
        }
    }
}
