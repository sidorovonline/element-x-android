/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.myclaw

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.myclaw.MyClawSessionStatusState
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.to_device.CustomToDeviceEvent
import io.element.android.libraries.matrix.test.A_DEVICE_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RustMyClawSessionStatusServiceTest {
    @Test
    fun `requestStatus sends subscribe request to MyClaw candidate`() = runTest {
        val sentEvents = mutableListOf<SentCustomToDevice>()
        val service = createService(sentEvents = sentEvents)

        service.requestStatus(A_ROOM_ID)

        assertThat(sentEvents).hasSize(1)
        val sent = sentEvents.single()
        assertThat(sent.eventType).isEqualTo(RustMyClawSessionStatusService.REQUEST_TYPE)
        assertThat(sent.userId).isEqualTo(A_BOT_USER_ID)
        val content = Json.parseToJsonElement(sent.content).jsonObject
        assertThat(content["room_id"]?.jsonPrimitive?.contentOrNull).isEqualTo(A_ROOM_ID.value)
        assertThat(content["device_id"]?.jsonPrimitive?.contentOrNull).isEqualTo(A_DEVICE_ID.value)
        assertThat(content["subscribe"]?.jsonPrimitive?.contentOrNull).isEqualTo("true")
        assertThat(sent.txnId).isEqualTo(content["txn_id"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `response updates status only when txn matches pending request`() = runTest {
        val toDeviceEvents = MutableSharedFlow<CustomToDeviceEvent>(extraBufferCapacity = 10)
        val sentEvents = mutableListOf<SentCustomToDevice>()
        val service = createService(
            toDeviceEvents = toDeviceEvents,
            sentEvents = sentEvents,
        )
        runCurrent()

        service.requestStatus(A_ROOM_ID)
        val txnId = sentEvents.single().txnId.orEmpty()
        toDeviceEvents.emit(
            aCustomToDeviceEvent(
                eventType = RustMyClawSessionStatusService.RESPONSE_TYPE,
                content = responseContent(txnId = "wrong-txn", state = "waiting_llm"),
            )
        )
        runCurrent()
        assertThat(service.statuses.value[A_ROOM_ID]).isNull()

        toDeviceEvents.emit(
            aCustomToDeviceEvent(
                eventType = RustMyClawSessionStatusService.RESPONSE_TYPE,
                content = responseContent(txnId = txnId, state = "waiting_llm"),
            )
        )
        runCurrent()

        assertThat(service.statuses.value[A_ROOM_ID]?.state).isEqualTo(MyClawSessionStatusState.WAITING_LLM)
    }

    @Test
    fun `update changes status and expiry clears it`() = runTest {
        val clock = FakeSystemClock(epochMillisResult = 1_000L)
        val toDeviceEvents = MutableSharedFlow<CustomToDeviceEvent>(extraBufferCapacity = 10)
        val service = createService(
            clock = clock,
            toDeviceEvents = toDeviceEvents,
        )
        runCurrent()

        service.requestStatus(A_ROOM_ID)
        toDeviceEvents.emit(
            aCustomToDeviceEvent(
                eventType = RustMyClawSessionStatusService.UPDATE_TYPE,
                content = updateContent(state = "waiting_agent", expiresAt = "1970-01-01T00:00:02Z"),
            )
        )
        runCurrent()
        assertThat(service.statuses.value[A_ROOM_ID]?.state).isEqualTo(MyClawSessionStatusState.WAITING_AGENT)

        clock.epochMillisResult = 2_000L
        advanceTimeBy(1_000L)
        runCurrent()

        assertThat(service.statuses.value[A_ROOM_ID]).isNull()
    }

    @Test
    fun `idle update clears waiting status immediately`() = runTest {
        val toDeviceEvents = MutableSharedFlow<CustomToDeviceEvent>(extraBufferCapacity = 10)
        val service = createService(toDeviceEvents = toDeviceEvents)
        runCurrent()

        service.requestStatus(A_ROOM_ID)
        toDeviceEvents.emit(
            aCustomToDeviceEvent(
                eventType = RustMyClawSessionStatusService.UPDATE_TYPE,
                content = updateContent(state = "waiting_llm"),
            )
        )
        runCurrent()
        assertThat(service.statuses.value[A_ROOM_ID]?.state).isEqualTo(MyClawSessionStatusState.WAITING_LLM)

        toDeviceEvents.emit(
            aCustomToDeviceEvent(
                eventType = RustMyClawSessionStatusService.UPDATE_TYPE,
                content = updateContent(state = "idle", expiresAt = null),
            )
        )
        runCurrent()

        assertThat(service.statuses.value[A_ROOM_ID]).isNull()
    }

    @Test
    fun `statusFlow stays null for idle and unknown room list badge states`() = runTest {
        val toDeviceEvents = MutableSharedFlow<CustomToDeviceEvent>(extraBufferCapacity = 10)
        val service = createService(toDeviceEvents = toDeviceEvents)
        runCurrent()

        service.statusFlow(A_ROOM_ID).test {
            assertThat(awaitItem()).isNull()
            service.requestStatus(A_ROOM_ID)
            toDeviceEvents.emit(
                aCustomToDeviceEvent(
                    eventType = RustMyClawSessionStatusService.UPDATE_TYPE,
                    content = updateContent(state = "idle", expiresAt = null),
                )
            )
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `requestStatus does not repeat subscribe request before refresh interval`() = runTest {
        val clock = FakeSystemClock(epochMillisResult = 1_000L)
        val sentEvents = mutableListOf<SentCustomToDevice>()
        val service = createService(
            clock = clock,
            sentEvents = sentEvents,
        )

        service.requestStatus(A_ROOM_ID)
        service.requestStatus(A_ROOM_ID)

        assertThat(sentEvents).hasSize(1)
    }

    @Test
    fun `requestStatus renews subscribe request after refresh interval`() = runTest {
        val clock = FakeSystemClock(epochMillisResult = 1_000L)
        val sentEvents = mutableListOf<SentCustomToDevice>()
        val service = createService(
            clock = clock,
            sentEvents = sentEvents,
        )

        service.requestStatus(A_ROOM_ID)
        clock.epochMillisResult = 1_000L + 8 * 60 * 1_000
        service.requestStatus(A_ROOM_ID)

        assertThat(sentEvents).hasSize(2)
        assertThat(sentEvents.all { it.eventType == RustMyClawSessionStatusService.REQUEST_TYPE }).isTrue()
        sentEvents.forEach { sent ->
            val content = Json.parseToJsonElement(sent.content).jsonObject
            assertThat(content["subscribe"]?.jsonPrimitive?.contentOrNull).isEqualTo("true")
        }
    }

    private fun TestScope.createService(
        clock: FakeSystemClock = FakeSystemClock(epochMillisResult = 1_000L),
        toDeviceEvents: MutableSharedFlow<CustomToDeviceEvent> = MutableSharedFlow(extraBufferCapacity = 10),
        sentEvents: MutableList<SentCustomToDevice> = mutableListOf(),
        joinedRoom: JoinedRoom = aMyClawDmRoom(),
    ): RustMyClawSessionStatusService {
        return RustMyClawSessionStatusService(
            sessionId = A_SESSION_ID,
            deviceId = A_DEVICE_ID,
            coroutineScope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = clock,
            getJoinedRoom = { roomId -> joinedRoom.takeIf { roomId == A_ROOM_ID } },
            sendCustomToDevice = { eventType, userId, deviceIds, content, txnId ->
                sentEvents += SentCustomToDevice(eventType, userId, deviceIds, content, txnId)
                Result.success(Unit)
            },
            customToDeviceEvents = { toDeviceEvents },
        )
    }

    private fun aMyClawDmRoom(): FakeJoinedRoom {
        return FakeJoinedRoom(
            baseRoom = FakeBaseRoom(
                initialRoomInfo = aRoomInfo(id = A_ROOM_ID, isDm = true),
                getDirectRoomMemberResult = {
                    aRoomMember(userId = A_BOT_USER_ID)
                },
            )
        )
    }

    private fun aCustomToDeviceEvent(
        eventType: String,
        content: String,
    ) = CustomToDeviceEvent(
        eventType = eventType,
        sender = A_BOT_USER_ID,
        content = content,
        encrypted = false,
    )

    private fun responseContent(
        txnId: String,
        state: String,
        expiresAt: String? = "2026-06-29T12:05:00Z",
    ): String {
        return updateContent(state = state, expiresAt = expiresAt)
            .replaceFirst("\"room_id\"", "\"txn_id\":\"$txnId\",\"room_id\"")
    }

    private fun updateContent(
        state: String,
        expiresAt: String? = "2026-06-29T12:05:00Z",
    ): String {
        val content = buildMap {
            put("version", JsonPrimitive(1))
            put("room_id", JsonPrimitive(A_ROOM_ID.value))
            put("session_id", JsonPrimitive("sess_123"))
            put("state", JsonPrimitive(state))
            put("label", JsonPrimitive("Waiting for model"))
            put("updated_at", JsonPrimitive("1970-01-01T00:00:01Z"))
            expiresAt?.let {
                put("expires_at", JsonPrimitive(it))
            }
        }
        return JsonObject(content).toString()
    }

    private data class SentCustomToDevice(
        val eventType: String,
        val userId: UserId,
        val deviceIds: List<DeviceId>,
        val content: String,
        val txnId: String?,
    )

    private companion object {
        val A_BOT_USER_ID = UserId("@myclaw:server.org")
    }
}
