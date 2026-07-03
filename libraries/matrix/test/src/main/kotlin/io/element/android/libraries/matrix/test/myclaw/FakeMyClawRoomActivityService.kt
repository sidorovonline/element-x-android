/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.test.myclaw

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.myclaw.MyClawRoomActivity
import io.element.android.libraries.matrix.api.myclaw.MyClawRoomActivityService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FakeMyClawRoomActivityService(
    initialActivities: Map<RoomId, MyClawRoomActivity> = emptyMap(),
) : MyClawRoomActivityService {
    private val _activities = MutableStateFlow(initialActivities)
    override val activities: StateFlow<Map<RoomId, MyClawRoomActivity>> = _activities

    val requestedRoomIds = mutableListOf<RoomId>()
    val unsubscribedRoomIds = mutableListOf<RoomId>()

    override fun activityFlow(roomId: RoomId): Flow<MyClawRoomActivity?> {
        return activities
            .map { it[roomId] }
            .distinctUntilChanged()
    }

    override suspend fun requestActivity(roomId: RoomId, subscribe: Boolean) {
        requestedRoomIds += roomId
    }

    override fun unsubscribeFromActivity(roomIds: Set<RoomId>) {
        unsubscribedRoomIds += roomIds
        _activities.value = _activities.value - roomIds
    }

    fun givenActivity(activity: MyClawRoomActivity) {
        _activities.value = _activities.value + (activity.roomId to activity)
    }

    fun clearActivity(roomId: RoomId) {
        _activities.value = _activities.value - roomId
    }
}
