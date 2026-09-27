package com.indic.meshvoice.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.indic.meshvoice.model.MeshNode
import com.indic.meshvoice.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleMeshTransport(
    private val context: Context,
    private val myNodeId: String,
    private val onPacketReceived: (rawJson: String, fromAddress: String) -> Unit
) {
    private val tag = "BleMeshTransport"
    private val serviceUuid = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null

    private val _discoveredBleNodes = MutableStateFlow<Map<String, MeshNode>>(emptyMap())
    val discoveredBleNodes: StateFlow<Map<String, MeshNode>> = _discoveredBleNodes.asStateFlow()

    private val _isBleActive = MutableStateFlow(false)
    val isBleActive: StateFlow<Boolean> = _isBleActive.asStateFlow()

    fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(tag, "Bluetooth is not enabled")
            return
        }

        bleAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bleScanner = bluetoothAdapter.bluetoothLeScanner

        startAdvertising()
        startScanning()
        _isBleActive.value = true
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()

        try {
            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(tag, "BLE Advertise permission missing: ${e.message}")
        }
    }

    private fun startScanning() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e(tag, "BLE Scan permission missing: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(tag, "BLE Mesh advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(tag, "BLE Mesh advertising failed with error code: $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { scanResult ->
                val address = scanResult.device.address
                val rssi = scanResult.rssi
                val current = _discoveredBleNodes.value.toMutableMap()
                current[address] = MeshNode(
                    id = address,
                    name = "BLE-$address",
                    endpointId = address,
                    transportType = TransportType.BLUETOOTH_LE,
                    rssi = rssi,
                    hopsAway = 1
                )
                _discoveredBleNodes.value = current
            }
        }
    }

    fun stop() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(tag, "Error stopping BLE: ${e.message}")
        }
        _isBleActive.value = false
    }
}
