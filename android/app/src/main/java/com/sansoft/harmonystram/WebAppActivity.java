E " + value);
        });
    }
}
package com.sansoft.harmonystram;

import android.Manifest;
import android.content.Context;
import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ConsoleMessage;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
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
    private static final String BUNDLED_HOME_URL           = "https://appassets.androidplatform.net/";
    private static final String BUNDLED_HOME_URL_BASE_PATH = "https://appassets.androidplatform.net/harmonystream/index.html";
    private static final String FALLBACK_SHELL_URL         = "https://appassets.androidplatform.net/assets/web/offline_shell.html";
    private static final String FALLBACK_SHELL_ASSET_PATH  = "web/offline_shell.html";
    private static final String BUNDLED_HOME_ASSET_PATH    = "public/index.html";
    private static final String BUNDLED_HOME_ASSET_PATH_BASE_PATH = "public/harmonystream/index.html";
    private static final String EMBEDDED_FALLBACK_HTML =
            "<!doctype html><html><head><meta charset=\"utf-8\" />"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />"
            + "<title>HarmonyStream</title><style>body{font-family:sans-serif;background:#0b1220;color:#fff;display:flex;"
            + "align-items:center;justify-content:center;height:100vh;margin:0}main{max-width:520px;padding:24px;text-align:center;"
            + "border:1px solid #2a3550;border-radius:12px;background:#131d33}</style></head><body><main><h1>HarmonyStream</h1>"
            + "<p>Bundled app shell is active. Build web assets into <code>android/app/src/main/assets/public</code>"
            + " to load the full web player UI offline.</p></main></body></html>";

    private static final String TAG              = "HarmonyWebAppActivity";
    private static final String LOGCAT_HINT_TAG  = "HarmonyContentDebug";
    private static final String WEB_CONSOLE_TAG  = "WebConsole";
    private static final long   MAIN_FRAME_TIMEOUT_MS  = 15000L;
    private static final int    REQUEST_CODE_POST_NOTIFICATIONS = 4242;

    private WebView          webView;
    private ProgressBar      loadingIndicator;
    private TextView         seekOverlayIndicator;
    private PlaybackViewModel playbackViewModel;
    private WindowInsetsControllerCompat insetsController;
    private GestureDetector  gestureDetector;
    private PlaybackService  playbackService;
    private boolean          serviceBound;
    private boolean          loadingFallbackShell;
    private boolean          mainFrameLoading;
    private String           mainFrameStartedUrl;
    private final Handler    mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable   mainFrameTimeoutRunnable = this::handleMainFrameTimeout;
    private boolean          playbackActive;

    // FIX #3: videoModeEnabled drives fullscreen â€“ false by default so the
    // app starts with the status bar and nav bar visible.
    private boolean          videoModeEnabled  = false;
    private boolean          doubleTapHandled;

    // -----------------------------------------------------------------------
    // Service connection
    // -----------------------------------------------------------------------
    private final ServiceConnection playbackServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, android.os.IBinder service) {
            if (service instanceof PlaybackService.LocalBinder) {
                playbackService = ((PlaybackService.LocalBinder) service).getService();
                serviceBound    = true;
                playbackViewModel.setSnapshot(playbackService.getCurrentSnapshot());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound    = false;
            playbackService = null;
        }
    };

    // -----------------------------------------------------------------------
    // Broadcast receivers
    // -----------------------------------------------------------------------
    private final BroadcastReceiver serviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !PlaybackService.ACTION_STATE_CHANGED.equals(intent.getAction())) return;

            // FIX #3 â€“ track video mode from service broadcasts and update bars accordingly
            boolean newVideoMode = intent.getBooleanExtra("video_mode", false);
            if (newVideoMode != videoModeEnabled) {
                videoModeEnabled = newVideoMode;
                if (videoModeEnabled) {
                    hideSystemBars();
                } else {
                    showNormalBars();
                }
            }

            JSONObject payload = new JSONObject();
            try {
                payload.put("title",        intent.getStringExtra("title"));
                payload.put("artist",       intent.getStringExtra("artist"));
                playbackActive = intent.getBooleanExtra("playing", false);
                payload.put("playing",      playbackActive);
                payload.put("isPlaying",    playbackActive);
                long positionMs = intent.getLongExtra("position_ms", 0);
                long durationMs = intent.getLongExtra("duration_ms", 0);
                payload.put("position_ms",  positionMs);
                payload.put("currentPosition", positionMs);
                payload.put("duration_ms",  durationMs);
                payload.put("duration",     durationMs);
                payload.put("pending_play", intent.getBooleanExtra("pending_play", false));
                int queueIndex = intent.getIntExtra("queue_index", -1);
                payload.put("queue_index",  queueIndex);
                payload.put("currentIndex", queueIndex);
                payload.put("queue_length", intent.getIntExtra("queue_length", 0));
                boolean videoMode = intent.getBooleanExtra("video_mode", false);
                payload.put("video_mode",   videoMode);
                payload.put("videoMode",    videoMode);
                payload.put("thumbnailUrl", intent.getStringExtra("thumbnailUrl"));
                payload.put("event_ts",     intent.getLongExtra("event_ts", System.currentTimeMillis()));
            } catch (JSONException ignored) {}

            dispatchToWeb("window.dispatchEvent(new CustomEvent('nativePlaybackState', { detail: " + payload + " }));");
            playbackViewModel.updateFromBroadcast(intent);
        }
    };

    private final BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !PlaybackService.ACTION_MEDIA_CONTROL.equals(intent.getAction())) return;
            dispatchPendingMediaAction(intent.getStringExtra("action"));
        }
    };

    // -----------------------------------------------------------------------
    // onCreate
    // -----------------------------------------------------------------------
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_app);

        webView              = findViewById(R.id.web_app_view);
        loadingIndicator     = findViewById(R.id.web_loading_indicator);
        seekOverlayIndicator = findViewById(R.id.seek_overlay_indicator);
        playbackViewModel    = new ViewModelProvider(this).get(PlaybackViewModel.class);

        // FIX #3: Set up insets controller but DO NOT hide bars on start.
        // Bars will only be hidden when video mode is activated.
        insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        showNormalBars(); // Explicitly show bars on launch

        configureVideoGestures();
        requestNotificationPermissionIfNeeded();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setBackgroundColor(Color.rgb(11, 18, 32));

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/_next/", new MultiPathAssetsHandler(new String[]{
                        "public/_next/", "_next/", "public/next/", "next/",
                        "public/harmonystream/_next/", "harmonystream/_next/",
                        "public/harmonystream/next/", "harmonystream/next/"}))
                .addPathHandler("/harmonystream/_next/", new MultiPathAssetsHandler(new String[]{
                        "public/_next/", "_next/", "public/next/", "next/",
                        "public/harmonystream/_next/", "harmonystream/_next/",
                        "public/harmonystream/next/", "harmonystream/next/"}))
                .addPathHandler("/harmonystream/", new PublicRoutesPathHandler("harmonystream/"))
                .addPathHandler("/", new PublicRoutesPathHandler())
                .build();

        NativePlaybackBridge bridge = new NativePlaybackBridge();
        webView.addJavascriptInterface(bridge, "HarmonyNative");
        webView.addJavascriptInterface(bridge, "AndroidNative");
        PlaybackService.attachWebView(webView);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    String jsLine = "JS " + consoleMessage.messageLevel()
                            + " " + consoleMessage.sourceId()
                            + ":" + consoleMessage.lineNumber()
                            + " " + consoleMessage.message();
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
        startPlaybackService(stateIntent);

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
        playbackActive = PlaybackService.readSnapshot(this).playing;
        playbackViewModel.setSnapshot(PlaybackService.readSnapshot(this));

        playbackViewModel.getState().observe(this, state -> {
            if (state != null) playbackActive = state.playing;
        });

        Intent bindIntent = new Intent(this, PlaybackService.class);
        bindService(bindIntent, playbackServiceConnection, Context.BIND_AUTO_CREATE);
    }

    // -----------------------------------------------------------------------
    // FIX #3 â€“ System bar helpers
    // showNormalBars: always visible (audio mode / default)
    // hideSystemBars: immersive (only called when videoModeEnabled == true)
    // -----------------------------------------------------------------------

    /**
     * Show status bar and navigation bar. Called when not in video mode.
     */
    private void showNormalBars() {
        if (insetsController == null) return;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        insetsController.show(WindowInsetsCompat.Type.statusBars()
                | WindowInsetsCompat.Type.navigationBars());
        insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
    }

    /**
     * Hide status bar and navigation bar. Only called when video mode is active.
     */
    private void hideSystemBars() {
        if (!videoModeEnabled) return; // Safety guard â€“ never hide outside video mode
        if (insetsController == null) return;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        insetsController.hide(WindowInsetsCompat.Type.statusBars()
                | WindowInsetsCompat.Type.navigationBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    // -----------------------------------------------------------------------
    // PiP
    // -----------------------------------------------------------------------
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        maybeEnterPictureInPicture();
    }

    private void maybeEnterPictureInPicture() {
        if (!playbackActive || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode()) return;
        PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .build();
        try {
            enterPictureInPictureMode(params);
        } catch (IllegalStateException ignored) {}
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        dispatchToWeb("window.dispatchEvent(new CustomEvent('nativePictureInPictureChanged', { detail: { isInPictureInPictureMode: "
                + isInPictureInPictureMode + " } }));");
    }

    // -----------------------------------------------------------------------
    // Activity lifecycle
    // -----------------------------------------------------------------------
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) return;
        dispatchPendingMediaAction(intent.getStringExtra(PlaybackService.EXTRA_PENDING_MEDIA_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            dispatchToWeb("window.dispatchEvent(new CustomEvent('nativeHostResumed'));");
        }
        // FIX #3: Only re-apply immersive mode if video mode is currently active.
        // In audio mode we always want the bars visible.
        if (videoModeEnabled) {
            hideSystemBars();
        } else {
            showNormalBars();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // FIX #3: Respect video mode state when window regains focus.
            if (videoModeEnabled) {
                hideSystemBars();
            } else {
                showNormalBars();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(playbackServiceConnection);
            serviceBound = false;
        }
        try { unregisterReceiver(serviceStateReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(mediaActionReceiver); }  catch (Exception ignored) {}
        if (webView != null) {
            PlaybackService.attachWebView(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    // -----------------------------------------------------------------------
    // Dispatching helpers
    // -----------------------------------------------------------------------
    private void dispatchPendingMediaAction(String action) {
        if (action == null || action.isEmpty()) return;
        JSONObject payload = new JSONObject();
        try { payload.put("action", action); } catch (JSONException ignored) {}
        dispatchToWeb("window.dispatchEvent(new CustomEvent('nativePlaybackCommand', { detail: " + payload + " }));");
        dispatchToWeb("window.__harmonyNativeApplyCommand && window.__harmonyNativeApplyCommand(" + JSONObject.quote(action) + ");");
        clearPendingMediaAction();
    }

    private void clearPendingMediaAction() {
        Intent clearIntent = new Intent(this, PlaybackService.class);
        clearIntent.setAction(PlaybackService.ACTION_CLEAR_PENDING_MEDIA_ACTION);
        startPlaybackService(clearIntent);
    }

    private void dispatchToWeb(String js) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void startPlaybackService(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // -----------------------------------------------------------------------
    // Gesture support (video seek overlay)
    // -----------------------------------------------------------------------
    private void configureVideoGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!videoModeEnabled) return false;
                float x    = e.getX();
                float midX = webView.getWidth() / 2f;
                long  deltaMs = (x < midX) ? -10_000L : 10_000L;
                doubleTapHandled = true;

                String label = (deltaMs < 0 ? "-" : "+") + "10s";
                if (seekOverlayIndicator != null) {
                    seekOverlayIndicator.setText(label);
                    seekOverlayIndicator.setVisibility(View.VISIBLE);
                    seekOverlayIndicator.postDelayed(
                            () -> seekOverlayIndicator.setVisibility(View.GONE), 800);
                }
                Intent seekIntent = new Intent(WebAppActivity.this, PlaybackService.class);
                seekIntent.setAction(PlaybackService.ACTION_SEEK_RELATIVE);
                seekIntent.putExtra("delta_ms", deltaMs);
                startPlaybackService(seekIntent);
                return true;
            }
        });

        if (webView != null) {
            webView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return false;
            });
        }
    }

    // -----------------------------------------------------------------------
    // Notification permission (Android 13+)
    // -----------------------------------------------------------------------
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Main-frame load timeout
    // -----------------------------------------------------------------------
    private void scheduleMainFrameTimeout() {
        mainHandler.removeCallbacks(mainFrameTimeoutRunnable);
        mainHandler.postDelayed(mainFrameTimeoutRunnable, MAIN_FRAME_TIMEOUT_MS);
    }

    private void clearMainFrameTimeout() {
        mainHandler.removeCallbacks(mainFrameTimeoutRunnable);
    }

    private void handleMainFrameTimeout() {
        if (webView == null || !mainFrameLoading) return;
        String currentUrl  = webView.getUrl();
        String startedUrl  = mainFrameStartedUrl == null ? "unknown" : mainFrameStartedUrl;
        if (currentUrl == null || currentUrl.isEmpty() || "about:blank".equals(currentUrl)) {
            Log.w(TAG, "Main-frame load timed out on empty URL. startedUrl=" + startedUrl);
            loadFallbackShell(webView);
            return;
        }
        if (currentUrl.contains("appassets.androidplatform.net/assets/web/offline_shell.html")) {
            Log.w(TAG, "Fallback shell URL timed out; rendering embedded fallback HTML");
            loadingFallbackShell = true;
            webView.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/assets/web/",
                    EMBEDDED_FALLBACK_HTML, "text/html", "UTF-8", null);
            return;
        }
        Log.w(TAG, "Main-frame load timed out on URL: " + currentUrl + " startedUrl=" + startedUrl);
        loadFallbackShell(webView);
    }

    private void loadFallbackShell(WebView view) {
        if (view == null) return;
        Log.w(TAG, "Loading fallback shell. Existing URL: " + view.getUrl());
        if (loadingFallbackShell) {
            view.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/assets/web/",
                    EMBEDDED_FALLBACK_HTML, "text/html", "UTF-8", null);
            return;
        }
        loadingFallbackShell = true;
        view.loadUrl(FALLBACK_SHELL_URL);
    }

    // -----------------------------------------------------------------------
    // Asset URL resolution
    // -----------------------------------------------------------------------
    private String resolveBundledEntryUrl() {
        if (assetExists(BUNDLED_HOME_ASSET_PATH_BASE_PATH)) {
            return BUNDLED_HOME_URL_BASE_PATH;
        }
        if (assetExists(BUNDLED_HOME_ASSET_PATH)) {
            return BUNDLED_HOME_URL;
        }
        return FALLBACK_SHELL_URL;
    }

    private boolean assetExists(String path) {
        try {
            getAssets().open(path).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Logging helpers
    // -----------------------------------------------------------------------
    private String contentLogPrefix() { return "pkg=" + getPackageName() + " "; }
    private void logContentInfo(String msg)  { Log.i(LOGCAT_HINT_TAG, contentLogPrefix() + msg); }
    private void logContentWarn(String msg)  { Log.w(LOGCAT_HINT_TAG, contentLogPrefix() + msg); }
    private void logContentError(String msg) { Log.e(LOGCAT_HINT_TAG, contentLogPrefix() + msg); }

    private void logStartupLoadPlan(String selectedUrl, String requestedStartUrl) {
        logContentInfo("STARTUP_LOAD_REPORT_BEGIN");
        logContentInfo("startup.requestedStartUrl="        + requestedStartUrl);
        logContentInfo("startup.selectedMainFrameUrl="     + selectedUrl);
        logContentInfo("startup.fallbackShellUrl="         + FALLBACK_SHELL_URL);
        logContentInfo("startup.expectedAsset[1]="         + BUNDLED_HOME_ASSET_PATH);
        logContentInfo("startup.expectedAsset[2]="         + BUNDLED_HOME_ASSET_PATH_BASE_PATH);
        logContentInfo("startup.expectedAsset[3]="         + FALLBACK_SHELL_ASSET_PATH);
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
        boolean exists = assetExists(assetPath);
        logContentInfo("asset.check path=" + assetPath + " exists=" + exists);
    }

    private void logBundledEntrySummary() {
        logContentInfo("bundled.entry resolvedUrl=" + resolveBundledEntryUrl());
    }

    // -----------------------------------------------------------------------
    // WebViewClient
    // -----------------------------------------------------------------------
    private class HarmonyWebViewClient extends WebViewClientCompat {
        private final WebViewAssetLoader loader;
        HarmonyWebViewClient(WebViewAssetLoader loader) { this.loader = loader; }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            WebResourceResponse response = loader.shouldInterceptRequest(request.getUrl());
            if (response != null) return response;
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mainFrameLoading    = true;
            mainFrameStartedUrl = url;
            scheduleMainFrameTimeout();
            if (loadingIndicator != null) loadingIndicator.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mainFrameLoading = false;
            clearMainFrameTimeout();
            if (loadingIndicator != null) loadingIndicator.setVisibility(View.GONE);
            logContentInfo("onPageFinished url=" + url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceErrorCompat error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                logContentError("onReceivedError mainFrame url=" + request.getUrl());
                clearMainFrameTimeout();
                mainFrameLoading = false;
                loadFallbackShell(view);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Asset path helpers
    // -----------------------------------------------------------------------

    /**
     * Serves files from multiple asset sub-directories, trying each in order.
     */
    private class MultiPathAssetsHandler implements WebViewAssetLoader.PathHandler {
        private final String[] prefixes;

        MultiPathAssetsHandler(String[] prefixes) { this.prefixes = prefixes; }

        @Override
        public WebResourceResponse handle(String path) {
            AssetManager assets = getAssets();
            for (String prefix : prefixes) {
                String assetPath = prefix + path;
                try {
                    InputStream is       = assets.open(assetPath);
                    String      mimeType = guessMimeType(path);
                    return new WebResourceResponse(mimeType, "UTF-8", is);
                } catch (IOException ignored) {}
            }
            return null;
        }
    }

    /**
     * Routes top-level paths to the public/ asset directory (and optionally a sub-prefix).
     */
    private class PublicRoutesPathHandler implements WebViewAssetLoader.PathHandler {
        private final String subPrefix;

        PublicRoutesPathHandler() { this.subPrefix = ""; }
        PublicRoutesPathHandler(String subPrefix) { this.subPrefix = subPrefix; }

        @Override
        public WebResourceResponse handle(String path) {
            AssetManager assets = getAssets();
            // Try direct path under public/
            String[] candidates = {
                    "public/" + subPrefix + path,
                    "public/" + path,
                    subPrefix + path,
                    path,
            };
            for (String candidate : candidates) {
                try {
                    InputStream is       = assets.open(candidate);
                    String      mimeType = guessMimeType(path);
                    return new WebResourceResponse(mimeType, "UTF-8", is);
                } catch (IOException ignored) {}
            }
            // SPA fallback: serve index.html for navigational paths
            String[] indexCandidates = {
                    "public/" + subPrefix + "index.html",
                    "public/index.html",
            };
            for (String candidate : indexCandidates) {
                try {
                    InputStream is = assets.open(candidate);
                    return new WebResourceResponse("text/html", "UTF-8", is);
                } catch (IOException ignored) {}
            }
            return null;
        }
    }

    private static String guessMimeType(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) return "text/html";
        String ext = path.substring(dot + 1).toLowerCase();
        switch (ext) {
            case "js":   return "application/javascript";
            case "css":  return "text/css";
            case "html": return "text/html";
            case "json": return "application/json";
            case "png":  return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "svg":  return "image/svg+xml";
            case "webp": return "image/webp";
            case "woff": return "font/woff";
            case "woff2":return "font/woff2";
            case "ttf":  return "font/ttf";
            case "ico":  return "image/x-icon";
            case "mp4":  return "video/mp4";
            case "webm": return "video/webm";
            case "mp3":  return "audio/mpeg";
            default:     return MimeTypeMap.getSingleton()
                                 .getMimeTypeFromExtension(ext) != null
                         ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                         : "application/octet-stream";
        }
    }

    // -----------------------------------------------------------------------
    // JavaScript bridge (inner class, same as original)
    // -----------------------------------------------------------------------
    private class NativePlaybackBridge {

        @JavascriptInterface
        public void play(String videoId, String title, String artist,
                         double durationMs, String thumbnailUrl) {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PLAY);
            intent.putExtra("video_id",     videoId);
            intent.putExtra("title",        title);
            intent.putExtra("artist",       artist);
            intent.putExtra("duration_ms",  (long) durationMs);
            intent.putExtra("thumbnailUrl", thumbnailUrl);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void pause() {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PAUSE);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void resume() {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PLAY);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void next() {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_NEXT);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void previous() {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PREVIOUS);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void seekTo(double positionMs) {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SEEK);
            intent.putExtra("position_ms", (long) positionMs);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void setQueue(String queueJson) {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SET_QUEUE);
            intent.putExtra("queue_json", queueJson);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void setIndex(int index) {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SET_INDEX);
            intent.putExtra("queue_index", index);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void setVideoMode(boolean enabled) {
            // FIX #3 + FIX #2: called from JS to switch modes
            videoModeEnabled = enabled;
            if (enabled) {
                hideSystemBars();
            } else {
                showNormalBars();
            }
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SET_MODE);
            intent.putExtra("video_mode", enabled);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void updateState(String title, String artist, boolean playing,
                                double positionMs, double durationMs, String thumbnailUrl) {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_UPDATE_STATE);
            intent.putExtra("title",        title);
            intent.putExtra("artist",       artist);
            intent.putExtra("playing",      playing);
            intent.putExtra("position_ms",  (long) positionMs);
            intent.putExtra("duration_ms",  (long) durationMs);
            intent.putExtra("thumbnailUrl", thumbnailUrl);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void getState() {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_GET_STATE);
            startPlaybackService(intent);
        }
    }
}
