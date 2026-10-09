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
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class PangkasService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private val GO_BINARY_URL = "https://github.com/IndogaroDevOps-SyS/CompIndogaro/raw/main/bin/pangkas"
    private val FFMPEG_BINARY_URL = "https://github.com/IndogaroDevOps-SyS/CompIndogaro/raw/main/bin/ffmpeg"

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PangkasHost::DaemonLock")
        wakeLock?.acquire(10 * 60 * 1000L)
        
        broadcastLog("[INIT] Memulai daemon system...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra("MODE") ?: "pangkas"
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, "PangkasDaemonChannel")
            .setContentTitle("Pangkas Host Engine Active")
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

    private suspend fun executeEngine(mode: String) {
        try {
            // PAKSA HARDCODE absolute path /data/data/com.jargo.pangkashost/files/home
            val homeDir = File("/data/data/com.jargo.pangkashost/files/home")
            if (!homeDir.exists()) {
                homeDir.mkdirs()
            }

            val pangkasBin = File(homeDir, "Golangbin")
            val ffmpegBin = File(homeDir, "ffmpeg")

            if (!pangkasBin.exists() || pangkasBin.length() == 0L) {
                broadcastLog("[DOWNLOAD] Downloading Go Engine binary from repo...")
                if (!downloadFile(GO_BINARY_URL, pangkasBin, "Golangbin")) {
                    broadcastLog("[CRITICAL ERROR] Gagal mengunduh Go Engine dari repo!")
                    return
                }
            }

            if (!ffmpegBin.exists() || ffmpegBin.length() == 0L) {
                broadcastLog("[DOWNLOAD] Downloading FFmpeg binary from repo...")
                if (!downloadFile(FFMPEG_BINARY_URL, ffmpegBin, "ffmpeg")) {
                    broadcastLog("[WARN] FFmpeg tidak diunduh atau opsional.")
                }
            }

            broadcastLog("[INIT] Binary path: ${pangkasBin.absolutePath}")

            // CHMOD 755 langsung di /data/data/com.jargo.pangkashost/files/home/
            val p1 = Runtime.getRuntime().exec(arrayOf("chmod", "755", pangkasBin.absolutePath))
            p1.waitFor()

            val p2 = Runtime.getRuntime().exec(arrayOf("chmod", "755", ffmpegBin.absolutePath))
            p2.waitFor()

            pangkasBin.setExecutable(true, false)
            ffmpegBin.setExecutable(true, false)

            broadcastLog("[INIT] Menjalankan Golangbin secara native...")

            val pb = ProcessBuilder(pangkasBin.absolutePath, "-mode", mode)
            pb.directory(homeDir)

            val env = pb.environment()
            env["PATH"] = "${homeDir.absolutePath}:" + (env["PATH"] ?: "")
            env["HOME"] = homeDir.absolutePath

            pb.redirectErrorStream(true)

            val process = pb.start()
            broadcastLog("[SUCCESS] Golangbin daemon aktif & terhubung!")

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { broadcastLog("[GO-DAEMON] $it") }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                broadcastLog("[SUCCESS] Proses daemon selesai dengan sukses.")
            } else {
                broadcastLog("[ERROR] Daemon keluar dengan exit code: $exitCode")
            }

        } catch (e: Exception) {
            broadcastLog("[CRITICAL ERROR] ${e.localizedMessage}")
        }
    }

    private fun downloadFile(urlString: String, outputFile: File, binaryName: String): Boolean {
        var currentUrl = urlString
        var connection: HttpURLConnection? = null
        try {
            var redirects = 0
            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                connection.connect()

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP || status == 307 || status == 308) {
                    currentUrl = connection.getHeaderField("Location")
                    redirects++
                    connection.disconnect()
                    continue
                }

                if (status != HttpURLConnection.HTTP_OK) {
                    broadcastLog("[DOWNLOAD ERROR] Server returned HTTP $status")
                    return false
                }
                break
            }

            val fileLength = connection!!.contentLength
            val input = connection.inputStream
            val output = FileOutputStream(outputFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            var lastProgress = -1

            while (input.read(data).also { count = it } != null) {
                total += count.toLong()
                if (fileLength > 0) {
                    val progress = (total * 100 / fileLength).toInt()
                    if (progress % 10 == 0 && progress != lastProgress) {
                        broadcastLog("[DOWNLOAD] $binaryName: $progress%")
                        lastProgress = progress
                    }
                }
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()
            broadcastLog("[DOWNLOAD SUCCESS] $binaryName berhasil diunduh.")
            return true
        } catch (e: Exception) {
            broadcastLog("[DOWNLOAD ERROR] Exception: ${e.localizedMessage}")
            return false
        } finally {
            connection?.disconnect()
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
