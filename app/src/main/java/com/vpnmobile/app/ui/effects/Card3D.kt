package com.vpnmobile.app.ui.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.tilt3D(maxTilt: Float = 12f): Modifier = composed {
    var rotX by remember { mutableStateOf(0f) }
    var rotY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val animX by animateFloatAsState(if (isDragging) rotX else 0f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "rx")
    val animY by animateFloatAsState(if (isDragging) rotY else 0f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "ry")
    val scale by animateFloatAsState(if (isDragging) 0.98f else 1f, spring(dampingRatio = 0.7f), label = "sc")
    this
        .graphicsLayer {
            rotationX = animX
            rotationY = animY
            scaleX = scale
            scaleY = scale
            cameraDistance = 12f * density
            shadowElevation = if (isDragging) 16f else 4f
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { isDragging = true },
                onDragEnd = { isDragging = false; rotX = 0f; rotY = 0f },
                onDragCancel = { isDragging = false; rotX = 0f; rotY = 0f },
            ) { change, dragAmount ->
                change.consume()
                rotY = (dragAmount.x / 30f).coerceIn(-maxTilt, maxTilt)
                rotX = (-dragAmount.y / 30f).coerceIn(-maxTilt, maxTilt)
            }
        }
}

fun Modifier.shimmer3D(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val offset by infinite.animateFloat(initialValue = -600f, targetValue = 600f, animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "offset")
    this.graphicsLayer {
        translationX = offset * 0.15f
    }
}
