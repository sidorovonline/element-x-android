/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.test.myclaw

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.myclaw.MyClawSessionStatus
import io.element.android.libraries.matrix.api.myclaw.MyClawSessionStatusService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FakeMyClawSessionStatusService(
    initialStatuses: Map<RoomId, MyClawSessionStatus> = emptyMap(),
) : MyClawSessionStatusService {
    private val _statuses = MutableStateFlow(initialStatuses)
    override val statuses: StateFlow<Map<RoomId, MyClawSessionStatus>> = _statuses

    val requestedRoomIds = mutableListOf<RoomId>()

    override fun statusFlow(roomId: RoomId): Flow<MyClawSessionStatus?> {
        return statuses
            .map { it[roomId] }
            .distinctUntilChanged()
    }

    override suspend fun requestStatus(roomId: RoomId, subscribe: Boolean) {
        requestedRoomIds += roomId
    }

    fun givenStatus(status: MyClawSessionStatus) {
        _statuses.value = _statuses.value + (status.roomId to status)
    }

    fun clearStatus(roomId: RoomId) {
        _statuses.value = _statuses.value - roomId
    }
}
