/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.room.RoomStateEvent
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MyClawCommandSuggestionsDataSourceTest {
    @Test
    fun `getSuggestions reads MyClaw command state events from current room state`() = runTest {
        var requestedEventType: String? = null
        val room = FakeJoinedRoom().apply {
            givenCurrentStateEvents { eventType ->
                requestedEventType = eventType
                Result.success(
                    listOf(
                        RoomStateEvent(
                            type = MyClawCommandStateEventParser.MYCLAW_COMMANDS_EVENT_TYPE,
                            stateKey = "@myclaw:example.org",
                            content = """
                            {
                              "version": 1,
                              "commands": [
                                {"name": "status", "description": "Show MyClaw state", "argument_hint": "[--json]"}
                              ]
                            }
                            """.trimIndent()
                        )
                    )
                )
            }
        }

        val result = MyClawCommandSuggestionsDataSource(room).getSuggestions()

        assertThat(requestedEventType).isEqualTo(MyClawCommandStateEventParser.MYCLAW_COMMANDS_EVENT_TYPE)
        assertThat(result.getOrThrow().map { it.command }).containsExactly("/status")
        assertThat(result.getOrThrow().single().parameters).isEqualTo("[--json]")
    }
}
