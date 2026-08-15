package com.seolhwa.armyrist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Isolated Nearby Connections background-receive PoC.
 *
 * It is intentionally separate from the existing NSD/TCP implementation.
 * Advertising is active only while the user has explicitly enabled the Home toggle.
 */
class NearbyConnectionsReceiverService : Service() {
    companion object {
        private const val CHANNEL_STATUS = "armyrist_nearby_receive_status"
        private const val CHANNEL_REQUEST = "armyrist_nearby_receive_request"
        private const val STATUS_NOTIFICATION_ID = 4310
        private const val REQUEST_NOTIFICATION_BASE = 4400
    }

    private lateinit var client: ConnectionsClient
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
    private val incomingPayloads = ConcurrentHashMap<Long, Payload>()

    private data class PendingRequest(
        val endpointId: String,
        val metadata: NearbyConnectionsPoC.RequestMetadata,
        val authenticationDigits: String
    )

    override fun onCreate() {
        super.onCreate()
        client = Nearby.getConnectionsClient(this)
        createChannels()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            NearbyConnectionsPoC.ACTION_STOP -> {
                stopReceiveMode()
                return START_NOT_STICKY
            }

            NearbyConnectionsPoC.ACTION_ACCEPT -> {
                val endpointId =
                    intent.getStringExtra(NearbyConnectionsPoC.EXTRA_ENDPOINT_ID)
                if (!endpointId.isNullOrBlank()) accept(endpointId)
            }

            NearbyConnectionsPoC.ACTION_REJECT -> {
                val endpointId =
                    intent.getStringExtra(NearbyConnectionsPoC.EXTRA_ENDPOINT_ID)
                if (!endpointId.isNullOrBlank()) reject(endpointId)
            }

            else -> Unit
        }

        if (
            NearbyConnectionsPoC.isReceiveEnabled(this) &&
            NearbyConnectionsPoC.hasRuntimePermissions(this)
        ) {
            startForeground(
                STATUS_NOTIFICATION_ID,
                statusNotification("주변 데이터 수신 대기 중")
            )
            startAdvertising()
        } else if (intent?.action == NearbyConnectionsPoC.ACTION_START) {
            NearbyConnectionsPoC.setReceiveEnabled(this, false)
            stopSelf()
        }

        return START_STICKY
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()

