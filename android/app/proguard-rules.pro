# Logging policy (architecture A-20): verbose, debug and info calls are removed in release builds.
-assumenosideeffects class nl.pe1mew.zangbundel.app.Log {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}
# libsodium through JNA: keep the native binding classes.
-keep class com.sun.jna.** { *; }
-keep class com.goterl.lazysodium.** { *; }
-dontwarn java.awt.**
