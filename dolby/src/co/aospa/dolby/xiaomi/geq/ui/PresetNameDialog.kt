/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R

@Composable
fun PresetNameDialog(
    title: String,
    presetName: String = "",
    onPresetNameSet: (String) -> PresetNameValidationError?,
    onDismissDialog: () -> Unit
) {
    var text by rememberSaveable(title, presetName) { mutableStateOf(presetName) }
    var error by remember(title, presetName) { mutableStateOf<PresetNameValidationError?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun submitName() {
        val validation = onPresetNameSet(text)
        error = validation
        if (validation == null) onDismissDialog()
    }

    AlertDialog(
        onDismissRequest = onDismissDialog,
        confirmButton = {
            TextButton(
                onClick = ::submitName,
            ) {
                Text(
                    stringResource(id = android.R.string.ok)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissDialog
            ) {
                Text(
                    stringResource(id = android.R.string.cancel)
                )
            }
        },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitName() }),
                    label = {
                        Text(
                            stringResource(id = R.string.dolby_geq_preset_name)
                        )
                    },
                    isError = error != null,
                    singleLine = true
                )
                LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
                error?.let {
                    Text(
                        text = it.toErrorMessage(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}
