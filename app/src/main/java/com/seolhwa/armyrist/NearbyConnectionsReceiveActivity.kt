package com.seolhwa.armyrist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Opens only after the user presses [받기] in the request notification.
 * It keeps Armyrist in the foreground while the actual Portable FILE payload moves,
 * then forwards the completed cache file into the existing validation/preview flow.
 */
class NearbyConnectionsReceiveActivity : ComponentActivity() {
    private val status = mutableStateOf("연결을 승인하고 데이터를 기다리는 중입니다.")

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NearbyConnectionsPoC.ACTION_PAYLOAD_READY -> {
                    val path =
                        intent.getStringExtra(NearbyConnectionsPoC.EXTRA_FILE_PATH)
                            ?: return
                    openPreview(path)
                }

                NearbyConnectionsPoC.ACTION_TRANSFER_ERROR -> {
                    status.value =
                        intent.getStringExtra(NearbyConnectionsPoC.EXTRA_ERROR)
                            ?: "데이터 수신에 실패했습니다."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter().apply {
            addAction(NearbyConnectionsPoC.ACTION_PAYLOAD_READY)
            addAction(NearbyConnectionsPoC.ACTION_TRANSFER_ERROR)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }

        setContent {
            ArmyristTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Armyrist 데이터 수신",
                        fontWeight = FontWeight.Bold
                    )
                    Text(status.value)
                    Text(
                        "전송이 끝나면 기존 데이터 검토 화면으로 자동 이동합니다."
                    )
                }
            }
        }

        val endpointId =
            intent.getStringExtra(NearbyConnectionsPoC.EXTRA_ENDPOINT_ID)

        if (endpointId.isNullOrBlank()) {
            status.value = "수신 요청 정보를 찾을 수 없습니다."
            return
        }

        startService(
            Intent(
                this,
                NearbyConnectionsReceiverService::class.java
            ).apply {
                action = NearbyConnectionsPoC.ACTION_ACCEPT
                putExtra(
                    NearbyConnectionsPoC.EXTRA_ENDPOINT_ID,
                    endpointId
                )
            }
        )
    }

    override fun onResume() {
        super.onResume()

        val ready =
            getSharedPreferences(
                NearbyConnectionsPoC.PREFS,
                MODE_PRIVATE
            ).getString(
                NearbyConnectionsPoC.PREF_READY_FILE,
                null
            )

        if (!ready.isNullOrBlank()) {
            openPreview(ready)
        }
    }

    private fun openPreview(path: String) {
        getSharedPreferences(
            NearbyConnectionsPoC.PREFS,
            MODE_PRIVATE
        ).edit()
            .remove(NearbyConnectionsPoC.PREF_READY_FILE)
            .apply()

        startActivity(
            Intent(
                this,
                PortableTransferActivity::class.java
            ).apply {
                putExtra(
                    PortableTransferActivity.EXTRA_MODE,
                    PortableTransferActivity.MODE_IMPORT_BYTES
                )
                putExtra(
                    PortableTransferActivity.EXTRA_CACHE_FILE,
                    path
                )
            }
        )
        finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }
}
