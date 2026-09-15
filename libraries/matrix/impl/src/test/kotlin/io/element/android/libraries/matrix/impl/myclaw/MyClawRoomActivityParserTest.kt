/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.myclaw

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.myclaw.MyClawRoomActivityState
import org.junit.Test

class MyClawRoomActivityParserTest {
    @Test
    fun `parse returns working activity`() {
        val result = MyClawRoomActivityParser.parse(
            content = """
                {
                  "version": 1,
                  "txn_id": "txn",
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "working",
                  "sender_display_name": "Windows",
                  "updated_at": "2026-06-29T12:00:00Z",
                  "expires_at": "2026-06-29T12:05:00Z"
                }
            """.trimIndent()
        )

        assertThat(result?.txnId).isEqualTo("txn")
        assertThat(result?.roomId).isEqualTo(RoomId("!room:server"))
        assertThat(result?.activity?.roomId).isEqualTo(RoomId("!room:server"))
        assertThat(result?.activity?.state).isEqualTo(MyClawRoomActivityState.WORKING)
        assertThat(result?.activity?.sessionId).isEqualTo("sess_123")
    }

    @Test
    fun `parse keeps active activity without display name`() {
        val result = MyClawRoomActivityParser.parse(
            content = """
                {
                  "version": 1,
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "working",
                  "updated_at": "2026-06-29T12:00:00Z",
                  "expires_at": "2026-06-29T12:05:00Z"
                }
            """.trimIndent()
        )

        assertThat(result?.activity?.state).isEqualTo(MyClawRoomActivityState.WORKING)
        assertThat(result?.activity?.sessionId).isEqualTo("sess_123")
    }

    @Test
    fun `parse ignores payload display name`() {
        val resultWithMachineName = MyClawRoomActivityParser.parse(
            content = """
                {
                  "version": 1,
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "working",
                  "sender_display_name": "spark-matrix",
                  "updated_at": "2026-06-29T12:00:00Z",
                  "expires_at": "2026-06-29T12:05:00Z"
                }
            """.trimIndent()
        )
        val resultWithoutName = MyClawRoomActivityParser.parse(
            content = """
                {
                  "version": 1,
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "working",
                  "updated_at": "2026-06-29T12:00:00Z",
                  "expires_at": "2026-06-29T12:05:00Z"
                }
            """.trimIndent()
        )

        assertThat(resultWithMachineName?.activity).isEqualTo(resultWithoutName?.activity)
    }

    @Test
    fun `parse returns idle clear without activity`() {
        val result = MyClawRoomActivityParser.parse(
            content = """
                {
                  "version": 1,
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "idle",
                  "updated_at": "2026-06-29T12:00:00Z"
                }
            """.trimIndent()
        )

        assertThat(result?.roomId).isEqualTo(RoomId("!room:server"))
        assertThat(result?.activity).isNull()
    }

    @Test
    fun `parse treats legacy custom typing as an idle clear`() {
        val result = MyClawRoomActivityParser.parse(
            content = """
                {
                  "version": 1,
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "typing",
                  "updated_at": "2026-06-29T12:00:00Z"
                }
            """.trimIndent()
        )

        assertThat(result?.roomId).isEqualTo(RoomId("!room:server"))
        assertThat(result?.activity).isNull()
    }

    @Test
    fun `parse rejects invalid state`() {
        val result = MyClawRoomActivityParser.parse(
            content = """
                {
                  "version": 1,
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "running",
                  "expires_at": "2026-06-29T12:05:00Z"
                }
            """.trimIndent()
        )

        assertThat(result).isNull()
    }

    @Test
    fun `parse rejects unsupported version`() {
        val result = MyClawRoomActivityParser.parse(
            content = """
                {
                  "version": 2,
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "working",
                  "expires_at": "2026-06-29T12:05:00Z"
                }
            """.trimIndent()
        )

        assertThat(result).isNull()
    }
}
