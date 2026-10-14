package de.lennart.minilauncher.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.lennart.minilauncher.data.AppEntry

@Composable
fun AppActionsDialog(
    app: AppEntry,
    isFavorite: Boolean,
    isHidden: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: (Boolean) -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.Black,
            modifier = Modifier.border(1.dp, Color.DarkGray),
        ) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                Text(
                    app.label,
                    color = DimText,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                fun action(label: String, block: () -> Unit) = label to {
                    onDismiss()
                    block()
                }
                listOf(
                    action(if (isFavorite) "Remove from favorites" else "Add to favorites", onToggleFavorite),
                    action("Rename", onRename),
                    action(if (isHidden) "Unhide" else "Hide") { onToggleHidden(!isHidden) },
                    action("App info", onAppInfo),
                    action("Uninstall", onUninstall),
                ).forEach { (label, onClick) ->
                    Text(
                        label,
                        color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .plainClickable(onClick = onClick)
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDialog(app: AppEntry, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(app.label, TextRange(0, app.label.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111111),
        title = { Text("Rename") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRename(value.text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                Text(
                    "Original: ${app.originalLabel}",
                    color = DimText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(value.text) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { onRename("") }) { Text("Reset") }
        },
    )
}
