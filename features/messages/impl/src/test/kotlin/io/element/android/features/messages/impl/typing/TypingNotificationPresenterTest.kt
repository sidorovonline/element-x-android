/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.typing

import app.cash.turbine.Event
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.myclaw.MyClawRoomActivity
import io.element.android.libraries.matrix.api.myclaw.MyClawRoomActivityState
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.A_USER_ID_3
import io.element.android.libraries.matrix.test.A_USER_ID_4
import io.element.android.libraries.matrix.test.myclaw.FakeMyClawRoomActivityService
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.preferences.test.InMemorySessionPreferencesStore
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@Suppress("LargeClass")
class TypingNotificationPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state`() = runTest {
        val presenter = createPresenter()
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.renderTypingNotifications).isTrue()
            assertThat(initialState.typingMembers).isEmpty()
            assertThat(initialState.workingDisplayName).isNull()
            assertThat(initialState.reserveSpace).isFalse()
        }
    }

    @Test
    fun `present - typing notification disabled`() = runTest {
        val typingMembersFlow = MutableStateFlow<List<UserId>>(emptyList())
        val room = FakeJoinedRoom(roomTypingMembersFlow = typingMembersFlow)
        val sessionPreferencesStore = InMemorySessionPreferencesStore(
            isRenderTypingNotificationsEnabled = false,
        )
        val presenter = createPresenter(
            joinedRoom = room,
            sessionPreferencesStore = sessionPreferencesStore,
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.renderTypingNotifications).isFalse()
            assertThat(initialState.typingMembers).isEmpty()
            assertThat(initialState.workingDisplayName).isNull()
            typingMembersFlow.emit(listOf(A_USER_ID_2))
            expectNoEvents()
            // Preferences changes
            sessionPreferencesStore.setRenderTypingNotifications(true)
            skipItems(1)
            val oneMemberTypingState = awaitItem()
            assertThat(oneMemberTypingState.renderTypingNotifications).isTrue()
            assertThat(oneMemberTypingState.typingMembers.size).isEqualTo(1)
            assertThat(oneMemberTypingState.typingMembers.first()).isEqualTo(
                TypingRoomMember(
                    disambiguatedDisplayName = A_USER_ID_2.value,
                )
            )
            // Preferences changes again
            sessionPreferencesStore.setRenderTypingNotifications(false)
            skipItems(2)
            val finalState = awaitItem()
            assertThat(finalState.renderTypingNotifications).isFalse()
            assertThat(finalState.typingMembers).isEmpty()
            assertThat(finalState.workingDisplayName).isNull()
        }
    }

    @Test
    fun `present - state is updated when a member is typing, member is not known`() = runTest {
        val typingMembersFlow = MutableStateFlow<List<UserId>>(emptyList())
        val room = FakeJoinedRoom(roomTypingMembersFlow = typingMembersFlow)
        val presenter = createPresenter(joinedRoom = room)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.typingMembers).isEmpty()
            assertThat(initialState.workingDisplayName).isNull()
            typingMembersFlow.emit(listOf(A_USER_ID_2))
            val oneMemberTypingState = awaitItem()
            assertThat(oneMemberTypingState.typingMembers.size).isEqualTo(1)
            assertThat(oneMemberTypingState.typingMembers.first()).isEqualTo(
                TypingRoomMember(
                    disambiguatedDisplayName = A_USER_ID_2.value,
                )
            )
            // User stops typing
            typingMembersFlow.emit(emptyList())
            skipItems(1)
            val finalState = awaitItem()
            assertThat(finalState.typingMembers).isEmpty()
        }
    }

    @Test
    fun `present - state is updated when a member is typing, member is known`() = runTest {
        val aKnownRoomMember = createKnownRoomMember(userId = A_USER_ID_2)
        val typingMembersFlow = MutableStateFlow<List<UserId>>(emptyList())
        val room = FakeJoinedRoom(roomTypingMembersFlow = typingMembersFlow).apply {
            givenRoomMembersState(
                RoomMembersState.Ready(
                    listOf(
                        createKnownRoomMember(A_USER_ID),
                        aKnownRoomMember,
                        createKnownRoomMember(A_USER_ID_3),
                        createKnownRoomMember(A_USER_ID_4),
                    ).toImmutableList()
                )
            )
        }
        val presenter = createPresenter(joinedRoom = room)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.typingMembers).isEmpty()
            assertThat(initialState.workingDisplayName).isNull()
            typingMembersFlow.emit(listOf(A_USER_ID_2))
            val oneMemberTypingState = awaitItem()
            assertThat(oneMemberTypingState.typingMembers.size).isEqualTo(1)
            assertThat(oneMemberTypingState.typingMembers.first()).isEqualTo(
                TypingRoomMember(
                    disambiguatedDisplayName = "Alice Doe (@bob:server.org)",
                )
            )
            // User stops typing
            typingMembersFlow.emit(emptyList())
            skipItems(1)
            val finalState = awaitItem()
            assertThat(finalState.typingMembers).isEmpty()
        }
    }

    @Test
    fun `present - state is updated when a member is typing, member is not known, then known`() = runTest {
        val aKnownRoomMember = createKnownRoomMember(A_USER_ID_2)
        val typingMembersFlow = MutableStateFlow<List<UserId>>(emptyList())
        val room = FakeJoinedRoom(roomTypingMembersFlow = typingMembersFlow)
        val presenter = createPresenter(joinedRoom = room)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.typingMembers).isEmpty()
            assertThat(initialState.workingDisplayName).isNull()
            typingMembersFlow.emit(listOf(A_USER_ID_2))
            val oneMemberTypingState = awaitItem()
            assertThat(oneMemberTypingState.typingMembers.size).isEqualTo(1)
            assertThat(oneMemberTypingState.typingMembers.first()).isEqualTo(
                TypingRoomMember(
                    disambiguatedDisplayName = A_USER_ID_2.value,
                )
            )
            // User is getting known
            room.givenRoomMembersState(
                RoomMembersState.Ready(
                    listOf(aKnownRoomMember).toImmutableList()
                )
            )
            skipItems(1)
            val finalState = awaitItem()
            assertThat(finalState.typingMembers.first()).isEqualTo(
                TypingRoomMember(
                    disambiguatedDisplayName = "Alice Doe (@bob:server.org)",
                )
            )
        }
    }

    @Test
    fun `present - reserveSpace becomes true once we get the first typing notification with room members`() = runTest {
        val typingMembersFlow = MutableStateFlow<List<UserId>>(emptyList())
        val room = FakeJoinedRoom(roomTypingMembersFlow = typingMembersFlow)
        val presenter = createPresenter(joinedRoom = room)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.typingMembers).isEmpty()
            assertThat(initialState.workingDisplayName).isNull()
            typingMembersFlow.emit(listOf(A_USER_ID_2))
            skipItems(1)
            val updatedTypingState = awaitItem()
            assertThat(updatedTypingState.reserveSpace).isTrue()
            // User stops typing
            typingMembersFlow.emit(emptyList())
            // Is still true for all future events
            val futureEvents = cancelAndConsumeRemainingEvents()
            for (event in futureEvents) {
                if (event is Event.Item) {
                    assertThat(event.value.reserveSpace).isTrue()
                }
            }
        }
    }

    @Test
    fun `present - MyClaw working activity exposes the resolved Matrix display name`() = runTest {
        val room = FakeJoinedRoom().apply {
            givenRoomInfo(aRoomInfo(id = roomId, name = ""))
        }
        val myClawRoomActivityService = FakeMyClawRoomActivityService()
        val presenter = createPresenter(
            joinedRoom = room,
            myClawRoomActivityService = myClawRoomActivityService,
        )

        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.workingDisplayName).isNull()

            myClawRoomActivityService.givenActivity(
                aMyClawRoomActivity(
                    room = room,
                    state = MyClawRoomActivityState.WORKING,
                    senderDisplayName = "Windows",
                )
            )
            var workingState = awaitItem()
            while (workingState.workingDisplayName == null) {
                workingState = awaitItem()
            }
            assertThat(workingState.workingDisplayName).isEqualTo("Windows")
            if (workingState.reserveSpace) {
                assertThat(workingState.reserveSpace).isTrue()
            } else {
                assertThat(awaitItem().reserveSpace).isTrue()
            }
        }
    }

    @Test
    fun `present - working activity suppresses native typing members`() = runTest {
        val typingMembersFlow = MutableStateFlow<List<UserId>>(emptyList())
        val room = FakeJoinedRoom(roomTypingMembersFlow = typingMembersFlow).apply {
            givenRoomInfo(aRoomInfo(id = roomId, name = ""))
        }
        val myClawRoomActivityService = FakeMyClawRoomActivityService()
        val presenter = createPresenter(
            joinedRoom = room,
            myClawRoomActivityService = myClawRoomActivityService,
        )

        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.typingMembers).isEmpty()
            assertThat(initialState.workingDisplayName).isNull()

            typingMembersFlow.emit(listOf(A_USER_ID_2))
            var typingState = awaitItem()
            while (typingState.typingMembers.isEmpty()) {
                typingState = awaitItem()
            }
            assertThat(typingState.typingMembers).isNotEmpty()
            assertThat(typingState.workingDisplayName).isNull()

            myClawRoomActivityService.givenActivity(
                aMyClawRoomActivity(
                    room = room,
                    state = MyClawRoomActivityState.WORKING,
                    senderDisplayName = "Windows",
                )
            )
            var workingState = awaitItem()
            while (workingState.workingDisplayName == null) {
                workingState = awaitItem()
            }
            assertThat(workingState.workingDisplayName).isEqualTo("Windows")
            assertThat(workingState.typingMembers).isEmpty()
        }
    }

    @Test
    fun `present - clearing MyClaw working restores native typing members`() = runTest {
        val typingMembersFlow = MutableStateFlow<List<UserId>>(emptyList())
        val room = FakeJoinedRoom(roomTypingMembersFlow = typingMembersFlow).apply {
            givenRoomInfo(aRoomInfo(id = roomId, name = ""))
            givenRoomMembersState(
                RoomMembersState.Ready(
                    listOf(
                        createKnownRoomMember(userId = A_USER_ID_2, displayName = "Windows", isNameAmbiguous = false),
                    ).toImmutableList()
                )
            )
        }
        val myClawRoomActivityService = FakeMyClawRoomActivityService()
        val presenter = createPresenter(
            joinedRoom = room,
            myClawRoomActivityService = myClawRoomActivityService,
        )

        presenter.test {
            skipItems(1)
            typingMembersFlow.emit(listOf(A_USER_ID_2))
            myClawRoomActivityService.givenActivity(
                aMyClawRoomActivity(
                    room = room,
                    state = MyClawRoomActivityState.WORKING,
                    senderDisplayName = "Windows",
                )
            )
            var workingState = awaitItem()
            while (workingState.workingDisplayName == null) {
                workingState = awaitItem()
            }
            assertThat(workingState.workingDisplayName).isEqualTo("Windows")
            assertThat(workingState.typingMembers).isEmpty()

            myClawRoomActivityService.clearActivity(room.roomId)
            var myClawTypingState = awaitItem()
            while (myClawTypingState.typingMembers.isEmpty()) {
                myClawTypingState = awaitItem()
            }
            assertThat(myClawTypingState.workingDisplayName).isNull()
            assertThat(myClawTypingState.typingMembers).containsExactly(
                TypingRoomMember(disambiguatedDisplayName = "Windows")
            )
        }
    }

    private fun createPresenter(
        joinedRoom: JoinedRoom = FakeJoinedRoom().apply {
            givenRoomInfo(aRoomInfo(id = roomId, name = ""))
        },
        sessionPreferencesStore: SessionPreferencesStore = InMemorySessionPreferencesStore(
            isRenderTypingNotificationsEnabled = true
        ),
        myClawRoomActivityService: FakeMyClawRoomActivityService = FakeMyClawRoomActivityService(),
    ) = TypingNotificationPresenter(
        room = joinedRoom,
        sessionPreferencesStore = sessionPreferencesStore,
        myClawRoomActivityService = myClawRoomActivityService,
    )

    private fun createKnownRoomMember(
        userId: UserId,
        displayName: String = "Alice Doe",
        isNameAmbiguous: Boolean = true,
    ) = aRoomMember(
        userId = userId,
        displayName = displayName,
        isNameAmbiguous = isNameAmbiguous,
    )

    private fun aMyClawRoomActivity(
        room: JoinedRoom,
        state: MyClawRoomActivityState,
        senderDisplayName: String = "Windows",
    ) = MyClawRoomActivity(
        roomId = room.roomId,
        sessionId = "sess_123",
        state = state,
        senderDisplayName = senderDisplayName,
        updatedAtMillis = null,
        expiresAtMillis = Long.MAX_VALUE,
    )
}
