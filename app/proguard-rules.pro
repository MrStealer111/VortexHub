# WireGuard
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

# Keep model
-keep class com.vpnmobile.app.data.TunnelProfile { *; }
-keepclassmembers class * { @kotlin.Metadata *; }

# R8 hardening
-obfuscationdictionary proguard-dict.txt
-classobfuscationdictionary proguard-dict.txt
-packageobfuscationdictionary proguard-dict.txt
-overloadaggressively
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# Strip logs
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Crypto
-keep class androidx.security.crypto.** { *; }
-keep class com.vpnmobile.app.data.Crypto { *; }
-keep class com.vpnmobile.app.vpn.SecurityCheck { *; }
