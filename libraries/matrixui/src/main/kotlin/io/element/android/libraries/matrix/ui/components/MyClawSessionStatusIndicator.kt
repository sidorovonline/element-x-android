/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.myclaw.MyClawSessionStatus
import io.element.android.libraries.matrix.api.myclaw.MyClawSessionStatusState

@Composable
fun MyClawSessionStatusBadge(
    status: MyClawSessionStatus?,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    val color = status.waitingColorOrNull() ?: return
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ElementTheme.colors.bgCanvasDefault)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics {},
    )
}

@Composable
fun MyClawSessionStatusPill(
    status: MyClawSessionStatus?,
    modifier: Modifier = Modifier,
) {
    val color = status.waitingColorOrNull() ?: return
    val label = status?.label ?: return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            modifier = Modifier.padding(start = 4.dp),
            text = label,
            style = ElementTheme.typography.fontBodyXsMedium,
            color = ElementTheme.colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun MyClawSessionStatus?.waitingColorOrNull(): Color? {
    return when (this?.state) {
        MyClawSessionStatusState.WAITING_LLM -> if (ElementTheme.isLightTheme) Color(0xFF168DE2) else Color(0xFF5ABEFF)
        MyClawSessionStatusState.WAITING_AGENT -> if (ElementTheme.isLightTheme) Color(0xFF003B73) else Color(0xFF168DE2)
        MyClawSessionStatusState.IDLE,
        MyClawSessionStatusState.RUNNING,
        null -> null
    }
}

@PreviewsDayNight
@Composable
internal fun MyClawSessionStatusIndicatorPreview() = ElementPreview {
    Row {
        MyClawSessionStatusBadge(status = aMyClawSessionStatus(MyClawSessionStatusState.WAITING_LLM))
        MyClawSessionStatusPill(
            modifier = Modifier.padding(start = 8.dp),
            status = aMyClawSessionStatus(MyClawSessionStatusState.WAITING_AGENT),
        )
    }
}

private fun aMyClawSessionStatus(state: MyClawSessionStatusState) = MyClawSessionStatus(
    roomId = RoomId("!room:example.org"),
    sessionId = "sess_123",
    state = state,
    label = when (state) {
        MyClawSessionStatusState.WAITING_LLM -> "Waiting for model"
        MyClawSessionStatusState.WAITING_AGENT -> "Waiting for agent"
        MyClawSessionStatusState.IDLE -> "Idle"
        MyClawSessionStatusState.RUNNING -> "Running"
    },
    updatedAtMillis = null,
    expiresAtMillis = Long.MAX_VALUE,
)
