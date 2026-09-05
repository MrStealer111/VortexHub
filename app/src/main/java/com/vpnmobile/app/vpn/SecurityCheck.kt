package com.vpnmobile.app.vpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import java.io.File

object SecurityCheck {
    fun isRooted(): Boolean {
        val paths = listOf("/system/xbin/su", "/system/bin/su", "/system/app/Superuser.apk", "/sbin/su", "/su/bin/su")
        if (paths.any { File(it).exists() }) return true
        return try { Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su")).inputStream.read() != -1 } catch (_: Exception) { false }
    }
    fun isDebuggable(context: Context): Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    fun isDebuggerAttached(): Boolean = Debug.isDebuggerConnected()
    fun isEmulator(): Boolean = android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("Emulator")
    fun isTampered(context: Context): Boolean = try {
        val sig = context.packageManager.getPackageInfo(context.packageName, 64).signatures
        sig.isNullOrEmpty()
    } catch (_: Exception) { true }

    fun sslPin(host: String): Boolean {
        return host in listOf("api.cloudflareclient.com", "raw.githubusercontent.com", "api.npoint.io")
    }
}
