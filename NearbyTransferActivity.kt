package com.seolhwa.armyrist

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Minor Patch B adopted Nearby transport.
 *
 * Transport: Android NSD (DNS-SD) discovery + local TCP on the same local
 * network. Internet/server/account are not required. Portable bytes are reused
 * unchanged and imported only through the existing validation/preview flow.
 */
class NearbyTransferActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SEND_FILE = "sendFile"
        private const val SERVICE_TYPE = "_armyrist._tcp."
        private const val MAX_BYTES = 32 * 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val IO_TIMEOUT_MS = 12_000
        private const val SEARCH_EMPTY_DELAY_MS = 6_000L
    }

    private enum class NearbyState { SEARCHING, FOUND, EMPTY, SENDING, SUCCESS, FAILED }

    data class Peer(
        val serviceId: String,
        val displayName: String,
        val host: InetAddress,
        val port: Int
    )

    private val io = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var nsd: NsdManager
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null

    private val peers = mutableStateListOf<Peer>()
    private var state by mutableStateOf(NearbyState.SEARCHING)
    private var message by mutableStateOf("주변 Armyrist를 찾는 중…")
    private var sendingPeerId by mutableStateOf<String?>(null)
    private var incoming by mutableStateOf<ByteArray?>(null)
    private var incomingFrom by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        startReceiver()
        startDiscovery()
        render()
    }

    private fun render() = setContent {
        ArmyristTheme {
            val sendFile = remember { intent.getStringExtra(EXTRA_SEND_FILE)?.let(::File) }
            Scaffold(
                topBar = {
                    ArmyristTopBar(
                        title = "주변 Armyrist",
                        subtitle = "같은 Wi-Fi · 인터넷 불필요",
                        leadingLabel = "뒤로",
                        onLeading = { finish() }
                    )
                }
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArmyristPanel(Modifier.fillMaxWidth()) {
                        Text("같은 Wi-Fi에 연결된 Armyrist를 찾습니다.", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("인터넷 연결은 필요하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }

                    if (sendFile != null) {
                        Text("주변 기기", fontWeight = FontWeight.Bold)

                        when (state) {
                            NearbyState.SEARCHING -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            NearbyState.EMPTY -> {
                                Text("주변 Armyrist를 찾지 못했습니다.\n두 기기가 같은 Wi-Fi에 연결되어 있는지 확인해주세요.")
                                OutlinedButton(onClick = { restartDiscovery() }, shape = ArmyristPanelShape) {
                                    Text("다시 검색")
                                }
                            }
                            NearbyState.FAILED -> {
                                Text("전송하지 못했습니다.\n두 기기의 Wi-Fi 연결 상태를 확인한 뒤 다시 시도해주세요.")
                                OutlinedButton(onClick = { restartDiscovery() }, shape = ArmyristPanelShape) {
                                    Text("다시 시도")
                                }
                            }
                            NearbyState.SUCCESS -> Text("전송했습니다.", fontWeight = FontWeight.Bold)
                            else -> Unit
                        }

                        peers.forEach { peer ->
                            ArmyristPanel(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(peer.displayName, fontWeight = FontWeight.SemiBold)
                                        Text("Armyrist", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(
                                        onClick = {
                                            val bytes = runCatching { sendFile.readBytes() }.getOrNull()
                                            if (bytes != null) send(peer, bytes)
                                        },
                                        enabled = state != NearbyState.SENDING,
                                        shape = ArmyristPanelShape
                                    ) {
                                        Text(if (sendingPeerId == peer.serviceId) "전송 중…" else "보내기")
                                    }
                                }
                            }
                        }
                    } else {
                        Text("수신 대기 중", fontWeight = FontWeight.Bold)
                        Text("송신 기기에서 이 기기를 선택하면 수신 요청이 표시됩니다.")
                        if (state == NearbyState.SEARCHING) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }

            incoming?.let { bytes ->
                AlertDialog(
                    onDismissRequest = { incoming = null },
                    title = { Text("Armyrist 데이터 수신") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(incomingFrom.ifBlank { "주변 Armyrist" }, fontWeight = FontWeight.Bold)
                            Text("Armyrist 데이터 ${bytes.size} bytes가 도착했습니다.")
                            Text("데이터를 받으시겠습니까?")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            incoming = null
                            message = "수신을 거절했습니다."
                        }) { Text("거절") }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val file = File(cacheDir, "nearby-received-${System.currentTimeMillis()}.armyrist")
                            file.writeBytes(bytes)
                            incoming = null
                            startActivity(android.content.Intent(this@NearbyTransferActivity, PortableTransferActivity::class.java).apply {
                                putExtra(PortableTransferActivity.EXTRA_MODE, PortableTransferActivity.MODE_IMPORT_BYTES)
                                putExtra(PortableTransferActivity.EXTRA_CACHE_FILE, file.absolutePath)
                            })
                        }) { Text("받기") }
                    }
                )
            }
        }
    }

    private fun startReceiver() {
        io.execute {
            runCatching {
                val socket = ServerSocket(0).also { server = it }
                registerService(socket.localPort)
                while (!socket.isClosed) {
                    val client = socket.accept()
                    client.soTimeout = IO_TIMEOUT_MS
                    io.execute { receive(client) }
                }
            }.onFailure {
                runOnUiThread {
                    state = NearbyState.FAILED
                    message = "수신 대기를 시작할 수 없습니다."
                }
            }
        }
    }

    private fun receive(socket: Socket) {
        socket.use { s ->
            runCatching {
                val input = DataInputStream(s.getInputStream())
                require(input.readUTF() == "ARMYRIST_NEARBY_V1")
                val senderName = input.readUTF().take(80)
                val length = input.readInt()
                require(length in 1..MAX_BYTES)
                val bytes = ByteArray(length)
                input.readFully(bytes)
                runOnUiThread {
                    incomingFrom = senderName.ifBlank { "주변 Armyrist" }
                    incoming = bytes
                    message = "수신 요청이 도착했습니다."
                }
            }.onFailure {
                runOnUiThread { message = "수신 데이터가 올바르지 않습니다." }
            }
        }
    }

    private fun registerService(port: Int) {
        val shortId = UUID.randomUUID().toString().take(4)
        val requestedName = "Armyrist-${Build.MODEL}-$shortId"
        val info = NsdServiceInfo().apply {
            serviceName = requestedName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                registeredServiceName = serviceInfo.serviceName
                runOnUiThread { message = "수신 대기 중 · 주변 기기를 검색하고 있습니다." }
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                runOnUiThread { state = NearbyState.FAILED; message = "주변 전송을 시작하지 못했습니다." }
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun startDiscovery() {
        state = NearbyState.SEARCHING
        message = "주변 Armyrist를 찾는 중…"
        peers.clear()
        scheduleEmptyState()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                if (serviceInfo.serviceName == registeredServiceName) return
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    @Suppress("DEPRECATION")
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        if (resolved.serviceName == registeredServiceName) return
                        val host = resolved.host ?: return
                        val peer = Peer(
                            serviceId = "${resolved.serviceName}:${resolved.port}",
                            displayName = displayName(resolved.serviceName),
                            host = host,
                            port = resolved.port
                        )
                        runOnUiThread {
                            if (peers.none { it.serviceId == peer.serviceId }) peers.add(peer)
                            if (peers.isNotEmpty() && state != NearbyState.SENDING) {
                                state = NearbyState.FOUND
                                message = "주변 기기 ${peers.size}대를 찾았습니다."
                            }
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                runOnUiThread {
                    peers.removeAll { it.serviceId.startsWith("${serviceInfo.serviceName}:") }
                    if (peers.isEmpty() && state == NearbyState.FOUND) {
                        state = NearbyState.EMPTY
                        message = "주변 Armyrist를 찾지 못했습니다."
                    }
                }
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runOnUiThread { state = NearbyState.FAILED; message = "주변 검색을 시작하지 못했습니다." }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun restartDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        startDiscovery()
    }

    private fun scheduleEmptyState() {
        mainHandler.postDelayed({
            if (peers.isEmpty() && state == NearbyState.SEARCHING) {
                state = NearbyState.EMPTY
                message = "주변 Armyrist를 찾지 못했습니다."
            }
        }, SEARCH_EMPTY_DELAY_MS)
    }

    private fun send(peer: Peer, bytes: ByteArray) {
        if (state == NearbyState.SENDING) return
        state = NearbyState.SENDING
        sendingPeerId = peer.serviceId
        message = "${peer.displayName}에 데이터를 보내는 중…"

        io.execute {
            runCatching {
                require(bytes.size in 1..MAX_BYTES)
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = IO_TIMEOUT_MS
                    val out = DataOutputStream(socket.getOutputStream())
                    out.writeUTF("ARMYRIST_NEARBY_V1")
                    out.writeUTF("Armyrist · ${Build.MODEL}")
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                }
            }.onSuccess {
                runOnUiThread {
                    sendingPeerId = null
                    state = NearbyState.SUCCESS
                    message = "전송했습니다."
                }
            }.onFailure {
                runOnUiThread {
                    sendingPeerId = null
                    state = NearbyState.FAILED
                    message = "전송하지 못했습니다. 두 기기의 Wi-Fi 연결 상태를 확인해주세요."
                }
            }
        }
    }

    private fun displayName(serviceName: String): String {
        val raw = serviceName.removePrefix("Armyrist-")
        val suffix = raw.substringAfterLast('-', "")
        return if (suffix.length == 4 && raw.length > 5) raw.dropLast(5) else raw
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        registration?.let { runCatching { nsd.unregisterService(it) } }
        runCatching { server?.close() }
        io.shutdownNow()
        super.onDestroy()
    }
}
