/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.myclaw

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.myclaw.MyClawSessionStatus
import io.element.android.libraries.matrix.api.myclaw.MyClawSessionStatusState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

internal object MyClawSessionStatusParser {
    fun parse(content: String): MyClawSessionStatusPayload? {
        return runCatching {
            val json = Json.parseToJsonElement(content).jsonObject
            if (json["version"]?.jsonPrimitive?.intOrNull != VERSION) return@runCatching null
            val roomId = RoomId(json["room_id"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null)
            val state = json["state"]?.jsonPrimitive?.contentOrNull?.toStatusState() ?: return@runCatching null
            val expiresAtMillis = json["expires_at"]?.jsonPrimitive?.contentOrNull?.toEpochMillisOrNull()
                ?: if (state.isWaiting) return@runCatching null else Long.MAX_VALUE
            val sessionId = json["session_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val updatedAtMillis = json["updated_at"]?.jsonPrimitive?.contentOrNull?.toEpochMillisOrNull()
            val label = json["label"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: state.defaultLabel()

            MyClawSessionStatusPayload(
                txnId = json["txn_id"]?.jsonPrimitive?.contentOrNull,
                status = MyClawSessionStatus(
                    roomId = roomId,
                    sessionId = sessionId,
                    state = state,
                    label = label,
                    updatedAtMillis = updatedAtMillis,
                    expiresAtMillis = expiresAtMillis,
                ),
            )
        }.getOrNull()
    }

    private fun String.toStatusState(): MyClawSessionStatusState? {
        return when (this) {
            "idle" -> MyClawSessionStatusState.IDLE
            "running" -> MyClawSessionStatusState.RUNNING
            "waiting_llm" -> MyClawSessionStatusState.WAITING_LLM
            "waiting_agent" -> MyClawSessionStatusState.WAITING_AGENT
            else -> null
        }
    }

    private fun MyClawSessionStatusState.defaultLabel(): String {
        return when (this) {
            MyClawSessionStatusState.IDLE -> "Idle"
            MyClawSessionStatusState.RUNNING -> "Running"
            MyClawSessionStatusState.WAITING_LLM -> "Waiting for model"
            MyClawSessionStatusState.WAITING_AGENT -> "Waiting for agent"
        }
    }

    private val MyClawSessionStatusState.isWaiting: Boolean
        get() = this == MyClawSessionStatusState.WAITING_LLM || this == MyClawSessionStatusState.WAITING_AGENT

    private fun String.toEpochMillisOrNull(): Long? {
        return runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
    }

    private const val VERSION = 1
}

internal data class MyClawSessionStatusPayload(
    val txnId: String?,
    val status: MyClawSessionStatus,
)
