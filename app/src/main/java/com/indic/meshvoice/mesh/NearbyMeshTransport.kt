package com.indic.meshvoice.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.indic.meshvoice.model.MeshNode
import com.indic.meshvoice.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

enum class PeerScanStatus(val label: String, val badge: String) {
    SEARCHING("Scanning for nearby mesh nodes...", "🔍 SCANNING"),
    CONNECTING("Connecting to discovered peer...", "🟡 CONNECTING"),
    CONNECTED("Connected to Mesh", "🟢 CONNECTED"),
    IDLE("Mesh offline", "⚪ IDLE")
}

class NearbyMeshTransport(
    private val context: Context,
    private val myNodeId: String,
    private val myNodeName: String,
    private val onPacketReceived: (rawJson: String, fromEndpoint: String) -> Unit
) {
    private val tag = "NearbyMesh"
    private val serviceId = "indic_mesh_voice_app"
    private val strategy = Strategy.P2P_CLUSTER // M-to-N Mesh topology (Wi-Fi + BLE)

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val endpointNames = mutableMapOf<String, String>()

    private val _connectedEndpoints = MutableStateFlow<Map<String, MeshNode>>(emptyMap())
    val connectedEndpoints: StateFlow<Map<String, MeshNode>> = _connectedEndpoints.asStateFlow()

    private val _scanStatus = MutableStateFlow(PeerScanStatus.IDLE)
    val scanStatus: StateFlow<PeerScanStatus> = _scanStatus.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    fun startMesh() {
        _scanStatus.value = PeerScanStatus.SEARCHING
        startAdvertising()
        startDiscovery()
    }

    fun restartMesh() {
        Log.i(tag, "Restarting mesh discovery and advertising...")
        stopMesh()
        startMesh()
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(strategy)
            .build()

        connectionsClient.startAdvertising(
            "$myNodeName|$myNodeId",
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.i(tag, "Nearby advertising started: $myNodeName|$myNodeId")
            _isAdvertising.value = true
        }.addOnFailureListener { e ->
            Log.e(tag, "Advertising failed: ${e.message}")
            _isAdvertising.value = false
        }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(strategy)
            .build()

        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.i(tag, "Nearby discovery started")
            _isDiscovering.value = true
        }.addOnFailureListener { e ->
            Log.e(tag, "Discovery failed: ${e.message}")
            _isDiscovering.value = false
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i(tag, "Discovered endpoint: $endpointId (${info.endpointName}). Requesting connection...")
            endpointNames[endpointId] = info.endpointName
            _scanStatus.value = PeerScanStatus.CONNECTING
            
            // Automatically request connection without prompting user
            connectionsClient.requestConnection(
                "$myNodeName|$myNodeId",
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener { e ->
                Log.e(tag, "Request connection failed to $endpointId: ${e.message}")
                if (_connectedEndpoints.value.isEmpty()) {
                    _scanStatus.value = PeerScanStatus.SEARCHING
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(tag, "Lost endpoint: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.i(tag, "Connection initiated with $endpointId (${connectionInfo.endpointName}). Auto-accepting...")
            endpointNames[endpointId] = connectionInfo.endpointName
            _scanStatus.value = PeerScanStatus.CONNECTING
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.i(tag, "Successfully connected to endpoint: $endpointId")
                val rawName = endpointNames[endpointId] ?: "Peer-$endpointId"
                val parts = rawName.split("|")
                val nodeName = if (parts.isNotEmpty()) parts[0] else "Peer-$endpointId"
                val nodeId = if (parts.size > 1) parts[1] else endpointId

                val current = _connectedEndpoints.value.toMutableMap()
                current[endpointId] = MeshNode(
                    id = nodeId,
                    name = nodeName,
                    endpointId = endpointId,
                    transportType = TransportType.NEARBY_WIFI,
                    hopsAway = 1
                )
                _connectedEndpoints.value = current
                _scanStatus.value = PeerScanStatus.CONNECTED
            } else {
                Log.w(tag, "Connection to $endpointId failed with status: ${result.status.statusCode}")
                if (_connectedEndpoints.value.isEmpty()) {
                    _scanStatus.value = PeerScanStatus.SEARCHING
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(tag, "Disconnected from endpoint: $endpointId")
            val current = _connectedEndpoints.value.toMutableMap()
            current.remove(endpointId)
            _connectedEndpoints.value = current
            endpointNames.remove(endpointId)

            if (current.isEmpty()) {
                _scanStatus.value = PeerScanStatus.SEARCHING
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val jsonString = String(bytes, StandardCharsets.UTF_8)
                onPacketReceived(jsonString, endpointId)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun broadcastPacket(rawJson: String, excludeEndpoint: String? = null) {
        val bytes = rawJson.toByteArray(StandardCharsets.UTF_8)
        val payload = Payload.fromBytes(bytes)

        val targetEndpoints = _connectedEndpoints.value.keys.filter { it != excludeEndpoint }
        if (targetEndpoints.isNotEmpty()) {
            connectionsClient.sendPayload(targetEndpoints, payload)
        }
    }

    fun stopMesh() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.e(tag, "stopMesh error: ${e.message}")
        }
        _connectedEndpoints.value = emptyMap()
        endpointNames.clear()
        _isAdvertising.value = false
        _isDiscovering.value = false
        _scanStatus.value = PeerScanStatus.IDLE
    }
}
