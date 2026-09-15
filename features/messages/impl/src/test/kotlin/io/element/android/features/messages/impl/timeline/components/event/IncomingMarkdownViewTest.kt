/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.timeline.components.event

import android.os.Looper
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.markdown.DefaultIncomingMarkdownCompatibilityFormatter
import io.element.android.features.messages.impl.timeline.markdown.DefaultIncomingMarkdownParser
import io.element.android.features.messages.impl.utils.FakeTextPillificationHelper
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.textcomposer.mentions.LocalMentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.MentionSpan
import io.element.android.libraries.textcomposer.mentions.MentionSpanFormatter
import io.element.android.libraries.textcomposer.mentions.MentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.MentionType
import io.element.android.libraries.textcomposer.mentions.getMentionSpans
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.wysiwyg.EditorStyledTextView
import io.element.android.wysiwyg.link.Link
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.time.Duration

class IncomingMarkdownViewTest : RobolectricTest() {
    @Test
    fun `renders one raw accessibility node without duplicate rendered text nodes`() = runAndroidComposeUiTest<ComponentActivity> {
        val body = """
            # Build matrix

            | Component | Status | Notes for phone layout |
            |---|---|---|
            | Parser | Ready | A deliberately long value that requires horizontal room |
        """.trimIndent()
        var layoutData = ContentAvoidingLayoutData()

        setContent {
            MarkdownTestContent {
                Box(Modifier.width(320.dp)) {
                    IncomingMarkdownView(
                        document = DefaultIncomingMarkdownParser().parse(body)!!,
                        rawBody = body,
                        onLinkClick = {},
                        onLinkLongClick = {},
                        onContentLayoutChange = { layoutData = it },
                    )
                }
            }
        }

        onAllNodesWithContentDescription(body).assertCountEquals(1)
        onNodeWithText("Build matrix").assertDoesNotExist()
        onNodeWithText("Component").assertDoesNotExist()
        assertThat(layoutData.contentWidth).isGreaterThan(0)
        assertThat(layoutData.contentHeight).isGreaterThan(0)
    }

    @Test
    fun `safe links use existing click and long-click callbacks while unsafe links stay inert`() = runAndroidComposeUiTest<ComponentActivity> {
        val body = "# Links\n\n[Web](https://example.org)\n\n[Script](javascript:alert(1))"
        val clicked = mutableListOf<Link>()
        val longClicked = mutableListOf<Link>()

        setContent {
            MarkdownTestContent {
                IncomingMarkdownView(
                    document = DefaultIncomingMarkdownParser().parse(body)!!,
                    rawBody = body,
                    onLinkClick = clicked::add,
                    onLinkLongClick = longClicked::add,
                )
            }
        }

        val webNode = onAllNodesWithTag(INCOMING_MARKDOWN_INLINE_TAG, useUnmergedTree = true)[1]
        val webClickX = webNode.clickLeadingTextUntil { clicked.isNotEmpty() }
        assertThat(webClickX).isAtLeast(0f)
        assertThat(clicked).containsExactly(Link("https://example.org", "Web"))

        performLongClick(findEditorStyledText(requireNotNull(activity), "Web"), webClickX)
        assertThat(longClicked).containsExactly(Link("https://example.org", "Web"))

        onAllNodesWithTag(INCOMING_MARKDOWN_INLINE_TAG, useUnmergedTree = true)[2].clickEveryLeadingTextOffset()
        assertThat(clicked).hasSize(1)
    }

