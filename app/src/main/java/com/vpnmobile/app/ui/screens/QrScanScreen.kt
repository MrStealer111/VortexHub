package com.vpnmobile.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import com.vpnmobile.app.ui.i18n.Text
import com.vpnmobile.app.ui.theme.AppTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.vpnmobile.app.data.ConfigTools
import com.vpnmobile.app.data.ShadowsocksUri
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.data.XrayConfig
import com.vpnmobile.app.ui.MainViewModel
import java.util.concurrent.Executors

@Suppress("UnsafeOptInUsageError")
private fun ImageProxy.asInputImage(): InputImage? {
    val mediaImage = image ?: return null
    return InputImage.fromMediaImage(mediaImage, imageInfo.rotationDegrees)
}

private fun qrError(raw: String): String? {
    val t = raw.trim()
    return when {
        t.isBlank() -> "Empty QR code"
        t.equals("warp://auto", true) || t.equals("warp://", true) -> null
        t.startsWith("ss://", true) ->
            if (ShadowsocksUri.parse(t) == null) "Invalid Shadowsocks link" else null
        t.startsWith("wireguard://", true) ->
            if (ConfigTools.normalizeWireGuard(t) == null) "Invalid WireGuard link" else null
        t.startsWith("vless://", true) || t.startsWith("vmess://", true) || t.startsWith("trojan://", true) ->
            try {
                XrayConfig.build(t)
                null
            } catch (e: Exception) {
                e.message ?: "Invalid VLESS/VMess/Trojan link"
            }
        else -> "Unsupported QR code — expected ss://, wireguard://, warp://, vless://, vmess:// or trojan://"
    }
}

private fun qrName(raw: String): String = when {
    raw.startsWith("ss://", true) ->
        ShadowsocksUri.parse(raw.trim())?.name?.takeIf { it.isNotBlank() } ?: "QR Import"
    raw.startsWith("wireguard://", true) -> "WireGuard (QR)"
    XrayConfig.isXrayUri(raw) ->
        XrayConfig.displayName(raw)?.takeIf { it.isNotBlank() }
            ?: "${XrayConfig.protocolName(raw) ?: "Xray"} (QR)"
    else -> "WARP (QR)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onScanned: (String) -> Unit,
    onConnect: (TunnelProfile) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCamPermission = granted }
    var lastScanned by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (!hasCamPermission) launcher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        containerColor = AppTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Scan QR", color = AppTheme.colors.text, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppTheme.colors.text) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.bg),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(AppTheme.colors.bg), contentAlignment = Alignment.Center) {
            if (!hasCamPermission) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Camera permission required", color = AppTheme.colors.text, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to grant camera access", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                val scanner = BarcodeScanning.getClient()
                                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                        val image = imageProxy.asInputImage()
                                        if (image != null) {
                                            scanner.process(image).addOnSuccessListener { barcodes ->
                                                for (b in barcodes) {
                                                    val raw = b.rawValue?.trim() ?: continue
                                                    if (lastScanned == raw) continue
                                                    lastScanned = raw
                                                    val error = qrError(raw)
                                                    if (error != null) {
                                                        viewModel.showMessage(error)
                                                        continue
                                                    }
                                                    val flag = when {
                                                        raw.startsWith("ss://", true) -> "🔒"
                                                        raw.startsWith("wireguard://", true) -> "⚡"
                                                        raw.startsWith("vless://", true) -> "🚀"
                                                        raw.startsWith("vmess://", true) -> "☁️"
                                                        raw.startsWith("trojan://", true) -> "🛡️"
                                                        else -> "🌐"
                                                    }
                                                    val configText = if (raw.startsWith("wireguard://", true)) {
                                                        ConfigTools.normalizeWireGuard(raw) ?: raw
                                                    } else {
                                                        raw
                                                    }
                                                    viewModel.addProfile(qrName(raw), flag, configText) { profile ->
                                                        onConnect(profile)
                                                    }
                                                    onScanned(raw)
                                                }
                                            }.addOnCompleteListener { imageProxy.close() }
                                        } else imageProxy.close()
                                    }
                                }
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                } catch (_: Exception) {}
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.size(220.dp).align(Alignment.Center).clip(RoundedCornerShape(16.dp)).border(2.dp, AppTheme.colors.accent, RoundedCornerShape(16.dp)).background(Color.Transparent)
                    )
                    Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(0.6f)).padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(lastScanned?.take(40) ?: "Point camera at QR code", color = AppTheme.colors.text, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
