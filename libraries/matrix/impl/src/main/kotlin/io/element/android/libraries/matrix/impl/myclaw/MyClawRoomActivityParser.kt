/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.myclaw

import io.element.android.libraries.matrix.api.core.RoomId
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
            val sessionId = json["session_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val updatedAtMillis = json["updated_at"]?.jsonPrimitive?.contentOrNull?.toEpochMillisOrNull()

            MyClawRoomActivityPayload(
                txnId = json["txn_id"]?.jsonPrimitive?.contentOrNull,
                roomId = roomId,
                activity = when (wireState) {
                    WORKING_STATE -> {
                        val expiresAtMillis = json["expires_at"]?.jsonPrimitive?.contentOrNull?.toEpochMillisOrNull() ?: return@runCatching null
                        ParsedMyClawRoomActivity(
                            roomId = roomId,
                            sessionId = sessionId,
                            state = MyClawRoomActivityState.WORKING,
                            updatedAtMillis = updatedAtMillis,
                            expiresAtMillis = expiresAtMillis,
                        )
                    }
                    IDLE_STATE, LEGACY_TYPING_STATE -> null
                    else -> return@runCatching null
                },
            )
        }.getOrNull()
    }

    private fun String.toEpochMillisOrNull(): Long? {
        return runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
    }

    private const val VERSION = 1
    private const val IDLE_STATE = "idle"
    private const val LEGACY_TYPING_STATE = "typing"
    private const val WORKING_STATE = "working"
}

internal data class MyClawRoomActivityPayload(
    val txnId: String?,
    val roomId: RoomId,
    val activity: ParsedMyClawRoomActivity?,
)

internal data class ParsedMyClawRoomActivity(
    val roomId: RoomId,
    val sessionId: String,
    val state: MyClawRoomActivityState,
    val updatedAtMillis: Long?,
    val expiresAtMillis: Long,
)
