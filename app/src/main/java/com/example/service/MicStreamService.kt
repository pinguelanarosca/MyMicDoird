package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioStreamManager
import com.example.data.PreferencesManager
import com.example.model.StreamConfig
import com.example.model.StreamMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MicStreamService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioStreamManager = AudioStreamManager(applicationContext)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DroidMic::StreamingWakeLock")
        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours max safety

        createNotificationChannel()

        serviceScope.launch {
            audioStreamManager?.metrics?.collect { metrics ->
                updateNotification(metrics)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> {
                val prefs = PreferencesManager(applicationContext)
                val config = prefs.loadConfig()
                startForegroundWithNotification()
                audioStreamManager?.start(config, serviceScope)
            }
            ACTION_STOP -> {
                stopStreamingAndService()
            }
            ACTION_TOGGLE_MUTE -> {
                val currentMute = audioStreamManager?.isCurrentlyMuted() ?: false
                audioStreamManager?.setMuted(!currentMute)
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Iniciando microfone...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(metrics: StreamMetrics) {
        val text = when {
            metrics.isMuted -> "Microfone MUTADO"
            metrics.isConnected -> "Transmitindo para Ubuntu (${formatBytes(metrics.bytesSent)})"
            metrics.isListening -> "Aguardando conexão via ADB..."
            metrics.errorMessage != null -> "Erro: ${metrics.errorMessage}"
            else -> "Pronto"
        }

        val notification = buildNotification(text)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isMuted = audioStreamManager?.isCurrentlyMuted() ?: false
        val muteIntent = Intent(this, MicStreamService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this,
            1,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MicStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DroidMic: Microfone Ativo")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(
                0,
                if (isMuted) "Desmutar" else "Mutar",
                mutePendingIntent
            )
            .addAction(0, "Parar", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_streaming_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_streaming_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopStreamingAndService() {
        audioStreamManager?.stop()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreamingAndService()
        serviceScope.cancel()
        instance = null
        audioStreamManager = null
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    companion object {
        const val CHANNEL_ID = "droidmic_stream_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_TOGGLE_MUTE = "com.example.ACTION_TOGGLE_MUTE"

        @SuppressLint("StaticFieldLeak")
        private var instance: MicStreamService? = null
        private var audioStreamManager: AudioStreamManager? = null

        fun getMetricsFlow(): StateFlow<StreamMetrics>? {
            return audioStreamManager?.metrics
        }

        fun getAudioStreamManager(): AudioStreamManager? = audioStreamManager

        fun isServiceRunning(): Boolean = instance != null

        fun startStreaming(context: Context) {
            val intent = Intent(context, MicStreamService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopStreaming(context: Context) {
            val intent = Intent(context, MicStreamService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun toggleMute(context: Context) {
            val intent = Intent(context, MicStreamService::class.java).apply {
                action = ACTION_TOGGLE_MUTE
            }
            context.startService(intent)
        }
    }
}
