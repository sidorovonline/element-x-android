/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.typing

import kotlinx.collections.immutable.ImmutableList

/**
 * State for the typing notification view.
 */
data class TypingNotificationState(
    /** Whether to render the typing notifications based on the user's preferences. */
    val renderTypingNotifications: Boolean,
    /** The room members currently typing. */
    val typingMembers: ImmutableList<TypingRoomMember>,
    /** The MyClaw room activity display name to render as typing, or null when inactive. */
    val typingDisplayName: String?,
    /** The MyClaw room activity display name to render as working, or null when inactive. */
    val workingDisplayName: String?,
    /** Whether to reserve space for the typing notifications at the bottom of the timeline. */
    val reserveSpace: Boolean,
)
