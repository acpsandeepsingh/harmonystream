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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

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
        webSettings.setNeedInitialFocus(true);

        webView.addJavascriptInterface(new NativeBridge(), "HarmonyAndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");

        registerReceiver(mediaActionReceiver, new IntentFilter(PlaybackService.ACTION_MEDIA_CONTROL));
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
