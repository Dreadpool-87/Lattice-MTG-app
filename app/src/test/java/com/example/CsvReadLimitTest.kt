package com.example

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.MAX_CSV_FILE_BYTES
import com.example.ui.screens.CsvImportDialog
import com.example.ui.screens.readCsvStreamBounded
import com.example.ui.theme.LatticeMtgTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CsvReadLimitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readsUtf8AndStopsOneByteBeyondTheLimit() = runTest {
        val csv = "Quantity,Name\n1,Lim-Dûl the Necromancer\n"
        assertEquals(csv, readCsvStreamBounded(csv.byteInputStream()))
        val exact = ByteArray(MAX_CSV_FILE_BYTES) { 'x'.code.toByte() }
        assertEquals(exact.size, readCsvStreamBounded(exact.inputStream()).length)

        val oversized = ByteArray(MAX_CSV_FILE_BYTES + 10).inputStream()
        val error = runCatching { readCsvStreamBounded(oversized) }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error?.message.orEmpty().contains("2 MiB"))
        assertEquals(9, oversized.available())
    }

    @Test
    fun rejectsMalformedUtf8() = runTest {
        val error = runCatching {
            readCsvStreamBounded(byteArrayOf(0xC3.toByte(), 0x28).inputStream())
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertTrue(error?.message.orEmpty().contains("invalid UTF-8"))
    }

    @Test
    fun cancellationStopsReadingAfterTheCurrentChunk() = runTest {
        var reads = 0
        var cancellationObserved = false
        launch {
            val jobContext = coroutineContext
            val stream = object : InputStream() {
                override fun read(): Int = error("Expected a buffered read")

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    reads++
                    jobContext.cancel()
                    buffer[offset] = 'x'.code.toByte()
                    return 1
                }
            }
            try {
                readCsvStreamBounded(stream)
            } catch (error: CancellationException) {
                cancellationObserved = true
                throw error
            }
        }.join()
        assertEquals(1, reads)
        assertTrue(cancellationObserved)
    }

    @Test
    fun failedSelectionBlocksImportAndSupersededReadCannotReplaceNewInput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = Shadows.shadowOf(context.contentResolver)
        val firstFile = Uri.parse("content://com.example.csv-test/first.csv")
        val badFile = Uri.parse("content://com.example.csv-test/bad.csv")
        val slowFile = Uri.parse("content://com.example.csv-test/slow.csv")
        val nextFile = Uri.parse("content://com.example.csv-test/next.csv")
        val exactFile = Uri.parse("content://com.example.csv-test/exact.csv")
        resolver.registerInputStream(exactFile, ByteArray(MAX_CSV_FILE_BYTES) { 'x'.code.toByte() }.inputStream())
        val firstCsv = "Quantity,Name\n1,Sol Ring"
        val nextCsv = "Quantity,Name\n1,Forest"
        resolver.registerInputStream(firstFile, firstCsv.byteInputStream())
        resolver.registerInputStream(badFile, ByteArrayInputStream(ByteArray(MAX_CSV_FILE_BYTES + 1)))
        val slowReadStarted = CountDownLatch(1)
        val releaseSlowRead = CountDownLatch(1)
        val slowReadClosed = CountDownLatch(1)
        resolver.registerInputStream(slowFile, object : ByteArrayInputStream(firstCsv.toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                slowReadStarted.countDown()
                check(releaseSlowRead.await(10, TimeUnit.SECONDS))
                return super.read(buffer, offset, length)
            }

            override fun close() {
                slowReadClosed.countDown()
                super.close()
            }
        })
        resolver.registerInputStream(nextFile, nextCsv.byteInputStream())

        var selectedUri = firstFile
        var submitted: String? = null
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) {
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, selectedUri as O)
                }
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                LatticeMtgTheme {
                    CsvImportDialog(
                        isImporting = false,
                        report = null,
                        collectionNames = emptySet(),
                        onImportCsv = { csv, _, _, _ -> submitted = csv },
                        onClearReport = {},
                        onDismiss = {}
                    )
                }
            }
        }

        val picker = composeRule.onNodeWithTag("pick_csv_file_button")
        val importButton = composeRule.onNodeWithTag("run_csv_import_button")
        val ready = composeRule.onNodeWithTag("csv_file_ready")
        val editor = composeRule.onNodeWithTag("csv_import_text_input")
        picker.performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { ready.assertExists() }.isSuccess
        }
        editor.assertDoesNotExist()
        importButton.assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(firstCsv, submitted) }

        composeRule.runOnIdle { selectedUri = badFile }
        picker.performClick()
        val error = composeRule.onNodeWithTag("csv_file_read_error")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { error.assertExists() }.isSuccess
        }
        ready.assertExists()
        importButton.assertIsNotEnabled()

        try {
            composeRule.runOnIdle { selectedUri = slowFile }
            picker.performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) { slowReadStarted.count == 0L }
            importButton.assertIsNotEnabled()

            composeRule.runOnIdle { selectedUri = nextFile }
            picker.performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching { importButton.assertIsEnabled() }.isSuccess
            }
        } finally {
            releaseSlowRead.countDown()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { slowReadClosed.count == 0L }
        error.assertDoesNotExist()
        importButton.assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(nextCsv, submitted) }

        composeRule.runOnIdle { selectedUri = exactFile }
        picker.performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { importButton.assertIsEnabled() }.isSuccess
        }
        ready.assertExists()
        editor.assertDoesNotExist()
        importButton.performClick()
        composeRule.runOnIdle { assertEquals(MAX_CSV_FILE_BYTES, submitted?.length) }
    }
}
