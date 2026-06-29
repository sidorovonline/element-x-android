/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.myclaw

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.joinedRoomMembers

private const val DEFAULT_MAX_ROOM_CANDIDATES = 10

suspend fun JoinedRoom.myClawCandidateUserIds(
    sessionId: SessionId,
    maxRoomCandidates: Int = DEFAULT_MAX_ROOM_CANDIDATES,
): List<UserId> {
    roomDirectCandidateUserId(sessionId)?.let {
        return listOf(it)
    }
    if (info().isDm) {
        return emptyList()
    }

    val joinedMembers = membersStateFlow.value.joinedRoomMembers()
    if (joinedMembers.isEmpty()) {
        runCatching { updateMembers() }
    }
    val candidateMembers = membersStateFlow.value
        .joinedRoomMembers()
        .filterNot { it.userId == sessionId }
        .filterNot { it.isServiceMember }

    return if (candidateMembers.size <= maxRoomCandidates) {
        candidateMembers.map { it.userId }
    } else {
        emptyList()
    }
}

private suspend fun JoinedRoom.roomDirectCandidateUserId(sessionId: SessionId): UserId? {
    return getDirectRoomMember()
        ?.takeUnless { it.isServiceMember }
        ?.userId
        ?.takeUnless { it == sessionId }
}
