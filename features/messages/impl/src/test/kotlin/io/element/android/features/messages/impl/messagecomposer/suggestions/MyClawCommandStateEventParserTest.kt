/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.room.RoomStateEvent
import org.junit.Test

class MyClawCommandStateEventParserTest {
    private val parser = MyClawCommandStateEventParser()

    @Test
    fun `parse returns commands for supported MyClaw command state event`() {
        val result = parser.parse(
            stateEvent(
                stateKey = "@myclaw:example.org",
                content = """
            {
              "version": 1,
              "commands": [
                {"name": "/status", "description": "Show status", "argument_hint": "[--json]"},
                {"name": "resume", "description": "Resume work"}
              ]
            }
                """.trimIndent()
            )
        )

        assertThat(result?.stateKey).isEqualTo("@myclaw:example.org")
        assertThat(result?.commands?.map { it.command }).containsExactly("/status", "/resume").inOrder()
        assertThat(result?.commands?.first()?.parameters).isEqualTo("[--json]")
    }

    @Test
    fun `parse ignores unsupported or malformed content`() {
        val result = parser.parse(
            stateEvent(
                content = """
            {
              "version": 1,
              "commands": [
                {"name": "", "description": "Missing name"},
                {"name": "bad name", "description": "Whitespace is invalid"},
                {"name": "missing-description"},
                {"name": "status", "description": "Show status"}
              ]
            }
                """.trimIndent()
            )
        )

        assertThat(result?.commands?.map { it.command }).containsExactly("/status")
    }

    @Test
    fun `parse ignores unsupported versions`() {
        val result = parser.parse(
            stateEvent(
                content = """
            {
              "version": 2,
              "commands": [{"name": "status", "description": "Show status"}]
            }
                """.trimIndent()
            )
        )

        assertThat(result).isNull()
    }

    @Test
    fun `parseAndMerge merges state keys deterministically and keeps first duplicate command`() {
        val result = parser.parseAndMerge(
            listOf(
                stateEvent(
                    stateKey = "@z:example.org",
                    commands = """{"name": "status", "description": "Status from z"}"""
                ),
                stateEvent(
                    stateKey = "@a:example.org",
                    commands = """
                    {"name": "status", "description": "Status from a"},
                    {"name": "help", "description": "Help from a"}
                    """.trimIndent()
                ),
            )
        )

        assertThat(result.map { it.command }).containsExactly("/status", "/help").inOrder()
        assertThat(result.first { it.command == "/status" }.description).isEqualTo("Status from a")
    }

    private fun stateEvent(
        type: String = MyClawCommandStateEventParser.MYCLAW_COMMANDS_EVENT_TYPE,
        stateKey: String = "@myclaw:example.org",
        content: String,
    ): RoomStateEvent {
        return RoomStateEvent(
            type = type,
            stateKey = stateKey,
            content = content,
        )
    }

    private fun stateEvent(
        stateKey: String = "@myclaw:example.org",
        commands: String,
    ): RoomStateEvent {
        return stateEvent(
            stateKey = stateKey,
            content = """
            {
              "version": 1,
              "commands": [$commands]
            }
            """.trimIndent()
        )
    }
}
