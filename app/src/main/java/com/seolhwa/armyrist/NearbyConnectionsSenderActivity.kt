package com.seolhwa.armyrist

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.io.File
import java.io.FileNotFoundException

class NearbyConnectionsSenderActivity : ComponentActivity() {
    data class Endpoint(
        val id: String,
        val name: String
    )

    private lateinit var client: ConnectionsClient
    private val endpoints = mutableStateListOf<Endpoint>()
    private var status by mutableStateOf("주변 Armyrist를 찾는 중입니다.")
    private var connectingEndpoint by mutableStateOf<String?>(null)
    private var activeEndpoint by mutableStateOf<String?>(null)
    private var activePayloadId by mutableStateOf<Long?>(null)

    private val sendFile: File?
        get() = intent.getStringExtra(NearbyConnectionsPoC.EXTRA_SEND_FILE)
            ?.let(::File)

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.values.all { it }) {
                startDiscovery()
            } else {
                status = "주변 기기 권한이 없어 전송을 사용할 수 없습니다."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = Nearby.getConnectionsClient(this)
        render()

        if (NearbyConnectionsPoC.hasRuntimePermissions(this)) {
            startDiscovery()
        } else {
            permissionLauncher.launch(
                NearbyConnectionsPoC.runtimePermissions()
            )
        }
    }

    private fun render() = setContent {
        ArmyristTheme {
            Scaffold(
                topBar = {
                    ArmyristTopBar(
                        title = "주변 Armyrist",
                        subtitle = "Background Receive PoC",
                        leadingLabel = "뒤로",
                        onLeading = { finish() }
                    )
                }
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArmyristPanel(Modifier.fillMaxWidth()) {
                        Text(
                            "수신 모드를 켜 둔 주변 Armyrist를 찾습니다.",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(status)
                    }

                    OutlinedButton(
                        onClick = { startDiscovery() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ArmyristPanelShape
                    ) {
                        Text("다시 검색")
                    }

                    if (endpoints.isEmpty()) {
                        Text("아직 발견된 기기가 없습니다.")
                    }

                    endpoints.forEach { endpoint ->
                        ArmyristPanel(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        endpoint.name,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Armyrist",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Button(
                                    onClick = { connect(endpoint) },
                                    enabled =
                                        connectingEndpoint == null &&
                                            activeEndpoint == null,
                                    shape = ArmyristPanelShape
                                ) {
                                    Text("보내기")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startDiscovery() {
        if (!NearbyConnectionsPoC.hasRuntimePermissions(this)) {
            permissionLauncher.launch(
                NearbyConnectionsPoC.runtimePermissions()
            )
            return
        }

        endpoints.clear()
        status = "주변 Armyrist를 찾는 중입니다."

        client.stopDiscovery()
        client.startDiscovery(
            NearbyConnectionsPoC.SERVICE_ID,
            endpointDiscoveryCallback,
            DiscoveryOptions.Builder()
                .setStrategy(Strategy.P2P_POINT_TO_POINT)
                .build()
        ).addOnSuccessListener {
            Log.i(
                "ArmyristNearby",
                "startDiscovery SUCCESS serviceId=${NearbyConnectionsPoC.SERVICE_ID} strategy=${NearbyConnectionsPoC.STRATEGY_LABEL}"
            )
            status = "검색 중 · 수신 모드가 켜진 기기를 찾습니다."
        }.addOnFailureListener { error ->
            val diagnostic =
                NearbyConnectionsPoC.diagnosticFailure("startDiscovery", error)
            Log.e(
                "ArmyristNearby",
                "$diagnostic · serviceId=${NearbyConnectionsPoC.SERVICE_ID} strategy=${NearbyConnectionsPoC.STRATEGY_LABEL}",
                error
            )
            status = "검색 시작 실패 · $diagnostic"
        }
    }

    private val endpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(
                endpointId: String,
                info: DiscoveredEndpointInfo
            ) {
                if (
                    endpoints.none { it.id == endpointId }
                ) {
                    endpoints += Endpoint(
                        id = endpointId,
                        name =
                            info.endpointName
                                .removePrefix("Armyrist · ")
                                .ifBlank { "Android 기기" }
                    )
                    status = "주변 Armyrist ${endpoints.size}대 발견"
                }
            }

            override fun onEndpointLost(endpointId: String) {
                endpoints.removeAll { it.id == endpointId }
            }
        }

    private fun connect(endpoint: Endpoint) {
        val file = sendFile
        if (file == null || !file.isFile) {
            status = "전송할 Portable Data를 찾을 수 없습니다."
            return
        }

        val title =
            intent.getStringExtra(NearbyConnectionsPoC.EXTRA_TITLE)
                ?: "Armyrist 데이터"
        val type =
            intent.getStringExtra(NearbyConnectionsPoC.EXTRA_TYPE)
                ?: "PORTABLE"

        connectingEndpoint = endpoint.id
        status = "${endpoint.name}에 전송 요청을 보내는 중입니다."

        val localEndpointName =
            NearbyConnectionsPoC.encodeRequestMetadata(
                device = Build.MODEL,
                title = title,
                type = type
            )

        client.requestConnection(
            localEndpointName,
            endpoint.id,
            lifecycleCallback
        ).addOnFailureListener {
            connectingEndpoint = null
            status = "전송 요청을 보낼 수 없습니다."
        }
    }

    private val lifecycleCallback =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(
                endpointId: String,
                info: ConnectionInfo
            ) {
                status =
                    if (info.authenticationDigits.isNullOrBlank()) {
                        "상대방의 수신 승인을 기다리는 중입니다."
                    } else {
                        "상대방의 수신 승인을 기다리는 중 · " +
                            "확인 코드 ${info.authenticationDigits}"
                    }

                client.acceptConnection(
                    endpointId,
                    payloadCallback
                )
            }

            override fun onConnectionResult(
                endpointId: String,
                resolution: ConnectionResolution
            ) {
                connectingEndpoint = null

                if (
                    resolution.status.statusCode ==
                    ConnectionsStatusCodes.STATUS_OK
                ) {
                    activeEndpoint = endpointId
                    sendPortable(endpointId)
                } else {
                    activeEndpoint = null
                    status =
                        if (
                            resolution.status.statusCode ==
                            ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED
                        ) {
                            "상대방이 전송을 거절했습니다."
                        } else {
                            "연결하지 못했습니다. 다시 시도해주세요."
                        }
                }
            }

            override fun onDisconnected(endpointId: String) {
                if (activeEndpoint == endpointId) {
                    activeEndpoint = null
                }
            }
        }

    private val payloadCallback =
        object : PayloadCallback() {
            override fun onPayloadReceived(
                endpointId: String,
                payload: Payload
            ) {
                // Sender does not expect incoming payloads in this 1→1 PoC.
                payload.close()
            }

            override fun onPayloadTransferUpdate(
                endpointId: String,
                update: PayloadTransferUpdate
            ) {
                if (activePayloadId != update.payloadId) return

                when (update.status) {
                    PayloadTransferUpdate.Status.IN_PROGRESS -> {
                        if (update.totalBytes > 0) {
                            val percent =
                                (
                                    update.bytesTransferred * 100L /
                                        update.totalBytes
                                ).coerceIn(0, 100)
                            status = "데이터를 보내는 중… ${percent}%"
                        }
                    }

                    PayloadTransferUpdate.Status.SUCCESS -> {
                        status = "전송했습니다."
                        activePayloadId = null
                        activeEndpoint = null
                        client.disconnectFromEndpoint(endpointId)
                    }

                    PayloadTransferUpdate.Status.FAILURE,
                    PayloadTransferUpdate.Status.CANCELED -> {
                        status = "전송하지 못했습니다. 다시 시도해주세요."
                        activePayloadId = null
                        activeEndpoint = null
                        client.disconnectFromEndpoint(endpointId)
                    }
                }
            }
        }

    private fun sendPortable(endpointId: String) {
        val file = sendFile ?: return

        runCatching {
            Payload.fromFile(file).also {
                it.setSensitive(true)
            }
        }.onSuccess { payload ->
            activePayloadId = payload.id
            status = "데이터를 보내는 중…"
            client.sendPayload(endpointId, payload)
                .addOnFailureListener {
                    activePayloadId = null
                    activeEndpoint = null
                    status = "전송을 시작하지 못했습니다."
                }
        }.onFailure {
            activeEndpoint = null
            status =
                if (it is FileNotFoundException) {
                    "전송할 데이터 파일을 찾을 수 없습니다."
                } else {
                    "전송 데이터를 준비할 수 없습니다."
                }
        }
    }

    override fun onDestroy() {
        runCatching { client.stopDiscovery() }
        activeEndpoint?.let {
            runCatching { client.disconnectFromEndpoint(it) }
        }
        super.onDestroy()
    }
}
