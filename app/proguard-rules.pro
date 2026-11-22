# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 忽略 R8 警告
-dontwarn org.xmlpull.v1.**
-dontwarn android.content.res.**

# Room generated implementations use direct references and ship consumer rules.
-dontwarn androidx.room.paging.**

# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose - 保持必要的类
-dontwarn androidx.compose.**

# CameraX
-dontwarn androidx.camera.**

# ML Kit
# Manifest component discovery instantiates these classes through reflection.
# A class-only consumer keep rule does not preserve its implicit constructor
# under current R8 optimization; keep exactly that construction contract.
-keep,allowoptimization class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# WorkManager also reflects no-argument InputMerger constructors by class name.
-keep,allowoptimization class * extends androidx.work.InputMerger {
    public <init>();
}

# ZXing
-dontwarn com.google.zxing.**

# WebDAV (Sardine)
-dontwarn com.thegrizzlylabs.sardineandroid.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Steam chat stickers use APNG4Android. Its decoder crosses worker-thread and
# Drawable callback boundaries; keep the decoder graph intact in both the
# minified debug delivery build and release builds so APNGs are not flattened
# to their first PNG frame.
-keep class com.github.penfeizhou.animation.** { *; }
-dontwarn com.github.penfeizhou.animation.**

# Rust JNI entry points use stable Java symbols and must not be renamed by R8.
-keep class takagi.ru.monica.steam.core.RustSteamCoreNative { *; }

# 保留 Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# 移除日志 (Release构建)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# App models use generated Room/Kotlin Serialization accessors. Avoid keeping
# unrelated password, card, Passkey, Wi-Fi, and Bitwarden models in Steam builds.
