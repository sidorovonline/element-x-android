/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.api.to_device.CustomToDeviceEvent
import io.element.android.libraries.matrix.test.A_DEVICE_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomMember
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DshCommandSuggestionsDataSourceTest {
    private val bot = UserId("@owned-bot:${A_SESSION_ID.value.substringAfter(':')}")
    private val transport = FakeMatrixClient(sessionId = A_SESSION_ID, deviceId = A_DEVICE_ID)
    private val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val client = object : MatrixClient by transport {
        override suspend fun verifyDeviceSignature(userId: UserId, deviceId: DeviceId, message: String, signature: String): Boolean =
            userId == bot && deviceId.value == "OWNED" && runCatching {
                Signature.getInstance("Ed25519").run {
                    initVerify(key.public)
                    update(message.toByteArray())
                    verify(Base64.getDecoder().decode(signature))
                }
            }.getOrDefault(false)
    }
    private val source = DshCommandSuggestionsDataSource(client)
    private val room = FakeJoinedRoom(baseRoom = FakeBaseRoom(
        roomId = A_ROOM_ID,
        updateMembersResult = {},
    ).apply { membersStateFlow.value = RoomMembersState.Ready(persistentListOf(aRoomMember(bot))) })

    private fun reply(request: String, name: String, sender: UserId = bot, invalidSignature: Boolean = false): CustomToDeviceEvent {
        val requestFields = Json.parseToJsonElement(request).jsonObject
        val data = JsonObject(requestFields + mapOf(
            "type" to JsonPrimitive(DshCommandSuggestionsDataSource.RESPONSE_TYPE),
            "sender" to JsonPrimitive(bot.value),
            "device_id" to JsonPrimitive("OWNED"),
            "definitions" to Json.parseToJsonElement("[]"),
            "commands" to Json.parseToJsonElement("""[{"name":"$name","description":"Owned dynamic choice"}]"""),
        )).toString()
        val signature = Signature.getInstance("Ed25519").run {
            initSign(key.private)
            update(data.toByteArray())
            sign()
        }
        if (invalidSignature) signature[0] = (signature[0].toInt() xor 1).toByte()
        return CustomToDeviceEvent(DshCommandSuggestionsDataSource.RESPONSE_TYPE, sender, JsonObject(mapOf(
            "version" to JsonPrimitive(1),
            "device_id" to JsonPrimitive("OWNED"),
            "payload" to JsonPrimitive(data),
            "signature" to JsonPrimitive(Base64.getEncoder().withoutPadding().encodeToString(signature)),
        )).toString(), false)
    }

    @Test
    fun `signed dynamic responses reject spoofing and replay and do not cache the catalog`() = runTest {
        val first = async { source.getSuggestions(room, "model ") }
        runCurrent()
        val request = transport.sentCustomToDeviceEvents.single().content
        transport.emitCustomToDeviceEvent(reply(request, "model owned/first", invalidSignature = true))
        transport.emitCustomToDeviceEvent(reply(request, "model owned/first", sender = UserId("@unknown:elsewhere")))
        runCurrent()
        assertThat(first.isCompleted).isFalse()
        transport.emitCustomToDeviceEvent(reply(request, "model owned/first"))
        runCurrent()
        assertThat(first.await().single().command).isEqualTo("/model owned/first")

        val second = async { source.getSuggestions(room, "model ") }
        runCurrent()
        transport.emitCustomToDeviceEvent(reply(request, "model owned/first"))
        runCurrent()
        assertThat(second.isCompleted).isFalse()
        transport.emitCustomToDeviceEvent(reply(transport.sentCustomToDeviceEvents.last().content, "model owned/second"))
        runCurrent()
        assertThat(second.await().single().command).isEqualTo("/model owned/second")
    }

    @Test
    fun `timeout and cancellation release pending discovery before subsequent request`() = runTest {
        val timed = async { source.getSuggestions(room, "", timeout = 1.seconds) }
        runCurrent()
        advanceTimeBy(1001)
        assertThat(timed.await()).isEmpty()
        val cancelled = async { source.getSuggestions(room, "model ") }
        runCurrent()
        val old = transport.sentCustomToDeviceEvents.last().content
        cancelled.cancel()
        runCurrent()
        val fresh = async { source.getSuggestions(room, "models") }
        runCurrent()
        transport.emitCustomToDeviceEvent(reply(old, "model owned/first"))
        runCurrent()
        assertThat(fresh.isCompleted).isFalse()
        transport.emitCustomToDeviceEvent(reply(transport.sentCustomToDeviceEvents.last().content, "models"))
        runCurrent()
        assertThat(fresh.await().single().command).isEqualTo("/models")
    }
    @Test
    fun `concurrent rooms are independent and removed membership invalidates pending responses`() = runTest {
        val otherRoom = FakeJoinedRoom(baseRoom = FakeBaseRoom(
            roomId = RoomId("!another:owned.test"),
            updateMembersResult = {},
        ).apply { membersStateFlow.value = RoomMembersState.Ready(persistentListOf(aRoomMember(bot))) })
        val first = async { source.getSuggestions(room, "model ") }
        val second = async { source.getSuggestions(otherRoom, "model ") }
        runCurrent()
        val firstRequest = transport.sentCustomToDeviceEvents[0].content
        val secondRequest = transport.sentCustomToDeviceEvents[1].content
        transport.emitCustomToDeviceEvent(reply(secondRequest, "model owned/second"))
        runCurrent()
        assertThat(second.await().single().command).isEqualTo("/model owned/second")
        assertThat(first.isCompleted).isFalse()
        // A valid signature must not grant access after its sender leaves this room.
        room.baseRoom.membersStateFlow.value = RoomMembersState.Ready(persistentListOf())
        transport.emitCustomToDeviceEvent(reply(firstRequest, "model owned/first"))
        runCurrent()
        assertThat(first.isCompleted).isFalse()
        advanceTimeBy(5001)
        assertThat(first.await()).isEmpty()
        val requestCount = transport.sentCustomToDeviceEvents.size
        assertThat(source.getSuggestions(room, "models")).isEmpty()
        assertThat(transport.sentCustomToDeviceEvents).hasSize(requestCount)
    }
}
