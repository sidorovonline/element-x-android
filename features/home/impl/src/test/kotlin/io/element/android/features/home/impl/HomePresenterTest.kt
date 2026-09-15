/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.roomlist.aRoomListState
import io.element.android.features.home.impl.spaces.HomeSpacesState
import io.element.android.features.home.impl.spaces.aHomeSpacesState
import io.element.android.features.logout.api.direct.aDirectLogoutState
import io.element.android.features.networkmonitor.api.NetworkMonitor
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.features.networkmonitor.test.FakeNetworkMonitor
import io.element.android.features.rageshake.api.RageshakeFeatureAvailability
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.indicator.test.FakeIndicatorService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.sync.SyncService
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.AN_AVATAR_URL
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_NAME
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.sync.FakeSyncService
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - should start with no user and then load user with success`() = runTest {
        val matrixClient = FakeMatrixClient(
            userDisplayName = null,
            userAvatarUrl = null,
        )
        matrixClient.givenGetProfileResult(matrixClient.sessionId, Result.success(MatrixUser(matrixClient.sessionId, A_USER_NAME, AN_AVATAR_URL)))
        val presenter = createHomePresenter(
            client = matrixClient,
            rageshakeFeatureAvailability = { flowOf(false) },
            sessionStore = InMemorySessionStore(
                initialList = listOf(
                    aSessionData(
                        sessionId = matrixClient.sessionId.value,
                        userDisplayName = null,
                        userAvatarUrl = null,
                    )
                ),
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.currentUserAndNeighbors.first()).isEqualTo(
                MatrixUser(A_USER_ID, null, null)
            )
            assertThat(initialState.canReportBug).isFalse()
            skipItems(1)
            val withUserState = awaitItem()
            assertThat(withUserState.currentUserAndNeighbors.first()).isEqualTo(
                MatrixUser(A_USER_ID, A_USER_NAME, AN_AVATAR_URL)
            )
            assertThat(withUserState.showAvatarIndicator).isFalse()
        }
    }

    @Test
    fun `present - can report bug`() = runTest {
        val presenter = createHomePresenter(
            rageshakeFeatureAvailability = { flowOf(true) },
            sessionStore = InMemorySessionStore(
                updateUserProfileResult = { _, _, _ -> },
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.canReportBug).isFalse()
            val finalState = awaitItem()
            assertThat(finalState.canReportBug).isTrue()
        }
    }

    @Test
    fun `present - show avatar indicator`() = runTest {
        val indicatorService = FakeIndicatorService()
        val presenter = createHomePresenter(
            indicatorService = indicatorService,
            sessionStore = InMemorySessionStore(
                updateUserProfileResult = { _, _, _ -> },
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.showAvatarIndicator).isFalse()
            indicatorService.setShowRoomListTopBarIndicator(true)
            val finalState = awaitItem()
            assertThat(finalState.showAvatarIndicator).isTrue()
        }
    }

    @Test
    fun `present - should start with no user and then load user with error`() = runTest {
        val matrixClient = FakeMatrixClient(
            userDisplayName = null,
            userAvatarUrl = null,
        )
        matrixClient.givenGetProfileResult(matrixClient.sessionId, Result.failure(AN_EXCEPTION))
        val presenter = createHomePresenter(
            client = matrixClient,
            sessionStore = InMemorySessionStore(
                updateUserProfileResult = { _, _, _ -> },
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.currentUserAndNeighbors.first()).isEqualTo(MatrixUser(matrixClient.sessionId))
            // No new state is coming
        }
    }

    @Test
    fun `present - NavigationBar change`() = runTest {
        val presenter = createHomePresenter(
            sessionStore = InMemorySessionStore(
                updateUserProfileResult = { _, _, _ -> },
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.currentHomeNavigationBarItem).isEqualTo(HomeNavigationBarItem.Chats)
            initialState.eventSink(HomeEvent.SelectHomeNavigationBarItem(HomeNavigationBarItem.Spaces))
            val finalState = awaitItem()
            assertThat(finalState.currentHomeNavigationBarItem).isEqualTo(HomeNavigationBarItem.Spaces)
        }
    }

    @Test
    fun `present - connection status is connected when sync is running`() = runTest {
        val presenter = createHomePresenter(
            syncService = FakeSyncService(initialSyncState = SyncState.Running),
            networkMonitor = FakeNetworkMonitor(initialStatus = NetworkStatus.Connected),
        )
        presenter.test {
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.Connected)
        }
    }

    @Test
    fun `present - connection status is error offline when network is disconnected`() = runTest {
        val presenter = createHomePresenter(
            syncService = FakeSyncService(initialSyncState = SyncState.Running),
            networkMonitor = FakeNetworkMonitor(initialStatus = NetworkStatus.Disconnected),
        )
        presenter.test {
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.Connecting)
            advanceTimeBy(ERROR_OFFLINE_CONNECTION_DELAY)
            runCurrent()
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.ErrorOffline)
        }
    }

    @Test
    fun `present - connection status is error offline when sync is running on an unvalidated network`() = runTest {
        val networkMonitor = FakeNetworkMonitor(initialStatus = NetworkStatus.Connected).apply {
            givenIsInAirGappedEnvironment(true)
        }
        val presenter = createHomePresenter(
            syncService = FakeSyncService(initialSyncState = SyncState.Running),
            networkMonitor = networkMonitor,
        )
        presenter.test {
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.Connecting)
            advanceTimeBy(ERROR_OFFLINE_CONNECTION_DELAY)
            runCurrent()
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.ErrorOffline)
        }
    }

    @Test
    fun `present - connection status falls back from connecting to error offline when sync stays idle`() = runTest {
        val presenter = createHomePresenter(
            syncService = FakeSyncService(initialSyncState = SyncState.Idle),
            networkMonitor = FakeNetworkMonitor(initialStatus = NetworkStatus.Connected),
        )
        presenter.test {
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.Connecting)
            advanceTimeBy(CONNECTED_IDLE_CONNECTION_TIMEOUT)
            runCurrent()
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.ErrorOffline)
        }
    }

    @Test
    fun `present - transient sync error does not immediately flash error offline`() = runTest {
        val syncService = FakeSyncService(initialSyncState = SyncState.Running)
        val presenter = createHomePresenter(
            syncService = syncService,
            networkMonitor = FakeNetworkMonitor(initialStatus = NetworkStatus.Connected),
        )
        presenter.test {
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.Connected)

            syncService.emitSyncState(SyncState.Error)
            advanceTimeBy(ERROR_OFFLINE_CONNECTION_DELAY / 2)
            runCurrent()
            expectNoEvents()

            syncService.emitSyncState(SyncState.Running)
            advanceTimeBy(ERROR_OFFLINE_CONNECTION_DELAY)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test
    fun `present - retry sync calls start sync and action state clears when sync runs`() = runTest {
        val syncService = FakeSyncService(initialSyncState = SyncState.Offline)
        var startSyncCallCount = 0
        syncService.startSyncLambda = {
            startSyncCallCount++
            Result.success(Unit)
        }
        val presenter = createHomePresenter(
            syncService = syncService,
            networkMonitor = FakeNetworkMonitor(initialStatus = NetworkStatus.Connected),
        )
        presenter.test {
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.Connecting)
            advanceTimeBy(ERROR_OFFLINE_CONNECTION_DELAY)
            runCurrent()
            val errorState = awaitItem()
            assertThat(errorState.connectionStatus).isEqualTo(HomeConnectionStatus.ErrorOffline)

            errorState.eventSink(HomeEvent.RetrySync)
            runCurrent()
            assertThat(startSyncCallCount).isEqualTo(1)

            syncService.emitSyncState(SyncState.Running)
            skipItems(1)
            assertThat(awaitItem().connectionStatus).isEqualTo(HomeConnectionStatus.Connected)
        }
    }
}

internal fun createHomePresenter(
    client: MatrixClient = FakeMatrixClient(),
    syncService: SyncService = FakeSyncService(initialSyncState = SyncState.Running),
    networkMonitor: NetworkMonitor = FakeNetworkMonitor(),
    snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher(),
    rageshakeFeatureAvailability: RageshakeFeatureAvailability = RageshakeFeatureAvailability { flowOf(false) },
    indicatorService: IndicatorService = FakeIndicatorService(),
    homeSpacesPresenter: Presenter<HomeSpacesState> = Presenter { aHomeSpacesState() },
    sessionStore: SessionStore = InMemorySessionStore(),
) = HomePresenter(
    client = client,
    syncService = syncService,
    networkMonitor = networkMonitor,
    snackbarDispatcher = snackbarDispatcher,
    indicatorService = indicatorService,
    roomListPresenter = { aRoomListState() },
    homeSpacesPresenter = homeSpacesPresenter,
    logoutPresenter = { aDirectLogoutState() },
    rageshakeFeatureAvailability = rageshakeFeatureAvailability,
    sessionStore = sessionStore,
)
