/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.myclaw

import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.core.RoomId

@Immutable
data class MyClawRoomActivity(
    val roomId: RoomId,
    val sessionId: String,
    val state: MyClawRoomActivityState,
    /** Matrix room-member profile display name resolved for the validated to-device sender. */
    val senderDisplayName: String,
    val updatedAtMillis: Long?,
    val expiresAtMillis: Long,
)

enum class MyClawRoomActivityState {
    WORKING,
}
