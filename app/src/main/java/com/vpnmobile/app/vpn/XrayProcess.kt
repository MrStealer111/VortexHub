package com.vpnmobile.app.vpn

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the bundled Xray-core Android binary as a child process. The TUN fd is
 * inherited through XRAY_TUN_FD (xray.tun.fd), so Xray reads/writes the VPN
 * interface created by [XrayVpnService] directly.
 */
object XrayProcess {
    private const val TAG = "XrayProcess"
    private const val VERSION = "26.10.21"

    @Volatile
    private var process: Process? = null

    @Volatile
    private var logFile: File? = null

    fun version(): String = VERSION

    fun start(context: Context, configJson: String, tunFd: Int): Result<Unit> = runCatching {
        stop()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw IllegalStateException("Xray requires Android 8.0 or newer")
        }

        val app = context.applicationContext
        val binary = nativeBinary(app)
        val config = File(app.filesDir, "xray-config.json")
        config.writeText(configJson)
        val log = File(app.filesDir, "xray.log").apply { writeText("") }

        val builder = ProcessBuilder(binary.absolutePath, "run", "-c", config.absolutePath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT)
        }
        builder.redirectErrorStream(true)
        builder.environment()["XRAY_TUN_FD"] = tunFd.toString()
        builder.environment()["xray.tun.fd"] = tunFd.toString()
        builder.environment()["XRAY_LOCATION_ASSET"] = app.filesDir.absolutePath

        val child = builder.start()
        process = child
        logFile = log

        Thread({
            try {
                child.inputStream.use { input ->
                    log.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
            }
        }, "xray-log").apply { isDaemon = true }.start()

        Thread.sleep(900)
        if (!child.isAliveCompat()) {
            val tail = readTail(log)
            process = null
            config.delete()
            throw IllegalStateException(if (tail.isBlank()) "Xray exited immediately" else "Xray exited: $tail")
        }

        config.delete()
        Log.d(TAG, "Xray $VERSION started (${binary.name})")
    }

    fun stop() {
        val child = process ?: return
        process = null
        runCatching { child.destroy() }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!child.waitFor(2, TimeUnit.SECONDS)) child.destroyForcibly()
            } else {
                child.waitFor()
            }
        }
        logFile = null
    }

    fun isRunning(): Boolean = process?.isAliveCompat() == true

    fun lastLog(): String = logFile?.let(::readTail).orEmpty()

    fun lastError(): String? = if (isRunning()) null else lastLog().takeIf { it.isNotBlank() }

    private fun Process.isAliveCompat(): Boolean = try {
        exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    private fun nativeBinary(context: Context): File {
        val binary = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        if (!binary.exists() || !binary.canExecute()) {
            throw IllegalStateException("Xray engine is not bundled for this device")
        }
        return binary
    }

    private fun readTail(file: File): String = try {
        file.readText().takeLast(2000)
    } catch (_: Exception) {
        ""
    }
}
