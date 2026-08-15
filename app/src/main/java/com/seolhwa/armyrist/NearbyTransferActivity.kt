package com.seolhwa.armyrist

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
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
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Minor Patch B Nearby Direct Transfer PoC.
 *
 * Transport: Android NSD (DNS-SD) discovery + a local TCP socket on the same
 * Wi-Fi/LAN. No Armyrist server/account/cloud is involved. Internet access is
 * not required, but both devices must currently share a local IP network.
 * Portable bytes are reused unchanged and are only handed to the existing
 * validation/preview/import flow after the receiver explicitly accepts them.
 */
class NearbyTransferActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SEND_FILE = "sendFile"
        private const val SERVICE_TYPE = "_armyrist._tcp."
        private const val MAX_BYTES = 32 * 1024 * 1024
    }

    data class Peer(val name: String, val host: InetAddress, val port: Int)

    private val io = Executors.newCachedThreadPool()
    private lateinit var nsd: NsdManager
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    private val peers = mutableStateListOf<Peer>()
    private var status by mutableStateOf("주변 Armyrist를 찾는 중입니다.")
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
            Scaffold(topBar = {
                ArmyristTopBar(
                    title = "주변 Armyrist",
                    subtitle = "근거리 직접전송 · PoC",
                    leadingLabel = "뒤로",
                    onLeading = { finish() }
                )
            }) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArmyristPanel(Modifier.fillMaxWidth()) {
                        Text("같은 Wi-Fi 또는 로컬 네트워크의 Armyrist를 찾습니다.", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("인터넷 서버·계정은 사용하지 않습니다. 두 기기 모두 이 화면을 열어두세요.")
                        Spacer(Modifier.height(8.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }

                    if (sendFile != null) {
                        Text("주변 기기", fontWeight = FontWeight.Bold)
                        if (peers.isEmpty()) Text("아직 발견된 기기가 없습니다.")
                        peers.forEach { peer ->
                            ArmyristPanel(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(peer.name, fontWeight = FontWeight.SemiBold)
                                        Text("${peer.host.hostAddress}:${peer.port}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(onClick = {
                                        val bytes = runCatching { sendFile.readBytes() }.getOrNull()
                                        if (bytes != null) send(peer, bytes)
                                    }, shape = ArmyristPanelShape) { Text("보내기") }
                                }
                            }
                        }
                    } else {
                        Text("수신 대기 중", fontWeight = FontWeight.Bold)
                        Text("송신 기기에서 이 기기를 선택하면 수신 요청이 표시됩니다.")
                    }
                }
            }

            incoming?.let { bytes ->
                AlertDialog(
                    onDismissRequest = { incoming = null },
                    title = { Text("Armyrist 데이터 수신") },
                    text = { Text("${incomingFrom.ifBlank { "주변 기기" }}에서 ${bytes.size} bytes의 데이터를 받았습니다. 내용을 확인하시겠습니까?") },
                    dismissButton = { TextButton(onClick = { incoming = null }) { Text("거절") } },
                    confirmButton = { Button(onClick = {
                        val file = File(cacheDir, "nearby-received-${System.currentTimeMillis()}.armyrist")
                        file.writeBytes(bytes)
                        incoming = null
                        startActivity(android.content.Intent(this@NearbyTransferActivity, PortableTransferActivity::class.java).apply {
                            putExtra(PortableTransferActivity.EXTRA_MODE, PortableTransferActivity.MODE_IMPORT_BYTES)
                            putExtra(PortableTransferActivity.EXTRA_CACHE_FILE, file.absolutePath)
                        })
                    }) { Text("받기") } }
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
                    io.execute { receive(client) }
                }
            }.onFailure { runOnUiThread { status = "수신 대기를 시작할 수 없습니다: ${it.javaClass.simpleName}" } }
        }
    }

    private fun receive(socket: Socket) {
        socket.use { s ->
            runCatching {
                val input = DataInputStream(s.getInputStream())
                val magic = input.readUTF()
                require(magic == "ARMYRIST_NEARBY_V1")
                val length = input.readInt()
                require(length in 1..MAX_BYTES)
                val bytes = ByteArray(length)
                input.readFully(bytes)
                runOnUiThread {
                    incomingFrom = s.inetAddress.hostAddress ?: "주변 기기"
                    incoming = bytes
                    status = "수신 요청이 도착했습니다."
                }
            }.onFailure { runOnUiThread { status = "수신 데이터가 올바르지 않습니다." } }
        }
    }

    private fun registerService(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "Armyrist-${android.os.Build.MODEL}-${UUID.randomUUID().toString().take(4)}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) { runOnUiThread { status = "수신 대기 중 · 주변 기기를 검색하고 있습니다." } }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { runOnUiThread { status = "주변 전송 등록 실패 ($errorCode)" } }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    @Suppress("DEPRECATION")
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host ?: return
                        val peer = Peer(resolved.serviceName, host, resolved.port)
                        runOnUiThread {
                            if (peers.none { it.host == peer.host && it.port == peer.port }) peers.add(peer)
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) { runOnUiThread { peers.removeAll { it.name == serviceInfo.serviceName } } }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { runOnUiThread { status = "주변 검색 시작 실패 ($errorCode)" } }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun send(peer: Peer, bytes: ByteArray) {
        status = "${peer.name}에 전송 중..."
        io.execute {
            runCatching {
                require(bytes.size <= MAX_BYTES)
                Socket(peer.host, peer.port).use { socket ->
                    val out = DataOutputStream(socket.getOutputStream())
                    out.writeUTF("ARMYRIST_NEARBY_V1")
                    out.writeInt(bytes.size)
                    out.write(bytes)
                    out.flush()
                }
            }.onSuccess { runOnUiThread { status = "전송 완료 · 수신 기기에서 확인하세요." } }
                .onFailure { runOnUiThread { status = "전송 실패: ${it.javaClass.simpleName}" } }
        }
    }

    override fun onDestroy() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        registration?.let { runCatching { nsd.unregisterService(it) } }
        runCatching { server?.close() }
        io.shutdownNow()
        super.onDestroy()
    }
}
