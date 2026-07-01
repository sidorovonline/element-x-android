/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageTextSelectionDialog(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = ElementTheme.colors.textPrimary.toArgb()
    val backgroundColor = Color.Transparent.toArgb()

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("message_text_selection_dialog"),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ElementTheme.colors.bgCanvasDefault,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(CommonStrings.action_select_text),
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = ElementTheme.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 420.dp)
                        .testTag("message_text_selection_text"),
                    factory = { context ->
                        EditText(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            setTextIsSelectable(true)
                            keyListener = null
                            inputType = InputType.TYPE_NULL
                            isFocusable = true
                            isFocusableInTouchMode = true
                            isVerticalScrollBarEnabled = true
                            isHorizontalScrollBarEnabled = false
                            setHorizontallyScrolling(false)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                            setTextColor(textColor)
                            setBackgroundColor(backgroundColor)
                            setPadding(0, 0, 0, 0)
                            setText(text)
                            showSoftInputOnFocus = false
                        }
                    },
                    update = { view ->
                        if (view.text.toString() != text) {
                            view.setText(text)
                        }
                        view.setTextColor(textColor)
                        view.setBackgroundColor(backgroundColor)
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = stringResource(CommonStrings.action_close),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}
