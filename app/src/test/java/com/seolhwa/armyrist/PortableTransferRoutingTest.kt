package com.seolhwa.armyrist

import org.junit.Assert.assertEquals
import org.junit.Test

class PortableTransferRoutingTest {

    @Test
    fun internalExportRouteIsPreserved() {
        assertEquals(
            PortableLaunchRoute.INTERNAL_EXPORT,
            resolvePortableLaunchRoute(
                action = null,
                extraMode = PortableTransferActivity.MODE_EXPORT,
                hasViewUri = false,
                hasSendUri = false
            )
        )
    }

    @Test
    fun internalImportRouteIsPreserved() {
        assertEquals(
            PortableLaunchRoute.INTERNAL_IMPORT,
            resolvePortableLaunchRoute(
                action = null,
                extraMode = PortableTransferActivity.MODE_IMPORT,
                hasViewUri = false,
                hasSendUri = false
            )
        )
    }

    @Test
    fun actionViewWithUriRoutesToExternalImport() {
        assertEquals(
            PortableLaunchRoute.EXTERNAL_VIEW,
            resolvePortableLaunchRoute(
                action = "android.intent.action.VIEW",
                extraMode = null,
                hasViewUri = true,
                hasSendUri = false
            )
        )
    }

    @Test
    fun actionSendWithUriRoutesToExternalImport() {
        assertEquals(
            PortableLaunchRoute.EXTERNAL_SEND,
            resolvePortableLaunchRoute(
                action = "android.intent.action.SEND",
                extraMode = null,
                hasViewUri = false,
                hasSendUri = true
            )
        )
    }

    @Test
    fun actionSendWithoutUriFailsGracefully() {
        assertEquals(
            PortableLaunchRoute.INVALID_EXTERNAL,
            resolvePortableLaunchRoute(
                action = "android.intent.action.SEND",
                extraMode = null,
                hasViewUri = false,
                hasSendUri = false
            )
        )
    }

    @Test
    fun unsupportedLaunchDoesNotPretendToImport() {
        assertEquals(
            PortableLaunchRoute.UNSUPPORTED,
            resolvePortableLaunchRoute(
                action = "example.unsupported",
                extraMode = null,
                hasViewUri = false,
                hasSendUri = false
            )
        )
    }
}
