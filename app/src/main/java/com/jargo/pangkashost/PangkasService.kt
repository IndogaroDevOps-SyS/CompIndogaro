package com.jargo.pangkashost

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class PangkasService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var daemonProcess: Process? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PangkasHost::DaemonLock")
        wakeLock?.acquire(10 * 60 * 1000L)
        
        broadcastLog("[INIT] Memulai daemon system...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("MODE") ?: "pangkas"
        val filePath = intent?.getStringExtra("FILE_PATH") ?: ""
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, "PangkasDaemonChannel")
            .setContentTitle("Pangkas Host Engine Active")
            .setContentText("Daemon aktif dalam mode: $mode")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        startForeground(101, notification)

        serviceScope.launch(Dispatchers.IO) {
            runDaemonSupervisor(mode, filePath)
        }

        return START_STICKY
    }

    private fun runDaemonSupervisor(mode: String, filePath: String) {
        try {
            val manager = DaemonManager(this)
            manager.prepareEnvironment()

            val argsList = mutableListOf<String>()

            when (mode) {
                "penjernihan" -> {
                    argsList.add("-d")
                    if (filePath.isNotEmpty()) argsList.add(filePath)
                }
                "direct" -> {
                    if (filePath.isNotEmpty()) argsList.add(filePath)
                }
                else -> {
                    // Default Watch Mode / Daemon Global (-O)
                    argsList.add("-O")
                }
            }

            broadcastLog("[INIT] Mengeksekusi daemon 'pangkas' dengan argumen: ${argsList.joinToString(" ")}...")
            
            daemonProcess = manager.startDaemon("pangkas", { log ->
                broadcastLog("[GO-DAEMON] $log")
            }, *argsList.toTypedArray())

            val exitCode = daemonProcess?.waitFor() ?: -1
            if (exitCode == 0) {
                broadcastLog("[SUCCESS] Proses daemon selesai dengan sukses.")
            } else {
                broadcastLog("[ERROR] Daemon keluar dengan exit code: $exitCode")
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
        daemonProcess?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                destroyForcibly()
            } else {
                destroy()
            }
        }
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
            manager?.createNotificationChannel(channel)
        }
    }
}
