package com.indic.meshvoice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.indic.meshvoice.mesh.MeshEngine
import com.indic.meshvoice.service.MeshForegroundService
import com.indic.meshvoice.speech.OfflineSpeechRecognizer
import com.indic.meshvoice.speech.OfflineTextToSpeech
import com.indic.meshvoice.speech.OfflineVoiceAudioEngine
import com.indic.meshvoice.ui.MainScreen
import com.indic.meshvoice.ui.theme.IndicMeshVoiceTheme

class MainActivity : ComponentActivity() {

    private lateinit var meshEngine: MeshEngine
    private lateinit var speechRecognizer: OfflineSpeechRecognizer
    private lateinit var textToSpeech: OfflineTextToSpeech
    private lateinit var voiceAudioEngine: OfflineVoiceAudioEngine

    private var meshService: MeshForegroundService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MeshForegroundService.LocalBinder
            meshService = binder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            meshService = null
            isServiceBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkAndEnableRadios()
        } else {
            Toast.makeText(
                this,
                "Microphone, Bluetooth, and Nearby permissions are required for offline mesh voice",
                Toast.LENGTH_LONG
            ).show()
            checkAndEnableRadios()
        }
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkWifiState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        meshEngine = MeshEngine(this)
        speechRecognizer = OfflineSpeechRecognizer(this).apply { init() }
        textToSpeech = OfflineTextToSpeech(this).apply { init() }
        voiceAudioEngine = OfflineVoiceAudioEngine(this)

        checkAndRequestPermissions()

        setContent {
            IndicMeshVoiceTheme {
                MainScreen(
                    meshEngine = meshEngine,
                    speechRecognizer = speechRecognizer,
                    textToSpeech = textToSpeech,
                    voiceAudioEngine = voiceAudioEngine
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isEmpty()) {
            checkAndEnableRadios()
        } else {
            requestPermissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    private fun checkAndEnableRadios() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter

        if (btAdapter != null && !btAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                enableBtLauncher.launch(enableBtIntent)
            } catch (e: Exception) {
                checkWifiState()
            }
        } else {
            checkWifiState()
        }
    }

    private fun checkWifiState() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager != null && !wifiManager.isWifiEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                try {
                    startActivity(panelIntent)
                } catch (e: Exception) {
                    val wifiSettingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    startActivity(wifiSettingsIntent)
                }
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
            }
        }

        startMeshNetwork()
    }

    private fun startMeshNetwork() {
        meshEngine.start()

        val serviceIntent = Intent(this, MeshForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        textToSpeech.destroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        meshEngine.stop()
    }
}
