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
# ML Kit's own consumer rules retain its manifest-discovered components.
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ZXing
-dontwarn com.google.zxing.**

# WebDAV (Sardine)
-dontwarn com.thegrizzlylabs.sardineandroid.**
-dontwarn okhttp3.**
-dontwarn okio.**

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
