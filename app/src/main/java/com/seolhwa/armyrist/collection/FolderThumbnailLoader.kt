package com.seolhwa.armyrist.collection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

internal object FolderThumbnailLoader {
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val decoders = Semaphore(2)

    fun cached(path: String, requestedPixels: Int): Bitmap? = cache.get("$path@${requestedPixels.coerceIn(1, 512)}")

    suspend fun load(path: String, requestedPixels: Int): Bitmap? = withContext(Dispatchers.IO) {
        val pixels = requestedPixels.coerceIn(1, 512)
        val key = "$path@$pixels"
        cache.get(key)?.let { return@withContext it }
        decoders.withPermit {
            cache.get(key)?.let { return@withPermit it }
            runCatching {
                if (!File(path).isFile) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = ThumbnailSizing.sampleSize(bounds.outWidth, bounds.outHeight, pixels)
                }
                val decoded = BitmapFactory.decodeFile(path, options) ?: return@runCatching null
                val ratio = minOf(1f, pixels.toFloat() / maxOf(decoded.width, decoded.height))
                val thumbnail = if (ratio < 1f) Bitmap.createScaledBitmap(
                    decoded, maxOf(1, (decoded.width * ratio).toInt()), maxOf(1, (decoded.height * ratio).toInt()), true
                ).also { if (it !== decoded) decoded.recycle() } else decoded
                cache.put(key, thumbnail)
                thumbnail
            }.getOrNull()
        }
    }
}
