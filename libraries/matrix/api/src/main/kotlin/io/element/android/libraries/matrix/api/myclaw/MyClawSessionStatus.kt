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
data class MyClawSessionStatus(
    val roomId: RoomId,
    val sessionId: String,
    val state: MyClawSessionStatusState,
    val label: String,
    val updatedAtMillis: Long?,
    val expiresAtMillis: Long,
) {
    val isWaiting: Boolean
        get() = state == MyClawSessionStatusState.WAITING_LLM || state == MyClawSessionStatusState.WAITING_AGENT
}

enum class MyClawSessionStatusState {
    IDLE,
    RUNNING,
    WAITING_LLM,
    WAITING_AGENT,
}
