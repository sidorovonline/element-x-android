/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.spaces.HomeSpacesState
import io.element.android.features.logout.api.direct.DirectLogoutState
import io.element.android.features.networkmonitor.api.NetworkMonitor
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.features.rageshake.api.RageshakeFeatureAvailability
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.indicator.api.IndicatorService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.sync.SyncService
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal val CONNECTED_IDLE_CONNECTION_TIMEOUT = 15.seconds
internal val ERROR_OFFLINE_CONNECTION_DELAY = 750.milliseconds

@Inject
class HomePresenter(
    private val client: MatrixClient,
    private val syncService: SyncService,
    private val networkMonitor: NetworkMonitor,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val indicatorService: IndicatorService,
    private val roomListPresenter: Presenter<RoomListState>,
    private val homeSpacesPresenter: Presenter<HomeSpacesState>,
    private val logoutPresenter: Presenter<DirectLogoutState>,
    private val rageshakeFeatureAvailability: RageshakeFeatureAvailability,
    private val sessionStore: SessionStore,
) : Presenter<HomeState> {
    private val currentUserWithNeighborsBuilder = CurrentUserWithNeighborsBuilder()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun present(): HomeState {
        val coroutineState = rememberCoroutineScope()
        val matrixUser by client.userProfile.collectAsState()
        val currentUserAndNeighbors by remember {
            combine(
                client.userProfile,
                sessionStore.sessionsFlow(),
                currentUserWithNeighborsBuilder::build,
            )
        }.collectAsState(initial = persistentListOf(matrixUser))
        val isOnline by syncService.isOnline.collectAsState()
        val connectionStatus by remember {
            combine(
                syncService.syncState,
                networkMonitor.connectivity,
                networkMonitor.isNetworkBlocked,
                networkMonitor.isInAirGappedEnvironment,
                ::ConnectionStatusInputs,
            )
                .flatMapLatest { inputs ->
                    connectionStatusFlow(inputs)
                }
                .distinctUntilChanged()
        }.collectAsState(
            initial = initialConnectionStatus(
                ConnectionStatusInputs(
                    syncState = syncService.syncState.value,
                    networkStatus = networkMonitor.connectivity.value,
                    isNetworkBlocked = networkMonitor.isNetworkBlocked.value,
                    isInAirGappedEnvironment = networkMonitor.isInAirGappedEnvironment.value,
                )
            )
        )
        val canReportBug by remember { rageshakeFeatureAvailability.isAvailable() }.collectAsState(false)
        val roomListState = roomListPresenter.present()
        val homeSpacesState = homeSpacesPresenter.present()
        var currentHomeNavigationBarItemOrdinal by rememberSaveable { mutableIntStateOf(HomeNavigationBarItem.Chats.ordinal) }
        val currentHomeNavigationBarItem by remember {
            derivedStateOf {
                HomeNavigationBarItem.from(currentHomeNavigationBarItemOrdinal)
            }
        }
        LaunchedEffect(Unit) {
            // Force a refresh of the profile
            client.getUserProfile()
        }
        // Avatar indicator
        val showAvatarIndicator by indicatorService.showRoomListTopBarIndicator()
        val directLogoutState = logoutPresenter.present()

        fun handleEvent(event: HomeEvent) {
            when (event) {
                is HomeEvent.SelectHomeNavigationBarItem -> {
                    currentHomeNavigationBarItemOrdinal = event.item.ordinal
                }
                is HomeEvent.SwitchToAccount -> coroutineState.launch {
                    sessionStore.setLatestSession(event.sessionId.value)
                }
                HomeEvent.RetrySync -> coroutineState.launch {
                    syncService.startSync()
                }
            }
        }

        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
        return HomeState(
            currentUserAndNeighbors = currentUserAndNeighbors,
            showAvatarIndicator = showAvatarIndicator,
            hasNetworkConnection = isOnline,
            connectionStatus = connectionStatus,
            currentHomeNavigationBarItem = currentHomeNavigationBarItem,
            roomListState = roomListState,
            homeSpacesState = homeSpacesState,
            snackbarMessage = snackbarMessage,
            canReportBug = canReportBug,
            directLogoutState = directLogoutState,
            eventSink = ::handleEvent,
        )
    }

    private fun connectionStatusFlow(inputs: ConnectionStatusInputs) = flow {
        when {
            inputs.isOffline() -> {
                delay(ERROR_OFFLINE_CONNECTION_DELAY)
                emit(HomeConnectionStatus.ErrorOffline)
            }
            inputs.syncState == SyncState.Running -> emit(HomeConnectionStatus.Connected)
            inputs.syncState == SyncState.Idle -> {
                emit(HomeConnectionStatus.Connecting)
                delay(CONNECTED_IDLE_CONNECTION_TIMEOUT)
                emit(HomeConnectionStatus.ErrorOffline)
            }
            else -> emit(HomeConnectionStatus.Connecting)
        }
    }

    private fun initialConnectionStatus(inputs: ConnectionStatusInputs): HomeConnectionStatus {
        return if (!inputs.isOffline() && inputs.syncState == SyncState.Running) {
            HomeConnectionStatus.Connected
        } else {
            HomeConnectionStatus.Connecting
        }
    }

    private fun ConnectionStatusInputs.isOffline(): Boolean {
        return networkStatus == NetworkStatus.Disconnected ||
            isNetworkBlocked ||
            isInAirGappedEnvironment ||
            syncState.isErrorOffline()
    }

    private fun SyncState.isErrorOffline(): Boolean {
        return this == SyncState.Error || this == SyncState.Offline || this == SyncState.Terminated
    }
}

private data class ConnectionStatusInputs(
    val syncState: SyncState,
    val networkStatus: NetworkStatus,
    val isNetworkBlocked: Boolean,
    val isInAirGappedEnvironment: Boolean,
)
