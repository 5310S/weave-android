package com.example.weave_andriod

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.tomp2p.dht.PeerBuilderDHT
import net.tomp2p.p2p.PeerBuilder
import net.tomp2p.peers.Number160
import org.ice4j.StunClient
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL

class P2PManager : ViewModel() {
    val messages = mutableStateListOf<String>()
    val publicAddress = mutableStateOf("")
    val publicPort = mutableStateOf(9999)
    var connectionStatus = mutableStateOf("Disconnected")
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var peer: net.tomp2p.dht.PeerDHT? = null
    val nodeID: ULong
        get() = peer?.peer()?.peerID()?.toString()?.toULong() ?: 0UL

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val randomID = Number160.createHash(System.currentTimeMillis().toString())
                peer = PeerBuilderDHT(PeerBuilder(randomID).ports(9999).start()).start()
                Log.d("P2PManager", "Kademlia DHT started on port 9999")
            } catch (e: Exception) {
                Log.e("P2PManager", "Kademlia failed to start: ${e.message}")
            }
        }
    }

    fun startListening(port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                connectionStatus.value = "Listening"
                Log.d("P2PManager", "Listening started on port $port")
                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d("P2PManager", "Accepted new connection")
                    handleConnection(socket)
                }
            } catch (e: Exception) {
                Log.e("P2PManager", "Listener failed: ${e.message}")
                connectionStatus.value = "Failed to start: ${e.message}"
            }
        }
    }

    fun fetchPublicIP() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("P2PManager", "Fetching public IP and port with STUN")
            val servers = listOf(
                "stun.l.google.com:19302",
                "stun.ekiga.net:3478",
                "stun.voipbuster.com:3478",
                "stun.sipgate.net:3478",
                "stun.nextcloud.com:3478"
            )
            for (server in servers) {
                Log.d("P2PManager", "Trying STUN server: $server")
                try {
                    val (host, port) = server.split(":")
                    val stunClient = StunClient(InetSocketAddress(host, port.toInt()))
                    val result = stunClient.getMappedAddress()
                    if (result != null) {
                        publicAddress.value = result.address.hostAddress
                        publicPort.value = result.port
                        Log.d("P2PManager", "STUN public IP: ${publicAddress.value}, port: ${publicPort.value}")
                        storePublicAddress()
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("P2PManager", "STUN error on $server: ${e.message}")
                }
            }
            Log.d("P2PManager", "All STUN servers failed, falling back to HTTP")
            try {
                val ip = URL("https://api.ipify.org").readText()
                publicAddress.value = ip.trim()
                publicPort.value = 9999
                Log.d("P2PManager", "HTTP public IP: ${publicAddress.value}, port: 9999")
                storePublicAddress()
            } catch (e: Exception) {
                Log.e("P2PManager", "HTTP IP fetch error: ${e.message}")
                connectionStatus.value = "HTTP IP fetch failed: ${e.message}"
            }
        }
    }

    fun joinNetwork(bootstrapHost: String, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                peer?.peer()?.bootstrap()?.bootstrapTo(InetSocketAddress(bootstrapHost, port))?.start()?.await()
                Log.d("P2PManager", "Joined DHT network via $bootstrapHost:$port")
            } catch (e: Exception) {
                Log.e("P2PManager", "Join failed: ${e.message}")
            }
        }
    }

    fun connectToPeer(peerID: ULong) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val peerData = peer?.get(Number160(peerID.toString()))?.get()?.data()?.toString(Charsets.UTF_8)
                if (peerData != null) {
                    val peerInfo = Json.decodeFromString<Peer>(peerData)
                    Log.d("P2PManager", "Found peer: ${peerInfo.host}:${peerInfo.port}")
                    connect(peerInfo.host, peerInfo.port)
                } else {
                    connectionStatus.value = "Peer not found"
                }
            } catch (e: Exception) {
                Log.e("P2PManager", "Peer lookup failed: ${e.message}")
                connectionStatus.value = "Peer lookup failed: ${e.message}"
            }
        }
    }

    private fun connect(host: String, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                clientSocket = Socket(host, port)
                connectionStatus.value = "Connected"
                Log.d("P2PManager", "Connection started to $host:$port")
                handleConnection(clientSocket!!)
            } catch (e: Exception) {
                Log.e("P2PManager", "Connection failed: ${e.message}")
                connectionStatus.value = "Connection failed: ${e.message}"
            }
        }
    }

    fun send(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                clientSocket?.getOutputStream()?.let {
                    DataOutputStream(it).writeUTF(text)
                    Log.d("P2PManager", "Sent message: $text")
                } ?: run {
                    Log.e("P2PManager", "No active connection")
                    connectionStatus.value = "No active connection"
                }
            }ideos {
                Log.e("P2PManager", "Send error: ${e.message}")
                connectionStatus.value = "Send failed: ${e.message}"
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val input = DataInputStream(socket.getInputStream())
                while (true) {
                    val text = input.readUTF()
                    messages.add(text)
                    Log.d("P2PManager", "Received message: $text")
                }
            } catch (e: Exception) {
                Log.e("P2PManager", "Receive error: ${e.message}")
                connectionStatus.value = "Receive error: ${e.message}"
                socket.close()
            }
        }
    }

    fun storePublicAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            if (publicAddress.value.isNotEmpty()) {
                val peerInfo = Peer(nodeID, publicAddress.value, publicPort.value)
                val data = Json.encodeToString(Peer.serializer(), peerInfo)
                peer?.put(Number160(nodeID.toString()))?.data(data.toByteArray())?.start()?.await()
                Log.d("P2PManager", "Stored public address in DHT: ${publicAddress.value}:${publicPort.value} for ID $nodeID")
            }
        }
    }

    override fun onCleared() {
        serverSocket?.close()
        clientSocket?.close()
        peer?.peer()?.shutdown()?.await()
        super.onCleared()
    }
}

@Serializable
data class Peer(val id: ULong, val host: String, val port: Int)