package de.lennart.minilauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    fun action(label: String, danger: Boolean = false, block: () -> Unit) = DialogAction(label, danger) {
        onDismiss()
        block()
    }
    ActionsDialog(
        title = app.label,
        actions = listOf(
            action(if (isFavorite) "Remove from favorites" else "Add to favorites", block = onToggleFavorite),
            action("Rename", block = onRename),
            action(if (isHidden) "Unhide" else "Hide") { onToggleHidden(!isHidden) },
            action("App info", block = onAppInfo),
            action("Uninstall", danger = true, block = onUninstall),
        ),
        onDismiss = onDismiss,
    )
}

data class DialogAction(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** Rounded sheet with a dim title and a centered list of text actions. */
@Composable
fun ActionsDialog(title: String, actions: List<DialogAction>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SurfaceColor,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    color = DimText,
                    fontSize = TypeScale.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                actions.forEach { action ->
                    Text(
                        action.label,
                        color = if (action.danger) DangerColor else Color.White,
                        fontSize = TypeScale.Body,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .plainClickable(onClick = action.onClick)
                            .padding(vertical = 13.dp),
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
        containerColor = SurfaceColor,
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
                    fontSize = TypeScale.Small,
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
