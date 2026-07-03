/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.myclaw

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.myclaw.MyClawRoomActivity
import io.element.android.libraries.matrix.api.myclaw.MyClawRoomActivityState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

internal object MyClawRoomActivityParser {
    fun parse(content: String): MyClawRoomActivityPayload? {
        return runCatching {
            val json = Json.parseToJsonElement(content).jsonObject
            if (json["version"]?.jsonPrimitive?.intOrNull != VERSION) return@runCatching null
            val roomId = RoomId(json["room_id"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null)
            val wireState = json["state"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val state = wireState.toActivityState()
            val sessionId = json["session_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val updatedAtMillis = json["updated_at"]?.jsonPrimitive?.contentOrNull?.toEpochMillisOrNull()
            val senderDisplayName = json["sender_display_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: json["display_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SENDER_DISPLAY_NAME

            MyClawRoomActivityPayload(
                txnId = json["txn_id"]?.jsonPrimitive?.contentOrNull,
                roomId = roomId,
                activity = when (state) {
                    null -> {
                        if (wireState != IDLE_STATE) return@runCatching null
                        null
                    }
                    else -> {
                        val expiresAtMillis = json["expires_at"]?.jsonPrimitive?.contentOrNull?.toEpochMillisOrNull() ?: return@runCatching null
                        MyClawRoomActivity(
                            roomId = roomId,
                            sessionId = sessionId,
                            state = state,
                            senderDisplayName = senderDisplayName,
                            updatedAtMillis = updatedAtMillis,
                            expiresAtMillis = expiresAtMillis,
                        )
                    }
                },
            )
        }.getOrNull()
    }

    private fun String.toActivityState(): MyClawRoomActivityState? {
        return when (this) {
            "typing" -> MyClawRoomActivityState.TYPING
            "working" -> MyClawRoomActivityState.WORKING
            else -> null
        }
    }

    private fun String.toEpochMillisOrNull(): Long? {
        return runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
    }

    private const val VERSION = 1
    private const val IDLE_STATE = "idle"
    private const val DEFAULT_SENDER_DISPLAY_NAME = "Spark"
}

internal data class MyClawRoomActivityPayload(
    val txnId: String?,
    val roomId: RoomId,
    val activity: MyClawRoomActivity?,
)
