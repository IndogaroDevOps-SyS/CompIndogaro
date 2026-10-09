package com.jargo.pangkashost

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class PangkasService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var pangkasProcess: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()
        acquireWakeLock()
        
        Thread {
            extractAssetsIfNeeded()
            runGoBinary()
        }.start()

        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PangkasHost::CPU_KeepAlive"
        ).apply {
            acquire()
        }
    }

    private fun extractAssetsIfNeeded() {
        val binDir = File(filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        listOf("pangkas", "ffmpeg").forEach { fileName ->
            val outFile = File(binDir, fileName)
            if (!outFile.exists() || outFile.length() == 0L) {
                try {
                    assets.open("bin/$fileName").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    outFile.setExecutable(true, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                outFile.setExecutable(true, false)
            }
        }
    }

    private fun runGoBinary() {
        val binDir = File(filesDir, "bin")
        val pangkasBin = File(binDir, "pangkas")

        try {
            val builder = ProcessBuilder(pangkasBin.absolutePath)
            builder.directory(binDir)
            
            val env = builder.environment()
            env["PATH"] = "${binDir.absolutePath}:" + (env["PATH"] ?: "")

            builder.redirectErrorStream(true)
            
            pangkasProcess = builder.start()
            pangkasProcess?.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            stopSelf()
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "pangkas_daemon_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pangkas Engine Daemon",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pangkas Engine Active")
            .setContentText("Memproses antrean video H.265 di latar belakang...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        pangkasProcess?.destroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
