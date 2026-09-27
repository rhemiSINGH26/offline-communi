package com.indic.meshvoice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.indic.meshvoice.R
import com.indic.meshvoice.mesh.MeshEngine

class MeshForegroundService : Service() {

    private val channelId = "indic_mesh_service_channel"
    private val notificationId = 1001

    private val binder = LocalBinder()
    var meshEngine: MeshEngine? = null
        private set

    inner class LocalBinder : Binder() {
        fun getService(): MeshForegroundService = this@MeshForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, buildNotification("Active • Mesh Multi-Hop Online"))

        meshEngine = MeshEngine(applicationContext)
        meshEngine?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "IndicMesh Background Relaying",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the offline mesh network active for multi-hop voice relaying"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("IndicMesh Voice Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        meshEngine?.stop()
        super.onDestroy()
    }
}
