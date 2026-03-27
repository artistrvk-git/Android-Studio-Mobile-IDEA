# Android Studio Mobile - ProGuard Rules
-keepclassmembers class com.rvk.studio.bridge.NativeBridge {
    native <methods>;
}
-keep class com.rvk.studio.** { *; }
