package com.vpnmobile.app.vpn

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object XrayDownloader {

    private const val TAG = "XrayDownloader"
    private const val GITHUB_API = "https://api.github.com/repos/xtls/Xray-core/releases/latest"
    private const val BINARY_NAME = "xray"

    data class DownloadResult(
        val success: Boolean,
        val version: String? = null,
        val error: String? = null,
        val filePath: String? = null
    )

    suspend fun downloadLatest(context: Context): DownloadResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Checking latest Xray release...")

            val apiUrl = URL(GITHUB_API)
            val apiConn = apiUrl.openConnection() as HttpURLConnection
            apiConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            apiConn.connectTimeout = 15000
            apiConn.readTimeout = 15000

            if (apiConn.responseCode != 200) {
                return@withContext DownloadResult(false, error = "API returned ${apiConn.responseCode}")
            }

            val apiResponse = apiConn.inputStream.bufferedReader().readText()
            val releaseJson = org.json.JSONObject(apiResponse)
            val tagName = releaseJson.getString("tag_name")
            val version = tagName.removePrefix("v")

            Log.i(TAG, "Latest version: $version")

            val assets = releaseJson.getJSONArray("assets")
            var downloadUrl: String? = null
            var fileName: String? = null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.contains("android") && name.contains("arm64") && name.endsWith(".zip")) {
                    downloadUrl = asset.getString("browser_download_url")
                    fileName = name
                    break
                }
            }

            if (downloadUrl == null) {
                return@withContext DownloadResult(false, version = version, error = "No Android ARM64 asset found")
            }

            Log.i(TAG, "Downloading $fileName...")

            val downloadUrlObj = URL(downloadUrl)
            val downloadConn = downloadUrlObj.openConnection() as HttpURLConnection
            downloadConn.connectTimeout = 60000
            downloadConn.readTimeout = 60000

            if (downloadConn.responseCode != 200) {
                return@withContext DownloadResult(false, version = version, error = "Download returned ${downloadConn.responseCode}")
            }

            val tempFile = File(context.cacheDir, "xray_download.zip")
            FileOutputStream(tempFile).use { output ->
                downloadConn.inputStream.use { input ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "Downloaded ${tempFile.length()} bytes, extracting...")

            val extractedFile = extractBinary(context, tempFile)
            tempFile.delete()

            if (extractedFile != null && extractedFile.exists()) {
                extractedFile.setExecutable(true)
                Log.i(TAG, "Xray binary installed at: ${extractedFile.absolutePath}")
                DownloadResult(true, version = version, filePath = extractedFile.absolutePath)
            } else {
                DownloadResult(false, version = version, error = "Failed to extract binary")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            DownloadResult(false, error = e.message)
        }
    }

    private fun extractBinary(context: Context, zipFile: File): File? {
        return try {
            val outputDir = context.filesDir
            val targetFile = File(outputDir, BINARY_NAME)

            ZipInputStream(zipFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName.endsWith(BINARY_NAME) && !entry.isDirectory) {
                        Log.d(TAG, "Extracting: $entryName")
                        FileOutputStream(targetFile).use { output ->
                            zip.copyTo(output)
                        }
                        return targetFile
                    }
                    entry = zip.nextEntry
                }
            }
            Log.e(TAG, "Binary not found in zip")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            null
        }
    }

    fun isInstalled(context: Context): Boolean {
        val binary = File(context.filesDir, BINARY_NAME)
        return binary.exists() && binary.canExecute()
    }

    fun getInstalledVersion(context: Context): String {
        return try {
            val binary = File(context.filesDir, BINARY_NAME)
            if (!binary.exists()) return "not installed"
            val pb = ProcessBuilder(listOf(binary.absolutePath, "version"))
            pb.redirectErrorStream(true)
            val p = pb.start()
            val version = p.inputStream.bufferedReader().readLine() ?: "unknown"
            p.waitFor()
            version
        } catch (_: Exception) {
            "not installed"
        }
    }
}
