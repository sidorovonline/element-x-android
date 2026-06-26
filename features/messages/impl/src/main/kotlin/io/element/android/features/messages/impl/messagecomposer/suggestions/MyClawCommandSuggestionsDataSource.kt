/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.joinedRoomMembers
import io.element.android.libraries.slashcommands.api.SlashCommandSuggestion
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Inject
class MyClawCommandSuggestionsDataSource(
    private val matrixClient: MatrixClient,
) {
    suspend fun getSuggestions(
        room: JoinedRoom,
        query: String,
        limit: Int = DEFAULT_LIMIT,
        timeout: Duration = DEFAULT_TIMEOUT,
    ): List<SlashCommandSuggestion> {
        val candidateUserIds = room.commandDiscoveryCandidateUserIds()
            .takeIf { it.isNotEmpty() }
            ?: return emptyList()
        val boundedQuery = query.removePrefix("/").take(MAX_QUERY_LENGTH)
        val boundedLimit = limit.coerceIn(1, MAX_LIMIT)
        val txnId = UUID.randomUUID().toString()
        val roomId = room.roomId.value

        return coroutineScope {
            val response = async {
                matrixClient.customToDeviceEvents(RESPONSE_TYPE)
                    .filter { event -> event.eventType == RESPONSE_TYPE && event.sender in candidateUserIds }
                    .first { event -> event.content.isMatchingResponse(txnId = txnId, roomId = roomId, query = boundedQuery) }
            }

            val request = buildRequestContent(
                txnId = txnId,
                roomId = roomId,
                query = boundedQuery,
                limit = boundedLimit,
                deviceId = matrixClient.deviceId.value,
            )
            val sendResults = candidateUserIds.map { candidateUserId ->
                matrixClient.sendCustomToDevice(
                    eventType = REQUEST_TYPE,
                    userId = candidateUserId,
                    deviceIds = emptyList(),
                    content = request,
                    txnId = txnId,
                )
            }
            val sentAnyRequest = sendResults.any { it.isSuccess }
            if (!sentAnyRequest) {
                response.cancel()
                Timber.w("Failed to send MyClaw command discovery request")
                return@coroutineScope emptyList()
            }

            val suggestions = withTimeoutOrNull(timeout) {
                response.await().content.toSuggestions(txnId = txnId, roomId = roomId, query = boundedQuery)
            }.orEmpty()
            response.cancel()
            suggestions
        }
    }

    private suspend fun JoinedRoom.commandDiscoveryCandidateUserIds(): List<UserId> {
        roomDirectCandidateUserId()?.let {
            return listOf(it)
        }
        if (info().isDm) {
            return emptyList()
        }

        val joinedMembers = membersStateFlow.value.joinedRoomMembers()
        if (joinedMembers.isEmpty()) {
            runCatching { updateMembers() }
        }
        val candidateMembers = membersStateFlow.value
            .joinedRoomMembers()
            .filterNot { matrixClient.isMe(it.userId) }
            .filterNot { it.isServiceMember }

        return if (candidateMembers.size <= MAX_ROOM_CANDIDATES) {
            candidateMembers.map { it.userId }
        } else {
            emptyList()
        }
    }

    private suspend fun JoinedRoom.roomDirectCandidateUserId(): UserId? {
        return getDirectRoomMember()
            ?.takeUnless { it.isServiceMember }
            ?.userId
            ?.takeUnless { matrixClient.isMe(it) }
    }

    internal fun buildRequestContent(
        txnId: String,
        roomId: String,
        query: String,
        limit: Int,
        deviceId: String,
    ): String {
        return JsonObject(
            mapOf(
                "version" to JsonPrimitive(VERSION),
                "txn_id" to JsonPrimitive(txnId),
                "room_id" to JsonPrimitive(roomId),
                "query" to JsonPrimitive(query.removePrefix("/").take(MAX_QUERY_LENGTH)),
                "limit" to JsonPrimitive(limit.coerceIn(1, MAX_LIMIT)),
                "device_id" to JsonPrimitive(deviceId),
            )
        ).toString()
    }

    internal fun parseResponseContent(content: String, txnId: String, roomId: String, query: String): List<SlashCommandSuggestion> {
        return content.toSuggestions(txnId = txnId, roomId = roomId, query = query.removePrefix("/").take(MAX_QUERY_LENGTH))
    }

    private fun String.isMatchingResponse(txnId: String, roomId: String, query: String): Boolean {
        return runCatching {
            val response = Json.parseToJsonElement(this).jsonObject
            response["version"]?.jsonPrimitive?.intOrNull == VERSION &&
                response["txn_id"]?.jsonPrimitive?.contentOrNull == txnId &&
                response["room_id"]?.jsonPrimitive?.contentOrNull == roomId &&
                response["query"]?.jsonPrimitive?.contentOrNull == query
        }.getOrDefault(false)
    }

    private fun String.toSuggestions(txnId: String, roomId: String, query: String): List<SlashCommandSuggestion> {
        return runCatching {
            val response = Json.parseToJsonElement(this).jsonObject
            if (!response.isValidResponse(txnId = txnId, roomId = roomId, query = query)) {
                return@runCatching emptyList()
            }
            response["commands"]
                ?.jsonArrayOrNull()
                .orEmpty()
                .mapNotNull { it.asCommandSuggestion() }
        }.getOrDefault(emptyList())
    }

    private fun JsonObject.isValidResponse(txnId: String, roomId: String, query: String): Boolean {
        return this["version"]?.jsonPrimitive?.intOrNull == VERSION &&
            this["txn_id"]?.jsonPrimitive?.contentOrNull == txnId &&
            this["room_id"]?.jsonPrimitive?.contentOrNull == roomId &&
            this["query"]?.jsonPrimitive?.contentOrNull == query
    }

    private fun JsonElement.asCommandSuggestion(): SlashCommandSuggestion? {
        val command = jsonObjectOrNull() ?: return null
        val name = command["name"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.removePrefix("/")
            ?.take(MAX_COMMAND_LENGTH)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val description = command["description"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.take(MAX_DESCRIPTION_LENGTH)
            .orEmpty()
        val argumentHint = command["argument_hint"]
            ?.takeUnless { it is JsonNull }
            ?.jsonPrimitive
            ?.contentOrNull
            ?.take(MAX_ARGUMENT_HINT_LENGTH)

        return SlashCommandSuggestion(
            command = "/$name",
            parameters = argumentHint,
            description = description,
        )
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    companion object {
        const val REQUEST_TYPE = "icu.victor.myclaw.commands.request"
        const val RESPONSE_TYPE = "icu.victor.myclaw.commands.response"

        private const val VERSION = 1
        private const val DEFAULT_LIMIT = 50
        private const val MAX_LIMIT = 100
        private const val MAX_QUERY_LENGTH = 100
        private const val MAX_COMMAND_LENGTH = 80
        private const val MAX_DESCRIPTION_LENGTH = 240
        private const val MAX_ARGUMENT_HINT_LENGTH = 160
        private const val MAX_ROOM_CANDIDATES = 10
        private val DEFAULT_TIMEOUT = 15.seconds
    }
}
