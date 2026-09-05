package com.vpnmobile.app.vpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object XrayManager {

    private const val TAG = "XrayManager"
    private const val BINARY_NAME = "xray"
    private const val LOCAL_SOCKS_PORT = 10808
    private const val LOCAL_HTTP_PORT = 10809

    private var process: Process? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    var isRunning = false
        private set

    var lastError: String? = null
        private set

    suspend fun connect(context: Context, uri: String, customDns: String = "1.1.1.1"): Boolean {
        if (isRunning) {
            Log.w(TAG, "Xray already running, stopping first")
            disconnect()
        }

        return withContext(Dispatchers.IO) {
            try {
                lastError = null

                val binaryFile = getBinaryFile(context)
                if (binaryFile == null || !binaryFile.exists()) {
                    lastError = "Xray binary not found"
                    Log.e(TAG, "Binary not found at expected location")
                    return@withContext false
                }

                if (!binaryFile.canExecute()) {
                    binaryFile.setExecutable(true)
                }

                val configFile = File(context.filesDir, "xray_config.json")
                val configJson = XrayConfigGenerator.generateConfig(uri, customDns)
                configFile.writeText(configJson)
                Log.d(TAG, "Config written: ${configFile.absolutePath}")
                Log.d(TAG, "Config: $configJson")

                val cmd = listOf(
                    binaryFile.absolutePath,
                    "run",
                    "-c", configFile.absolutePath
                )

                val pb = ProcessBuilder(cmd)
                    .directory(context.filesDir)
                    .redirectErrorStream(true)

                process = pb.start()
                isRunning = true
                Log.i(TAG, "Xray process started (pid=${process?.toString()})")

                monitorProcess()

                if (isRunning) {
                    Log.i(TAG, "Xray running on socks=127.0.0.1:$LOCAL_SOCKS_PORT http=127.0.0.1:$LOCAL_HTTP_PORT")
                    true
                } else {
                    lastError = lastError ?: "Process exited immediately"
                    Log.e(TAG, "Xray failed to start: $lastError")
                    false
                }
            } catch (e: Exception) {
                lastError = e.message
                Log.e(TAG, "Connect failed: ${e.message}", e)
                false
            }
        }
    }

    suspend fun disconnect() {
        Log.d(TAG, "Disconnecting Xray")
        monitorJob?.cancel()
        monitorJob = null

        try {
            process?.destroy()
            process?.waitFor()
        } catch (_: Exception) {}

        process = null
        isRunning = false
        lastError = null
        Log.i(TAG, "Xray disconnected")
    }

    private fun monitorProcess() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            try {
                val p = process ?: return@launch
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    Log.d(TAG, "xray: $l")
                    if (l.contains("failed to") || l.contains("error") || l.contains("panic")) {
                        lastError = l
                    }
                }
                val exitCode = p.waitFor()
                Log.w(TAG, "Xray process exited with code=$exitCode")
                if (isRunning) {
                    isRunning = false
                    lastError = lastError ?: "Process exited with code $exitCode"
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Monitor error: ${e.message}")
                    isRunning = false
                    lastError = e.message
                }
            }
        }
    }

    fun isSupported(uri: String): Boolean {
        val t = uri.trim()
        return t.startsWith("ss://", true) ||
                t.startsWith("vless://", true) ||
                t.startsWith("vmess://", true) ||
                t.startsWith("trojan://", true)
    }

    fun getSocksPort(): Int = LOCAL_SOCKS_PORT

    fun getHttpPort(): Int = LOCAL_HTTP_PORT

    fun getProxyAddress(): String = "127.0.0.1:$LOCAL_SOCKS_PORT"

    private fun getBinaryFile(context: Context): File? {
        val internalBin = File(context.filesDir, BINARY_NAME)
        if (internalBin.exists()) return internalBin

        val assetBin = copyAssetToInternal(context, BINARY_NAME)
        return assetBin
    }

    private fun copyAssetToInternal(context: Context, assetName: String): File? {
        return try {
            val outFile = File(context.filesDir, assetName)
            if (outFile.exists()) return outFile

            context.assets.open(assetName).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outFile.setExecutable(true)
            Log.i(TAG, "Copied asset $assetName to ${outFile.absolutePath}")
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset $assetName: ${e.message}")
            null
        }
    }

    fun getInstalledVersion(context: Context): String {
        return try {
            val binary = getBinaryFile(context) ?: return "not installed"
            val pb = ProcessBuilder(listOf(binary.absolutePath, "version"))
            pb.redirectErrorStream(true)
            val p = pb.start()
            val version = BufferedReader(InputStreamReader(p.inputStream)).readLine() ?: "unknown"
            p.waitFor()
            version
        } catch (_: Exception) {
            "not installed"
        }
    }

    fun isBinaryInstalled(context: Context): Boolean {
        val binary = getBinaryFile(context)
        return binary != null && binary.exists() && binary.canExecute()
    }
}
