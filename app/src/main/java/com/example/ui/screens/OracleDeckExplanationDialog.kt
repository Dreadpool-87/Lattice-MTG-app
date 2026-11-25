package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ai.OracleDeckExplanation
import com.example.ai.OracleDeckPreviewClient
import com.example.ai.OraclePreviewModel
import com.example.ai.OracleQuestionTarget
import com.example.data.local.DeckWithEntries
import com.example.domain.OracleDeckCheck
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun OracleDeckExplanationDialog(
    online: Boolean,
    onDismiss: () -> Unit,
    deck: DeckWithEntries? = null,
    questionTarget: OracleQuestionTarget? = null
) {
    val check = remember(deck) { deck?.let { OracleDeckCheck.analyze(it) } }
    val scope = rememberCoroutineScope()
    var model by remember { mutableStateOf(if (questionTarget == null) OraclePreviewModel.STANDARD else OraclePreviewModel.ECONOMY) }
    var question by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<OracleDeckExplanation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (questionTarget == null) "Explain deck check" else "Ask Oracle") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (questionTarget != null) {
                    Text(questionTarget.title)
                    val disclosure = when (questionTarget) {
                        is OracleQuestionTarget.Deck -> "Sends your question, mainboard and commander card names, mana costs and local deck statistics to Cloudflare."
                        is OracleQuestionTarget.Card -> "Sends your question, this card’s text and available rulings to Cloudflare."
                    }
                    Text(disclosure)
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it.take(400) },
                        enabled = !busy,
                        label = { Text("MTG question") },
                        minLines = 2,
                        maxLines = 4
                    )
                } else if (check != null) {
                    Text("${check.cards} playable cards · ${check.lands} lands")
                    if (check.copyWarning) Text("The local check flagged a copy limit.")
                    when (check.identity) {
                        "flagged" -> Text("Outside commander color identity: ${check.offColorNames.joinToString()}")
                        "incomplete" -> Text("Commander color identity could not be fully checked.")
                        "no_mismatch" -> Text("No cached color-identity mismatch found in the mainboard.")
                    }
                    Text("Sends counts and check results to Cloudflare. Card names and chat history stay on this device.")
                }
                if (!online) Text("An internet connection and online mode are required for AI explanations.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OraclePreviewModel.entries.forEach { option ->
                        FilterChip(
                            selected = model == option,
                            enabled = !busy,
                            onClick = { model = option; result = null; error = null },
                            label = { Text(option.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.take(80) },
                    enabled = !busy,
                    label = { Text("Preview code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (busy) Text("Asking Oracle…")
                result?.let { response ->
                    Text(response.text)
                    val usage = response.neurons?.let { String.format(Locale.ROOT, "%.1f Neurons", it) }
                        ?: "Neuron usage unavailable"
                    Text("$usage · ${response.remaining} preview requests left today")
                }
                error?.let { Text(it) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") } },
        confirmButton = {
            TextButton(
                enabled = online && !busy && token.trim().matches(Regex("[A-Za-z0-9_-]{16,80}")) &&
                    (if (questionTarget == null) check != null else question.isNotBlank()),
                onClick = {
                    busy = true
                    error = null
                    result = null
                    scope.launch {
                        try {
                            result = when {
                                questionTarget != null -> OracleDeckPreviewClient.ask(questionTarget, question, token, model)
                                check != null -> OracleDeckPreviewClient.explain(check, token, model)
                                else -> null
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: IOException) {
                            error = e.message ?: "Oracle preview is unavailable."
                        } catch (_: Exception) {
                            error = "Oracle preview is unavailable."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (questionTarget == null) "Explain" else "Ask") }
        }
    )
}
