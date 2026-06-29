/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.user

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.PresenceState
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.room.aRoomMember
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.matrix.rustcomponents.sdk.PresenceState as RustPresenceState
import org.matrix.rustcomponents.sdk.UserPresence as RustUserPresence

@OptIn(ExperimentalCoroutinesApi::class)
class RustUserPresenceRepositoryTest {
    @Test
    fun `getCachedOrFetch loads presence and enriches room members`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val requestedUserIds = mutableListOf<UserId>()
        val repository = RustUserPresenceRepository(
            getPresence = { userId ->
                requestedUserIds += userId
                RustUserPresence(
                    state = RustPresenceState.ONLINE,
                    statusMsg = "At keyboard",
                    lastActiveAgo = 42UL,
                    currentlyActive = true,
                )
            },
            coroutineScope = this,
            dispatcher = dispatcher,
            refreshInterval = null,
        )

        assertThat(repository.getCachedOrFetch(A_USER_ID)).isNull()

        advanceUntilIdle()

        assertThat(requestedUserIds).containsExactly(A_USER_ID)
        val presence = repository.getCachedOrFetch(A_USER_ID)
        assertThat(presence?.state).isEqualTo(PresenceState.ONLINE)
        assertThat(presence?.statusMessage).isEqualTo("At keyboard")
        assertThat(repository.enrich(aRoomMember(userId = A_USER_ID)).presence).isEqualTo(presence)
    }
}
