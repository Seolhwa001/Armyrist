package com.seolhwa.armyrist

import android.net.Uri
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Technical-validation-only self-contained transfer link.
 *
 * No Armyrist user payload is uploaded to a server. The existing Portable bytes are
 * GZIP-compressed and Base64URL encoded directly into the URI fragment.
 *
 * Protocol is intentionally PoC-only and must not be treated as a long-term public
 * transfer protocol until Minor Patch B design is approved.
 */
internal object ArmyristTransferLinkPoC {
    const val SCHEME = "armyrist"
    const val HOST = "import"
    private const val VERSION_PREFIX = "v1."
    private const val MAX_ENCODED_CHARS = 8 * 1024 * 1024
    private const val MAX_DECOMPRESSED_BYTES = 32 * 1024 * 1024

    data class Generated(
        val uri: String,
        val originalBytes: Int,
        val compressedBytes: Int,
        val encodedChars: Int,
        val finalUrlChars: Int
    )

    fun isTransferLink(uri: Uri?): Boolean =
        uri != null &&
            uri.scheme.equals(SCHEME, ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true)

    fun build(portableBytes: ByteArray): Generated {
        require(portableBytes.isNotEmpty()) { "Portable payload is empty." }

        val compressed = gzip(portableBytes)
        val encoded = Base64.encodeToString(
            compressed,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val uri = "$SCHEME://$HOST#$VERSION_PREFIX$encoded"

        return Generated(
            uri = uri,
            originalBytes = portableBytes.size,
            compressedBytes = compressed.size,
            encodedChars = encoded.length,
            finalUrlChars = uri.length
        )
    }

    fun decode(uri: Uri): ByteArray {
        require(isTransferLink(uri)) { "Not an Armyrist transfer link." }

        val fragment = uri.fragment.orEmpty()
        require(fragment.startsWith(VERSION_PREFIX)) {
            "Unsupported Armyrist transfer-link version."
        }

        val encoded = fragment.removePrefix(VERSION_PREFIX)
        require(encoded.isNotBlank()) { "Transfer payload is missing." }
        require(encoded.length <= MAX_ENCODED_CHARS) { "Transfer link is too large." }

        val compressed = runCatching {
            Base64.decode(
                encoded,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }.getOrElse {
            throw IllegalArgumentException("Transfer payload encoding is invalid.", it)
        }

        return gunzipLimited(compressed)
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun gunzipLimited(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(8192)
            var total = 0

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_DECOMPRESSED_BYTES) {
                    "Decoded transfer payload is too large."
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }
}