        client.startAdvertising(
            "Armyrist · ${Build.MODEL}",
            NearbyConnectionsPoC.SERVICE_ID,
            lifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.i(
                "ArmyristNearby",
                "startAdvertising SUCCESS serviceId=${NearbyConnectionsPoC.SERVICE_ID} strategy=${NearbyConnectionsPoC.STRATEGY_LABEL}"
            )
            notifyStatus("주변 데이터 수신 대기 중")
        }.addOnFailureListener { error ->
            val diagnostic =
                NearbyConnectionsPoC.diagnosticFailure("startAdvertising", error)
            Log.e(
                "ArmyristNearby",
                "$diagnostic · serviceId=${NearbyConnectionsPoC.SERVICE_ID} strategy=${NearbyConnectionsPoC.STRATEGY_LABEL}",
                error
            )
            notifyStatus("수신 대기 시작 실패 · $diagnostic")
        }
    }

    private val lifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(
                endpointId: String,
                info: ConnectionInfo
            ) {
                val request = PendingRequest(
                    endpointId = endpointId,
                    metadata =
                        NearbyConnectionsPoC.decodeRequestMetadata(info.endpointName),
                    authenticationDigits = info.authenticationDigits.orEmpty()
                )
                pendingRequests[endpointId] = request
                postRequestNotification(request)
            }

            override fun onConnectionResult(
                endpointId: String,
                resolution: ConnectionResolution
            ) {
                if (
                    resolution.status.statusCode !=
                    ConnectionsStatusCodes.STATUS_OK
                ) {
                    pendingRequests.remove(endpointId)
                    cancelRequestNotification(endpointId)
                }
            }

            override fun onDisconnected(endpointId: String) {
                pendingRequests.remove(endpointId)
                cancelRequestNotification(endpointId)
            }
        }

    private val payloadCallback =
        object : PayloadCallback() {
            override fun onPayloadReceived(
                endpointId: String,
                payload: Payload
            ) {
                if (payload.type == Payload.Type.FILE) {
                    incomingPayloads[payload.id] = payload
                } else {
                    payload.close()
                }
            }

            override fun onPayloadTransferUpdate(
                endpointId: String,
                update: PayloadTransferUpdate
            ) {
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        val payload =
                            incomingPayloads.remove(update.payloadId)
                                ?: return
                        handleCompletedFile(endpointId, payload)
                    }

                    PayloadTransferUpdate.Status.FAILURE,
                    PayloadTransferUpdate.Status.CANCELED -> {
                        incomingPayloads.remove(update.payloadId)?.close()
                        sendTransferError("데이터 수신에 실패했습니다.")
                    }
                }
            }
        }

    private fun accept(endpointId: String) {
        val request = pendingRequests[endpointId] ?: return
        cancelRequestNotification(endpointId)

        client.acceptConnection(endpointId, payloadCallback)
            .addOnSuccessListener {
                notifyStatus("${request.metadata.device}에서 데이터 받는 중")
            }
            .addOnFailureListener {
                pendingRequests.remove(endpointId)
                sendTransferError("연결을 승인할 수 없습니다.")
            }
    }

    private fun reject(endpointId: String) {
        cancelRequestNotification(endpointId)
        pendingRequests.remove(endpointId)
        client.rejectConnection(endpointId)
        notifyStatus("주변 데이터 수신 대기 중")
    }

    private fun handleCompletedFile(
        endpointId: String,
        payload: Payload
    ) {
        runCatching {
            val uri = payload.asFile()?.asUri()
                ?: error("Nearby file URI missing")

            val target = File(
                cacheDir,
                "nearby-connections-${System.currentTimeMillis()}.armyrist"
            )

            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }

            runCatching { contentResolver.delete(uri, null, null) }
            payload.close()

            getSharedPreferences(
                NearbyConnectionsPoC.PREFS,
                MODE_PRIVATE
            ).edit()
                .putString(
                    NearbyConnectionsPoC.PREF_READY_FILE,
                    target.absolutePath
                )
                .apply()

            sendBroadcast(
                Intent(NearbyConnectionsPoC.ACTION_PAYLOAD_READY)
                    .setPackage(packageName)
                    .putExtra(
                        NearbyConnectionsPoC.EXTRA_FILE_PATH,
                        target.absolutePath
                    )
            )

            pendingRequests.remove(endpointId)
            client.disconnectFromEndpoint(endpointId)
            notifyStatus("데이터 수신 완료 · 검토 화면으로 이동합니다.")
        }.onFailure {
            payload.close()
            sendTransferError("수신 데이터를 준비할 수 없습니다.")
        }
    }

    private fun sendTransferError(message: String) {
        sendBroadcast(
            Intent(NearbyConnectionsPoC.ACTION_TRANSFER_ERROR)
                .setPackage(packageName)
                .putExtra(NearbyConnectionsPoC.EXTRA_ERROR, message)
        )
        notifyStatus(message)
    }

    private fun stopReceiveMode() {
        NearbyConnectionsPoC.setReceiveEnabled(this, false)
        runCatching { client.stopAdvertising() }
        pendingRequests.keys.forEach {
            runCatching { client.disconnectFromEndpoint(it) }
        }
        pendingRequests.clear()
        incomingPayloads.values.forEach { runCatching { it.close() } }
        incomingPayloads.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                "Armyrist 주변 데이터 수신",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "주변 데이터 수신 모드가 켜져 있을 때 표시됩니다."
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REQUEST,
                "Armyrist 데이터 수신 요청",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "주변 Armyrist에서 데이터 전송 요청이 왔을 때 표시됩니다."
            }
        )
    }

    private fun statusNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Armyrist")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun notifyStatus(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(STATUS_NOTIFICATION_ID, statusNotification(text))
    }

    private fun postRequestNotification(request: PendingRequest) {
        val acceptIntent = Intent(
            this,
            NearbyConnectionsReceiveActivity::class.java
        ).apply {
            putExtra(
                NearbyConnectionsPoC.EXTRA_ENDPOINT_ID,
                request.endpointId
            )
        }

        val rejectIntent = Intent(
            this,
            NearbyConnectionsReceiverService::class.java
        ).apply {
            action = NearbyConnectionsPoC.ACTION_REJECT
            putExtra(
                NearbyConnectionsPoC.EXTRA_ENDPOINT_ID,
                request.endpointId
            )
        }

        val requestCode = request.endpointId.hashCode()

        val acceptPendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectPendingIntent = PendingIntent.getService(
            this,
            requestCode + 1,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val authText =
            if (request.authenticationDigits.isBlank()) {
                ""
            } else {
                " · 확인 코드 ${request.authenticationDigits}"
            }

        val notification =
            NotificationCompat.Builder(this, CHANNEL_REQUEST)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Armyrist 데이터 수신 요청")
                .setContentText(
                    "${request.metadata.device} · ${request.metadata.title}"
                )
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "${request.metadata.device}에서 데이터를 보내려고 합니다.\n" +
                            "${request.metadata.title} · ${request.metadata.type}" +
                            authText
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(
                    0,
                    "거절",
                    rejectPendingIntent
                )
                .addAction(
                    0,
                    "받기",
                    acceptPendingIntent
                )
                .build()

        getSystemService(NotificationManager::class.java)
            .notify(requestNotificationId(request.endpointId), notification)
    }

    private fun cancelRequestNotification(endpointId: String) {
        getSystemService(NotificationManager::class.java)
            .cancel(requestNotificationId(endpointId))
    }

    private fun requestNotificationId(endpointId: String): Int =
        REQUEST_NOTIFICATION_BASE + (endpointId.hashCode() and 0x0FFF)

    override fun onDestroy() {
        runCatching { client.stopAdvertising() }
        incomingPayloads.values.forEach { runCatching { it.close() } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
