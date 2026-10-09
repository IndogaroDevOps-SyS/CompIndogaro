package com.jargo.pangkashost

import android.content.Context
import android.util.Log
import java.io.*

class DaemonManager(private val context: Context) {

    private val workDir: File = context.filesDir

    fun prepareEnvironment() {
        Log.i(TAG, "Mengekstrak aset ke: ${workDir.absolutePath}")
        copyAssetFolder("", workDir)
        makeBinariesExecutable(File(workDir, "bin"))
    }

    private fun copyAssetFolder(fromAssetPath: String, toDir: File) {
        val files = context.assets.list(fromAssetPath) ?: return
        if (!toDir.exists()) {
            toDir.mkdirs()
        }

        for (file in files) {
            if (file == "images" || file == "webkit" || file == "sounds") continue
            val assetSubPath = if (fromAssetPath.isEmpty()) file else "$fromAssetPath/$file"
            val subFiles = context.assets.list(assetSubPath)
            val destFile = File(toDir, file)

            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(assetSubPath, destFile)
            } else {
                copyAssetFile(assetSubPath, destFile)
            }
        }
    }

    private fun copyAssetFile(assetPath: String, destFile: File) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
    }

    private fun makeBinariesExecutable(binDir: File) {
        if (binDir.exists() && binDir.isDirectory) {
            binDir.listFiles()?.forEach { bin ->
                val success = bin.setExecutable(true, false)
                if (!success) {
                    try {
                        Runtime.getRuntime().exec("chmod 755 ${bin.absolutePath}").waitFor()
                        Log.i(TAG, "Chmod 755 via shell: ${bin.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Gagal chmod file: ${bin.name}", e)
                    }
                } else {
                    Log.i(TAG, "Set executable (Java API): ${bin.name}")
                }
            }
        }
    }

    fun startDaemon(binaryName: String, onLog: (String) -> Unit, vararg args: String): Process {
        val binaryFile = File(workDir, "bin/$binaryName")
        if (!binaryFile.exists()) {
            throw IOException("File biner tidak ditemukan: ${binaryFile.absolutePath}")
        }

        val pb = ProcessBuilder()
        pb.command().add(binaryFile.absolutePath)
        args.forEach { pb.command().add(it) }
        pb.directory(workDir)

        val envFile = File(workDir, ".env")
        if (envFile.exists()) {
            val envMap = parseEnvFile(envFile)
            pb.environment().putAll(envMap)
        }

        Log.i(TAG, "Mengeksekusi daemon biner: ${binaryFile.absolutePath}")
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
