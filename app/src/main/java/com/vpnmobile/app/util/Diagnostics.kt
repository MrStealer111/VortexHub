package com.vpnmobile.app.util

import android.content.Context
import android.os.Build
import com.vpnmobile.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small in-memory log with secret redaction, used for user-visible diagnostics exports. */
object Diagnostics {

    private const val MAX_ENTRIES = 500
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun log(tag: String, message: String) {
        val line = "${timestamp.format(Date())} [$tag] ${redact(message)}"
        synchronized(entries) {
            if (entries.size >= MAX_ENTRIES) entries.removeFirst()
            entries.addLast(line)
        }
    }

    fun log(tag: String, throwable: Throwable) {
        log(tag, "${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}")
    }

    fun snapshot(): List<String> = synchronized(entries) { entries.toList() }

    fun export(context: Context): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "vortexhub-diagnostics-$stamp.txt")
        val header = buildString {
            appendLine("VortexHub diagnostics")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Exported: ${timestamp.format(Date())}")
            appendLine("Secrets are redacted.")
            appendLine("----")
        }
        file.writeText(header + snapshot().joinToString("\n") + "\n")
        return file
    }

    fun redact(input: String): String {
        var text = input
        text = Regex("ss://[^\\s@]+@").replace(text, "ss://***@")
        text = Regex("ss://(?![^\\s]*@)[^\\s]+").replace(text, "ss://***")
        text = Regex("(?im)^(\\s*(?:PrivateKey|PresharedKey)\\s*=\\s*).*$").replace(text, "$1***")
        text = Regex("(?i)(\"(?:password|token|secret)\"\\s*:\\s*\")[^\"]*\"").replace(text, "$1***\"")
        text = Regex("(?i)((?:password|token|secret)\\s*[=:]\\s*)\\S+").replace(text, "$1***")
        return text
    }
}
