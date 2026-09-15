/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.myclaw

import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.myclaw.MyClawRoomActivity
import io.element.android.libraries.matrix.api.myclaw.MyClawRoomActivityService
import io.element.android.libraries.matrix.api.myclaw.myClawCandidateUserIds
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.roomMembers
import io.element.android.libraries.matrix.api.to_device.CustomToDeviceEvent
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

internal class RustMyClawRoomActivityService(
    private val sessionId: SessionId,
    private val deviceId: DeviceId,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val clock: SystemClock,
    private val getRoom: suspend (RoomId) -> BaseRoom?,
    private val getProfile: suspend (UserId) -> Result<MatrixUser>,
    private val sendCustomToDevice: suspend (String, UserId, List<DeviceId>, String, String?) -> Result<Unit>,
    private val customToDeviceEvents: (String) -> Flow<CustomToDeviceEvent>,
) : MyClawRoomActivityService {
    private val _activities = MutableStateFlow<Map<RoomId, MyClawRoomActivity>>(emptyMap())
    override val activities: StateFlow<Map<RoomId, MyClawRoomActivity>> = _activities

    private val lock = Mutex()
    private val pendingResponseRooms = mutableMapOf<String, RoomId>()
    private val candidateUserIdsByRoom = mutableMapOf<RoomId, Set<UserId>>()
    private val verifiedDisplayNames = mutableMapOf<Pair<RoomId, UserId>, String>()
    private val subscribedRoomTimestamps = mutableMapOf<RoomId, Long>()
    private val displayNameResolutionJobs = mutableMapOf<RoomId, Job>()
    private val expiryJobs = mutableMapOf<RoomId, Job>()

    init {
        observeToDeviceEvents(RESPONSE_TYPE)
        observeToDeviceEvents(UPDATE_TYPE)
    }

    override fun activityFlow(roomId: RoomId): Flow<MyClawRoomActivity?> {
        return activities
            .map { it[roomId] }
            .distinctUntilChanged()
    }

    override suspend fun requestActivity(roomId: RoomId, subscribe: Boolean) = withContext(dispatcher) {
        val room = getRoom(roomId) ?: return@withContext
        val candidateUserIds = room.myClawCandidateUserIds(sessionId)
        if (candidateUserIds.isEmpty()) return@withContext
        candidateUserIds.forEach { candidateUserId ->
            resolveSenderDisplayName(roomId, candidateUserId, room)
        }
        val nowMillis = clock.epochMillis()
        val shouldRequest = lock.withLock {
            val subscribedAtMillis = subscribedRoomTimestamps[roomId]
            if (subscribe && subscribedAtMillis != null && nowMillis - subscribedAtMillis < SUBSCRIPTION_REFRESH_INTERVAL.inWholeMilliseconds) {
                candidateUserIdsByRoom[roomId] = candidateUserIds.toSet()
                false
            } else {
                if (subscribe) {
                    subscribedRoomTimestamps[roomId] = nowMillis
                }
                true
            }
        }
        if (!shouldRequest) return@withContext

        val txnId = UUID.randomUUID().toString()
        lock.withLock {
            pendingResponseRooms[txnId] = roomId
            candidateUserIdsByRoom[roomId] = candidateUserIds.toSet()
        }
        schedulePendingResponseCleanup(txnId, roomId, subscribe)

        val requestContent = buildRequestContent(
            txnId = txnId,
            roomId = roomId,
            subscribe = subscribe,
        )
        val sendResults = candidateUserIds.map { candidateUserId ->
            sendCustomToDevice(
                REQUEST_TYPE,
                candidateUserId,
                emptyList(),
                requestContent,
                txnId,
            )
        }
        if (sendResults.none { it.isSuccess }) {
            lock.withLock {
                pendingResponseRooms.remove(txnId)
                subscribedRoomTimestamps.remove(roomId)
            }
            Timber.w("Failed to send MyClaw room activity request for roomId=$roomId")
        }
    }

    override fun unsubscribeFromActivity(roomIds: Set<RoomId>) {
        if (roomIds.isEmpty()) return
        coroutineScope.launch {
            lock.withLock {
                roomIds.forEach { roomId ->
                    candidateUserIdsByRoom.remove(roomId)
                    subscribedRoomTimestamps.remove(roomId)
                    pendingResponseRooms.entries.removeAll { it.value == roomId }
                    verifiedDisplayNames.keys.removeAll { it.first == roomId }
                }
            }
            roomIds.forEach(::clearActivity)
        }
    }

    private fun observeToDeviceEvents(eventType: String) {
        customToDeviceEvents(eventType)
            .filter { it.eventType == eventType }
            .onEach { event ->
                runCatchingExceptions {
                    handleEvent(eventType, event)
                }.onFailure {
                    Timber.w(it, "Failed to handle MyClaw room activity event")
                }
            }
            .launchIn(coroutineScope)
    }

    private suspend fun handleEvent(eventType: String, event: CustomToDeviceEvent) {
        val payload = MyClawRoomActivityParser.parse(event.content) ?: return
        val roomId = payload.roomId
        val validCandidate = lock.withLock {
            event.sender in candidateUserIdsByRoom[roomId].orEmpty()
        }
        if (!validCandidate) return

        if (eventType == RESPONSE_TYPE) {
            val txnId = payload.txnId ?: return
            val matchingRoomId = lock.withLock { pendingResponseRooms[txnId] }
            if (matchingRoomId != roomId) return
            lock.withLock {
                pendingResponseRooms.remove(txnId)
            }
        }

        val parsedActivity = payload.activity
        if (parsedActivity == null) {
            clearActivity(roomId)
        } else {
            resolveAndApplyActivity(parsedActivity, event.sender)
        }
    }

    private suspend fun resolveSenderDisplayName(roomId: RoomId, sender: UserId, room: BaseRoom? = null): String? {
        val resolvedRoom = room ?: getRoom(roomId)
        val loadedRoomDisplayName = resolvedRoom
            ?.membersStateFlow
            ?.value
            ?.roomMembers()
            ?.firstOrNull { it.userId == sender }
            ?.displayName
            ?.takeIf(String::isNotBlank)
        val updatedRoomDisplayName = if (loadedRoomDisplayName == null) {
            resolvedRoom
                ?.getUpdatedMember(sender)
                ?.getOrNull()
                ?.displayName
                ?.takeIf(String::isNotBlank)
        } else {
            null
        }
        val roomDisplayName = loadedRoomDisplayName
            ?: updatedRoomDisplayName
            ?: runCatchingExceptions { resolvedRoom?.getDirectRoomMember() }
                .getOrNull()
                ?.takeIf { it.userId == sender }
                ?.displayName
                ?.takeIf(String::isNotBlank)
        val cachedDisplayName = lock.withLock {
            verifiedDisplayNames[roomId to sender]
        }
        val profileDisplayName = if (roomDisplayName == null && cachedDisplayName == null) {
            getProfile(sender).getOrNull()?.displayName?.takeIf(String::isNotBlank)
        } else {
            null
        }
        val resolvedDisplayName = roomDisplayName ?: cachedDisplayName ?: profileDisplayName
        if (resolvedDisplayName != null) {
            lock.withLock {
                verifiedDisplayNames[roomId to sender] = resolvedDisplayName
            }
            return resolvedDisplayName
        }
        return cachedDisplayName
    }

    private fun resolveAndApplyActivity(parsedActivity: ParsedMyClawRoomActivity, sender: UserId) {
        val roomId = parsedActivity.roomId
        if (parsedActivity.expiresAtMillis <= clock.epochMillis()) {
            clearActivity(roomId)
            return
        }
        displayNameResolutionJobs.remove(roomId)?.cancel()
        displayNameResolutionJobs[roomId] = coroutineScope.launch {
            while (clock.epochMillis() < parsedActivity.expiresAtMillis) {
                val senderDisplayName = withContext(dispatcher) {
                    runCatchingExceptions {
                        resolveSenderDisplayName(roomId, sender)
                    }.getOrNull()
                }
                if (senderDisplayName != null) {
                    applyActivity(
                        MyClawRoomActivity(
                            roomId = parsedActivity.roomId,
                            sessionId = parsedActivity.sessionId,
                            state = parsedActivity.state,
                            senderDisplayName = senderDisplayName,
                            updatedAtMillis = parsedActivity.updatedAtMillis,
                            expiresAtMillis = parsedActivity.expiresAtMillis,
                        )
                    )
                    return@launch
                }
                delay(DISPLAY_NAME_RETRY_INTERVAL)
            }
            Timber.w("Unable to resolve MyClaw Matrix profile before activity expired for roomId=$roomId sender=$sender")
            clearActivity(roomId)
        }
    }

    private fun applyActivity(activity: MyClawRoomActivity) {
        val nowMillis = clock.epochMillis()
        if (activity.expiresAtMillis <= nowMillis) {
            clearActivity(activity.roomId)
            return
        }

        _activities.update { current ->
            current + (activity.roomId to activity)
        }
        expiryJobs.remove(activity.roomId)?.cancel()
        expiryJobs[activity.roomId] = coroutineScope.launch {
            delay(activity.expiresAtMillis - nowMillis)
            val current = _activities.value[activity.roomId]
            if (current?.expiresAtMillis == activity.expiresAtMillis && current.expiresAtMillis <= clock.epochMillis()) {
                clearActivity(activity.roomId)
            }
        }
    }

    private fun clearActivity(roomId: RoomId) {
        displayNameResolutionJobs.remove(roomId)?.cancel()
        expiryJobs.remove(roomId)?.cancel()
        _activities.update { current ->
            current - roomId
        }
    }

    private fun schedulePendingResponseCleanup(txnId: String, roomId: RoomId, subscribe: Boolean) {
        coroutineScope.launch {
            delay(PENDING_RESPONSE_TTL)
            val shouldRetry = lock.withLock {
                val expiredRoomId = pendingResponseRooms.remove(txnId)
                if (expiredRoomId != null) {
                    subscribedRoomTimestamps.remove(expiredRoomId)
                }
                expiredRoomId != null && subscribe
            }
            if (shouldRetry) {
                requestActivity(roomId = roomId, subscribe = subscribe)
            }
        }
    }

    private fun buildRequestContent(txnId: String, roomId: RoomId, subscribe: Boolean): String {
        return JsonObject(
            mapOf(
                "version" to JsonPrimitive(VERSION),
                "txn_id" to JsonPrimitive(txnId),
                "room_id" to JsonPrimitive(roomId.value),
                "device_id" to JsonPrimitive(deviceId.value),
                "subscribe" to JsonPrimitive(subscribe),
            )
        ).toString()
    }

    companion object {
        const val REQUEST_TYPE = "icu.victor.myclaw.room_activity.request"
        const val RESPONSE_TYPE = "icu.victor.myclaw.room_activity.response"
        const val UPDATE_TYPE = "icu.victor.myclaw.room_activity.update"

        private const val VERSION = 1
        private val DISPLAY_NAME_RETRY_INTERVAL = 1.seconds
        private val PENDING_RESPONSE_TTL = 8.seconds
        private val SUBSCRIPTION_REFRESH_INTERVAL = 8.seconds
    }
}
