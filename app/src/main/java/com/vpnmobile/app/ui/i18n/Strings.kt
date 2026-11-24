package com.vpnmobile.app.ui.i18n

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

val LocalLanguage = compositionLocalOf { "en" }

fun resolveLanguage(setting: String): String = when (setting) {
    "my" -> "my"
    "en" -> "en"
    else -> if (Locale.getDefault().language == "my") "my" else "en"
}

@Composable
@ReadOnlyComposable
fun translated(text: String): String =
    if (LocalLanguage.current == "my") Burmese.translate(text) else text

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = translated(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val resolved = translated(text.text)
    MaterialText(
        text = if (resolved == text.text) text else AnnotatedString(resolved),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}

object Burmese {

    private val exact = mapOf(
        "Home" to "မူလ",
        "Configs" to "ဆာဗာများ",
        "Person" to "ကိုယ်ရေး",
        "Settings" to "ဆက်တင်",
        "Servers" to "ဆာဗာများ",
        "Premium" to "ပရီမီယံ",
        "Free" to "အခမဲ့",
        "Favorites" to "အကြိုက်ဆုံး",
        "Personal" to "ကိုယ်ပိုင်",
        "Subs" to "စာရင်းသွင်း",
        "All" to "အားလုံး",
        "Connected" to "ချိတ်ဆက်ပြီး",
        "Connecting" to "ချိတ်ဆက်နေသည်",
        "Connecting..." to "ချိတ်ဆက်နေသည်...",
        "Disconnected" to "ချိတ်ဆက်မထား",
        "Blocked" to "ပိတ်ဆို့ထား",
        "Connected for" to "ချိတ်ဆက်ထားသည့် ကြာချိန်",
        "Tap to Connect" to "ချိတ်ဆက်ရန် နှိပ်ပါ",
        "Tap to connect securely" to "လုံခြုံစွာ ချိတ်ဆက်ရန် နှိပ်ပါ",
        "Manage" to "စီမံ",
        "Import" to "ထည့်သွင်း",
        "Add Server" to "ဆာဗာ ထည့်ရန်",
        "Add subscription" to "စာရင်းသွင်း ထည့်ရန်",
        "Add subscription URL" to "စာရင်းသွင်း URL ထည့်ရန်",
        "Update Subs" to "စာရင်းသွင်း အသစ်ပြန်",
        "Scan QR" to "QR စကန်",
        "Scan config from QR Code" to "QR ကုဒ်မှ config စကန်",
        "Import from Clipboard" to "Clipboard မှ ထည့်သွင်း",
        "Import url from Clipboard" to "Clipboard မှ URL ထည့်သွင်း",
        "Add manual config" to "ကိုယ်တိုင် config ထည့်",
        "Add manual WireGuard" to "WireGuard ကိုယ်တိုင်ထည့်",
        "Add manual WARP" to "WARP ကိုယ်တိုင်ထည့်",
        "Add manual Shadowsocks" to "Shadowsocks ကိုယ်တိုင်ထည့်",
        "Search servers..." to "ဆာဗာ ရှာရန်...",
        "Search apps..." to "အက်ပ် ရှာရန်...",
        "Check Pings" to "Ping စစ်ရန်",
        "Checking..." to "စစ်ဆေးနေသည်...",
        "Sort: Ping" to "စီစဉ်: Ping",
        "Sort: Time" to "စီစဉ်: အချိန်",
        "Fastest server" to "အမြန်ဆုံး ဆာဗာ",
        "Testing latency..." to "Latency စမ်းသပ်နေသည်...",
        "Ping all and connect to the best" to "အားလုံး Ping စစ်ပြီး အကောင်းဆုံးကို ချိတ်ပါ",
        "No favorite servers" to "အကြိုက်ဆုံး ဆာဗာ မရှိပါ",
        "Tap the star on any server to add to favorites" to "အကြိုက်ဆုံးထည့်ရန် ဆာဗာပေါ်ရှိ ကြယ်ကို နှိပ်ပါ",
        "No subscriptions" to "စာရင်းသွင်း မရှိပါ",
        "Add an https:// subscription URL" to "https:// စာရင်းသွင်း URL ထည့်ပါ",
        "No personal configs" to "ကိုယ်ပိုင် config မရှိပါ",
        "Tap + to add WireGuard, Shadowsocks, VLESS, VMess or Trojan" to "WireGuard, Shadowsocks, VLESS, VMess, Trojan ထည့်ရန် + နှိပ်ပါ",
        "Loading free servers..." to "အခမဲ့ ဆာဗာများ ရယူနေသည်...",
        "Name" to "အမည်",
        "Name (optional)" to "အမည် (မလိုအပ်)",
        "Protocol" to "ပရိုတိုကော",
        "Endpoint" to "အဆုံးသတ်",
        "Port" to "ပို့",
        "Save" to "သိမ်းမည်",
        "Save & Connect" to "သိမ်းပြီး ချိတ်ဆက်",
        "Cancel" to "မလုပ်တော့",
        "Close" to "ပိတ်",
        "Copy" to "ကူးယူ",
        "Delete" to "ဖျက်",
        "Add" to "ထည့်",
        "Buy" to "ဝယ်ယူ",
        "Upgrade" to "အဆင့်မြှင့်",
        "Back" to "နောက်သို့",
        "VPN Settings" to "VPN ဆက်တင်",
        "Auto Connect" to "အလိုအလျောက် ချိတ်ဆက်",
        "Connect VPN on app start" to "အက်ပ်ဖွင့်သည့်အခါ VPN ချိတ်ဆက်",
        "Auto Reconnect" to "အလိုအလျောက် ပြန်ချိတ်",
        "Reconnect when VPN drops" to "VPN ပြတ်လျှင် ပြန်ချိတ်ဆက်",
        "Kill Switch" to "Kill Switch",
        "Block internet if the tunnel drops" to "Tunnel ပြတ်လျှင် အင်တာနက် ပိတ်ပင်",
        "Always-on VPN" to "အမြဲဖွင့် VPN",
        "Battery optimization" to "ဘက်ထရီ အကောင်းဆုံးဖြစ်အောင်",
        "Split Tunnel" to "Split Tunnel",
        "Split Tunnel apps" to "Split Tunnel အက်ပ်များ",
        "Choose apps to include or exclude" to "ထည့်သွင်း/ဖယ်ထုတ်မည့် အက်ပ်များ ရွေးပါ",
        "Route only selected apps through VPN" to "ရွေးထားသော အက်ပ်များကိုသာ VPN ဖြင့် ပို့",
        "Block IPv6" to "IPv6 ပိတ်ပင်",
        "Route IPv6 into the tunnel to prevent leaks" to "Leak မဖြစ်ရန် IPv6 ကို tunnel ထဲပို့",
        "DNS Settings" to "DNS ဆက်တင်",
        "Custom DNS" to "ကိုယ်ပိုင် DNS",
        "Private DNS (DoH)" to "Private DNS (DoH)",
        "Connection & DNS check" to "ချိတ်ဆက်မှုနှင့် DNS စစ်ဆေး",
        "Show public IP and the DNS resolver in use" to "လက်ရှိ public IP နှင့် DNS ကို ပြ",
        "Security" to "လုံခြုံရေး",
        "App lock" to "အက်ပ် လော့ခ်",
        "Require biometrics to open VortexHub" to "VortexHub ဖွင့်ရန် biometric လိုအပ်",
        "Biometric unlock is not available on this device" to "ဤစက်တွင် biometric မရနိုင်ပါ",
        "Block screenshots" to "ဖန်သားပြင်ဓာတ်ပုံ ပိတ်ပင်",
        "Hide app content in screenshots and Recents" to "Screenshot နှင့် Recents တွင် အကြောင်းအရာ ဖျောက်",
        "Hide keys by default" to "Key များကို ပုံမှန် ဖျောက်ထား",
        "Mask configs and QR codes until you reveal them" to "မပြမချင်း config နှင့် QR ကို ဖုံးထား",
        "Diagnostics" to "စစ်ဆေးမှု",
        "Export diagnostics" to "စစ်ဆေးမှု ထုတ်ယူ",
        "Redacted connection log for troubleshooting" to "ပြဿနာရှာရန် ချိတ်ဆက်မှု log",
        "About" to "အကြောင်း",
        "App version" to "အက်ပ် ဗားရှင်း",
        "Features:" to "အင်္ဂါရပ်များ:",
        "Language" to "ဘာသာစကား",
        "Theme" to "အပြင်အဆင်",
        "System" to "စနစ်",
        "English" to "English",
        "Myanmar" to "မြန်မာ",
        "Dark" to "အမှောင်",
        "Light" to "အလင်း",
        "Device Hardware ID" to "စက် ဟာ့ဒ်ဝဲ ID",
        "Save & Refresh Free" to "သိမ်းပြီး အခမဲ့ အသစ်ပြန်",
        "VortexHub Premium" to "VortexHub ပရီမီယံ",
        "Unlock premium servers" to "ပရီမီယံ ဆာဗာများ ဖွင့်",
        "Unlock all premium servers, fastest speeds, no limits" to "ပရီမီယံ ဆာဗာအားလုံး၊ အမြန်ဆုံး၊ ကန့်သတ်မရှိ",
        "You are Premium" to "ပရီမီယံ ဖြစ်ပါသည်",
        "Premium Active" to "ပရီမီယံ အသုံးပြုနိုင်သည်",
        "Unlimited premium servers" to "အကန့်အသတ်မဲ့ ပရီမီယံ ဆာဗာများ",
        "Fastest WarpGuard nodes" to "အမြန်ဆုံး WarpGuard node များ",
        "No ads" to "ကြော်ငြာ မရှိ",
        "Priority support" to "ဦးစားပေး အကူအညီ",
        "Billing is not available right now." to "ငွေပေးချေမှု ယခု မရနိုင်ပါ။",
        "Split tunneling disabled" to "Split tunneling ပိတ်ထား",
        "All app traffic goes through VPN" to "အက်ပ် traffic အားလုံး VPN မှ သွားသည်",
        "Exclude selected" to "ရွေးထားသည်ကို ဖယ်",
        "Only selected" to "ရွေးထားသည်သာ",
        "Show system" to "စနစ် အက်ပ်များ ပြ",
        "Keys hidden" to "Key များ ဖျောက်ထား",
        "Reveal keys" to "Key များ ပြ",
        "Scan or share this config" to "ဤ config ကို စကန် သို့မဟုတ် မျှဝေ",
        "Share QR" to "QR မျှဝေ",
        "Share link" to "လင့် မျှဝေ",
        "This config is too large for a QR code.\nUse Copy or Share instead." to "ဤ config သည် QR အတွက် ကြီးလွန်းသည်။\nCopy သို့မဟုတ် Share ကို သုံးပါ။",
        "VPN permission required — Tap to grant" to "VPN ခွင့်ပြုချက် လိုအပ်သည် — ခွင့်ပြုရန် နှိပ်ပါ",
        "Traffic is encrypted end-to-end" to "Traffic ကို အစအဆုံး encrypt ထားသည်",
        "Negotiating secure tunnel..." to "လုံခြုံသော tunnel ညှိနှိုင်းနေသည်...",
        "Kill switch active — traffic is blocked" to "Kill switch အလုပ်လုပ်နေသည် — traffic ပိတ်ထား",
        "Select a server & tap to start" to "ဆာဗာ ရွေးပြီး စတင်ရန် နှိပ်ပါ",
        "Select a server" to "ဆာဗာ ရွေးပါ",
        "Tap to choose a server" to "ဆာဗာ ရွေးရန် နှိပ်ပါ",
        "SECURE TUNNEL" to "လုံခြုံသော TUNNEL",
        "ENCRYPTED" to "ENCRYPT ထား",
        "PUBLIC IP" to "PUBLIC IP",
        "LOCATION" to "တည်နေရာ",
        "LIVE SPEED" to "အမြန်နှုန်း",
        "Download" to "ဒေါင်းလုဒ်",
        "Upload" to "အပ်လုဒ်",
        "Duration" to "ကြာချိန်",
        "Session" to "Session",
        "Peak" to "အမြင့်ဆုံး",
        "VPN server" to "VPN ဆာဗာ",
        "No keys yet\nAdd from the Configs tab" to "Key မရှိသေးပါ\nConfigs tab မှ ထည့်ပါ",
        "Tap to grant camera access" to "ကင်မရာ ခွင့်ပြုရန် နှိပ်ပါ",
        "Camera permission required" to "ကင်မရာ ခွင့်ပြုချက် လိုအပ်",
        "This build supports WireGuard, Shadowsocks and Cloudflare WARP." to "ဤ build သည် WireGuard၊ Shadowsocks နှင့် Cloudflare WARP ကို ပံ့ပိုးသည်။",
        "Generated!" to "ထုတ်ပြီးပါပြီ!",
        "Cloudflare WARP" to "Cloudflare WARP",
        "WARP Auto" to "WARP အလိုအလျောက်",
        "Key မထည့်ပဲ Auto WireGuard config" to "Key မထည့်ဘဲ Auto WireGuard config",
    )

    private val patterns = listOf(
        Regex("^Connected to (.+)$") to { m: MatchResult -> "ချိတ်ဆက်ပြီး: ${m.groupValues[1]}" },
        Regex("^Fastest: (.+) \\((\\d+) ms\\)$") to { m: MatchResult -> "အမြန်ဆုံး: ${m.groupValues[1]} (${m.groupValues[2]} ms)" },
        Regex("^(.+) configs · updated (.+)$") to { m: MatchResult -> "${m.groupValues[1]} config ခု · ${updatedMy(m.groupValues[2])}" },
        Regex("^(.+) configs$") to { m: MatchResult -> "${m.groupValues[1]} config ခု" },
        Regex("^updated just now$") to { _: MatchResult -> "ယခုလေးတင် အသစ်ပြန်ပြီး" },
        Regex("^updated (\\d+)m ago$") to { m: MatchResult -> "${m.groupValues[1]} မိနစ်က အသစ်ပြန်ပြီး" },
        Regex("^updated (\\d+)h ago$") to { m: MatchResult -> "${m.groupValues[1]} နာရီက အသစ်ပြန်ပြီး" },
        Regex("^updated (\\d+)d ago$") to { m: MatchResult -> "${m.groupValues[1]} ရက်က အသစ်ပြန်ပြီး" },
        Regex("^never updated$") to { _: MatchResult -> "အသစ် မပြန်ရသေးပါ" },
        Regex("^Ping$") to { _: MatchResult -> "Ping" },
        Regex("^Tap ping$") to { _: MatchResult -> "Ping နှိပ်ပါ" },
        Regex("^Failed$") to { _: MatchResult -> "မအောင်မြင်" },
        Regex("^(\\d+) ms$") to { m: MatchResult -> "${m.groupValues[1]} ms" },
        Regex("^Tap Check$") to { _: MatchResult -> "စစ်ရန် နှိပ်ပါ" },
        Regex("^Download (.+)$") to { m: MatchResult -> "ဒေါင်းလုဒ် ${m.groupValues[1]}" },
        Regex("^Upload (.+)$") to { m: MatchResult -> "အပ်လုဒ် ${m.groupValues[1]}" },
        Regex("^Session (.+)$") to { m: MatchResult -> "Session ${m.groupValues[1]}" },
        Regex("^Peak ↓ (.+) · ↑ (.+)$") to { m: MatchResult -> "အမြင့်ဆုံး ↓ ${m.groupValues[1]} · ↑ ${m.groupValues[2]}" },
        Regex("^ID: (.+)$") to { m: MatchResult -> "ID: ${m.groupValues[1]}" },
        Regex("^Version (.+)$") to { m: MatchResult -> "ဗားရှင်း ${m.groupValues[1]}" },
        Regex("^(\\d+) apps$") to { m: MatchResult -> "အက်ပ် ${m.groupValues[1]} ခု" },
        Regex("^Enabled • (\\d+) apps$") to { m: MatchResult -> "ဖွင့်ထား • အက်ပ် ${m.groupValues[1]} ခု" },
        Regex("^Selected apps will BYPASS VPN \\(direct internet\\)$") to { _: MatchResult -> "ရွေးထားသော အက်ပ်များ VPN ကို ကျော်သွားမည် (တိုက်ရိုက် အင်တာနက်)" },
        Regex("^Only selected apps will USE VPN$") to { _: MatchResult -> "ရွေးထားသော အက်ပ်များသာ VPN ကို သုံးမည်" },
    )

    private fun updatedMy(value: String): String = when {
        value == "updated just now" -> "ယခုလေးတင် အသစ်ပြန်ပြီး"
        value.startsWith("updated ") -> value.removePrefix("updated ") + " က အသစ်ပြန်ပြီး"
        else -> value
    }

    fun translate(text: String): String {
        exact[text]?.let { return it }
        for ((regex, builder) in patterns) {
            val match = regex.find(text) ?: continue
            return builder(match)
        }
        return text
    }
}
