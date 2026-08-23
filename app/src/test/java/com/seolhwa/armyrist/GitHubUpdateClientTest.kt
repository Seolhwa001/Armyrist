package com.seolhwa.armyrist.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubUpdateClientTest {
    private val client = GitHubUpdateClient()
    private val apkAsset = GitHubUpdateClient.Asset(
        name = "Armyrist-0.6.27.apk",
        downloadUrl = "https://example.invalid/Armyrist-0.6.27.apk"
    )

    @Test
    fun metadataParsesStableContract() {
        val metadata = client.parseMetadata(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "versionName": "0.6.27",
                  "versionCode": 115,
                  "releaseType": "stable",
                  "apkAsset": "Armyrist-0.6.27.apk",
                  "apkSha256": "${"a".repeat(64)}",
                  "releaseNotes": ["첫 번째", "두 번째"]
                }
                """.trimIndent()
            ),
            listOf(apkAsset)
        )

        assertEquals("0.6.27", metadata.versionName)
        assertEquals(115L, metadata.versionCode)
        assertEquals(2, metadata.releaseNotes.size)
        assertEquals(apkAsset.downloadUrl, metadata.apkDownloadUrl)
    }

    @Test
    fun metadataRejectsMissingApkAsset() {
        assertThrows(IllegalStateException::class.java) {
            client.parseMetadata(
                JSONObject(
                    """
                    {
                      "schemaVersion": 1,
                      "versionName": "0.6.27",
                      "versionCode": 115,
                      "releaseType": "stable",
                      "apkAsset": "Armyrist-0.6.27.apk",
                      "apkSha256": "${"b".repeat(64)}",
                      "releaseNotes": []
                    }
                    """.trimIndent()
                ),
                emptyList()
            )
        }
    }

    @Test
    fun metadataRejectsUnsupportedSchema() {
        assertThrows(IllegalArgumentException::class.java) {
            client.parseMetadata(
                JSONObject(
                    """
                    {
                      "schemaVersion": 2,
                      "versionName": "0.6.27",
                      "versionCode": 115,
                      "releaseType": "stable",
                      "apkAsset": "Armyrist-0.6.27.apk",
                      "apkSha256": "${"c".repeat(64)}"
                    }
                    """.trimIndent()
                ),
                listOf(apkAsset)
            )
        }
    }
}
