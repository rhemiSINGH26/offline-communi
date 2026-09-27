package com.indic.meshvoice.mesh

import android.util.Log
import android.util.LruCache
import com.indic.meshvoice.model.MeshMessage

class MeshRouter(private val myNodeId: String) {

    private val tag = "MeshRouter"

    // Cache of processed message IDs to prevent loops and duplicate processing
    private val seenMessageIds = LruCache<String, Long>(500)

    sealed class RouteDecision {
        data class ConsumeOnly(val message: MeshMessage) : RouteDecision()
        data class ConsumeAndRelay(val message: MeshMessage, val nextHopMessage: MeshMessage) : RouteDecision()
        data class RelayOnly(val nextHopMessage: MeshMessage) : RouteDecision()
        object DropDuplicate : RouteDecision()
        object DropMaxHopsExceeded : RouteDecision()
    }

    @Synchronized
    fun routeIncomingPacket(rawJson: String): RouteDecision {
        val message = MeshMessage.fromJson(rawJson) ?: run {
            Log.e(tag, "Failed to parse mesh packet JSON")
            return RouteDecision.DropDuplicate
        }

        // 1. Duplicate check
        if (seenMessageIds.get(message.id) != null) {
            return RouteDecision.DropDuplicate
        }

        // 2. Self-loop check (don't process my own relayed packets)
        if (message.senderId == myNodeId) {
            return RouteDecision.DropDuplicate
        }

        // Mark as seen
        seenMessageIds.put(message.id, System.currentTimeMillis())

        val isForMe = message.isForMe(myNodeId)
        val canRelay = message.canRelay(message.maxHops)

        return when {
            isForMe && !canRelay -> RouteDecision.ConsumeOnly(message)
            isForMe && canRelay -> {
                val nextHop = message.createNextHop(myNodeId)
                RouteDecision.ConsumeAndRelay(message, nextHop)
            }
            !isForMe && canRelay -> {
                val nextHop = message.createNextHop(myNodeId)
                RouteDecision.RelayOnly(nextHop)
            }
            else -> RouteDecision.DropMaxHopsExceeded
        }
    }

    fun markMessageSent(messageId: String) {
        seenMessageIds.put(messageId, System.currentTimeMillis())
    }
}
