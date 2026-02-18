package com.sansoft.harmonystram;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private boolean attemptedFileSchemeFallback = false;

    private final BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if (!PlaybackService.ACTION_MEDIA_CONTROL.equals(intent.getAction())) return;

            String action = intent.getStringExtra("action");
            if (action == null) return;

            switch (action) {
                case PlaybackService.ACTION_PREVIOUS:
                    dispatchToWeb("previous");
                    break;
                case PlaybackService.ACTION_PLAY_PAUSE:
                    dispatchToWeb("playpause");
                    break;
                case PlaybackService.ACTION_NEXT:
                    dispatchToWeb("next");
                    break;
                default:
                    break;
            }
        }
    };

    private void dispatchToWeb(String action) {
        if (webView == null) return;
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('harmonystream-native-action', { detail: { action: '" + action + "' } }));",
                null
        ));
    }

    private void setImmersiveMode(boolean enabled) {
        WindowInsetsControllerCompat windowInsetsController =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (windowInsetsController == null) return;

        if (enabled) {
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
            windowInsetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setImmersiveMode(true);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setNeedInitialFocus(true);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                // Serve bundled web build files from the app asset root.
                // This supports routes like /, /search/, /_next/* and keeps client-side navigation working.
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                // Keep legacy /assets/* requests working for older builds.
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.addJavascriptInterface(new NativeBridge(), "HarmonyAndroidBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return assetLoader.shouldInterceptRequest(android.net.Uri.parse(url));
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request == null || !request.isForMainFrame() || attemptedFileSchemeFallback) {
                    return;
                }

                if (error != null && error.getErrorCode() == WebViewClient.ERROR_HOST_LOOKUP) {
                    attemptedFileSchemeFallback = true;
                    view.loadUrl("file:///android_asset/index.html");
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("https://appassets.androidplatform.net/index.html");

        IntentFilter mediaFilter = new IntentFilter(PlaybackService.ACTION_MEDIA_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaActionReceiver, mediaFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaActionReceiver, mediaFilter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setImmersiveMode(true);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(mediaActionReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (webView != null) {
            webView.removeJavascriptInterface("HarmonyAndroidBridge");
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class NativeBridge {
        @JavascriptInterface
        public void updatePlaybackState(
                String title,
                String artist,
                boolean playing,
                double positionSeconds,
                double durationSeconds,
                @Nullable String artworkBase64
        ) {
            Intent serviceIntent = new Intent(MainActivity.this, PlaybackService.class);
            serviceIntent.setAction(PlaybackService.ACTION_UPDATE_STATE);
            serviceIntent.putExtra("title", title);
            serviceIntent.putExtra("artist", artist);
            serviceIntent.putExtra("playing", playing);
            serviceIntent.putExtra("position_ms", Math.max(0L, (long) (positionSeconds * 1000)));
            serviceIntent.putExtra("duration_ms", Math.max(0L, (long) (durationSeconds * 1000)));
            serviceIntent.putExtra("artwork_base64", artworkBase64);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }

        @JavascriptInterface
        public void setFullscreen(boolean enabled) {
            runOnUiThread(() -> setImmersiveMode(enabled));
        }
    }
}
