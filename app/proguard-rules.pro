# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep names of all Jellyfin classes
-keepnames class org.jellyfin.mobile.**.* { *; }
-keepnames interface org.jellyfin.mobile.**.* { *; }

# Keep WebView JS interfaces
-keepclassmembers class org.jellyfin.mobile.bridge.* {
    @android.webkit.JavascriptInterface public *;
}

# Keep Chromecast methods
-keepclassmembers class org.jellyfin.mobile.cast.Chromecast {
    public *;
}

# Keep file names/line numbers
-keepattributes SourceFile,LineNumberTable

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Keep AndroidX ComponentFactory
-keep class androidx.core.app.CoreComponentFactory { *; }

# Assume SDK >= 21 to remove unnecessary compat code
-assumevalues class android.os.Build$VERSION {
  int SDK_INT return 21..2147483647;
}
