package com.seolhwa.armyrist

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Technical Validation Handover No.005
 *
 * Wi-Fi Direct / Wi-Fi P2P PoC.
 *
 * Existing NSD + TCP implementation remains untouched in NearbyTransferActivity.
 * This Activity is a separate transport experiment for the no-access-point case.
 *
 * After the P2P group is formed, a symmetric TCP handshake is used:
 * - group owner listens on a fixed local port
 * - group client connects to the owner
 * - whichever side has EXTRA_SEND_FILE becomes the sender
 * - the other side becomes the receiver
 *
 * The received bytes are not imported automatically. They are passed to the
 * existing PortableTransferActivity validation / preview path only after the
 * receiver presses "받기".
 */
class WifiDirectTransferActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SEND_FILE = "sendFile"
        private const val PORT = 42817
        private const val MAX_BYTES = 32 * 1024 * 1024
        private const val MAGIC = "ARMYRIST_WFD_POC_V1"
        private const val ROLE_SEND = "SEND"
        private const val ROLE_RECEIVE = "RECEIVE"
    }

    private val io = Executors.newCachedThreadPool()
    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel

    private val peers = mutableStateListOf<WifiP2pDevice>()
    private var status by mutableStateOf("Wi-Fi Direct 준비 중")
    private var p2pEnabled by mutableStateOf(false)
    private var connected by mutableStateOf(false)
    private var connectionInfo: WifiP2pInfo? = null

    private var incoming by mutableStateOf<ByteArray?>(null)
    private var incomingFrom by mutableStateOf("")
    private var receiverRegistered = false
    private val transportStarted = AtomicBoolean(false)

    private val sendFile: File?
        get() = intent.getStringExtra(EXTRA_SEND_FILE)?.let(::File)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) {
                status = "권한 승인됨 · 주변 기기를 검색하세요."
                discoverPeers()
            } else {
                status = "주변 기기 검색 권한이 필요합니다."
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    )
                    p2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    status =
                        if (p2pEnabled) "Wi-Fi Direct 사용 가능 · 주변 기기를 검색하세요."
                        else "Wi-Fi Direct가 꺼져 있거나 지원되지 않습니다."
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    @Suppress("DEPRECATION")
                    val networkInfo =
                        intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                    connected = networkInfo?.isConnected == true
                    if (connected) {
                        status = "P2P 연결됨 · 전송 채널 준비 중"
                        requestConnectionInfo()
                    } else {
                        connectionInfo = null
                        transportStarted.set(false)
                        status = "연결 해제됨 · 다시 검색할 수 있습니다."
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper) {
            runOnUiThread { status = "Wi-Fi Direct 채널이 끊어졌습니다." }
        }

        registerP2pReceiver()
        render()

        if (hasRequiredPermission()) {
            discoverPeers()
        } else {
            requestRequiredPermission()
        }
    }

    private fun render() = setContent {
        ArmyristTheme {
            Scaffold(
                topBar = {
                    ArmyristTopBar(
                        title = "Wi-Fi Direct PoC",
                        subtitle = "공유기 없는 근거리 직접전송",
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
                            if (sendFile != null) "송신 모드" else "수신 모드",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "기존 NSD+TCP PoC는 유지됩니다. 이 화면은 Wi-Fi 공유기 없이 Wi-Fi Direct만으로 연결되는지 검증합니다."
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Wi-Fi 기능은 켜져 있어야 하며, 인터넷 연결은 필요하지 않습니다.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { discoverPeers() },
                            modifier = Modifier.weight(1f),
                            shape = ArmyristPanelShape
                        ) {
                            Text("다시 검색")
                        }

                        OutlinedButton(
                            onClick = {
                                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            },
                            modifier = Modifier.weight(1f),
                            shape = ArmyristPanelShape
                        ) {
                            Text("Wi-Fi 설정")
                        }
                    }

                    Text("Wi-Fi Direct 기기", fontWeight = FontWeight.Bold)

                    if (peers.isEmpty()) {
                        Text("아직 발견된 기기가 없습니다.")
                    }

                    peers.forEach { peer ->
                        ArmyristPanel(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        peer.deviceName.ifBlank { "Android 기기" },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        deviceStatusLabel(peer.status),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Button(
                                    onClick = { connect(peer) },
                                    enabled = !connected,
                                    shape = ArmyristPanelShape
                                ) {
                                    Text("연결")
                                }
                            }
                        }
                    }

                    if (connected) {
                        Text(
                            "P2P 그룹 연결 완료. 송신/수신 역할을 자동 협상한 뒤 Portable Data를 전송합니다.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            incoming?.let { bytes ->
                AlertDialog(
                    onDismissRequest = { incoming = null },
                    title = { Text("Armyrist 데이터 수신") },
                    text = {
                        Text(
                            "${incomingFrom.ifBlank { "Wi-Fi Direct 기기" }}에서 " +
                                "${bytes.size} bytes의 Portable Data를 받았습니다. " +
                                "기존 검증/Preview로 이동하시겠습니까?"
                        )
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                incoming = null
                                status = "수신을 거절했습니다. Domain Data는 변경되지 않았습니다."
                            }
                        ) { Text("거절") }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val file = File(
                                    cacheDir,
                                    "wifi-direct-received-${System.currentTimeMillis()}.armyrist"
                                )
                                file.writeBytes(bytes)
                                incoming = null
                                startActivity(
                                    Intent(
                                        this@WifiDirectTransferActivity,
                                        PortableTransferActivity::class.java
                                    ).apply {
                                        putExtra(
                                            PortableTransferActivity.EXTRA_MODE,
                                            PortableTransferActivity.MODE_IMPORT_BYTES
                                        )
                                        putExtra(
                                            PortableTransferActivity.EXTRA_CACHE_FILE,
                                            file.absolutePath
                                        )
                                    }
                                )
                            }
                        ) { Text("받기") }
                    }
                )
            }
        }
    }

    private fun registerP2pReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    private fun hasRequiredPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun requestRequiredPermission() {
        val permissions =
            if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        permissionLauncher.launch(permissions)
    }

    private fun discoverPeers() {
        if (!hasRequiredPermission()) {
            requestRequiredPermission()
            return
        }

        if (!p2pEnabled) {
            status = "Wi-Fi Direct 상태 확인 중 · Wi-Fi가 켜져 있는지 확인하세요."
        }

        peers.clear()
        status = "Wi-Fi Direct 주변 기기를 검색 중..."

        try {
            manager.discoverPeers(
                channel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        runOnUiThread { status = "검색 시작됨 · 두 기기에서 이 화면을 열어두세요." }
                    }

                    override fun onFailure(reason: Int) {
                        runOnUiThread { status = "검색 시작 실패 (${reasonLabel(reason)})" }
                    }
                }
            )
        } catch (e: SecurityException) {
            status = "주변 Wi-Fi 권한이 필요합니다."
        }
    }

    private fun requestPeers() {
        if (!hasRequiredPermission()) return

        try {
            manager.requestPeers(channel) { list: WifiP2pDeviceList ->
                runOnUiThread {
                    peers.clear()
                    peers.addAll(list.deviceList.sortedBy { it.deviceName })
                    status =
                        if (peers.isEmpty()) "검색 중 · 아직 Wi-Fi Direct 기기가 없습니다."
                        else "주변 기기 ${peers.size}대 발견"
                }
            }
        } catch (e: SecurityException) {
            status = "주변 기기 목록을 읽을 권한이 없습니다."
        }
    }

    private fun connect(peer: WifiP2pDevice) {
        if (!hasRequiredPermission()) {
            requestRequiredPermission()
            return
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            // Sender prefers to be a client so the passive receiving device normally
            // becomes group owner; the symmetric socket protocol also supports the
            // opposite negotiation result.
            groupOwnerIntent = if (sendFile != null) 0 else 15
        }

        status = "${peer.deviceName.ifBlank { "기기" }}에 연결 요청 중..."

        try {
            manager.connect(
                channel,
                config,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        runOnUiThread { status = "연결 요청 전송됨 · 시스템 연결 절차를 확인하세요." }
                    }

                    override fun onFailure(reason: Int) {
                        runOnUiThread { status = "연결 요청 실패 (${reasonLabel(reason)})" }
                    }
                }
            )
        } catch (e: SecurityException) {
            status = "연결에 필요한 주변 Wi-Fi 권한이 없습니다."
        }
    }

    private fun requestConnectionInfo() {
        if (!hasRequiredPermission()) return

        try {
            manager.requestConnectionInfo(channel) { info ->
                connectionInfo = info
                if (info.groupFormed && transportStarted.compareAndSet(false, true)) {
                    startTransport(info)
                }
            }
        } catch (e: SecurityException) {
            status = "연결 정보를 읽을 권한이 없습니다."
        }
    }

    private fun startTransport(info: WifiP2pInfo) {
        if (!info.groupFormed) {
            transportStarted.set(false)
            return
        }

        if (info.isGroupOwner) {
            startAsGroupOwner()
        } else {
            val owner = info.groupOwnerAddress
            if (owner == null) {
                status = "그룹 Owner 주소를 확인할 수 없습니다."
                transportStarted.set(false)
            } else {
                startAsGroupClient(owner.hostAddress ?: owner.toString())
            }
        }
    }

    private fun startAsGroupOwner() {
        status = "P2P Group Owner · 상대 연결 대기 중"
        io.execute {
            runCatching {
                ServerSocket(PORT).use { server ->
                    server.soTimeout = 30_000
                    server.accept().use { socket ->
                        socket.soTimeout = 30_000
                        val input = DataInputStream(socket.getInputStream())
                        val output = DataOutputStream(socket.getOutputStream())

                        val magic = input.readUTF()
                        require(magic == MAGIC) { "Invalid protocol" }
                        val remoteRole = input.readUTF()

                        if (sendFile != null) {
                            require(remoteRole == ROLE_RECEIVE) { "Both peers are senders." }
                            val bytes = sendFile!!.readBytes()
                            sendPayload(output, bytes)
                            runOnUiThread { status = "Wi-Fi Direct 전송 완료" }
                        } else {
                            require(remoteRole == ROLE_SEND) { "Both peers are receivers." }
                            val bytes = receivePayload(input)
                            runOnUiThread {
                                incomingFrom = socket.inetAddress.hostAddress ?: "Wi-Fi Direct 기기"
                                incoming = bytes
                                status = "Wi-Fi Direct 수신 요청 도착"
                            }
                        }
                    }
                }
            }.onFailure {
                runOnUiThread {
                    status = "P2P 전송 실패: ${it.javaClass.simpleName}"
                    transportStarted.set(false)
                }
            }
        }
    }

    private fun startAsGroupClient(ownerHost: String) {
        status = "P2P Group Client · Owner 연결 중"
        io.execute {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ownerHost, PORT), 15_000)
                    socket.soTimeout = 30_000

                    val output = DataOutputStream(socket.getOutputStream())
                    val input = DataInputStream(socket.getInputStream())

                    output.writeUTF(MAGIC)
                    output.writeUTF(if (sendFile != null) ROLE_SEND else ROLE_RECEIVE)
                    output.flush()

                    if (sendFile != null) {
                        val bytes = sendFile!!.readBytes()
                        sendPayload(output, bytes)
                        runOnUiThread { status = "Wi-Fi Direct 전송 완료" }
                    } else {
                        val bytes = receivePayload(input)
                        runOnUiThread {
                            incomingFrom = ownerHost
                            incoming = bytes
                            status = "Wi-Fi Direct 수신 요청 도착"
                        }
                    }
                }
            }.onFailure {
                runOnUiThread {
                    status = "P2P 전송 실패: ${it.javaClass.simpleName}"
                    transportStarted.set(false)
                }
            }
        }
    }

    private fun sendPayload(output: DataOutputStream, bytes: ByteArray) {
        require(bytes.size in 1..MAX_BYTES)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun receivePayload(input: DataInputStream): ByteArray {
        val length = input.readInt()
        require(length in 1..MAX_BYTES)
        return ByteArray(length).also { input.readFully(it) }
    }

    private fun deviceStatusLabel(status: Int): String =
        when (status) {
            WifiP2pDevice.CONNECTED -> "연결됨"
            WifiP2pDevice.INVITED -> "연결 요청 중"
            WifiP2pDevice.AVAILABLE -> "연결 가능"
            WifiP2pDevice.FAILED -> "연결 실패 상태"
            WifiP2pDevice.UNAVAILABLE -> "현재 사용 불가"
            else -> "상태 확인 중"
        }

    private fun reasonLabel(reason: Int): String =
        when (reason) {
            WifiP2pManager.P2P_UNSUPPORTED -> "P2P 미지원"
            WifiP2pManager.BUSY -> "Wi-Fi P2P 사용 중"
            WifiP2pManager.ERROR -> "시스템 오류"
            else -> "code=$reason"
        }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(receiver) }
            receiverRegistered = false
        }

        if (::manager.isInitialized && ::channel.isInitialized) {
            runCatching {
                manager.cancelConnect(channel, null)
                manager.stopPeerDiscovery(channel, null)
            }
        }

        io.shutdownNow()
        super.onDestroy()
    }
}
