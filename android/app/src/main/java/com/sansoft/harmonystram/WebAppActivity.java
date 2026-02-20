package com.sansoft.harmonystram;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WebAppActivity extends AppCompatActivity {

    public static final String EXTRA_START_URL = "start_url";
    private static final String BUNDLED_HOME_URL = "https://appassets.androidplatform.net/assets/public/index.html";
    private static final String BUNDLED_HOME_URL_BASE_PATH = "https://appassets.androidplatform.net/harmonystream/index.html";
    private static final String FALLBACK_SHELL_URL = "https://appassets.androidplatform.net/assets/web/offline_shell.html";
    private static final String BUNDLED_HOME_ASSET_PATH = "public/index.html";
    private static final String BUNDLED_HOME_ASSET_PATH_BASE_PATH = "public/harmonystream/index.html";
    private static final String EMBEDDED_FALLBACK_HTML = "<!doctype html><html><head><meta charset=\"utf-8\" />"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />"
            + "<title>HarmonyStream</title><style>body{font-family:sans-serif;background:#0b1220;color:#fff;display:flex;"
            + "align-items:center;justify-content:center;height:100vh;margin:0}main{max-width:520px;padding:24px;text-align:center;"
            + "border:1px solid #2a3550;border-radius:12px;background:#131d33}</style></head><body><main><h1>HarmonyStream</h1>"
            + "<p>Bundled app shell is active. Build web assets into <code>android/app/src/main/assets/public</code>"
            + " to load the full web player UI offline.</p></main></body></html>";

    private WebView webView;
    private ProgressBar loadingIndicator;
    private boolean loadingFallbackShell;

    private final BroadcastReceiver serviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !PlaybackService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }
            JSONObject payload = new JSONObject();
            try {
                payload.put("title", intent.getStringExtra("title"));
                payload.put("artist", intent.getStringExtra("artist"));
                payload.put("playing", intent.getBooleanExtra("playing", false));
                payload.put("position_ms", intent.getLongExtra("position_ms", 0));
                payload.put("duration_ms", intent.getLongExtra("duration_ms", 0));
            } catch (JSONException ignored) {
            }
            dispatchToWeb("window.dispatchEvent(new CustomEvent('nativePlaybackState', { detail: " + payload + " }));");
        }
    };

    private final BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !PlaybackService.ACTION_MEDIA_CONTROL.equals(intent.getAction())) {
                return;
            }
            dispatchPendingMediaAction(intent.getStringExtra("action"));
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_app);

        webView = findViewById(R.id.web_app_view);
        loadingIndicator = findViewById(R.id.web_loading_indicator);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/_next/", new PublicAssetsPathHandler("_next/"))
                .addPathHandler("/harmonystream/", new PublicAssetsPathHandler("harmonystream/"))
                .build();

        webView.addJavascriptInterface(new NativePlaybackBridge(), "HarmonyNative");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new HarmonyWebViewClient(assetLoader));

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            String startUrl = getIntent().getStringExtra(EXTRA_START_URL);
            if (startUrl != null && startUrl.startsWith("https://appassets.androidplatform.net/assets/")) {
                webView.loadUrl(startUrl);
            } else {
                webView.loadUrl(resolveBundledEntryUrl());
            }
        }

        Intent stateIntent = new Intent(this, PlaybackService.class);
        stateIntent.setAction(PlaybackService.ACTION_GET_STATE);
        startService(stateIntent);

        IntentFilter filter = new IntentFilter(PlaybackService.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(serviceStateReceiver, filter);
        }

        IntentFilter mediaFilter = new IntentFilter(PlaybackService.ACTION_MEDIA_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaActionReceiver, mediaFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaActionReceiver, mediaFilter);
        }

        dispatchPendingMediaAction(getIntent().getStringExtra(PlaybackService.EXTRA_PENDING_MEDIA_ACTION));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) {
            return;
        }
        dispatchPendingMediaAction(intent.getStringExtra(PlaybackService.EXTRA_PENDING_MEDIA_ACTION));
    }

    private void dispatchPendingMediaAction(String action) {
        if (action == null || action.isEmpty()) {
            return;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("action", action);
        } catch (JSONException ignored) {
        }
        dispatchToWeb("window.dispatchEvent(new CustomEvent('nativePlaybackCommand', { detail: " + payload + " }));");
        clearPendingMediaAction();
    }

    private void clearPendingMediaAction() {
        Intent clearIntent = new Intent(this, PlaybackService.class);
        clearIntent.setAction(PlaybackService.ACTION_CLEAR_PENDING_MEDIA_ACTION);
        startService(clearIntent);
    }

    private void dispatchToWeb(String js) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void loadFallbackShell(WebView view) {
        if (view == null) {
            return;
        }
        if (loadingFallbackShell) {
            view.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/web/", EMBEDDED_FALLBACK_HTML, "text/html", "UTF-8", null);
            return;
        }
        loadingFallbackShell = true;
        view.loadUrl(FALLBACK_SHELL_URL);
    }

    private String resolveBundledEntryUrl() {
        AssetManager assets = getAssets();
        try {
            assets.open(BUNDLED_HOME_ASSET_PATH).close();
            return BUNDLED_HOME_URL;
        } catch (Exception ignored) {
            try {
                assets.open(BUNDLED_HOME_ASSET_PATH_BASE_PATH).close();
                return BUNDLED_HOME_URL_BASE_PATH;
            } catch (Exception ignoredBasePathBuild) {
                return FALLBACK_SHELL_URL;
            }
        }
    }

    private class PublicAssetsPathHandler implements WebViewAssetLoader.PathHandler {
        private final String assetPrefix;

        PublicAssetsPathHandler(String assetPrefix) {
            this.assetPrefix = assetPrefix;
        }

        @Override
        public WebResourceResponse handle(String path) {
            String normalizedPath = path == null ? "" : path;
            if (normalizedPath.startsWith("/")) {
                normalizedPath = normalizedPath.substring(1);
            }
            String assetPath = "public/" + assetPrefix + normalizedPath;
            try {
                String extension = MimeTypeMap.getFileExtensionFromUrl(assetPath);
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mime == null) {
                    mime = "application/octet-stream";
                }
                return new WebResourceResponse(mime, "UTF-8", getAssets().open(assetPath));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private class NativePlaybackBridge {
        @JavascriptInterface
        public void play() { sendCommand(PlaybackService.ACTION_PLAY); }

        @JavascriptInterface
        public void pause() { sendCommand(PlaybackService.ACTION_PAUSE); }

        @JavascriptInterface
        public void next() { sendCommand(PlaybackService.ACTION_NEXT); }

        @JavascriptInterface
        public void previous() { sendCommand(PlaybackService.ACTION_PREVIOUS); }

        @JavascriptInterface
        public void seek(long positionMs) {
            Intent stateIntent = new Intent(WebAppActivity.this, PlaybackService.class);
            stateIntent.setAction(PlaybackService.ACTION_SEEK);
            stateIntent.putExtra("position_ms", Math.max(0L, positionMs));
            stateIntent.putExtra("source", "web");
            startService(stateIntent);
        }

        @JavascriptInterface
        public void setQueue(String queueJson) {
            Intent queueIntent = new Intent(WebAppActivity.this, PlaybackService.class);
            queueIntent.setAction(PlaybackService.ACTION_SET_QUEUE);
            try {
                JSONArray queue = new JSONArray(queueJson == null ? "[]" : queueJson);
                queueIntent.putExtra("queue_json", queue.toString());
                queueIntent.putExtra("source", "web");
            } catch (JSONException ignored) {
                queueIntent.putExtra("queue_json", "[]");
                queueIntent.putExtra("source", "web");
            }
            startService(queueIntent);
        }

        @JavascriptInterface
        public void getState() {
            Intent stateIntent = new Intent(WebAppActivity.this, PlaybackService.class);
            stateIntent.setAction(PlaybackService.ACTION_GET_STATE);
            startService(stateIntent);
        }

        private void sendCommand(String action) {
            Intent serviceIntent = new Intent(WebAppActivity.this, PlaybackService.class);
            serviceIntent.setAction(action);
            serviceIntent.putExtra("source", "web");
            startService(serviceIntent);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(serviceStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(mediaActionReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private class HarmonyWebViewClient extends WebViewClientCompat {
        private final WebViewAssetLoader assetLoader;

        HarmonyWebViewClient(WebViewAssetLoader assetLoader) {
            this.assetLoader = assetLoader;
        }

        @Override
        public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return assetLoader.shouldInterceptRequest(android.net.Uri.parse(url));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceErrorCompat error) {
            super.onReceivedError(view, request, error);
            if (request != null && request.isForMainFrame()) {
                loadFallbackShell(view);
            }
        }


        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            loadFallbackShell(view);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request != null && request.isForMainFrame() && errorResponse != null && errorResponse.getStatusCode() >= 400) {
                loadFallbackShell(view);
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            loadingIndicator.setVisibility(View.GONE);
            if (url != null && url.startsWith("https://appassets.androidplatform.net/assets/web/offline_shell")) {
                loadingFallbackShell = false;
                return;
            }
            if (BUNDLED_HOME_URL.equals(url)) {
                loadingFallbackShell = false;
                return;
            }
            if (url != null && url.contains("appassets.androidplatform.net")) {
                loadingFallbackShell = false;
                return;
            }
            loadFallbackShell(webView);
        }
    }
}
