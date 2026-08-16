package com.seolhwa.armyrist

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object NearbyConnectionsPoC {
    const val SERVICE_ID = "com.seolhwa.armyrist.nearby.poc"
    const val STRATEGY_LABEL = "P2P_POINT_TO_POINT"

    const val PREFS = "armyrist_nearby_connections_poc"
    const val PREF_RECEIVE_ENABLED = "receive_enabled"
    const val PREF_READY_FILE = "ready_file"

    const val ACTION_START = "com.seolhwa.armyrist.nearby.START"
    const val ACTION_STOP = "com.seolhwa.armyrist.nearby.STOP"
    const val ACTION_ACCEPT = "com.seolhwa.armyrist.nearby.ACCEPT"
    const val ACTION_REJECT = "com.seolhwa.armyrist.nearby.REJECT"
    const val ACTION_PAYLOAD_READY = "com.seolhwa.armyrist.nearby.PAYLOAD_READY"
    const val ACTION_TRANSFER_ERROR = "com.seolhwa.armyrist.nearby.TRANSFER_ERROR"
    const val ACTION_ADVERTISING_ACTIVE = "com.seolhwa.armyrist.nearby.ADVERTISING_ACTIVE"
    const val ACTION_ADVERTISING_FAILED = "com.seolhwa.armyrist.nearby.ADVERTISING_FAILED"

    const val EXTRA_ENDPOINT_ID = "endpointId"
    const val EXTRA_FILE_PATH = "filePath"
    const val EXTRA_ERROR = "error"
    const val EXTRA_SEND_FILE = "sendFile"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TYPE = "type"

    /**
     * Nearby Connections permissions from the current Google Android guide.
     * Request only when the user explicitly enables receive mode or opens sender PoC.
     */
    fun runtimePermissions(): Array<String> {
        val out = mutableListOf<String>()

        if (Build.VERSION.SDK_INT in 29..31) {
            out += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (Build.VERSION.SDK_INT >= 31) {
            out += Manifest.permission.BLUETOOTH_ADVERTISE
            out += Manifest.permission.BLUETOOTH_CONNECT
            out += Manifest.permission.BLUETOOTH_SCAN
        }

        if (Build.VERSION.SDK_INT >= 33) {
            out += Manifest.permission.NEARBY_WIFI_DEVICES
            out += Manifest.permission.POST_NOTIFICATIONS
        }

        return out.distinct().toTypedArray()
    }

    fun hasRuntimePermissions(context: Context): Boolean =
        runtimePermissions().all {
            ContextCompat.checkSelfPermission(context, it) ==
                PackageManager.PERMISSION_GRANTED
        }


    fun diagnosticFailure(operation: String, throwable: Throwable): String {
        val api = throwable as? ApiException
        val code = api?.statusCode
        val codeText = code?.let { ConnectionsStatusCodes.getStatusCodeString(it) }
            ?: throwable::class.java.simpleName
        val detail = throwable.message?.take(160).orEmpty()
        return buildString {
            append(operation)
            append(" FAIL")
            if (code != null) {
                append(" · statusCode=")
                append(code)
            }
            append(" · ")
            append(codeText)
            if (detail.isNotBlank()) {
                append(" · ")
                append(detail)
            }
        }
    }

    fun isReceiveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_RECEIVE_ENABLED, false)

    fun setReceiveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_RECEIVE_ENABLED, enabled)
            .apply()
    }

    fun startReceiverService(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, NearbyConnectionsReceiverService::class.java)
                .setAction(ACTION_START)
        )
    }

    fun stopReceiverService(context: Context) {
        setReceiveEnabled(context, false)
        context.startService(
            Intent(context, NearbyConnectionsReceiverService::class.java)
                .setAction(ACTION_STOP)
        )
    }

    data class RequestMetadata(
        val device: String,
        val title: String,
        val type: String
    )

    /**
     * ConnectionInfo.endpointName is available before the receiver accepts.
     * For this PoC it carries only small, non-sensitive display metadata so the
     * notification can tell the receiver what is being offered before Portable bytes move.
     */
    fun encodeRequestMetadata(device: String, title: String, type: String): String {
        fun enc(value: String): String =
            URLEncoder.encode(
                value.take(48),
                StandardCharsets.UTF_8.name()
            )

        return "ARMYRIST|${enc(device)}|${enc(title)}|${enc(type)}"
            .take(180)
    }

    fun decodeRequestMetadata(raw: String): RequestMetadata {
        val parts = raw.split('|', limit = 4)
        if (parts.size != 4 || parts[0] != "ARMYRIST") {
            return RequestMetadata(
                device = raw.ifBlank { "주변 Android 기기" }.take(48),
                title = "Armyrist 데이터",
                type = "PORTABLE"
            )
        }

        fun dec(value: String): String =
            runCatching {
                URLDecoder.decode(
                    value,
                    StandardCharsets.UTF_8.name()
                )
            }.getOrDefault(value)

        return RequestMetadata(
            device = dec(parts[1]).ifBlank { "주변 Android 기기" }.take(48),
            title = dec(parts[2]).ifBlank { "Armyrist 데이터" }.take(64),
            type = dec(parts[3]).ifBlank { "PORTABLE" }.take(24)
        )
    }
}
