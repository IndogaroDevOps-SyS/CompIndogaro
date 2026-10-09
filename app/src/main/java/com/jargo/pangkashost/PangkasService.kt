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
        wakeLock?.acquire(10 * 60 * 1000L)
        
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
            // Panggil binary langsung dari nativeLibraryDir Android OS (/data/app/.../lib/arm64)
            val nativeDir = applicationInfo.nativeLibraryDir
            val pangkasBin = File(nativeDir, "libpangkas.so")

            if (!pangkasBin.exists()) {
                broadcastLog("[CRITICAL ERROR] Binary libpangkas.so not found in $nativeDir")
                return
            }

            broadcastLog("[NATIVE] Found executable binary in nativeLibraryDir: ${pangkasBin.absolutePath}")
            broadcastLog("[NATIVE] Launching ProcessBuilder via Native Executable Path...")
            
            val pb = ProcessBuilder(pangkasBin.absolutePath, "-mode", mode)
            pb.directory(filesDir)
            
            // Masukkan nativeLibraryDir ke PATH lingkungan eksekusi agar pangkas bisa memanggil libffmpeg.so
            val env = pb.environment()
            env["PATH"] = "$nativeDir:" + (env["PATH"] ?: "")
            env["LD_LIBRARY_PATH"] = "$nativeDir:" + (env["LD_LIBRARY_PATH"] ?: "")

            pb.redirectErrorStream(true)

            val process = pb.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { broadcastLog("[ENGINE] $it") }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                broadcastLog("[SUCCESS] Engine process finished successfully.")
            } else {
                broadcastLog("[ERROR] Engine exited with code: $exitCode")
            }

        } catch (e: Exception) {
            broadcastLog("[CRITICAL ERROR] ${e.localizedMessage}")
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
