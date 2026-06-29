/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.roomlist

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.roomlist.LatestEventValue
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import io.element.android.libraries.matrix.api.user.UserPresence
import io.element.android.libraries.matrix.impl.room.RoomInfoMapper
import io.element.android.libraries.matrix.impl.room.member.RoomMemberMapper
import io.element.android.libraries.matrix.impl.timeline.item.event.TimelineEventContentMapper
import io.element.android.libraries.matrix.impl.timeline.item.event.map
import io.element.android.libraries.matrix.impl.user.NoOpUserPresenceRepository
import io.element.android.libraries.matrix.impl.user.UserPresenceRepository
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.use
import uniffi.matrix_sdk_ui.LatestEventValueLocalState
import org.matrix.rustcomponents.sdk.LatestEventValue as RustLatestEventValue

class RoomSummaryFactory(
    private val sessionId: SessionId,
    private val userPresenceRepository: UserPresenceRepository = NoOpUserPresenceRepository,
    private val contentMapper: TimelineEventContentMapper = TimelineEventContentMapper(),
    private val roomInfoMapper: RoomInfoMapper = RoomInfoMapper(),
) {
    suspend fun create(room: Room): RoomSummary {
        val roomInfo = room.roomInfo().let(roomInfoMapper::map)
        val latestEvent = room.latestEvent().use { event ->
            when (event) {
                is RustLatestEventValue.None -> LatestEventValue.None
                is RustLatestEventValue.Local -> when (event.state) {
                    LatestEventValueLocalState.IS_SENDING,
                    LatestEventValueLocalState.CANNOT_BE_SENT -> LatestEventValue.Local(
                        timestamp = event.timestamp.toLong(),
                        content = contentMapper.map(event.content),
                        isSending = event.state == LatestEventValueLocalState.IS_SENDING,
                        senderId = UserId(event.sender),
                        senderProfile = event.profile.map(),
                    )
                    // This is the same as a remote event, we just haven't received the local -> remote update yet
                    LatestEventValueLocalState.HAS_BEEN_SENT -> LatestEventValue.Remote(
                        timestamp = event.timestamp.toLong(),
                        content = contentMapper.map(event.content),
                        senderId = UserId(event.sender),
                        senderProfile = event.profile.map(),
                        isOwn = true,
                    )
                }
                is RustLatestEventValue.Remote -> LatestEventValue.Remote(
                    timestamp = event.timestamp.toLong(),
                    content = contentMapper.map(event.content),
                    senderId = UserId(event.sender),
                    senderProfile = event.profile.map(),
                    isOwn = event.isOwn,
                )
                is RustLatestEventValue.RemoteInvite -> LatestEventValue.RoomInvite(
                    timestamp = event.timestamp.toLong(),
                    inviterId = event.inviter?.let(::UserId),
                    invitedProfile = event.inviterProfile.map(),
                )
            }
        }
        return RoomSummary(
            info = roomInfo,
            latestEvent = latestEvent,
            directUserPresence = room.directUserPresence(roomInfo),
        )
    }

    private suspend fun Room.directUserPresence(roomInfo: RoomInfo): UserPresence? {
        if (!roomInfo.isDm) return null
        return membersNoSync().use { members ->
            members.nextChunk(members.len())
                ?.map(RoomMemberMapper::map)
                ?.firstOrNull { roomMember ->
                    !roomMember.isServiceMember &&
                        roomMember.userId != sessionId &&
                        roomMember.membership.isActive()
                }
                ?.let(userPresenceRepository::enrich)
                ?.presence
        }
    }
}