    @Test
    fun `compatibility links and mentions use existing callbacks and react to name updates`() = runAndroidComposeUiTest<ComponentActivity> {
        val body = "**Strong release**\n\nhttps://example.org/docs\n\n@alice:example.org"
        val document = compatibleDocument(body)
        val clicked = mutableListOf<Link>()
        val longClicked = mutableListOf<Link>()
        val displayName = mutableStateOf("Alice")
        val mentionUpdater = ReactiveMentionSpanUpdater(displayName)

        setContent {
            MarkdownTestContent {
                CompositionLocalProvider(LocalMentionSpanUpdater provides mentionUpdater) {
                    IncomingMarkdownView(
                        document = document,
                        rawBody = body,
                        onLinkClick = clicked::add,
                        onLinkLongClick = longClicked::add,
                    )
                }
            }
        }

        val bareLinkNode = onAllNodesWithTag(INCOMING_MARKDOWN_INLINE_TAG, useUnmergedTree = true)[1]
        val bareLinkClickX = bareLinkNode.clickLeadingTextUntil { clicked.size == 1 }
        val mentionNode = onAllNodesWithTag(INCOMING_MARKDOWN_INLINE_TAG, useUnmergedTree = true)[2]
        val mentionClickX = mentionNode.clickLeadingTextUntil { clicked.size == 2 }
        assertThat(bareLinkClickX).isAtLeast(0f)
        assertThat(mentionClickX).isAtLeast(0f)
        assertThat(clicked).containsExactly(
            Link("https://example.org/docs", "https://example.org/docs"),
            Link("https://matrix.to/#/@alice:example.org", "@"),
        ).inOrder()

        performLongClick(findEditorStyledText(requireNotNull(activity), "https://example.org/docs"), bareLinkClickX)
        performLongClick(findEditorStyledText(requireNotNull(activity), "@ "), mentionClickX)
        assertThat(longClicked).containsExactly(
            Link("https://example.org/docs", "https://example.org/docs"),
            Link("https://matrix.to/#/@alice:example.org", "@"),
        ).inOrder()
        assertThat(mentionUpdater.lastDisplayText).isEqualTo("Alice")

        displayName.value = "Alicia"
        waitForIdle()
        assertThat(mentionUpdater.lastDisplayText).isEqualTo("Alicia")
    }

    @Test
    fun `overlapping strong bare link dispatches one action`() = runAndroidComposeUiTest<ComponentActivity> {
        val body = "**https://example.org/bold**"
        val clicked = mutableListOf<Link>()

        setContent {
            MarkdownTestContent {
                IncomingMarkdownView(
                    document = compatibleDocument(body),
                    rawBody = body,
                    onLinkClick = clicked::add,
                    onLinkLongClick = {},
                )
            }
        }

        onAllNodesWithTag(INCOMING_MARKDOWN_INLINE_TAG, useUnmergedTree = true)[0]
            .clickLeadingTextUntil { clicked.isNotEmpty() }
        assertThat(clicked).containsExactly(Link("https://example.org/bold", "https://example.org/bold"))
    }

