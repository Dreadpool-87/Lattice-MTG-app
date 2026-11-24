package com.example.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import kotlin.coroutines.coroutineContext

internal const val MAX_CSV_FILE_BYTES = 2 * 1024 * 1024

internal suspend fun readCsvStreamBounded(input: InputStream): String {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream(DEFAULT_BUFFER_SIZE)
    while (true) {
        coroutineContext.ensureActive()
        val count = input.read(buffer, 0, minOf(buffer.size, MAX_CSV_FILE_BYTES - output.size() + 1))
        coroutineContext.ensureActive()
        if (count < 0) break
        if (count > MAX_CSV_FILE_BYTES - output.size()) {
            throw IOException("CSV file exceeds the 2 MiB size limit and was not loaded.")
        }
        output.write(buffer, 0, count)
    }
    return try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString()
    } catch (error: CharacterCodingException) {
        throw IOException("The selected CSV contains invalid UTF-8 text.", error)
    }
}

private class CsvFileSelection(val uri: Uri)

private suspend fun readCsvFile(resolver: ContentResolver, uri: Uri) = withContext(Dispatchers.IO) {
    val stream = resolver.openInputStream(uri)
        ?: throw IOException("Could not open the selected CSV file.")
    val text = stream.use { readCsvStreamBounded(it) }
    val name = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
    text to (name ?: "selected.csv")
}

@Composable
fun VaultCsvImportDialog(
    isImporting: Boolean,
    report: DelverImportReport?,
    collectionNames: Set<String>,
    onImportCsv: (
        csvText: String, deckName: String?, deckFormat: String, collectionName: String?, replace: Boolean
    ) -> Unit,
    onClearReport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var csvText by remember { mutableStateOf("") }
    var fileLabel by remember { mutableStateOf<String?>(null) }
    var saveAsDeck by remember { mutableStateOf(false) }
    var importName by remember { mutableStateOf("") }
    val collectionName = importName.trim().ifBlank { "My cards" }
    val existingCollection = collectionNames.any { it.equals(collectionName, ignoreCase = true) }
    var replaceCollection by remember(collectionName, saveAsDeck, csvText) { mutableStateOf(false) }
    var deckFormat by remember { mutableStateOf("Commander") }
    var fileReadError by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<CsvFileSelection?>(null) }
    val requestedFile = selection
    val isReadingFile = requestedFile != null

    val openCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !isImporting) {
            selection = CsvFileSelection(uri)
            fileReadError = null
            onClearReport()
        }
    }

    LaunchedEffect(requestedFile) {
        val request = requestedFile ?: return@LaunchedEffect
        try {
            val (text, name) = readCsvFile(context.contentResolver, request.uri)
            if (selection === request) {
                csvText = text
                fileLabel = name
                importName = name.substringBeforeLast('.').takeUnless { it == "selected" }.orEmpty()
                replaceCollection = false
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (selection === request) {
                fileReadError = error.localizedMessage ?: "Could not read the selected CSV file."
            }
        } finally {
            if (selection === request) selection = null
        }
    }

    AlertDialog(
        onDismissRequest = {
            selection = null
            onClearReport()
            onDismiss()
        },
        title = {
            Text(
                text = "Import CSV",
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
                    enabled = !isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pick_csv_file_button")
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(fileLabel?.let { "File: $it" } ?: "Choose Delver Lens .CSV File")
                }

                if (isReadingFile) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Reading CSV file...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                fileReadError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("csv_file_read_error")
                    )
                }

                OutlinedTextField(
                    value = csvText,
                    onValueChange = {
                        if (csvText != it) {
                            csvText = it
                            fileLabel = null
                            fileReadError = null
                            onClearReport()
                        }
                    },
                    enabled = !isImporting && !isReadingFile,
                    label = { Text("CSV Content") },
                    placeholder = { Text("Quantity,Name,Edition code,Collector's number,Language,Scryfall ID") },
                    minLines = 4,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("csv_import_text_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !saveAsDeck, onClick = { saveAsDeck = false; onClearReport() },
                        enabled = !isImporting, label = { Text("My collection") })
                    FilterChip(selected = saveAsDeck, onClick = { saveAsDeck = true; onClearReport() },
                        enabled = !isImporting, label = { Text("Deck") })
                }

                OutlinedTextField(
                    value = importName,
                    onValueChange = { importName = it; onClearReport() },
                    label = { Text(if (saveAsDeck) "Deck name" else "Collection name") },
                    placeholder = { Text(if (saveAsDeck) "Imported deck" else "My cards") },
                    enabled = !isImporting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!saveAsDeck && existingCollection) {
                    FilterChip(
                        selected = replaceCollection,
                        onClick = { replaceCollection = !replaceCollection },
                        enabled = !isImporting,
                        label = { Text("Replace existing collection") }
                    )
                }

                if (saveAsDeck) {
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
                        if (saveAsDeck) importName.trim().ifBlank { "Imported deck" } else null,
                        deckFormat,
                        collectionName.takeUnless { saveAsDeck },
                        replaceCollection
                    )
                },
                enabled = csvText.isNotBlank() && !isImporting && !isReadingFile && fileReadError == null &&
                    (saveAsDeck || !existingCollection || replaceCollection),
                modifier = Modifier.testTag("run_csv_import_button")
            ) {
                Text(if (saveAsDeck) "Import deck" else "Import collection")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    selection = null
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
        report.collectionName?.let { Text("Collection saved: $it", style = MaterialTheme.typography.titleSmall) }
        report.collectionNotice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
                    text = "Verified: ${report.totalVerifiedCopies} copies in CSV. ${report.savedCardsCount} unique printings saved to Vault.",
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
