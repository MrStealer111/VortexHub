package com.vpnmobile.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.vpnmobile.app.ui.theme.AppTheme

@Composable
fun Modifier.glass(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    selected: Boolean = false,
): Modifier {
    val c = AppTheme.colors
    val fill = Brush.verticalGradient(listOf(c.glassTop, c.glassBottom))
    val stroke = if (selected) c.glassBorderSelected else c.glassBorder
    return this.background(fill, shape).border(BorderStroke(1.dp, stroke), shape)
}