    @Test
    fun `table uses Compose text except for native mention pills`() = runAndroidComposeUiTest<ComponentActivity> {
        val body = """
            | Label | Value |
            | --- | --- |
            | Link | https://example.org/table |
            | Owner | @alice:example.org |
        """.trimIndent()
        val clicked = mutableListOf<Link>()
        val longClicked = mutableListOf<Link>()
        var messageLongClicks = 0

        setContent {
            MarkdownTestContent {
                Box(
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { messageLongClicks++ })
                    }
                ) {
                    CompositionLocalProvider(LocalMentionSpanUpdater provides ReactiveMentionSpanUpdater(mutableStateOf("Alice"))) {
                        IncomingMarkdownView(
                            document = compatibleDocument(body),
                            rawBody = body,
                            onLinkClick = clicked::add,
                            onLinkLongClick = longClicked::add,
                            onLongClick = { messageLongClicks++ },
                        )
                    }
                }
            }
        }

        onAllNodesWithTag(INCOMING_MARKDOWN_COMPOSE_INLINE_TAG, useUnmergedTree = true).assertCountEquals(5)
        onAllNodesWithTag(INCOMING_MARKDOWN_INLINE_TAG, useUnmergedTree = true).assertCountEquals(1)

        val linkNode = onAllNodesWithTag(INCOMING_MARKDOWN_COMPOSE_INLINE_TAG, useUnmergedTree = true)[3]
        val clickX = linkNode.clickLeadingTextUntil { clicked.isNotEmpty() }
        assertThat(clickX).isAtLeast(0f)
        assertThat(clicked).containsExactly(Link("https://example.org/table", "https://example.org/table"))

        linkNode.performTouchInput { longClick(Offset(clickX, center.y)) }
        assertThat(longClicked).containsExactly(Link("https://example.org/table", "https://example.org/table"))

        onAllNodesWithTag(INCOMING_MARKDOWN_COMPOSE_INLINE_TAG, useUnmergedTree = true)[0]
            .performTouchInput { longClick(center) }
        assertThat(messageLongClicks).isEqualTo(1)
    }

    private fun compatibleDocument(body: String) = DefaultIncomingMarkdownCompatibilityFormatter(
        FakeTextPillificationHelper { text, _ -> pillifyMention(text) }
    ).format(checkNotNull(DefaultIncomingMarkdownParser().parse(body)))

    @Composable
    private fun MarkdownTestContent(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            ElementPreview(content = content)
        }
    }

    private fun pillifyMention(text: CharSequence): CharSequence {
        val result = SpannableStringBuilder(text)
        val start = result.indexOf(MENTION)
        if (start < 0) return result
        result.replace(start, start + MENTION.length, "@ ")
        result.setSpan(
            MentionSpan(MentionType.User(UserId(MENTION))),
            start,
            start + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        result.setSpan(
            URLSpan("https://matrix.to/#/$MENTION"),
            start,
            start + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return result
    }

    private fun SemanticsNodeInteraction.clickLeadingTextUntil(handled: () -> Boolean): Float {
        LEADING_TEXT_X_OFFSETS.forEach { x ->
            performTouchInput { click(Offset(x, center.y)) }
            if (handled()) return x
        }
        return -1f
    }

    private fun SemanticsNodeInteraction.clickEveryLeadingTextOffset() {
        LEADING_TEXT_X_OFFSETS.forEach { x ->
            performTouchInput { click(Offset(x, center.y)) }
        }
    }

    private fun findEditorStyledText(activity: ComponentActivity, text: String): EditorStyledTextView {
        lateinit var result: EditorStyledTextView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = checkNotNull(activity.window.decorView.findEditorStyledText(text))
        }
        return result
    }

    private fun View.findEditorStyledText(text: String): EditorStyledTextView? {
        if (this is EditorStyledTextView && this.text.toString() == text) return this
        if (this !is ViewGroup) return null
        repeat(childCount) { index ->
            getChildAt(index).findEditorStyledText(text)?.let { return it }
        }
        return null
    }

    private fun performLongClick(view: EditorStyledTextView, x: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val y = view.height / 2f
        val downTime = SystemClock.uptimeMillis()
        instrumentation.runOnMainSync { view.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 100L))
        val upTime = SystemClock.uptimeMillis()
        instrumentation.runOnMainSync { view.dispatchTouchEvent(MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0)) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private class ReactiveMentionSpanUpdater(
        private val displayName: MutableState<String>,
    ) : MentionSpanUpdater {
        var lastDisplayText: CharSequence? = null

        override fun updateMentionSpans(text: CharSequence): CharSequence {
            text.getMentionSpans().forEach { span ->
                span.updateDisplayText(
                    object : MentionSpanFormatter {
                        override fun formatDisplayText(mentionType: MentionType): CharSequence = displayName.value
                    }
                )
                lastDisplayText = span.displayText
            }
            return text
        }

        @Composable
        override fun rememberMentionSpans(text: CharSequence): CharSequence {
            val name = displayName.value
            return remember(text, name) { updateMentionSpans(text) }
        }
    }

    private companion object {
        const val MENTION = "@alice:example.org"
        val LEADING_TEXT_X_OFFSETS = listOf(1f, 2f, 4f, 8f, 12f, 16f, 24f, 32f, 48f)
    }
}
