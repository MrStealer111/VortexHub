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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.vpnmobile.app.ui.MainViewModel
import java.util.concurrent.Executors

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun ImageProxy.asInputImage(): InputImage? {
    val mediaImage = image ?: return null
    return InputImage.fromMediaImage(mediaImage, imageInfo.rotationDegrees)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(viewModel: MainViewModel, onBack: () -> Unit, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCamPermission = granted }
    var lastScanned by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (!hasCamPermission) launcher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            TopAppBar(
                title = { Text("Scan QR", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A)),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF0A0A0A)), contentAlignment = Alignment.Center) {
            if (!hasCamPermission) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Camera permission required", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to grant camera access", color = Color(0xFF8E8E93), fontSize = 12.sp)
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
                                                    val raw = b.rawValue ?: continue
                                                    if (raw.startsWith("ss://") || raw.startsWith("vless://") || raw.startsWith("vmess://") || raw.startsWith("trojan://") || raw.contains("://")) {
                                                        if (lastScanned != raw) {
                                                            lastScanned = raw
                                                            viewModel.addProfile("QR Import", "📷", raw)
                                                            onScanned(raw)
                                                        }
                                                    }
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
                        Modifier.size(220.dp).align(Alignment.Center).clip(RoundedCornerShape(16.dp)).border(2.dp, Color(0xFFFF8C00), RoundedCornerShape(16.dp)).background(Color.Transparent)
                    )
                    Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(0.6f)).padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(lastScanned?.take(40) ?: "Point camera at QR code", color = Color.White, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
