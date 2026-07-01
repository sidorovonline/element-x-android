/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import android.text.InputType
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TopAppBar
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("message_text_selection_dialog"),
            topBar = {
                TopAppBar(
                    titleStr = stringResource(CommonStrings.action_select_text),
                    navigationIcon = {
                        BackButton(
                            imageVector = CompoundIcons.Close(),
                            contentDescription = stringResource(CommonStrings.action_close),
                            onClick = onDismiss,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ElementTheme.colors.bgCanvasDefault,
                    ),
                )
            },
        ) { padding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .testTag("message_text_selection_text"),
                factory = { context ->
                    val density = context.resources.displayMetrics.density
                    val horizontalPadding = (20 * density).toInt()
                    val topPadding = (12 * density).toInt()
                    EditText(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setTextIsSelectable(true)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        setSingleLine(false)
                        minLines = 1
                        maxLines = Int.MAX_VALUE
                        keyListener = null
                        gravity = Gravity.START or Gravity.TOP
                        isFocusable = true
                        isFocusableInTouchMode = true
                        isCursorVisible = false
                        isVerticalScrollBarEnabled = true
                        isHorizontalScrollBarEnabled = false
                        setHorizontallyScrolling(false)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                        setTextColor(textColor)
                        setBackgroundColor(backgroundColor)
                        setPadding(horizontalPadding, topPadding, horizontalPadding, horizontalPadding)
                        setLineSpacing(0f, 1.15f)
                        setText(text)
                        showSoftInputOnFocus = false
                        customSelectionActionModeCallback = ReadOnlySelectionActionModeCallback
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
        }
    }
}

private object ReadOnlySelectionActionModeCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        removeEditActions(menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        removeEditActions(menu)
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode) = Unit

    private fun removeEditActions(menu: Menu) {
        menu.removeItem(android.R.id.cut)
        menu.removeItem(android.R.id.paste)
        menu.removeItem(android.R.id.pasteAsPlainText)
    }
}
