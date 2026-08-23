package com.seolhwa.armyrist.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class ArmyristUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = UpdatePreferences(appContext)
    private val client = GitHubUpdateClient()

    fun installedVersion(): InstalledVersion = ApkUpdateValidator.installedVersion(appContext)

    fun preferences(): UpdatePreferences = preferences

    fun knownAvailable(): UpdateReleaseMetadata? =
        preferences.knownAvailable(installedVersion().versionCode)

    suspend fun check(manual: Boolean): UpdateCheckResult = withContext(Dispatchers.IO) {
        val installed = installedVersion()
        if (!hasNetwork()) {
            return@withContext UpdateCheckResult.Failure(
                message = "업데이트 정보를 확인할 수 없습니다. 인터넷 연결을 확인해주세요.",
                networkRelated = true
            )
        }

        val release = client.fetchLatestStableMetadata().getOrElse { error ->
            return@withContext UpdateCheckResult.Failure(
                message = if (manual) {
                    "업데이트 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요."
                } else {
                    "Update check failed."
                },
                networkRelated = error is java.io.IOException
            )
        }

        preferences.markSuccessfulCheck()
        if (release.versionCode > installed.versionCode) {
            preferences.saveKnownAvailable(release)
            UpdateSessionState.latestAvailable = release
            UpdateCheckResult.Available(installed, release)
        } else {
            preferences.clearKnownAvailable()
            UpdateSessionState.latestAvailable = null
            UpdateCheckResult.Latest(installed, release)
        }
    }

    suspend fun downloadAndValidate(
        release: UpdateReleaseMetadata,
        onProgress: suspend (Int?) -> Unit,
        onValidating: suspend () -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updateDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
            updateDir.listFiles()?.forEach { stale ->
                if (stale.name.endsWith(".part")) stale.delete()
            }

            val finalFile = File(updateDir, release.apkAsset)
            val partFile = File(updateDir, "${release.apkAsset}.part")
            finalFile.delete()
            partFile.delete()

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(release.apkDownloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/octet-stream")
                    setRequestProperty("User-Agent", "Armyrist-Android-Updater")
                }
                val status = connection.responseCode
                if (status !in 200..299) error("APK download returned HTTP $status.")

                val total = connection.contentLengthLong.takeIf { it > 0L }
                var written = 0L
                var lastProgress = -1
                BufferedInputStream(connection.inputStream).use { input ->
                    BufferedOutputStream(partFile.outputStream()).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            written += count
                            val progress = total?.let {
                                ((written * 100L) / it).toInt().coerceIn(0, 100)
                            }
                            if (progress != lastProgress) {
                                lastProgress = progress ?: lastProgress
                                onProgress(progress)
                            }
                        }
                        output.flush()
                    }
                }
                coroutineContext.ensureActive()
                if (total != null && written != total) {
                    error("APK download was incomplete.")
                }
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                onProgress(100)
                onValidating()
                when (val validation = ApkUpdateValidator.validate(appContext, finalFile, release)) {
                    UpdateValidationResult.Valid -> finalFile
                    is UpdateValidationResult.Invalid -> {
                        finalFile.delete()
                        error(validation.message)
                    }
                }
            } finally {
                connection?.disconnect()
                if (partFile.exists()) partFile.delete()
            }
        }
    }

    private fun hasNetwork(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

object UpdateSessionState {
    @Volatile var automaticCheckAttempted: Boolean = false
    @Volatile var latestAvailable: UpdateReleaseMetadata? = null
    private val promptedCodes = mutableSetOf<Long>()

    @Synchronized
    fun shouldPrompt(versionCode: Long): Boolean = promptedCodes.add(versionCode)
}
