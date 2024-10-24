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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class com.tiefensuche.soundcrowd.plugins.** { *; }
-keep class xyz.gianlu.librespot.** { *; }
-keep class com.spotify.metadata.** { *; }
-keep class com.spotify.storage.** { *; }
-keep class com.spotify.clienttoken.** { *; }
-keep class android.support.v4.media.** { *; }
-keep class androidx.preference.** { *; }
-keep class kotlin.** { *; }
-dontwarn com.sun.net.httpserver.HttpContext
-dontwarn com.sun.net.httpserver.HttpExchange
-dontwarn com.sun.net.httpserver.HttpHandler
-dontwarn com.sun.net.httpserver.HttpServer
-dontobfuscate