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
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.joinedRoomMembers
import io.element.android.libraries.matrix.api.to_device.CustomToDeviceEvent
import io.element.android.libraries.slashcommands.api.SlashCommandSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Room-local discovery, independent of other bots. No catalog or account state is cached. */
@Inject
class DshCommandSuggestionsDataSource(private val matrixClient: MatrixClient) {
    suspend fun getSuggestions(room: JoinedRoom, query: String, timeout: Duration = 5.seconds): List<SlashCommandSuggestion> {
        if (query.length > 128 || query.any { it.isISOControl() }) return emptyList()
        // Bound the entire operation, including member lookup, sends and signature verification.
        return try {
            withTimeoutOrNull(timeout) {
                room.updateMembers()
                val candidates = room.membersStateFlow.value.joinedRoomMembers()
                    .map { it.userId }
                    .filter { it != matrixClient.sessionId && it.value.substringAfter(':') == matrixClient.sessionId.value.substringAfter(':') }
                if (candidates.isEmpty() || candidates.size > 10) return@withTimeoutOrNull emptyList()
                val txnId = UUID.randomUUID().toString()
                coroutineScope {
                    var accepted: List<SlashCommandSuggestion> = emptyList()
                    // Subscribe before sending, including when the local homeserver answers synchronously.
                    val response = async(start = CoroutineStart.UNDISPATCHED) {
                        matrixClient.customToDeviceEvents(RESPONSE_TYPE).first { event ->
                            val parsed = validate(event, candidates, txnId, room.roomId.value, query) ?: return@first false
                            // Membership can change while discovery is in flight.
                            if (event.sender !in room.membersStateFlow.value.joinedRoomMembers().map { it.userId }) return@first false
                            accepted = parsed
                            true
                        }
                    }
                    try {
                        val request = JsonObject(mapOf(
                            "version" to JsonPrimitive(1),
                            "txn_id" to JsonPrimitive(txnId),
                            "room_id" to JsonPrimitive(room.roomId.value),
                            "query" to JsonPrimitive(query),
                            "device_id" to JsonPrimitive(matrixClient.deviceId.value),
                            "limit" to JsonPrimitive(100),
                            "signed_response" to JsonPrimitive(true),
                        )).toString()
                        val sent = candidates.map { candidate ->
                            async { matrixClient.sendCustomToDevice(REQUEST_TYPE, candidate, emptyList(), request, txnId).isSuccess }
                        }.map { it.await() }.any { it }
                        if (!sent) return@coroutineScope emptyList()
                        response.await()
                        accepted
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

    private suspend fun validate(
        event: CustomToDeviceEvent,
        candidates: List<UserId>,
        txnId: String,
        roomId: String,
        query: String,
    ): List<SlashCommandSuggestion>? {
        if (event.eventType != RESPONSE_TYPE || event.sender !in candidates || event.content.length > 70000) return null
        val envelope = parseObject(event.content) ?: return null
        if ((envelope["version"] as? JsonPrimitive)?.intOrNull != 1) return null
        val payload = envelope.string("payload", 65536) ?: return null
        val signature = envelope.string("signature", 128) ?: return null
        val device = envelope.string("device_id", 255) ?: return null
        val data = parseObject(payload) ?: return null
        if ((data["version"] as? JsonPrimitive)?.intOrNull != 1 ||
            data.string("type") != RESPONSE_TYPE || data.string("sender") != event.sender.value ||
            data.string("device_id") != device || data.string("txn_id") != txnId ||
            data.string("room_id") != roomId || data.string("query") != query
        ) {
            return null
        }
        if (!matrixClient.verifyDeviceSignature(event.sender, DeviceId(device), payload, signature)) return null
        val commands = data["commands"] as? JsonArray ?: return null
        if (commands.size > 100) return null
        val definitions = data["definitions"] as? JsonArray ?: return null
        if (definitions.size > 100) return null
        return (definitions + commands).mapNotNull { item ->
            val command = item as? JsonObject ?: return@mapNotNull null
            val name = command.string("name", 512) ?: return@mapNotNull null
            // Descriptors may include an exact argument completion. Reject rather than truncate a route.
            if (!COMMAND.matches(name)) return@mapNotNull null
            val description = command.string("description", 512) ?: return@mapNotNull null
            val hint = command.string("argument_hint", 160)
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
