/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.avatar.anAvatarData
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.api.user.PresenceState
import io.element.android.libraries.matrix.api.user.UserPresence

@Composable
fun AvatarWithPresence(
    avatarData: AvatarData,
    avatarType: AvatarType,
    presence: UserPresence?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    forcedAvatarSize: Dp? = null,
    hideImage: Boolean = false,
) {
    val avatarSize = forcedAvatarSize ?: avatarData.size.dp
    Box(modifier = modifier.size(avatarSize)) {
        Avatar(
            avatarData = avatarData,
            avatarType = avatarType,
            modifier = Modifier.fillMaxSize(),
            contentDescription = contentDescription,
            forcedAvatarSize = avatarSize,
            hideImage = hideImage,
        )
        presence?.let {
            PresenceBadge(
                state = it.state,
                size = avatarData.size.presenceBadgeSize(),
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun PresenceBadge(
    state: PresenceState,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ElementTheme.colors.bgCanvasDefault)
            .padding(2.dp)
            .clip(CircleShape)
            .background(state.color())
            .clearAndSetSemantics {},
    )
}

@Composable
private fun PresenceState.color(): Color {
    return when (this) {
        PresenceState.ONLINE -> Color(0xFF0DBD8B)
        PresenceState.UNAVAILABLE -> Color(0xFFD9B072)
        PresenceState.OFFLINE -> if (ElementTheme.isLightTheme) Color(0xFFC1C6CD) else Color(0xFF394049)
        PresenceState.BUSY -> Color(0xFFFF5B55)
    }
}

private fun AvatarSize.presenceBadgeSize(): Dp {
    return when (this) {
        AvatarSize.RoomListItem -> 16.dp
        AvatarSize.RoomDetailsHeader,
        AvatarSize.UserHeader,
        AvatarSize.RoomListManageUser,
        AvatarSize.EditProfileDetails,
        AvatarSize.EditSpaceDetails -> 20.dp
        AvatarSize.UserListItem,
        AvatarSize.RoomSelectRoomListItem,
        AvatarSize.CustomRoomNotificationSetting -> 12.dp
        AvatarSize.TimelineReadReceipt -> 8.dp
        else -> 11.dp
    }
}

@PreviewsDayNight
@Composable
internal fun AvatarWithPresencePreview() = ElementPreview {
    AvatarWithPresence(
        avatarData = anAvatarData(size = AvatarSize.RoomListItem),
        avatarType = AvatarType.User,
        presence = UserPresence(
            state = PresenceState.ONLINE,
            statusMessage = null,
            lastActiveAgo = null,
            currentlyActive = null,
        ),
    )
}
