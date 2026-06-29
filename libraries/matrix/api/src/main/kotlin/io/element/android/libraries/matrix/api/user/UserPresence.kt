/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.user

import androidx.compose.runtime.Immutable

@Immutable
data class UserPresence(
    val state: PresenceState,
    val statusMessage: String?,
    val lastActiveAgo: Long?,
    val currentlyActive: Boolean?,
)

enum class PresenceState {
    ONLINE,
    OFFLINE,
    UNAVAILABLE,
    BUSY,
}
