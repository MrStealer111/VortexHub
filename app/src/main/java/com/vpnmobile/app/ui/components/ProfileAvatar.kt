package com.vpnmobile.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Orange = Color(0xFFFF8C00)

/**
 * Renders a server badge: a real vector icon for protocol/generic markers,
 * or the country flag glyph when the profile carries one.
 */
@Composable
fun ProfileAvatar(
    flag: String,
    modifier: Modifier = Modifier,
    contentSize: Dp = 18.dp,
    tint: Color = Orange,
) {
    val icon = when (flag) {
        "\uD83C\uDF10" -> Icons.Rounded.Public
        "\uD83D\uDD12" -> Icons.Rounded.Lock
        "\u26A1" -> Icons.Rounded.Bolt
        "\uD83D\uDCF7" -> Icons.Rounded.QrCodeScanner
        else -> null
    }
    if (icon != null) {
        Icon(icon, null, modifier.size(contentSize), tint)
    } else {
        Text(flag, modifier = modifier, fontSize = contentSize.value.sp)
    }
}
