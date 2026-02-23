# Keep JavaScript bridge entrypoints invoked by WebView.
-keepclassmembers class com.sansoft.harmonystram.WebAppActivity$NativePlaybackBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep activity/service/widget symbols referenced by manifest and reflection paths.
-keep class com.sansoft.harmonystram.WebAppActivity { *; }
-keep class com.sansoft.harmonystram.PlaybackService { *; }
-keep class com.sansoft.harmonystram.PlaybackWidgetProvider { *; }

# Preserve NewPipe extractor interfaces used at runtime.
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**

# Rhino includes desktop-only JavaBeans adapters (java.beans.*) that are not
# available on Android. NewPipe does not execute those adapter paths on Android,
# so suppressing these warnings avoids false-positive missing class failures.
-dontwarn java.beans.**
