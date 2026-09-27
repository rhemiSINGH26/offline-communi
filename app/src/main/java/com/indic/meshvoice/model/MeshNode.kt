package com.indic.meshvoice.model

enum class TransportType {
    NEARBY_WIFI,
    BLUETOOTH_LE,
    DIRECT_SOCKET
}

data class MeshNode(
    val id: String,
    val name: String,
    val endpointId: String = "",
    val transportType: TransportType = TransportType.NEARBY_WIFI,
    val hopsAway: Int = 1,
    val rssi: Int = -60, // dBm
    val isConnected: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)
