package com.seolhwa.armyrist.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

internal fun reportStorageError(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
