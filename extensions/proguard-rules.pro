-dontobfuscate
-dontoptimize
-keepattributes *
-keep class app.morphe.** {
  *;
}
-keep class app.revanced.** {
  *;
}
-keep class com.google.** {
  *;
}
# GoBackend uses JNI names from the upstream libwg-go.so.
-keep class com.wireguard.android.backend.GoBackend { *; }
-keep class com.wireguard.android.backend.GoBackend$VpnService { *; }
