package com.marcogn.coverdex.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.marcogn.coverdex.R

/** The Showdown export dialog — reachable from a team's overflow menu, per
 * `docs/plan/phase-5-import-export-and-settings.md` §2. Offers copy-to-clipboard, native to
 * `legacy-web`'s own clipboard-only export, and save-to-file via SAF `CreateDocument`, the
 * native-only affordance the phase calls for. */
@Composable
fun ExportShowdownDialog(
    teamName: String,
    exportedText: String,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exportedText.toByteArray(Charsets.UTF_8)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_showdown_title)) },
        text = {
            SelectionContainer {
                Text(
                    exportedText,
                    modifier = Modifier.fillMaxWidth().height(240.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { clipboardManager.setText(AnnotatedString(exportedText)) }) {
                    Text(stringResource(R.string.export_showdown_copy))
                }
                TextButton(onClick = { createDocumentLauncher.launch(suggestedShowdownFileName(teamName)) }) {
                    Text(stringResource(R.string.export_showdown_save_file))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun suggestedShowdownFileName(teamName: String): String {
    val safe = teamName.ifBlank { "team" }.replace(Regex("[^A-Za-z0-9-_]"), "_")
    return "$safe.txt"
}
