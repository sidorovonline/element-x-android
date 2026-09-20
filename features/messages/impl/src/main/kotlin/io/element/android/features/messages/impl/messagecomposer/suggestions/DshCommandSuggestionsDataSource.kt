/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.joinedRoomMembers
import io.element.android.libraries.matrix.api.to_device.CustomToDeviceEvent
import io.element.android.libraries.slashcommands.api.SlashCommandSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Draft-free room-local discovery. No privileged room state or persistent catalog cache. */
@Inject
class DshCommandSuggestionsDataSource(private val matrixClient: MatrixClient) {
    suspend fun getSuggestions(room: JoinedRoom, query: String, timeout: Duration = 5.seconds, completeArguments: Boolean = false): List<SlashCommandSuggestion> {
        if (!query.matches(Regex("(?:[a-z][a-z0-9_-]{0,63})?"))) return emptyList()
        return try {
            withTimeoutOrNull(timeout) {
                room.updateMembers()
                val candidates = localPeers(room)
                if (candidates.isEmpty() || candidates.size > 32) return@withTimeoutOrNull emptyList()
                val txnId = UUID.randomUUID().toString()
                coroutineScope {
                    val accepted = mutableMapOf<Pair<String, String>, CustomToDeviceEvent>()
                    // Subscribe before sending; collect a bounded window so competing services fail closed.
                    val response = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeoutOrNull(2.seconds) {
                            matrixClient.customToDeviceEvents(RESPONSE_TYPE).collect { event ->
                                if (event.sender in candidates && event.sender in localPeers(room) &&
                                    validate(event, txnId, room.roomId.value) != null
                                ) {
                                    val device = parseObject(event.content)?.string("device_id") ?: return@collect
                                    accepted[event.sender.value to device] = event
                                }
                            }
                        }
                    }
                    try {
                        val request = JsonObject(mapOf(
                            "version" to JsonPrimitive(3),
                            "txn_id" to JsonPrimitive(txnId),
                            "room_id" to JsonPrimitive(room.roomId.value),
                            "device_id" to JsonPrimitive(matrixClient.deviceId.value),
                            "limit" to JsonPrimitive(100),
                        )).toString()
                        // Only metadata already shared by room members is sent. Not even a command name leaves the composer.
                        for (peer in candidates) {
                            matrixClient.sendCustomToDevice(REQUEST_TYPE, peer, emptyList(), request, txnId)
                        }
                        response.await()
                        val event = accepted.values.singleOrNull() ?: return@coroutineScope emptyList()
                        if (event.sender !in localPeers(room)) return@coroutineScope emptyList()
                        // Recheck trust and expiry after collection, including revocation during the request.
                        validate(event, txnId, room.roomId.value).orEmpty().filter {
                            val name = it.command.removePrefix("/")
                            name.startsWith(query, ignoreCase = true) && (completeArguments || ' ' !in name)
                        }
                    } finally {
                        response.cancel()
                    }
                }
            }.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun localPeers(room: JoinedRoom) = room.membersStateFlow.value.joinedRoomMembers().map { it.userId }.filter {
        it != matrixClient.sessionId && it.value.substringAfter(':') == matrixClient.sessionId.value.substringAfter(':')
    }.toSet()

    private suspend fun validate(event: CustomToDeviceEvent, txnId: String, roomId: String): List<SlashCommandSuggestion>? {
        if (event.eventType != RESPONSE_TYPE || event.content.length > 70000) return null
        val envelope = parseObject(event.content) ?: return null
        if ((envelope["version"] as? JsonPrimitive)?.intOrNull != 3) return null
        val payload = envelope.string("payload", 65536) ?: return null
        val signature = envelope.string("signature", 128) ?: return null
        val device = envelope.string("device_id", 255) ?: return null
        val data = parseObject(payload) ?: return null
        val expires = (data["expires_at"] as? JsonPrimitive)?.longOrNull ?: return null
        val issued = (data["issued_at"] as? JsonPrimitive)?.longOrNull ?: return null
        val now = System.currentTimeMillis()
        if ((data["version"] as? JsonPrimitive)?.intOrNull != 3 ||
            data.string("type") != RESPONSE_TYPE || data.string("sender") != event.sender.value ||
            data.string("device_id") != device || data.string("txn_id") != txnId || data.string("room_id") != roomId ||
            data.string("recipient") != matrixClient.sessionId.value || data.string("request_device") != matrixClient.deviceId.value ||
            expires <= now || issued > now + 30000 || expires - issued !in 1..30000
        ) return null
        if (!matrixClient.verifyDeviceSignature(event.sender, DeviceId(device), payload, signature)) return null
        val commands = data["commands"] as? JsonArray ?: return null
        val definitions = data["definitions"] as? JsonArray ?: return null
        if (commands.size > 100 || definitions.size > 100) return null
        return (definitions + commands).mapNotNull { item ->
            val itemObject = item as? JsonObject ?: return@mapNotNull null
            val name = itemObject.string("name", 512) ?: return@mapNotNull null
            if (!COMMAND.matches(name)) return@mapNotNull null
            val description = itemObject.string("description", 512) ?: return@mapNotNull null
            val hint = itemObject.string("argument_hint", 160)
            SlashCommandSuggestion("/$name", hint, description)
        }.distinctBy { it.command }
    }

    private fun parseObject(value: String): JsonObject? = runCatching { Json.parseToJsonElement(value) as? JsonObject }.getOrNull()

    private fun JsonObject.string(key: String, max: Int = 1024): String? = (get(key) as? JsonPrimitive)
        ?.takeIf { it.isString }?.content?.takeIf { it.length <= max && it.none(Char::isISOControl) }

    companion object {
        const val REQUEST_TYPE = "icu.victor.dsh.commands.request"
        const val RESPONSE_TYPE = "icu.victor.dsh.commands.response"
        private val COMMAND = Regex("[a-z][a-z0-9_-]*(?: [^\\s\\p{C}]+)*")
    }
}
