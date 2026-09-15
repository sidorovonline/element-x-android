/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.AN_EVENT_ID
import io.element.android.libraries.matrix.test.A_THREAD_ID
import org.junit.Test

class TimelineItemMarkdownEligibilityTest {
    @Test
    fun `incoming live and focused timelines enable Markdown rendering`() {
        assertThat(shouldRenderIncomingMarkdown(isMine = false, Timeline.Mode.Live)).isTrue()
        assertThat(shouldRenderIncomingMarkdown(isMine = false, Timeline.Mode.FocusedOnEvent(AN_EVENT_ID))).isTrue()
    }

    @Test
    fun `outgoing and non-main timeline surfaces disable Markdown rendering`() {
        assertThat(shouldRenderIncomingMarkdown(isMine = true, Timeline.Mode.Live)).isFalse()
        assertThat(shouldRenderIncomingMarkdown(isMine = false, Timeline.Mode.PinnedEvents)).isFalse()
        assertThat(shouldRenderIncomingMarkdown(isMine = false, Timeline.Mode.Thread(A_THREAD_ID))).isFalse()
        assertThat(shouldRenderIncomingMarkdown(isMine = false, Timeline.Mode.Media)).isFalse()
    }
}
