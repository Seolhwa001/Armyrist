package com.seolhwa.armyrist.collection

internal object ThumbnailSizing {
    fun sampleSize(width: Int, height: Int, targetPixels: Int): Int {
        require(width > 0 && height > 0 && targetPixels > 0)
        var sample = 1
        // Keep at most twice the requested longest edge before the final resize.
        while (sample <= Int.MAX_VALUE / 2 && maxOf(width, height) / sample / 2 >= targetPixels) sample *= 2
        return sample
    }
}
