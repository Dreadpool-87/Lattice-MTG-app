package com.example

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

internal object PreviewLog {
    private const val MAX_ENTRIES = 200
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries = _entries.asStateFlow()

    fun initialize(context: Context) {
        if (!BuildConfig.DEBUG) return
        val file = AtomicFile(File(context.filesDir, "preview-diagnostics.log"))
        scope.launch {
            synchronized(this@PreviewLog) {
                val previous = runCatching { file.readFully().decodeToString().lines() }.getOrDefault(emptyList())
                _entries.value = (previous.filter(String::isNotBlank) + _entries.value).takeLast(MAX_ENTRIES)
            }
            entries.collect { lines ->
                runCatching {
                    val output = file.startWrite()
                    try {
                        output.write(lines.joinToString("\n").encodeToByteArray())
                        file.finishWrite(output)
                    } catch (error: Exception) {
                        file.failWrite(output)
                        throw error
                    }
                }
            }
        }
    }

    @Synchronized
    fun record(category: String, event: String) {
        if (!BuildConfig.DEBUG) return
        val time = DateFormat.getDateTimeInstance().format(Date())
        _entries.value = (_entries.value + "$time | $category | $event").takeLast(MAX_ENTRIES)
    }
}
