package com.seolhwa.armyrist.update

import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class LegacyStableWithoutMetadataException(
    val releaseVersionName: String?
) : IllegalStateException("Stable release is missing release.json.")

class GitHubUpdateClient(
    private val owner: String = "Seolhwa001",
    private val repository: String = "Armyrist"
) {
    data class Asset(
        val name: String,
        val downloadUrl: String
    )

    fun fetchLatestStableMetadata(): Result<UpdateReleaseMetadata> = runCatching {
        val releaseJson = getText(
            "https://api.github.com/repos/$owner/$repository/releases/latest"
        )
        val release = JSONObject(releaseJson)

        require(!release.optBoolean("draft", false)) { "Draft release is not eligible." }
        require(!release.optBoolean("prerelease", false)) { "Pre-release is not eligible." }

        val assetsJson = release.optJSONArray("assets")
            ?: error("Stable release has no assets.")
        val assets = buildList {
            for (index in 0 until assetsJson.length()) {
                val item = assetsJson.getJSONObject(index)
                val name = item.optString("name").trim()
                val url = item.optString("browser_download_url").trim()
                if (name.isNotBlank() && url.startsWith("https://")) {
                    add(Asset(name, url))
                }
            }
        }

        val metadataAsset = assets.firstOrNull { it.name == METADATA_ASSET }
            ?: throw LegacyStableWithoutMetadataException(
                releaseVersionName = stableVersionNameFromRelease(release)
            )
        val metadataJson = JSONObject(getText(metadataAsset.downloadUrl))
        val metadata = parseMetadata(metadataJson, assets)

        require(metadata.releaseType == "stable") {
            "Release metadata is not stable."
        }
        metadata
    }

    internal fun stableVersionNameFromRelease(release: JSONObject): String? {
        val tag = release.optString("tag_name").trim()
        val title = release.optString("name").trim()

        fun extract(raw: String): String? =
            Regex("(?:^|\\\\s|v)(\\\\d+(?:\\\\.\\\\d+){1,3})(?:$|\\\\s|[-_])")
                .find(raw)
                ?.groupValues
                ?.getOrNull(1)

        return extract(tag) ?: extract(title)
    }

    internal fun parseMetadata(
        json: JSONObject,
        releaseAssets: List<Asset>
    ): UpdateReleaseMetadata {
        val schema = json.optInt("schemaVersion", -1)
        require(schema == 1) { "Unsupported update metadata schema." }

        val versionName = json.optString("versionName").trim()
        val versionCode = json.optLong("versionCode", -1L)
        val releaseType = json.optString("releaseType").trim().lowercase()
        val apkAsset = json.optString("apkAsset").trim()
        val sha256 = json.optString("apkSha256").trim().lowercase()

        require(versionName.isNotBlank()) { "Missing versionName." }
        require(versionCode > 0L) { "Invalid versionCode." }
        require(releaseType == "stable") { "Invalid releaseType." }
        require(apkAsset.endsWith(".apk", ignoreCase = true)) { "Invalid APK asset name." }
        require(sha256.matches(Regex("^[0-9a-f]{64}$"))) { "Invalid APK SHA-256." }

        val matchingApk = releaseAssets.firstOrNull { it.name == apkAsset }
            ?: error("Metadata APK asset does not exist in the release.")

        val notesJson = json.optJSONArray("releaseNotes")
        val notes = buildList {
            if (notesJson != null) {
                for (index in 0 until notesJson.length()) {
                    notesJson.optString(index).trim()
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
        }

        return UpdateReleaseMetadata(
            schemaVersion = schema,
            versionName = versionName,
            versionCode = versionCode,
            releaseType = releaseType,
            apkAsset = apkAsset,
            apkSha256 = sha256,
            releaseNotes = notes,
            apkDownloadUrl = matchingApk.downloadUrl
        )
    }

    private fun getText(url: String): String {
        val connection = open(url)
        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                error("GitHub returned HTTP $status.")
            }
            BufferedInputStream(connection.inputStream).use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Armyrist-Android-Updater")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    companion object {
        const val METADATA_ASSET = "release.json"
    }
}
