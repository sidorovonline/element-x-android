/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.datasource

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.eventformatter.api.RoomLatestEventFormatter
import io.element.android.libraries.eventformatter.test.FakeRoomLatestEventFormatter
import io.element.android.libraries.matrix.api.myclaw.MyClawSessionStatusState
import io.element.android.libraries.matrix.test.room.aRoomSummary
import org.junit.Test

class RoomListRoomSummaryFactoryTest {
    @Test
    fun `create includes MyClaw session status`() {
        val status = io.element.android.features.home.impl.model.aMyClawSessionStatus(state = MyClawSessionStatusState.WAITING_AGENT)
        val result = aRoomListRoomSummaryFactory().create(
            roomSummary = aRoomSummary(roomId = status.roomId),
            myClawSessionStatus = status,
        )

        assertThat(result.myClawSessionStatus).isEqualTo(status)
    }
}

fun aRoomListRoomSummaryFactory(
    dateFormatter: DateFormatter = FakeDateFormatter { _, _, _ -> "Today" },
    roomLatestEventFormatter: RoomLatestEventFormatter = FakeRoomLatestEventFormatter(),
) = RoomListRoomSummaryFactory(
    dateFormatter = dateFormatter,
    roomLatestEventFormatter = roomLatestEventFormatter,
)
