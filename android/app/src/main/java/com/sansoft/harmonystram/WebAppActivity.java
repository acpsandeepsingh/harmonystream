package com.sansoft.harmonystram;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ConsoleMessage;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebAppActivity extends AppCompatActivity {

    public static final String EXTRA_START_URL = "start_url";
    private static final String BUNDLED_HOME_URL = "https://appassets.androidplatform.net/";
    private static final String BUNDLED_HOME_URL_BASE_PATH = "https://appassets.androidplatform.net/harmonystream/index.html";
    private static final String FALLBACK_SHELL_URL = "https://appassets.androidplatform.net/assets/web/offline_shell.html";
    private static final String FALLBACK_SHELL_ASSET_PATH = "web/offline_shell.html";
    private static final String BUNDLED_HOME_ASSET_PATH = "public/index.html";
    private static final String BUNDLED_HOME_ASSET_PATH_BASE_PATH = "public/harmonystream/index.html";
    private static final String EMBEDDED_FALLBACK_HTML = "<!doctype html><html><head><meta charset=\"utf-8\" />"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />"
            + "<title>HarmonyStream</title><style>body{font-family:sans-serif;background:#0b1220;color:#fff;display:flex;"
            + "align-items:center;justify-content:center;height:100vh;margin:0}main{max-width:520px;padding:24px;text-align:center;"
            + "border:1px solid #2a3550;border-radius:12px;background:#131d33}</style></head><body><main><h1>HarmonyStream</h1>"
            + "<p>Bundled app shell is active. Build web assets into <code>android/app/src/main/assets/public</code>"
            + " to load the full web player UI offline.</p></main></body></html>";
    private static final String TAG = "HarmonyWebAppActivity";
    private static final String LOGCAT_HINT_TAG = "HarmonyContentDebug";
    private static final String WEB_CONSOLE_TAG = "WebConsole";
    private static final long MAIN_FRAME_TIMEOUT_MS = 15000L;

    private WebView webView;
    private ProgressBar loadingIndicator;
    private boolean loadingFallbackShell;
    private boolean mainFrameLoading;
    private String mainFrameStartedUrl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mainFrameTimeoutRunnable = this::handleMainFrameTimeout;

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

        webView.setBackgroundColor(Color.rgb(11, 18, 32));

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
<<<<<<< codex/analyze-apk-error-logcat-output-x85szu
                .addPathHandler("/_next/", new MultiPathAssetsHandler(new String[]{"public/_next/", "_next/", "public/next/", "next/", "public/harmonystream/_next/", "harmonystream/_next/", "public/harmonystream/next/", "harmonystream/next/"}))
                .addPathHandler("/harmonystream/_next/", new MultiPathAssetsHandler(new String[]{"public/_next/", "_next/", "public/next/", "next/", "public/harmonystream/_next/", "harmonystream/_next/", "public/harmonystream/next/", "harmonystream/next/"}))
=======
                .addPathHandler("/_next/", new MultiPathAssetsHandler(new String[]{"public/_next/", "_next/", "public/harmonystream/_next/", "harmonystream/_next/"}))
                .addPathHandler("/harmonystream/_next/", new MultiPathAssetsHandler(new String[]{"public/_next/", "_next/", "public/harmonystream/_next/", "harmonystream/_next/"}))
>>>>>>> main
                .addPathHandler("/harmonystream/", new PublicAssetsPathHandler("harmonystream/"))
                .addPathHandler("/", new PublicRoutesPathHandler())
                .build();

        webView.addJavascriptInterface(new NativePlaybackBridge(), "HarmonyNative");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    String jsLine = "JS " + consoleMessage.messageLevel() + " " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + " " + consoleMessage.message();
                    Log.d(TAG, jsLine);
                    Log.d(WEB_CONSOLE_TAG, jsLine);
                    logContentInfo(jsLine);
                }
                return super.onConsoleMessage(consoleMessage);
            }
        });
        webView.setWebViewClient(new HarmonyWebViewClient(assetLoader));

        logStartupDiagnostics(getIntent());
        logBundledEntrySummary();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            String startUrl = getIntent().getStringExtra(EXTRA_START_URL);
            if (startUrl != null && startUrl.startsWith("https://appassets.androidplatform.net/assets/")) {
                Log.i(TAG, "Loading explicit start URL: " + startUrl);
                logStartupLoadPlan(startUrl, startUrl);
                webView.loadUrl(startUrl);
            } else {
                String resolvedUrl = resolveBundledEntryUrl();
                Log.i(TAG, "Loading resolved entry URL: " + resolvedUrl);
                logStartupLoadPlan(resolvedUrl, startUrl);
                webView.loadUrl(resolvedUrl);
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

    private void scheduleMainFrameTimeout() {
        mainHandler.removeCallbacks(mainFrameTimeoutRunnable);
        mainHandler.postDelayed(mainFrameTimeoutRunnable, MAIN_FRAME_TIMEOUT_MS);
    }

    private void clearMainFrameTimeout() {
        mainHandler.removeCallbacks(mainFrameTimeoutRunnable);
    }

    private void handleMainFrameTimeout() {
        if (webView == null) {
            return;
        }
        if (!mainFrameLoading) {
            return;
        }
        String currentUrl = webView.getUrl();
        String startedUrl = mainFrameStartedUrl == null ? "unknown" : mainFrameStartedUrl;
        if (currentUrl == null || currentUrl.isEmpty() || "about:blank".equals(currentUrl)) {
            Log.w(TAG, "Main-frame load timed out on empty URL. startedUrl=" + startedUrl + "; forcing fallback shell");
            loadFallbackShell(webView);
            return;
        }
        if (currentUrl.contains("appassets.androidplatform.net/assets/web/offline_shell.html")) {
            Log.w(TAG, "Fallback shell URL timed out; rendering embedded fallback HTML");
            loadingFallbackShell = true;
            webView.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/web/", EMBEDDED_FALLBACK_HTML, "text/html", "UTF-8", null);
            return;
        }
        Log.w(TAG, "Main-frame load timed out on URL: " + currentUrl + " startedUrl=" + startedUrl + "; forcing fallback shell");
        loadFallbackShell(webView);
    }

    private void loadFallbackShell(WebView view) {
        if (view == null) {
            return;
        }
        Log.w(TAG, "Loading fallback shell. Existing URL: " + view.getUrl());
        if (loadingFallbackShell) {
            view.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/web/", EMBEDDED_FALLBACK_HTML, "text/html", "UTF-8", null);
            return;
        }
        loadingFallbackShell = true;
        view.loadUrl(FALLBACK_SHELL_URL);
    }

    private String contentLogPrefix() {
        return "pkg=" + getPackageName() + " ";
    }

    private void logContentInfo(String message) {
        Log.i(LOGCAT_HINT_TAG, contentLogPrefix() + message);
    }

    private void logContentWarn(String message) {
        Log.w(LOGCAT_HINT_TAG, contentLogPrefix() + message);
    }

    private void logContentError(String message) {
        Log.e(LOGCAT_HINT_TAG, contentLogPrefix() + message);
    }

    private void logStartupLoadPlan(String selectedUrl, String requestedStartUrl) {
        logContentInfo("STARTUP_LOAD_REPORT_BEGIN");
        logContentInfo("startup.requestedStartUrl=" + requestedStartUrl);
        logContentInfo("startup.selectedMainFrameUrl=" + selectedUrl);
        logContentInfo("startup.fallbackShellUrl=" + FALLBACK_SHELL_URL);
        logContentInfo("startup.expectedAsset[1]=" + BUNDLED_HOME_ASSET_PATH);
        logContentInfo("startup.expectedAsset[2]=" + BUNDLED_HOME_ASSET_PATH_BASE_PATH);
        logContentInfo("startup.expectedAsset[3]=" + FALLBACK_SHELL_ASSET_PATH);
        logContentInfo("startup.expectedRequestPattern[1]=https://appassets.androidplatform.net/");
        logContentInfo("startup.expectedRequestPattern[2]=https://appassets.androidplatform.net/_next/");
        logContentInfo("startup.expectedRequestPattern[3]=https://appassets.androidplatform.net/harmonystream/");
        logContentInfo("STARTUP_LOAD_REPORT_END");
    }

    private void logStartupDiagnostics(Intent launchIntent) {
        String requestedStartUrl = launchIntent == null ? null : launchIntent.getStringExtra(EXTRA_START_URL);
        logContentInfo("Startup diagnostics requestedStartUrl=" + requestedStartUrl);
        logAssetPresence(BUNDLED_HOME_ASSET_PATH);
        logAssetPresence(BUNDLED_HOME_ASSET_PATH_BASE_PATH);
        logAssetPresence(FALLBACK_SHELL_ASSET_PATH);
    }

    private void logAssetPresence(String assetPath) {
        try {
            getAssets().open(assetPath).close();
            logContentInfo("Asset available: " + assetPath);
        } catch (Exception exception) {
            logContentError("Asset missing: " + assetPath + " reason=" + exception.getClass().getSimpleName());
        }
    }

    private String resolveBundledEntryUrl() {
        AssetManager assets = getAssets();
        try {
            assets.open(BUNDLED_HOME_ASSET_PATH).close();
            Log.i(LOGCAT_HINT_TAG, "Resolved bundled entry from asset: " + BUNDLED_HOME_ASSET_PATH);
            return BUNDLED_HOME_URL;
        } catch (Exception ignored) {
            Log.w(LOGCAT_HINT_TAG, "Missing bundled entry asset: " + BUNDLED_HOME_ASSET_PATH);
            try {
                assets.open(BUNDLED_HOME_ASSET_PATH_BASE_PATH).close();
                Log.i(LOGCAT_HINT_TAG, "Resolved bundled entry from base-path asset: " + BUNDLED_HOME_ASSET_PATH_BASE_PATH);
                return BUNDLED_HOME_URL_BASE_PATH;
            } catch (Exception ignoredBasePathBuild) {
                Log.e(LOGCAT_HINT_TAG, "Missing base-path bundled entry asset: " + BUNDLED_HOME_ASSET_PATH_BASE_PATH + "; falling back to offline shell");
                return FALLBACK_SHELL_URL;
            }
        }
    }

    private void logBundledEntrySummary() {
        try {
            String html = readAssetText(BUNDLED_HOME_ASSET_PATH, 32 * 1024);
            if (html == null) {
                logContentWarn("Bundled entry summary: unable to read " + BUNDLED_HOME_ASSET_PATH);
                return;
            }
            logContentInfo("Bundled entry length=" + html.length());
            logContentInfo("Bundled entry has '/_next/': " + html.contains("/_next/"));
            logContentInfo("Bundled entry has '/harmonystream/_next/': " + html.contains("/harmonystream/_next/"));
            logContentInfo("Bundled entry has '/harmonystream/': " + html.contains("/harmonystream/"));
            List<String> discoveredAssets = extractBundledAssetRefs(html);
            if (discoveredAssets.isEmpty()) {
                logContentWarn("Bundled entry summary: no script/link refs discovered in " + BUNDLED_HOME_ASSET_PATH);
            } else {
                for (int i = 0; i < discoveredAssets.size(); i++) {
                    logContentInfo("Bundled entry ref[" + (i + 1) + "]=" + discoveredAssets.get(i));
                }
            }
        } catch (Exception exception) {
            logContentError("Bundled entry summary failed reason=" + exception.getClass().getSimpleName() + " message=" + exception.getMessage());
        }
    }

    private String readAssetText(String assetPath, int byteLimit) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = getAssets().open(assetPath);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (total + read > byteLimit) {
                    int allowed = byteLimit - total;
                    if (allowed > 0) {
                        output.write(buffer, 0, allowed);
                    }
                    break;
                }
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private List<String> extractBundledAssetRefs(String html) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(?:src|href)=\"([^\"]+)\"").matcher(html);
        while (matcher.find() && refs.size() < 30) {
            String value = matcher.group(1);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (value.contains("_next") || value.contains("harmonystream") || value.endsWith(".js") || value.endsWith(".css")) {
                refs.add(value);
            }
        }
        return new ArrayList<>(refs);
    }

    private class MultiPathAssetsHandler implements WebViewAssetLoader.PathHandler {
        private final String[] candidatePrefixes;

        MultiPathAssetsHandler(String[] candidatePrefixes) {
            this.candidatePrefixes = candidatePrefixes;
        }

        @Override
        public WebResourceResponse handle(String path) {
            String normalizedPath = path == null ? "" : path;
            if (normalizedPath.startsWith("/")) {
                normalizedPath = normalizedPath.substring(1);
            }

            String mime = "application/octet-stream";
            String extension = MimeTypeMap.getFileExtensionFromUrl(normalizedPath);
            String resolvedMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (resolvedMime != null) {
                mime = resolvedMime;
            }

            for (String prefix : candidatePrefixes) {
                String candidatePath = prefix + normalizedPath;
                try {
                    InputStream stream = getAssets().open(candidatePath);
                    logContentInfo("MultiPathAssetsHandler resolved requestPath=" + path + " assetPath=" + candidatePath);
                    return new WebResourceResponse(mime, "UTF-8", stream);
                } catch (Exception ignored) {
                }
            }

            logContentWarn("MultiPathAssetsHandler miss requestPath=" + path + " candidates=" + java.util.Arrays.toString(candidatePrefixes));
            return null;
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
            String publicAssetPath = "public/" + assetPrefix + normalizedPath;
            String rootAssetPath = assetPrefix + normalizedPath;
            String extension = MimeTypeMap.getFileExtensionFromUrl(publicAssetPath);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime == null) {
                mime = "application/octet-stream";
            }

            InputStream assetStream = openAssetWithFallback(publicAssetPath, rootAssetPath);
            if (assetStream != null) {
                return new WebResourceResponse(mime, "UTF-8", assetStream);
            }

            Log.w(LOGCAT_HINT_TAG, "Asset miss (public assets): " + publicAssetPath + " requestedPath=" + path);
            return null;
        }
    }

    private class PublicRoutesPathHandler implements WebViewAssetLoader.PathHandler {
        @Override
        public WebResourceResponse handle(String path) {
            String normalizedPath = path == null ? "" : path;
            if (normalizedPath.startsWith("/")) {
                normalizedPath = normalizedPath.substring(1);
            }

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

            String extension = MimeTypeMap.getFileExtensionFromUrl(assetPath);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime == null) {
                mime = "application/octet-stream";
            }

            String rootAssetPath = assetPath.startsWith("public/") ? assetPath.substring("public/".length()) : assetPath;
            InputStream assetStream = openAssetWithFallback(assetPath, rootAssetPath);
            if (assetStream != null) {
                return new WebResourceResponse(mime, "UTF-8", assetStream);
            }

            Log.w(LOGCAT_HINT_TAG, "Asset miss (public route): " + assetPath + " requestedPath=" + path);
            return null;
        }
    }

    private InputStream openAssetWithFallback(String primaryPath, String fallbackPath) {
        try {
            return getAssets().open(primaryPath);
        } catch (Exception ignoredPrimary) {
            if (fallbackPath == null || fallbackPath.isEmpty() || fallbackPath.equals(primaryPath)) {
                return null;
            }

            try {
                Log.i(LOGCAT_HINT_TAG, "Falling back to root asset path: " + fallbackPath + " (primary missing: " + primaryPath + ")");
                return getAssets().open(fallbackPath);
            } catch (Exception ignoredFallback) {
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
        clearMainFrameTimeout();
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
            if (request == null || request.getUrl() == null) {
                return null;
            }
            android.net.Uri requestUri = request.getUrl();
            String method = request.getMethod();
            boolean isMainFrame = request.isForMainFrame();
            logContentInfo("Intercept request method=" + method + " url=" + requestUri + " isMainFrame=" + isMainFrame);
            android.webkit.WebResourceResponse response = assetLoader.shouldInterceptRequest(requestUri);
            if (response == null) {
                Log.w(LOGCAT_HINT_TAG, "No intercept match for request: " + requestUri + " method=" + method);
            } else {
                logContentInfo("Intercept hit url=" + requestUri + " mime=" + response.getMimeType() + " status=" + response.getStatusCode());
            }
            return response;
        }

        @Override
        public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            logContentInfo("Intercept legacy-request url=" + url);
            android.webkit.WebResourceResponse response = assetLoader.shouldInterceptRequest(android.net.Uri.parse(url));
            if (response == null) {
                logContentWarn("Intercept legacy miss url=" + url);
            }
            return response;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceErrorCompat error) {
            if (request != null && request.isForMainFrame()) {
                CharSequence errorDescription = error == null ? "unknown" : error.getDescription();
                int errorCode = error == null ? -1 : error.getErrorCode();
                Log.e(TAG, "Main-frame error code=" + errorCode + " url=" + request.getUrl() + " description=" + errorDescription);
                logContentError("Main-frame error code=" + errorCode + " url=" + request.getUrl() + " description=" + errorDescription);
                loadFallbackShell(view);
            }
        }

       
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request != null && request.isForMainFrame() && errorResponse != null && errorResponse.getStatusCode() >= 400) {
                Log.e(TAG, "Main-frame HTTP error code=" + errorResponse.getStatusCode() + " url=" + request.getUrl());
                logContentError("Main-frame HTTP error code=" + errorResponse.getStatusCode() + " url=" + request.getUrl());
                loadFallbackShell(view);
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            loadingIndicator.setVisibility(View.VISIBLE);
            mainFrameLoading = true;
            mainFrameStartedUrl = url;
            scheduleMainFrameTimeout();
            Log.d(TAG, "Page started: " + url);
            logContentInfo("Page started: " + url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mainFrameLoading = false;
            clearMainFrameTimeout();
            loadingIndicator.setVisibility(View.GONE);
            Log.d(TAG, "Page finished: " + url);
            logContentInfo("Page finished: " + url);
            runDomProbe(view, "onPageFinished");
            if (url != null && url.contains("appassets.androidplatform.net")) {
                loadingFallbackShell = false;
                return;
            }
            if (url == null || "about:blank".equals(url)) {
                Log.w(TAG, "Main-frame finished with blank URL; forcing fallback shell");
                logContentWarn("Main-frame finished with blank URL; forcing fallback shell");
                loadFallbackShell(webView);
                return;
            }
            Log.w(TAG, "Main-frame finished with non-appassets URL " + url + "; forcing fallback shell");
            logContentWarn("Main-frame finished with non-appassets URL " + url + "; forcing fallback shell");
            loadFallbackShell(webView);
        }
    }

    private void runDomProbe(WebView view, String stage) {
        if (view == null) {
            return;
        }
        String js = "(function(){try{var scripts=document.scripts?document.scripts.length:0;var links=document.querySelectorAll('link').length;var body=document.body?document.body.innerText.slice(0,300):'';var state=(window.__NEXT_DATA__?'has_next_data':'no_next_data');return JSON.stringify({stage:'" + stage + "',url:location.href,readyState:document.readyState,title:document.title,scripts:scripts,links:links,state:state,body:body});}catch(e){return JSON.stringify({stage:'" + stage + "',probe_error:String(e)});}})();";
        view.evaluateJavascript(js, value -> {
            Log.i(WEB_CONSOLE_TAG, "DOM_PROBE " + value);
            logContentInfo("DOM_PROBE " + value);
        });
    }
}
