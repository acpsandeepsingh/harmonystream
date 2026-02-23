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
