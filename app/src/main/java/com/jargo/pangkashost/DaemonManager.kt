package com.jargo.pangkashost

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class DaemonManager(private val context: Context) {

    private val workDir: File = context.filesDir
    // Mengambil direktori resmi native library yang diizinkan SELinux untuk eksekusi
    private val nativeLibDir: String = context.applicationInfo.nativeLibraryDir

    fun prepareEnvironment() {
        Log.i(TAG, "Native library directory: $nativeLibDir")
    }

    fun startDaemon(binaryName: String, onLog: (String) -> Unit, vararg args: String): Process {
        // Jika dipanggil "pangkas", ubah menjadi "libpangkas.so"
        val realBinaryName = if (!binaryName.startsWith("lib")) "lib$binaryName.so" else binaryName
        val binaryFile = File(nativeLibDir, realBinaryName)

        if (!binaryFile.exists()) {
            throw IOException("File biner native tidak ditemukan: ${binaryFile.absolutePath}")
        }

        val commandList = mutableListOf<String>()
        commandList.add(binaryFile.absolutePath)
        commandList.addAll(args)

        val pb = ProcessBuilder(commandList)
        pb.directory(workDir)

        val env = pb.environment()
        env["PATH"] = "$nativeLibDir:/system/bin:" + (env["PATH"] ?: "")
        env["HOME"] = workDir.absolutePath

        val envFile = File(workDir, ".env")
        if (envFile.exists()) {
            env.putAll(parseEnvFile(envFile))
        }

        Log.i(TAG, "Mengeksekusi native daemon: ${binaryFile.absolutePath}")
        val process = pb.start()

        drainStream(process.inputStream, "STDOUT", onLog)
        drainStream(process.errorStream, "STDERR", onLog)
        return process
    }

    private fun parseEnvFile(envFile: File): Map<String, String> {
        val envMap = mutableMapOf<String, String>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(envFile))).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim() ?: continue
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        envMap[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Gagal membaca .env: ", e)
        }
        return envMap
    }

    private fun drainStream(stream: InputStream, type: String, onLog: (String) -> Unit) {
        Thread {
            try {
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { onLog("[$type] $it") }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error membaca stream $type", e)
            }
        }.start()
    }

    companion object {
        private const val TAG = "DaemonManager"
    }
}
