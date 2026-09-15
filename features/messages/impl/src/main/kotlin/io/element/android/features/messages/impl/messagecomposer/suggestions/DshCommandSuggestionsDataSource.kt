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
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Room-local discovery, independent of other bots. No catalog or account state is cached. */
@Inject
class DshCommandSuggestionsDataSource(private val matrixClient: MatrixClient) {
    suspend fun getSuggestions(room: JoinedRoom, query: String, timeout: Duration = 5.seconds): List<SlashCommandSuggestion> {
        if (query.length > 640 || query.any { it.isISOControl() }) return emptyList()
        // Bound the entire operation, including member lookup, sends and signature verification.
        return try {
            withTimeoutOrNull(timeout) {
                room.updateMembers()
                val service = trustedService(room) ?: return@withTimeoutOrNull emptyList()
                val word = query.substringBefore(' ').lowercase()
                val command = word.takeIf { ' ' in query && it in service.commands }.orEmpty()
                val txnId = UUID.randomUUID().toString()
                coroutineScope {
                    var accepted: List<SlashCommandSuggestion> = emptyList()
                    // Subscribe before sending, including when the local homeserver answers synchronously.
                    val response = async(start = CoroutineStart.UNDISPATCHED) {
                        matrixClient.customToDeviceEvents(RESPONSE_TYPE).first { event ->
                            val parsed = validate(event, service, txnId, room.roomId.value, command) ?: return@first false
                            // Membership can change while discovery is in flight.
                            if (event.sender !in room.membersStateFlow.value.joinedRoomMembers().map { it.userId }) return@first false
                            val current = trustedService(room) ?: return@first false
                            if (current.user != service.user || current.device != service.device || current.commands != service.commands) return@first false
                            accepted = parsed
                            true
                        }
                    }
                    try {
                        val request = JsonObject(mapOf(
                            "version" to JsonPrimitive(2),
                            "txn_id" to JsonPrimitive(txnId),
                            "room_id" to JsonPrimitive(room.roomId.value),
                            "command" to JsonPrimitive(command),
                            "device_id" to JsonPrimitive(matrixClient.deviceId.value),
                            "limit" to JsonPrimitive(100),
                        )).toString()
                        // Only a verified, room-bound DSH service receives metadata.
                        // All argument filtering stays local, including unknown drafts.
                        val sent = matrixClient.sendCustomToDevice(
                            REQUEST_TYPE, service.user, listOf(service.device), request, txnId,
                        ).isSuccess
                        if (!sent) return@coroutineScope emptyList()
                        response.await()
                        accepted.filter { it.command.removePrefix("/").startsWith(query, ignoreCase = true) || ' ' !in it.command }
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

    private data class Service(
        val user: UserId,
        val device: DeviceId,
        val commands: Set<String>,
        val expires: Long,
    )

    private suspend fun trustedService(room: JoinedRoom): Service? {
        val members = room.membersStateFlow.value.joinedRoomMembers().map { it.userId }.toSet()
        val events = matrixClient.getRoomStateEvents(room.roomId, SERVICE_TYPE).getOrElse { return null }
        if (events.size > 100) return null
        val accepted = events.mapNotNull { raw ->
            if (raw.length > 70000) return@mapNotNull null
            val event = parseObject(raw) ?: return@mapNotNull null
            val user = event.string("sender")?.let(::UserId) ?: return@mapNotNull null
            if (event.string("type") != SERVICE_TYPE || event.string("state_key") != user.value || user !in members ||
                user == matrixClient.sessionId || user.value.substringAfter(':') != matrixClient.sessionId.value.substringAfter(':')
            ) return@mapNotNull null
            val envelope = event["content"] as? JsonObject ?: return@mapNotNull null
            val payload = envelope.string("payload", 65536) ?: return@mapNotNull null
            val signature = envelope.string("signature", 128) ?: return@mapNotNull null
            val data = parseObject(payload) ?: return@mapNotNull null
            val device = data.string("device_id", 255)?.let(::DeviceId) ?: return@mapNotNull null
            val expires = (data["expires_at"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val issued = (data["issued_at"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val now = System.currentTimeMillis()
            if ((data["version"] as? JsonPrimitive)?.intOrNull != 2 || data.string("type") != SERVICE_TYPE || data.string("room_id") != room.roomId.value ||
                data.string("sender") != user.value || expires <= now || issued > now + 30000 || expires - issued !in 1..120000 ||
                !matrixClient.verifyDeviceSignature(user, device, payload, signature)
            ) return@mapNotNull null
            val names = data["commands"] as? JsonArray ?: return@mapNotNull null
            if (names.size > 100) return@mapNotNull null
            val commands = names.map { (it as? JsonPrimitive)?.takeIf { v -> v.isString }?.content ?: return@mapNotNull null }
            if (commands.any { !DECLARATION.matches(it) }) return@mapNotNull null
            Service(user, device, commands.toSet(), expires)
        }
        // Ambiguous services do not grant each other access to room context.
        return accepted.singleOrNull()
    }

    private suspend fun validate(
        event: CustomToDeviceEvent,
        service: Service,
        txnId: String,
        roomId: String,
        command: String,
    ): List<SlashCommandSuggestion>? {
        if (event.eventType != RESPONSE_TYPE || event.sender != service.user || event.content.length > 70000 || service.expires <= System.currentTimeMillis()) return null
        val envelope = parseObject(event.content) ?: return null
        if ((envelope["version"] as? JsonPrimitive)?.intOrNull != 2) return null
        val payload = envelope.string("payload", 65536) ?: return null
        val signature = envelope.string("signature", 128) ?: return null
        val device = envelope.string("device_id", 255) ?: return null
        val data = parseObject(payload) ?: return null
        if ((data["version"] as? JsonPrimitive)?.intOrNull != 2 ||
            data.string("type") != RESPONSE_TYPE || data.string("sender") != event.sender.value ||
            data.string("device_id") != device || device != service.device.value || data.string("txn_id") != txnId ||
            data.string("room_id") != roomId || data.string("command") != command
        ) {
            return null
        }
        if (!matrixClient.verifyDeviceSignature(event.sender, DeviceId(device), payload, signature)) return null
        val commands = data["commands"] as? JsonArray ?: return null
        if (commands.size > 100) return null
        val definitions = data["definitions"] as? JsonArray ?: return null
        if (definitions.size > 100) return null
        return (definitions + commands).mapNotNull { item ->
            val itemObject = item as? JsonObject ?: return@mapNotNull null
            val name = itemObject.string("name", 512) ?: return@mapNotNull null
            // Descriptors may include an exact argument completion. Reject rather than truncate a route.
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
        const val SERVICE_TYPE = "icu.victor.dsh.commands.service"
        private val DECLARATION = Regex("[a-z][a-z0-9_-]*")
        const val REQUEST_TYPE = "icu.victor.dsh.commands.request"
        const val RESPONSE_TYPE = "icu.victor.dsh.commands.response"
        private val COMMAND = Regex("[a-z][a-z0-9_-]*(?: [^\\s\\p{C}]+)*")
    }
}
