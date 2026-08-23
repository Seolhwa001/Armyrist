package com.seolhwa.armyrist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Central runtime-permission contract for Nearby Connections. */
internal object NearbyPermissionGate {
    fun requiredPermissions(): Array<String> {
        val out = mutableListOf<String>()

        // Legacy Nearby Connections location contract. Request coarse together with fine
        // so Android can grant the location permission group consistently.
        when (Build.VERSION.SDK_INT) {
            in 23..28 -> out += Manifest.permission.ACCESS_COARSE_LOCATION
            in 29..32 -> {
                out += Manifest.permission.ACCESS_COARSE_LOCATION
                out += Manifest.permission.ACCESS_FINE_LOCATION
            }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            out += Manifest.permission.BLUETOOTH_ADVERTISE
            out += Manifest.permission.BLUETOOTH_CONNECT
            out += Manifest.permission.BLUETOOTH_SCAN
        }

        if (Build.VERSION.SDK_INT >= 33) {
            out += Manifest.permission.NEARBY_WIFI_DEVICES
        }

        return out.distinct().toTypedArray()
    }

    fun missingPermissions(context: Context): Array<String> =
        requiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) !=
                PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun hasRequiredPermissions(context: Context): Boolean =
        missingPermissions(context).isEmpty()
}
