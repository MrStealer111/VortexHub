package com.vpnmobile.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vpnmobile.app.data.PremiumManager
import com.vpnmobile.app.ui.effects.GlassPrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isPremium by PremiumManager.isPremium.collectAsStateWithLifecycle()
    val products by PremiumManager.products.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { PremiumManager.init(context) }
    Scaffold(containerColor = Color(0xFF0A0A0A), topBar = {
        TopAppBar(title = { Text("Premium", color = Color.White, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A)))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF8C00), Color(0xFFFF6B00)))).padding(20.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Star, null, tint = Color.White) }
                        Text("VortexHub Premium", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (isPremium) "You are Premium ✓" else "Unlock all premium servers, fastest speeds, no limits", color = Color.White, fontSize = 13.sp)
                }
            }
            listOf("Unlimited premium servers", "Fastest WarpGuard nodes", "No ads", "Priority support").forEach { feat ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E1E1E)).padding(12.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFFF8C00).copy(0.2f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, tint = Color(0xFFFF8C00), modifier = Modifier.size(14.dp)) }
                    Text(feat, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (!isPremium) {
                products.forEach { pd ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), border = BorderStroke(1.dp, Color(0xFFFF8C00).copy(0.3f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(pd.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(pd.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "", color = Color(0xFFFF8C00), fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                            GlassPrimaryButton(onClick = { (context as? android.app.Activity)?.let { PremiumManager.launchPurchase(it, pd) } }) { Text("Buy", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (products.isEmpty()) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), border = BorderStroke(1.dp, Color(0xFF2C2C2E))) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Billing is not available right now.", color = Color(0xFF8E8E93), fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0xFF00C853).copy(0.15f)).padding(12.dp), contentAlignment = Alignment.Center) { Text("Premium Active ✓", color = Color(0xFF00C853), fontWeight = FontWeight.Bold) }
            }
        }
    }
}
