# VPN Mobile — Android VPN App

တကယ်အလုပ်လုပ်တဲ့ Android VPN app — [WireGuard](https://www.wireguard.com/) ကို အခြေခံပြီး
`VpnService` + `wireguard-go` (userspace backend) နဲ့ တည်ဆောက်ထားပါတယ်။
Jetpack Compose (Material 3) UI ဖြစ်ပြီး Kotlin နဲ့ ရေးထားပါတယ်။

> **အရေးကြီး:** ဒါက VPN **client** app ဖြစ်လို့ အသုံးပြုဖို့ ကိုယ်ပိုင် WireGuard **server** တစ်ခု
> လိုပါတယ်။ Server မရှိဘဲနဲ့ အင်တာနက်ကို VPN ကြားခံ မလုပ်နိုင်ပါ။

## Features

- ✅ Real WireGuard VPN (userspace `wireguard-go`, native `libwg-go.so`)
- ✅ Connect / disconnect with system VPN permission flow
- ✅ Multi-server management — import WireGuard config from file (SAF) or paste
- ✅ Live stats — download/upload bytes, last handshake, session timer
- ✅ Kill switch (route everything through VPN, block leaks when tunnel drops)
- ✅ Auto-connect on app launch
- ✅ Dark theme UI (Material 3, Jetpack Compose)
- ✅ English + မြန်မာ UI strings
- ✅ Data stays on-device (DataStore) — configs are never uploaded

## Tech Stack

| Piece | Choice |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose (BOM 2024.12.01, Material 3) |
| VPN engine | `com.wireguard.android:tunnel:1.0.20230706` (official WireGuard library) |
| Persistence | DataStore Preferences |
| Navigation | Navigation Compose |
| Min / Target SDK | 24 / 35 |

## Project Structure

```
app/
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/vpnmobile/app/
    │   ├── MainActivity.kt          # VPN permission flow + entry point
    │   ├── VpnApplication.kt        # DI container
    │   ├── data/
    │   │   ├── TunnelProfile.kt     # server profile model + JSON
    │   │   ├── TunnelStore.kt       # DataStore persistence
    │   │   └── ConfigTools.kt       # kill-switch rewrite, formatting
    │   ├── vpn/
    │   │   └── VpnConnectionManager.kt  # GoBackend wrapper + stats polling
    │   └── ui/
    │       ├── MainViewModel.kt
    │       ├── VpnApp.kt            # navigation shell
    │       └── screens/             # Home / Servers / Import / Settings
    └── res/
        ├── values/ + values-my/     # English + Burmese strings
        └── mipmap-*/                # launcher icons (adaptive + PNG fallback)
```

## Build & Install

Requirements: **Android Studio** (Ladybug or newer) or CLI with JDK 17 + Android SDK 35.

```bash
# CLI build (Android SDK path required):
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

In Android Studio: open the project folder → let Gradle sync → **Run ▶** on `app`.

## Set Up a WireGuard Server

### Option A — wg-easy (fastest, Docker)

```bash
docker run -d \
  --name=wg-easy \
  -e WG_HOST=vpn.example.com \
  -e PASSWORD=admin123 \
  -v ~/.wg-easy:/etc/wireguard \
  -p 51820:51820/udp -p 51821:51821/tcp \
  --cap-add=NET_ADMIN \
  --sysctl net.ipv4.ip_forward=1 \
  --sysctl net.ipv6.conf.all.disable_ipv6=0 \
  weejewel/wg-easy
```

1. Open `http://<server-ip>:51821`, log in, create a client.
2. Download the client config (`.conf`) or scan the QR with the config text.
3. Import that file/text into the app.

### Option B — manual (Debian/Ubuntu)

```bash
sudo apt install wireguard
cd /etc/wireguard
umask 077
wg genkey | tee server_private.key | wg pubkey > server_public.key
```

`/etc/wireguard/wg0.conf` (adjust `eth0` to your NIC):

```ini
[Interface]
PrivateKey = <server private key>
Address = 10.66.66.1/24
ListenPort = 51820
PostUp = iptables -t nat -A POSTROUTING -s 10.66.66.0/24 -o eth0 -j MASQUERADE; sysctl -w net.ipv4.ip_forward=1
PostDown = iptables -t nat -D POSTROUTING -s 10.66.66.0/24 -o eth0 -j MASQUERADE
```

```bash
sudo systemctl enable --now wg-quick@wg0
```

Generate a client keypair, then create the **client** config:

```ini
[Interface]
PrivateKey = <client private key>
Address = 10.66.66.2/32
DNS = 1.1.1.1

[Peer]
PublicKey = <server public key>
Endpoint = <server-ip>:51820
AllowedIPs = 0.0.0.0/0, ::/0
```

On the server, add the client as a peer (`AllowedIPs = 10.66.66.2/32`) and reload.

## Using the App

1. **Servers** tab → **+** → import `.conf` file or paste config text → Save.
2. Back on **Home**, tap the big button to connect (grant VPN permission on first use).
3. Connected state shows live download/upload, session time, and last handshake.

**Kill switch:** Settings → Kill switch ON rewrites every peer's `AllowedIPs` to
`0.0.0.0/0, ::/0` before connecting, so all traffic goes through the tunnel and nothing
can bypass it when the tunnel is down.

## Troubleshooting

- **"VPN permission required" card stays** — the system dialog must be accepted; check Android Settings → VPN for the app.
- **DNS resolution failure** — your `Endpoint = hostname:port` couldn't resolve. Use an IP, or check connectivity/DNS.
- **No handshake / connected but no internet** — server firewall: open UDP `51820`, confirm `IP forwarding` on the server, and that the server has the client's public key.
- **Prefer stable connection on Android** — enable Android **Settings → Network → VPN → VPN Mobile → Always-on VPN**.

## Roadmap Ideas

- QR code import (camera)
- Per-app split tunneling (excluded apps)
- WireGuard key generation in-app
- Boot-time auto-connect (always-on service)
- Server ping / latency testing
