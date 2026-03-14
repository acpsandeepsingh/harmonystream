package com.sansoft.harmonystram;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.webkit.MimeTypeMap;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

final class WebViewManager {

    static final String BUNDLED_HOME_URL = "https://appassets.androidplatform.net/";
    static final String BUNDLED_HOME_URL_BASE_PATH =
            "https://appassets.androidplatform.net/index.html";
    static final String FALLBACK_SHELL_URL =
            "https://appassets.androidplatform.net/assets/web/offline_shell.html";
    static final String BUNDLED_HOME_ASSET_PATH =
            "public/index.html";

    interface BridgeActions {
        void sendServiceIntent(@NonNull Intent intent);
        void setVideoMode(boolean enabled);
        void dispatchToWeb(@NonNull String js);
    }

    private final WebAppActivity activity;
    private final WebView webView;
    private final BridgeActions actions;

    private final WebViewAssetLoader assetLoader;
    private boolean loadingFallback;

    WebViewManager(@NonNull WebAppActivity activity,
                   @NonNull WebView webView,
                   @NonNull BridgeActions actions) {
        this.activity = activity;
        this.webView = webView;
        this.actions = actions;
        String[] nextCandidates = new String[] {
                "public/_next/",
                "_next/",
                "public/next/",
                "next/",
                "public/harmonystream/_next/",
                "harmonystream/_next/",
                "public/harmonystream/next/",
                "harmonystream/next/"
        };

        this.assetLoader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(activity))
                .addPathHandler("/_next/", new MultiPathAssetsHandler(nextCandidates))
                .addPathHandler("/harmonystream/_next/", new MultiPathAssetsHandler(nextCandidates))
                .addPathHandler("/next/", new MultiPathAssetsHandler(nextCandidates))
                .addPathHandler("/harmonystream/next/", new MultiPathAssetsHandler(nextCandidates))
                .addPathHandler("/harmonystream/", new PublicAssetsPathHandler("harmonystream/"))
                .addPathHandler("/", new PublicRoutesPathHandler())
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
    if (startUrl != null && !startUrl.trim().isEmpty()) {
        webView.loadUrl(startUrl.trim());
        return;
    }
    webView.loadUrl(BUNDLED_HOME_URL_BASE_PATH);
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

    }

    private final class MultiPathAssetsHandler implements WebViewAssetLoader.PathHandler {
        private final String[] candidatePrefixes;

        MultiPathAssetsHandler(String[] candidatePrefixes) {
            this.candidatePrefixes = candidatePrefixes;
        }

        @Override
        public WebResourceResponse handle(String path) {
            String normalizedPath = normalize(path);
            String mime = detectMime(normalizedPath);

            for (String prefix : candidatePrefixes) {
                InputStream stream = openAsset(activity.getAssets(), prefix + normalizedPath);
                if (stream != null) {
                    return new WebResourceResponse(mime, "UTF-8", stream);
                }
            }

            return null;
        }
    }

    private final class PublicAssetsPathHandler implements WebViewAssetLoader.PathHandler {
        private final String assetPrefix;

        PublicAssetsPathHandler(String assetPrefix) {
            this.assetPrefix = assetPrefix;
        }

        @Override
        public WebResourceResponse handle(String path) {
            String normalizedPath = normalize(path);
            String primary = "public/" + assetPrefix + normalizedPath;
            String fallback = assetPrefix + normalizedPath;
            InputStream stream = openAssetWithFallback(activity.getAssets(), primary, fallback);
            if (stream == null) {
                return null;
            }
            return new WebResourceResponse(detectMime(primary), "UTF-8", stream);
        }
    }

    private final class PublicRoutesPathHandler implements WebViewAssetLoader.PathHandler {
        @Override
        public WebResourceResponse handle(String path) {
            String normalizedPath = normalize(path);
            String assetPath;
            if (normalizedPath.isEmpty()) {
                assetPath = "public/index.html";
            } else if (normalizedPath.endsWith("/")) {
                assetPath = "public/" + normalizedPath + "index.html";
            } else if (normalizedPath.contains(".")) {
                assetPath = "public/" + normalizedPath;
            } else {
                assetPath = "public/" + normalizedPath + "/index.html";
            }

            String fallback = assetPath.startsWith("public/")
                    ? assetPath.substring("public/".length())
                    : assetPath;
            InputStream stream = openAssetWithFallback(activity.getAssets(), assetPath, fallback);
            if (stream == null) {
                return null;
            }
            return new WebResourceResponse(detectMime(assetPath), "UTF-8", stream);
        }
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static String detectMime(String path) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(path);
        if (extension == null || extension.isEmpty()) {
            return "application/octet-stream";
        }
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.US));
        return mime == null ? "application/octet-stream" : mime;
    }

    private static InputStream openAssetWithFallback(
            AssetManager assetManager,
            String primary,
            String fallback
    ) {
        InputStream stream = openAsset(assetManager, primary);
        if (stream != null) {
            return stream;
        }
        if (fallback == null || fallback.isEmpty() || fallback.equals(primary)) {
            return null;
        }
        return openAsset(assetManager, fallback);
    }

    private static InputStream openAsset(AssetManager assetManager, String assetPath) {
        try {
            return assetManager.open(assetPath);
        } catch (IOException ignored) {
            return null;
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
        public void seek(double positionMs) {
            seekTo(positionMs);
        }

        @JavascriptInterface
        public void setVolume(double volume) {
            float normalizedVolume = (float) volume;
            if (normalizedVolume > 1f) {
                normalizedVolume = normalizedVolume / 100f;
            }
            normalizedVolume = Math.max(0f, Math.min(1f, normalizedVolume));

            Intent intent = new Intent(activity, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SET_VOLUME);
            intent.putExtra("volume", normalizedVolume);
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
