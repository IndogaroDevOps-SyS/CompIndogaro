package com.jargo.pangkashost

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class PangkasService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PangkasHost::DaemonLock")
        wakeLock?.acquire(10 * 60 * 1000L) // 10 min lock
        
        broadcastLog("[SERVICE] Foreground Service & WakeLock Initialized.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("MODE") ?: "pangkas"
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, "PangkasDaemonChannel")
            .setContentTitle("Pangkas Host Engine Running")
            .setContentText("Daemon aktif dalam mode: $mode")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(101, notification)

        serviceScope.launch {
            executeEngine(mode)
        }

        return START_STICKY
    }

    private fun executeEngine(mode: String) {
        try {
            broadcastLog("[NATIVE] Extracting binaries from assets...")
            val binDir = File(filesDir, "bin")
            if (!binDir.exists()) binDir.mkdirs()

            val pangkasBin = File(binDir, "pangkas")
            val ffmpegBin = File(binDir, "ffmpeg")

            if (!pangkasBin.exists()) copyAssetToFile("bin/pangkas", pangkasBin)
            if (!ffmpegBin.exists()) copyAssetToFile("bin/ffmpeg", ffmpegBin)

            pangkasBin.setExecutable(true)
            ffmpegBin.setExecutable(true)

            broadcastLog("[NATIVE] Binaries ready. Launching ProcessBuilder...")
            
            val pb = ProcessBuilder(pangkasBin.absolutePath, "-mode", mode)
            pb.directory(filesDir)
            pb.redirectErrorStream(true)

            val process = pb.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { broadcastLog("[ENGINE] $it") }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                broadcastLog("[SUCCESS] Engine finished process successfully.")
            } else {
                broadcastLog("[ERROR] Engine exited with code: $exitCode")
            }

        } catch (e: Exception) {
            broadcastLog("[CRITICAL ERROR] ${e.localizedMessage}")
        }
    }

    private fun copyAssetToFile(assetPath: String, outFile: File) {
        assets.open(assetPath).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun broadcastLog(msg: String) {
        val intent = Intent("com.jargo.pangkashost.LOG_EVENT").apply {
            putExtra("LOG_MESSAGE", msg)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        broadcastLog("[SERVICE] Foreground Service Stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "PangkasDaemonChannel",
                "Pangkas Daemon Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
