/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.timeline.components.event

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.markdown.DefaultIncomingMarkdownParser
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.wysiwyg.link.Link
import org.junit.Test

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
            ElementPreview {
                Box(Modifier.width(320.dp)) {
                    IncomingMarkdownView(
                        document = DefaultIncomingMarkdownParser().parse(body)!!,
                        rawBody = body,
                        onLinkClick = {},
                        onLinkLongClick = {},
                        onLongClick = {},
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
            ElementPreview {
                IncomingMarkdownView(
                    document = DefaultIncomingMarkdownParser().parse(body)!!,
                    rawBody = body,
                    onLinkClick = clicked::add,
                    onLinkLongClick = longClicked::add,
                    onLongClick = {},
                )
            }
        }

        onNodeWithText("Web", useUnmergedTree = true).performTouchInput { click(center) }
        assertThat(clicked).containsExactly(Link("https://example.org", "Web"))

        onNodeWithText("Web", useUnmergedTree = true).performTouchInput { longClick(center) }
        assertThat(longClicked).containsExactly(Link("https://example.org", "Web"))

        onNodeWithText("Script", useUnmergedTree = true).performTouchInput { click(center) }
        assertThat(clicked).hasSize(1)
    }
}
