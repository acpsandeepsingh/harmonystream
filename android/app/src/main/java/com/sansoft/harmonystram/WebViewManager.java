package com.sansoft.harmonystram;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

final class WebViewManager {

    static final String EXTRA_START_URL = "start_url";

    static final String BUNDLED_HOME_URL = "https://appassets.androidplatform.net/";
    static final String BUNDLED_HOME_URL_BASE_PATH =
            "https://appassets.androidplatform.net/public/index.html";
    static final String BUNDLED_HOME_URL_BASE_PATH_WITH_GH_PAGES =
            "https://appassets.androidplatform.net/public/harmonystream/index.html";
    static final String LEGACY_BUNDLED_HOME_URL_BASE_PATH =
            "https://appassets.androidplatform.net/index.html";
    static final String LEGACY_BUNDLED_HOME_URL_BASE_PATH_WITH_GH_PAGES =
            "https://appassets.androidplatform.net/harmonystream/index.html";
    static final String FALLBACK_SHELL_URL =
            "https://appassets.androidplatform.net/web/offline_shell.html";

    interface BridgeActions {
        void sendServiceIntent(@NonNull Intent intent);
        void setVideoMode(boolean enabled);
        void dispatchToWeb(@NonNull String js);
    }

    private final WebAppActivity activity;
    private final WebView webView;
    private final BridgeActions actions;

    private final WebViewAssetLoader assetLoader;
    private int startupCandidateIndex;

    private final String[] startupCandidates = new String[] {
            BUNDLED_HOME_URL_BASE_PATH,
            BUNDLED_HOME_URL_BASE_PATH_WITH_GH_PAGES,
            LEGACY_BUNDLED_HOME_URL_BASE_PATH,
            LEGACY_BUNDLED_HOME_URL_BASE_PATH_WITH_GH_PAGES,
            FALLBACK_SHELL_URL
    };

    WebViewManager(@NonNull WebAppActivity activity,
                   @NonNull WebView webView,
                   @NonNull BridgeActions actions) {
        this.activity = activity;
        this.webView = webView;
        this.actions = actions;
        this.assetLoader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(activity))
                .build();
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    void initialize() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setBackgroundColor(Color.rgb(11, 18, 32));
        webView.addJavascriptInterface(new NativePlaybackBridge(), "HarmonyNative");
        webView.addJavascriptInterface(new NativePlaybackBridge(), "AndroidNative");
        webView.setWebViewClient(new AssetBackedWebViewClient());
        PlaybackService.attachWebView(webView);
    }

    void loadInitialUrl(String startUrl) {
        startupCandidateIndex = 0;
        if (startUrl != null && !startUrl.trim().isEmpty()) {
            String normalized = normalizeLegacyFileUrl(startUrl.trim());
            webView.loadUrl(normalized);
            return;
        }
        webView.loadUrl(startupCandidates[startupCandidateIndex]);
    }

    private String normalizeLegacyFileUrl(@NonNull String url) {
        if (url.startsWith("file:///android_asset/public/harmonystream/index.html")) {
            return BUNDLED_HOME_URL_BASE_PATH_WITH_GH_PAGES;
        }
        if (url.startsWith("file:///android_asset/public/index.html")) {
            return BUNDLED_HOME_URL_BASE_PATH;
        }
        return url;
    }

    void onPause() {
        webView.onPause();
    }

    void onResume() {
        webView.onResume();
    }

    void destroy() {
        PlaybackService.attachWebView(null);
        webView.destroy();
    }

    void dispatchPendingMediaAction(@NonNull String action) {
        actions.dispatchToWeb("window.dispatchEvent(new CustomEvent('nativePlaybackCommand', { detail: { action: "
                + JSONObject.quote(action)
                + " } }));");
        actions.dispatchToWeb("window.__harmonyNativeApplyCommand&&window.__harmonyNativeApplyCommand("
                + JSONObject.quote(action) + ");");
    }

    private final class AssetBackedWebViewClient extends WebViewClientCompat {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        public void onReceivedError(WebView view,
                                    WebResourceRequest request,
                                    android.webkit.WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request == null || !request.isForMainFrame()) {
                return;
            }
            loadNextStartupCandidate(view, request.getUrl() != null ? request.getUrl().toString() : "");
        }

        private void loadNextStartupCandidate(@NonNull WebView view, @NonNull String failingUrl) {
            if (!failingUrl.startsWith(BUNDLED_HOME_URL)) {
                return;
            }
            if (startupCandidateIndex + 1 >= startupCandidates.length) {
                return;
            }
            startupCandidateIndex += 1;
            view.loadUrl(startupCandidates[startupCandidateIndex]);
        }
    }

    private final class NativePlaybackBridge {
        @JavascriptInterface
        public void play(String videoId, String title, String artist, String thumbnailUrl) {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PLAY);
            intent.putExtra("video_id", videoId);
            intent.putExtra("title", title);
            intent.putExtra("artist", artist);
            intent.putExtra("thumbnailUrl", thumbnailUrl);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void pause() {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PAUSE);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void resume() {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PLAY);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void next() {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_NEXT);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void previous() {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PREVIOUS);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void seekTo(double positionMs) {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SEEK);
            intent.putExtra("position_ms", (long) positionMs);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void setQueue(String queueJson) {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SET_QUEUE);
            intent.putExtra("queue_json", queueJson);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void setIndex(int index) {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SET_INDEX);
            intent.putExtra("queue_index", index);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void addToQueue(String queueJson) {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_ADD_TO_QUEUE);
            intent.putExtra("queue_json", queueJson);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void setVideoMode(boolean enabled) {
            actions.setVideoMode(enabled);
        }

        @JavascriptInterface
        public void getState() {
            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_GET_STATE);
            actions.sendServiceIntent(intent);
        }

        @JavascriptInterface
        public void setPlayerBackgroundColor(String color) {
            // no-op: preserved bridge API
        }

        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // permission requested by activity startup flow
            }
        }
    }
}
