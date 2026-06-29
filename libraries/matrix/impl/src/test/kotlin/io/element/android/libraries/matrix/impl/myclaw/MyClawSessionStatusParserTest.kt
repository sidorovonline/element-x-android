/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.myclaw

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.myclaw.MyClawSessionStatusState
import org.junit.Test

class MyClawSessionStatusParserTest {
    @Test
    fun `parse returns waiting status`() {
        val result = MyClawSessionStatusParser.parse(
            content = """
                {
                  "version": 1,
                  "txn_id": "txn",
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "waiting_agent",
                  "label": "Waiting for agent",
                  "updated_at": "2026-06-29T12:00:00Z",
                  "expires_at": "2026-06-29T12:05:00Z"
                }
            """.trimIndent()
        )

        assertThat(result?.txnId).isEqualTo("txn")
        assertThat(result?.status?.roomId).isEqualTo(RoomId("!room:server"))
        assertThat(result?.status?.state).isEqualTo(MyClawSessionStatusState.WAITING_AGENT)
        assertThat(result?.status?.label).isEqualTo("Waiting for agent")
    }

    @Test
    fun `parse returns idle status without expiry`() {
        val result = MyClawSessionStatusParser.parse(
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

        assertThat(result?.status?.roomId).isEqualTo(RoomId("!room:server"))
        assertThat(result?.status?.state).isEqualTo(MyClawSessionStatusState.IDLE)
    }

    @Test
    fun `parse rejects waiting status without expiry`() {
        val result = MyClawSessionStatusParser.parse(
            content = """
                {
                  "version": 1,
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "waiting_llm",
                  "updated_at": "2026-06-29T12:00:00Z"
                }
            """.trimIndent()
        )

        assertThat(result).isNull()
    }

    @Test
    fun `parse rejects invalid state`() {
        val result = MyClawSessionStatusParser.parse(
            content = """
                {
                  "version": 1,
                  "room_id": "!room:server",
                  "session_id": "sess_123",
                  "state": "blocked",
                  "expires_at": "2026-06-29T12:05:00Z"
                }
            """.trimIndent()
        )

        assertThat(result).isNull()
    }
}
