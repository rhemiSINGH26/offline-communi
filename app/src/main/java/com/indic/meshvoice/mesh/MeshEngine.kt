package com.indic.meshvoice.mesh

import android.content.Context
import android.os.Build
import android.util.Log
import com.indic.meshvoice.AppLogger
import com.indic.meshvoice.model.MeshMessage
import com.indic.meshvoice.model.MeshNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

class MeshEngine(
    private val context: Context,
    val myNodeId: String = "NODE-" + UUID.randomUUID().toString().substring(0, 4).uppercase(),
    val myNodeName: String = Build.MODEL
) {
    private val tag = "MeshEngine"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val router = MeshRouter(myNodeId)

    private val _messages = MutableStateFlow<List<MeshMessage>>(emptyList())
    val messages: StateFlow<List<MeshMessage>> = _messages.asStateFlow()

    private val _activeNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val activeNodes: StateFlow<List<MeshNode>> = _activeNodes.asStateFlow()

    private val _packetStats = MutableStateFlow(PacketStats())
    val packetStats: StateFlow<PacketStats> = _packetStats.asStateFlow()

    var onSpeechMessageReceived: ((MeshMessage) -> Unit)? = null

    data class PacketStats(
        val totalSent: Int = 0,
        val totalReceived: Int = 0,
        val totalRelayed: Int = 0
    )

    // Dual-Stack Mesh Transports
    private val localSocketTransport = LocalSocketMeshTransport(
        context = context,
        myNodeId = myNodeId,
        myNodeName = myNodeName,
        onPacketReceived = { rawJson, fromEndpoint ->
            handleIncomingPacket(rawJson, fromEndpoint)
        }
    )

    private val nearbyTransport = NearbyMeshTransport(
        context = context,
        myNodeId = myNodeId,
        myNodeName = myNodeName,
        onPacketReceived = { rawJson, fromEndpoint ->
            handleIncomingPacket(rawJson, fromEndpoint)
        }
    )

    val scanStatus: StateFlow<PeerScanStatus> = nearbyTransport.scanStatus

    fun start() {
        AppLogger.log(tag, "Starting Dual-Stack Mesh Engine for Node: $myNodeId ($myNodeName)")
        localSocketTransport.start()
        nearbyTransport.startMesh()

        // Combine peer lists from both transports
        scope.launch {
            combine(
                localSocketTransport.connectedEndpoints,
                nearbyTransport.connectedEndpoints
            ) { localMap, nearbyMap ->
                val combined = mutableMapOf<String, MeshNode>()
                localMap.forEach { (k, v) -> combined[k] = v }
                nearbyMap.forEach { (k, v) -> combined[v.id] = v }
                combined.values.toList()
            }.collect { mergedNodes ->
                _activeNodes.value = mergedNodes
            }
        }
    }

    fun restartMesh() {
        AppLogger.log(tag, "Restarting mesh discovery and transports...")
        localSocketTransport.stop()
        localSocketTransport.start()
        nearbyTransport.restartMesh()
    }

    private fun handleIncomingPacket(rawJson: String, fromEndpoint: String) {
        val decision = router.routeIncomingPacket(rawJson)

        when (decision) {
            is MeshRouter.RouteDecision.ConsumeOnly -> {
                AppLogger.log(tag, "Consumed message: ${decision.message.id} (Sender: ${decision.message.senderName}, Hops: ${decision.message.hopCount})")
                deliverMessage(decision.message)
                updateStats(received = 1)
            }
            is MeshRouter.RouteDecision.ConsumeAndRelay -> {
                AppLogger.log(tag, "Consumed & Relaying: ${decision.message.id} -> Next Hop: ${decision.nextHopMessage.hopCount}")
                deliverMessage(decision.message)
                broadcastAll(decision.nextHopMessage.toJson(), excludeNearby = fromEndpoint)
                updateStats(received = 1, relayed = 1)
            }
            is MeshRouter.RouteDecision.RelayOnly -> {
                AppLogger.log(tag, "Relaying multi-hop: ${decision.nextHopMessage.id}")
                broadcastAll(decision.nextHopMessage.toJson(), excludeNearby = fromEndpoint)
                updateStats(relayed = 1)
            }
            is MeshRouter.RouteDecision.DropDuplicate -> {}
            is MeshRouter.RouteDecision.DropMaxHopsExceeded -> {
                Log.w(tag, "Dropped packet: Max hops exceeded")
            }
        }
    }

    private fun broadcastAll(rawJson: String, excludeNearby: String? = null) {
        localSocketTransport.broadcastPacket(rawJson)
        nearbyTransport.broadcastPacket(rawJson, excludeEndpoint = excludeNearby)
    }

    private fun deliverMessage(message: MeshMessage) {
        scope.launch(Dispatchers.Main) {
            val list = _messages.value.toMutableList()
            list.add(0, message)
            _messages.value = list
            onSpeechMessageReceived?.invoke(message)
        }
    }

    fun sendVoiceTranscript(
        text: String,
        targetId: String = MeshMessage.BROADCAST_TARGET,
        langCode: String = "hi"
    ): MeshMessage {
        val message = MeshMessage(
            senderId = myNodeId,
            senderName = myNodeName,
            targetId = targetId,
            text = text,
            languageCode = langCode,
            audioBase64 = null,
            hopCount = 0,
            routeTrace = listOf(myNodeId)
        )

        router.markMessageSent(message.id)
        broadcastAll(message.toJson())

        scope.launch(Dispatchers.Main) {
            val list = _messages.value.toMutableList()
            list.add(0, message)
            _messages.value = list
        }

        updateStats(sent = 1)
        return message
    }

    private fun updateStats(sent: Int = 0, received: Int = 0, relayed: Int = 0) {
        val current = _packetStats.value
        _packetStats.value = current.copy(
            totalSent = current.totalSent + sent,
            totalReceived = current.totalReceived + received,
            totalRelayed = current.totalRelayed + relayed
        )
    }

    fun stop() {
        localSocketTransport.stop()
        nearbyTransport.stopMesh()
    }
}
