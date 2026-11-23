package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.repository.AppRelease
import com.example.data.repository.AppUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AppUpdateSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by rememberSaveable(
        stateSaver = listSaver<AppRelease?, Any>(
            save = { value -> value?.let { listOf(it.tag, it.versionCode, it.sha256) }.orEmpty() },
            restore = { values ->
                if (values.isEmpty()) null else AppRelease(values[0] as String, values[1] as Long, values[2] as String)
            }
        )
    ) { mutableStateOf<AppRelease?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    SettingsSectionCard(title = "App updates", icon = Icons.Filled.Refresh) {
        Text("Installed version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    try {
                        val update = release
                        if (update == null) {
                            status = "Checking for updates…"
                            release = AppUpdates.latest(context)
                            status = release?.let { "Version ${it.tag} is available." }
                                ?: "No newer published update is available."
                        } else if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                            status = "Allow installs from Lattice MTG, then return and tap Update again."
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                            )
                        } else {
                            status = "Downloading and checking the update…"
                            val apk = AppUpdates.download(context, update)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(uri, "application/vnd.android.package-archive")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                            status = "Confirm the update in the Android installer."
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        release = null
                        status = "Could not prepare the update. Check your connection and try again."
                    } finally {
                        busy = false
                    }
                }
            }
        ) {
            Text(if (release == null) "Check for updates" else "Update")
        }
    }
}
