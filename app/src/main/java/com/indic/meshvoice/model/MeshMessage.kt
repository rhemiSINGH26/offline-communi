package com.indic.meshvoice.model

import com.google.gson.Gson
import java.util.UUID

enum class MessageType {
    VOICE_TRANSCRIPT,
    VOICE_AUDIO_SNIPPET,
    PING,
    ACK
}

data class MeshMessage(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val senderId: String,
    val senderName: String,
    val targetId: String = BROADCAST_TARGET, // "ALL" or specific Node ID
    val text: String,
    val languageCode: String = "hi",
    val audioBase64: String? = null,         // Compressed 16kHz voice audio snippet
    val timestamp: Long = System.currentTimeMillis(),
    val hopCount: Int = 0,
    val maxHops: Int = 5,
    val routeTrace: List<String> = listOf(senderId),
    val messageType: MessageType = MessageType.VOICE_TRANSCRIPT
) {
    companion object {
        const val BROADCAST_TARGET = "ALL"
        private val gson = Gson()

        fun fromJson(json: String): MeshMessage? {
            return try {
                gson.fromJson(json, MeshMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        return gson.toJson(this)
    }

    fun createNextHop(currentNodeId: String): MeshMessage {
        val updatedTrace = ArrayList(routeTrace)
        if (!updatedTrace.contains(currentNodeId)) {
            updatedTrace.add(currentNodeId)
        }
        return this.copy(
            hopCount = this.hopCount + 1,
            routeTrace = updatedTrace
        )
    }

    fun isForMe(myNodeId: String): Boolean {
        return targetId == BROADCAST_TARGET || targetId == myNodeId
    }

    fun canRelay(maxAllowedHops: Int = 5): Boolean {
        return hopCount < maxAllowedHops
    }
}
