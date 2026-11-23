package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.DelverImportReport

@Composable
fun VaultCsvImportDialog(
    isImporting: Boolean,
    report: DelverImportReport?,
    onImportCsv: (csvText: String, optionalDeckName: String?, deckFormat: String) -> Unit,
    onClearReport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var csvText by remember { mutableStateOf("") }
    var fileLabel by remember { mutableStateOf<String?>(null) }
    var optionalDeckName by remember { mutableStateOf("") }
    var deckFormat by remember { mutableStateOf("Commander") }
    var fileReadError by remember { mutableStateOf<String?>(null) }

    val openCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.onSuccess { text ->
                csvText = text
                fileLabel = uri.lastPathSegment ?: "selected.csv"
                fileReadError = null
            }.onFailure { err ->
                fileReadError = "Could not read CSV file: ${err.localizedMessage ?: "error"}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            onClearReport()
            onDismiss()
        },
        title = {
            Text(
                text = "Import Delver Lens CSV",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Matches exact Scryfall printing IDs, set codes (including (SET)), collector numbers, and languages without fuzzy substitution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        openCsvLauncher.launch(
                            arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pick_csv_file_button")
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(fileLabel?.let { "File: $it" } ?: "Choose Delver Lens .CSV File")
                }

                fileReadError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    label = { Text("CSV Content") },
                    placeholder = { Text("Quantity,Name,Edition code,Collector's number,Language,Scryfall ID") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("csv_import_text_input")
                )

                OutlinedTextField(
                    value = optionalDeckName,
                    onValueChange = { optionalDeckName = it },
                    label = { Text("Also create deck (optional)") },
                    placeholder = { Text("Leave blank to import to Vault only") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (optionalDeckName.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Commander", "Modern", "Standard", "Casual").forEach { fmt ->
                            FilterChip(
                                selected = deckFormat == fmt,
                                onClick = { deckFormat = fmt },
                                label = { Text(fmt) }
                            )
                        }
                    }
                }

                if (isImporting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Verifying printings & Oracle IDs...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (report != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CsvReviewSummarySection(report = report)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onImportCsv(
                        csvText,
                        optionalDeckName.trim().takeIf { it.isNotEmpty() },
                        deckFormat
                    )
                },
                enabled = csvText.isNotBlank() && !isImporting,
                modifier = Modifier.testTag("run_csv_import_button")
            ) {
                Text(if (report == null) "Verify & Import" else "Re-Verify")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClearReport()
                    onDismiss()
                }
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun CsvReviewSummarySection(report: DelverImportReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("csv_verified_summary")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(10.dp)
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Verified: ${report.totalVerifiedCopies} copies (${report.savedCardsCount} unique printings) saved to Vault",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (report.conflictRows.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("csv_conflicts_section")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Conflicting / Mismatched Rows (${report.conflictRows.size}) — Skipped",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    report.conflictRows.forEach { res ->
                        Text(
                            text = "Row ${res.row.rowNumber} (${res.row.name ?: res.row.scryfallId ?: "Row"}): ${res.statusDetail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (report.unresolvedRows.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("csv_unresolved_section")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unresolved Rows (${report.unresolvedRows.size}) — Needs Review",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    report.unresolvedRows.forEach { res ->
                        Text(
                            text = "Row ${res.row.rowNumber} (${res.row.name ?: res.row.scryfallId ?: "Unknown"}): ${res.statusDetail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
