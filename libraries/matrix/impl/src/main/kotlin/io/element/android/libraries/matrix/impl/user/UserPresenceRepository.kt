/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.user

import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.user.UserPresence
import io.element.android.libraries.matrix.impl.room.member.RoomMemberMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.UserPresence as RustUserPresence
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface UserPresenceRepository {
    val presences: StateFlow<Map<UserId, UserPresence>>

    fun getCachedOrFetch(userId: UserId): UserPresence?

    fun enrich(roomMember: RoomMember): RoomMember {
        return roomMember.copy(presence = roomMember.presence ?: getCachedOrFetch(roomMember.userId))
    }
}

object NoOpUserPresenceRepository : UserPresenceRepository {
    override val presences: StateFlow<Map<UserId, UserPresence>> = MutableStateFlow(emptyMap())

    override fun getCachedOrFetch(userId: UserId): UserPresence? = null
}

class RustUserPresenceRepository(
    private val getPresence: suspend (UserId) -> RustUserPresence?,
    private val coroutineScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val refreshInterval: Duration? = 60.seconds,
) : UserPresenceRepository {
    constructor(
        client: Client,
        coroutineScope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
    ) : this(
        getPresence = { userId -> client.getPresence(userId.value) },
        coroutineScope = coroutineScope,
        dispatcher = dispatcher,
        refreshInterval = 60.seconds,
    )

    private val mutex = Mutex()
    private val requestedUserIds = mutableSetOf<UserId>()
    private val inFlightUserIds = mutableSetOf<UserId>()

    private val _presences = MutableStateFlow<Map<UserId, UserPresence>>(emptyMap())
    override val presences: StateFlow<Map<UserId, UserPresence>> = _presences.asStateFlow()

    init {
        refreshInterval?.let { interval ->
            coroutineScope.launch(dispatcher) {
                while (isActive) {
                    delay(interval)
                    refreshRequestedUsers()
                }
            }
        }
    }

    override fun getCachedOrFetch(userId: UserId): UserPresence? {
        val cached = presences.value[userId]
        if (cached == null) {
            coroutineScope.launch(dispatcher) {
                fetchPresence(userId, force = false)
            }
        }
        return cached
    }

    private suspend fun refreshRequestedUsers() {
        val userIds = mutex.withLock { requestedUserIds.toList() }
        userIds.forEach { userId ->
            fetchPresence(userId, force = true)
        }
    }

    private suspend fun fetchPresence(userId: UserId, force: Boolean) {
        val shouldFetch = mutex.withLock {
            if (!force && userId in requestedUserIds) {
                false
            } else {
                requestedUserIds += userId
                if (userId in inFlightUserIds) {
                    false
                } else {
                    inFlightUserIds += userId
                    true
                }
            }
        }
        if (!shouldFetch) {
            return
        }

        try {
            val presence = getPresence(userId)?.let(RoomMemberMapper::mapPresence)
            if (presence != null) {
                Timber.d("Loaded presence ${presence.state} for $userId")
                _presences.update { current -> current + (userId to presence) }
            } else {
                Timber.d("No supported presence found for $userId")
            }
        } catch (failure: Throwable) {
            Timber.w(failure, "Failed to load presence for $userId")
        } finally {
            mutex.withLock {
                inFlightUserIds -= userId
            }
        }
    }
}
