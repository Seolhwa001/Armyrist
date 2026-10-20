package com.seolhwa.armyrist.collection

import org.junit.Assert.*
import org.junit.Test

class ThumbnailSizingTest {
    @Test fun cameraImageIsSubsampledBeforeAllocatingPixels() {
        val sample = ThumbnailSizing.sampleSize(8000, 6000, 156)
        assertTrue(sample > 1)
        assertTrue(8000 / sample <= 312)
        assertTrue(8000 / sample >= 156)
    }
    @Test fun smallImageIsNotUpscaled() {
        assertEquals(1, ThumbnailSizing.sampleSize(20, 10, 156))
    }
    @Test fun panoramaStillHasBoundedLongestEdge() {
        val sample = ThumbnailSizing.sampleSize(20000, 100, 128)
        assertTrue(20000 / sample <= 256)
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidBoundsAreRejected() { ThumbnailSizing.sampleSize(-1, 0, 156) }
}
