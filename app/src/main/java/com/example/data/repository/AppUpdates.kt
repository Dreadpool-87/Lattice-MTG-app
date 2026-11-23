package com.example.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject

internal data class AppRelease(val tag: String, val versionCode: Long, val sha256: String)

internal object AppUpdates {
    private const val RELEASES = "https://github.com/Dreadpool-87/Lattice-MTG-app/releases"
    private const val MAX_APK_BYTES = 128L * 1024 * 1024
    private val client = OkHttpClient.Builder()
        .followSslRedirects(false)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    suspend fun latest(context: Context): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$RELEASES/latest/download/android-update.json").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            val body = response.body ?: throw IOException("Empty release response.")
            if (!response.isSuccessful) throw IOException("Update server returned HTTP ${response.code}.")
            val source = body.source()
            if (source.request(4097)) throw IOException("Invalid release information.")
            val json = JSONObject(source.readUtf8())
            val release = AppRelease(json.getString("tag"), json.getLong("versionCode"), json.getString("sha256"))
            require(release.tag.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")))
            require(release.versionCode > 0 && release.sha256.matches(Regex("[a-f0-9]{64}")))
            val installed = context.packageManager.getPackageInfo(context.packageName, 0)
            release.takeIf { it.versionCode > versionCode(installed) }
        }
    }

    suspend fun download(context: Context, release: AppRelease): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "app-updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not open the update cache.")
        val apk = File(directory, "lattice-android.apk")
        val temporary = File.createTempFile("download-", ".apk", context.cacheDir)
        try {
            val request = Request.Builder().url("$RELEASES/download/${release.tag}/lattice-android.apk").build()
            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) throw IOException("Update download is unavailable.")
                if (body.contentLength() > MAX_APK_BYTES) throw IOException("The update file is too large.")
                copyApk(body, temporary, digest)
            }
            val checksum = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (checksum != release.sha256) throw IOException("The update checksum does not match.")
            val archive = context.packageManager.getPackageArchiveInfo(temporary.path, 0)
            if (archive == null || archive.packageName != context.packageName || versionCode(archive) != release.versionCode) {
                throw IOException("The update does not match this app or release version.")
            }
            ensureActive()
            if (!temporary.renameTo(apk)) throw IOException("Could not save the update.")
            apk
        } finally {
            temporary.delete()
        }
    }

    private suspend fun copyApk(body: ResponseBody, destination: File, digest: MessageDigest) =
        withContext(Dispatchers.IO) {
            body.byteStream().use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > MAX_APK_BYTES) throw IOException("The update file is too large.")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
        }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
}
