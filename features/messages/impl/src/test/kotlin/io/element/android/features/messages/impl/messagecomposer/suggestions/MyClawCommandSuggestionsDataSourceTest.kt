/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.api.to_device.CustomToDeviceEvent
import io.element.android.libraries.matrix.test.A_DEVICE_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomMember
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MyClawCommandSuggestionsDataSourceTest {
    private val matrixClient = FakeMatrixClient(
        sessionId = A_SESSION_ID,
        deviceId = A_DEVICE_ID,
    )
    private val sut = MyClawCommandSuggestionsDataSource(matrixClient)

    @Test
    fun `buildRequestContent includes bounded protocol fields`() {
        val content = sut.buildRequestContent(
            txnId = "txn",
            roomId = A_ROOM_ID.value,
            query = "/${"a".repeat(120)}",
            limit = 999,
            deviceId = A_DEVICE_ID.value,
        )

        val json = Json.parseToJsonElement(content).jsonObject
        assertThat(json["version"]?.jsonPrimitive?.int).isEqualTo(1)
        assertThat(json["txn_id"]?.jsonPrimitive?.contentOrNull).isEqualTo("txn")
        assertThat(json["room_id"]?.jsonPrimitive?.contentOrNull).isEqualTo(A_ROOM_ID.value)
        assertThat(json["query"]?.jsonPrimitive?.contentOrNull).hasLength(100)
        assertThat(json["query"]?.jsonPrimitive?.contentOrNull?.startsWith("/")).isFalse()
        assertThat(json["limit"]?.jsonPrimitive?.int).isEqualTo(100)
        assertThat(json["device_id"]?.jsonPrimitive?.contentOrNull).isEqualTo(A_DEVICE_ID.value)
    }

    @Test
    fun `parseResponseContent validates envelope and sanitizes commands`() {
        val response = """
            {
              "version": 1,
              "txn_id": "txn",
              "room_id": "${A_ROOM_ID.value}",
              "query": "sta",
              "commands": [
                { "name": "/status", "description": "Show MyClaw state", "argument_hint": "--json" },
                { "name": "", "description": "invalid" },
                { "name": "logs", "description": "Read recent logs", "argument_hint": null }
              ]
            }
        """.trimIndent()

        val suggestions = sut.parseResponseContent(
            content = response,
            txnId = "txn",
            roomId = A_ROOM_ID.value,
            query = "sta",
        )

        assertThat(suggestions.map { it.command }).containsExactly("/status", "/logs").inOrder()
        assertThat(suggestions.first().parameters).isEqualTo("--json")
        assertThat(suggestions.last().parameters).isNull()
    }

    @Test
    fun `getSuggestions probes DM member devices and returns matching response`() = runTest {
        val room = aDmRoom()

        val result = async {
            sut.getSuggestions(room = room, query = "/sta", timeout = 2.seconds)
        }
        runCurrent()

        val sent = matrixClient.sentCustomToDeviceEvents.single()
        val sentContent = Json.parseToJsonElement(sent.content).jsonObject
        val txnId = sentContent["txn_id"]!!.jsonPrimitive.contentOrNull!!
        assertThat(sent.eventType).isEqualTo(MyClawCommandSuggestionsDataSource.REQUEST_TYPE)
        assertThat(sent.userId).isEqualTo(A_USER_ID_2)
        assertThat(sent.deviceIds).isEmpty()
        assertThat(sent.txnId).isEqualTo(txnId)
        assertThat(sentContent["query"]?.jsonPrimitive?.contentOrNull).isEqualTo("sta")

        matrixClient.emitCustomToDeviceEvent(
            CustomToDeviceEvent(
                eventType = MyClawCommandSuggestionsDataSource.RESPONSE_TYPE,
                sender = A_USER_ID_2,
                content = """
                    {
                      "version": 1,
                      "txn_id": "$txnId",
                      "room_id": "${A_ROOM_ID.value}",
                      "query": "sta",
                      "commands": [
                        { "name": "status", "description": "Show MyClaw state", "argument_hint": null }
                      ]
                    }
                """.trimIndent(),
                encrypted = false,
            )
        )
        runCurrent()

        assertThat(result.await().map { it.command }).containsExactly("/status")
    }

    @Test
    fun `getSuggestions skips rooms without a direct member`() = runTest {
        val suggestions = sut.getSuggestions(
            room = FakeJoinedRoom(),
            query = "",
            timeout = 100.milliseconds,
        )

        assertThat(suggestions).isEmpty()
        assertThat(matrixClient.sentCustomToDeviceEvents).isEmpty()
    }

    @Test
    fun `getSuggestions probes small non-DM room members and returns first matching response`() = runTest {
        val botUserId = UserId("@room-bot:server.org")
        val humanUserId = UserId("@human:server.org")
        val room = aRoomWithMembers(listOf(botUserId, humanUserId))

        val result = async {
            sut.getSuggestions(room = room, query = "sta", timeout = 2.seconds)
        }
        runCurrent()

        val sentEvents = matrixClient.sentCustomToDeviceEvents
        assertThat(sentEvents.map { it.userId }).containsExactly(botUserId, humanUserId)
        assertThat(sentEvents.map { it.userId }).doesNotContain(A_SESSION_ID)
        assertThat(sentEvents.all { it.eventType == MyClawCommandSuggestionsDataSource.REQUEST_TYPE }).isTrue()

        val sentContent = Json.parseToJsonElement(sentEvents.first { it.userId == botUserId }.content).jsonObject
        val txnId = sentContent["txn_id"]!!.jsonPrimitive.contentOrNull!!
        matrixClient.emitCustomToDeviceEvent(
            responseEvent(
                sender = botUserId,
                txnId = txnId,
                query = "sta",
            )
        )
        runCurrent()

        assertThat(result.await().map { it.command }).containsExactly("/status")
    }

    @Test
    fun `getSuggestions waits for slow non-DM room response`() = runTest {
        val botUserId = UserId("@room-bot:server.org")
        val room = aRoomWithMembers(listOf(botUserId))

        val result = async {
            sut.getSuggestions(room = room, query = "sta")
        }
        runCurrent()

        val sentContent = Json.parseToJsonElement(matrixClient.sentCustomToDeviceEvents.single().content).jsonObject
        val txnId = sentContent["txn_id"]!!.jsonPrimitive.contentOrNull!!
        advanceTimeBy(10_000)
        matrixClient.emitCustomToDeviceEvent(
            responseEvent(
                sender = botUserId,
                txnId = txnId,
                query = "sta",
            )
        )
        runCurrent()

        assertThat(result.await().map { it.command }).containsExactly("/status")
    }

    @Test
    fun `getSuggestions times out quietly in non-DM room when no candidate responds`() = runTest {
        val botUserId = UserId("@room-bot:server.org")
        val room = aRoomWithMembers(listOf(botUserId))

        val result = async {
            sut.getSuggestions(room = room, query = "", timeout = 100.milliseconds)
        }
        runCurrent()
        advanceTimeBy(101)
        runCurrent()

        assertThat(result.await()).isEmpty()
        assertThat(matrixClient.sentCustomToDeviceEvents.map { it.userId }).containsExactly(botUserId)
    }

    @Test
    fun `getSuggestions skips large non-DM rooms instead of broadcasting`() = runTest {
        val room = aRoomWithMembers(List(11) { index -> UserId("@member$index:server.org") })

        val suggestions = sut.getSuggestions(
            room = room,
            query = "",
            timeout = 100.milliseconds,
        )

        assertThat(suggestions).isEmpty()
        assertThat(matrixClient.sentCustomToDeviceEvents).isEmpty()
    }

    @Test
    fun `getSuggestions times out quietly`() = runTest {
        val result = async {
            sut.getSuggestions(room = aDmRoom(), query = "", timeout = 100.milliseconds)
        }
        runCurrent()
        advanceTimeBy(101)
        runCurrent()

        assertThat(result.await()).isEmpty()
        assertThat(matrixClient.sentCustomToDeviceEvents).hasSize(1)
    }

    private fun aDmRoom(): FakeJoinedRoom {
        return FakeJoinedRoom(
            baseRoom = FakeBaseRoom(
                sessionId = A_SESSION_ID,
                roomId = A_ROOM_ID,
                getDirectRoomMemberResult = {
                    aRoomMember(userId = A_USER_ID_2)
                },
            )
        )
    }

    private fun aRoomWithMembers(candidateUserIds: List<UserId>): FakeJoinedRoom {
        return FakeJoinedRoom(
            baseRoom = FakeBaseRoom(
                sessionId = A_SESSION_ID,
                roomId = A_ROOM_ID,
            ).apply {
                givenRoomMembersState(
                    RoomMembersState.Ready(
                        persistentListOf(
                            aRoomMember(userId = A_SESSION_ID),
                            *candidateUserIds.map { aRoomMember(userId = it) }.toTypedArray(),
                            aRoomMember(userId = UserId("@service:server.org"), isServiceMember = true),
                        )
                    )
                )
            }
        )
    }

    private fun responseEvent(sender: UserId, txnId: String, query: String): CustomToDeviceEvent {
        return CustomToDeviceEvent(
            eventType = MyClawCommandSuggestionsDataSource.RESPONSE_TYPE,
            sender = sender,
            content = """
                {
                  "version": 1,
                  "txn_id": "$txnId",
                  "room_id": "${A_ROOM_ID.value}",
                  "query": "$query",
                  "commands": [
                    { "name": "status", "description": "Show MyClaw state", "argument_hint": null }
                  ]
                }
            """.trimIndent(),
            encrypted = false,
        )
    }
}
