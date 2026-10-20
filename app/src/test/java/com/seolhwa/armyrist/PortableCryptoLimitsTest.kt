package com.seolhwa.armyrist

import org.junit.Test

class PortableCryptoLimitsTest {
    @Test fun currentAndLegacyWorkFactorsRemainReadable() {
        PortableCryptoLimits.validate(600_000, 16, 12)
        PortableCryptoLimits.validate(100_000, 32, 12)
    }
    @Test(expected = IllegalArgumentException::class)
    fun excessiveUntrustedWorkFactorIsRejected() = PortableCryptoLimits.validate(Int.MAX_VALUE, 16, 12)
    @Test(expected = IllegalArgumentException::class)
    fun zeroWorkFactorIsRejected() = PortableCryptoLimits.validate(0, 16, 12)
    @Test(expected = IllegalArgumentException::class)
    fun oversizedSaltIsRejected() = PortableCryptoLimits.validate(600_000, 65, 12)
    @Test(expected = IllegalArgumentException::class)
    fun invalidGcmNonceIsRejected() = PortableCryptoLimits.validate(600_000, 16, 1)
}
