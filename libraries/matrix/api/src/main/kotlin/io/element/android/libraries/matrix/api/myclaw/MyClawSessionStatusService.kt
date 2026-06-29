/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.myclaw

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MyClawSessionStatusService {
    val statuses: StateFlow<Map<RoomId, MyClawSessionStatus>>

    fun statusFlow(roomId: RoomId): Flow<MyClawSessionStatus?>

    suspend fun requestStatus(roomId: RoomId, subscribe: Boolean = true)
}
