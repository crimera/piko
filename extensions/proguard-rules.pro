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

# Media3 and Guava retain descriptors for compile-time-only annotations after relocation.
-dontwarn app.morphe.extension.crimera.internal.google.errorprone.annotations.**
-dontwarn app.morphe.extension.crimera.internal.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn kotlin.annotations.jvm.**
-dontwarn org.checkerframework.**
