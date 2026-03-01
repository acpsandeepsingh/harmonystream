package com.sansoft.harmonystram;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.view.Gravity;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.google.android.exoplayer2.ui.PlayerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

public class WebAppActivity extends AppCompatActivity {

    public static final String EXTRA_START_URL = "start_url";

    private static final String BUNDLED_HOME_URL =
            "https://appassets.androidplatform.net/";
    private static final String BUNDLED_HOME_URL_BASE_PATH =
            "https://appassets.androidplatform.net/harmonystream/index.html";
    private static final String FALLBACK_SHELL_URL =
            "https://appassets.androidplatform.net/assets/web/offline_shell.html";
    private static final String FALLBACK_SHELL_ASSET_PATH =
            "web/offline_shell.html";
    private static final String BUNDLED_HOME_ASSET_PATH =
            "public/index.html";
    private static final String BUNDLED_HOME_ASSET_PATH_BASE_PATH =
            "public/harmonystream/index.html";

    private static final String EMBEDDED_FALLBACK_HTML =
            "<!doctype html><html><head><meta charset=\"utf-8\" />"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />"
            + "<title>HarmonyStream</title>"
            + "<style>body{font-family:sans-serif;background:#0b1220;color:#fff;"
            + "display:flex;align-items:center;justify-content:center;"
            + "height:100vh;margin:0}"
            + "main{max-width:520px;padding:24px;text-align:center;"
            + "border:1px solid #2a3550;border-radius:12px;background:#131d33}"
            + "</style></head><body><main><h1>HarmonyStream</h1>"
            + "<p>Build web assets into <code>android/app/src/main/assets/public</code>"
            + " to load the full UI offline.</p></main></body></html>";

    private static final String TAG             = "HarmonyWebAppActivity";
    private static final String LOGCAT_HINT_TAG = "HarmonyContentDebug";
    private static final String WEB_CONSOLE_TAG = "WebConsole";
    private static final String PLAYER_DEBUG_TAG = "PLAYER_DEBUG";
    private static final long   MAIN_FRAME_TIMEOUT_MS = 15000L;
    private static final int    REQUEST_CODE_POST_NOTIFICATIONS = 4242;
    private static final int    REQUEST_CODE_READ_EXTERNAL_STORAGE = 4243;
    private static final boolean NATIVE_PLAYER_UI_PREVIEW = false;

    // -------------------------------------------------------------------------
    // Views / state
    // -------------------------------------------------------------------------
    private WebView          webView;
    private ProgressBar      loadingIndicator;
    private TextView         seekOverlayIndicator;
    private TextView         nativePlayerTitle;
    private TextView         nativePlayerArtist;
    private TextView         nativePlayerTimeCurrent;
    private TextView         nativePlayerTimeDuration;
    private FrameLayout      playerContainer;
    private PlayerView       nativePlayerView;
    private ImageButton      btnPlay;
    private ImageButton      btnNext;
    private ImageButton      btnPrev;
    private ImageButton      btnMode;
    private ImageButton      btnQueue;
    private ImageButton      btnAdd;
    private SeekBar          seekBar;
    private SeekBar          volumeBar;
    private boolean          isSeeking;
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
    private boolean          lastKnownVideoMode;
    private static final long CONTROLS_AUTO_HIDE_DELAY_MS = 3000L;
    private final Runnable controlsAutoHideRunnable = this::autoHidePlayerOverlayIfNeeded;

    // FIX #3: Default false – bars visible until user enters video mode.
    private boolean          videoModeEnabled = false;
    private long             lastProgressMs;
    private long             lastBridgePlayRequestedAtMs;
    private String           lastBridgePlaySignature = "";

    // -------------------------------------------------------------------------
    // Service connection
    // -------------------------------------------------------------------------
    private final ServiceConnection playbackServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, android.os.IBinder service) {
            if (service instanceof PlaybackService.LocalBinder) {
                playbackService = ((PlaybackService.LocalBinder) service).getService();
                serviceBound    = true;
                playbackViewModel.setSnapshot(playbackService.getCurrentSnapshot());
                attachNativePlayer();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound    = false;
            playbackService = null;
            if (nativePlayerView != null) nativePlayerView.setPlayer(null);
        }
    };

    // -------------------------------------------------------------------------
    // Broadcast receivers
    // -------------------------------------------------------------------------
    private final BroadcastReceiver serviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null
                    || !PlaybackService.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }

            // FIX #3: Track video mode and update system bars accordingly.
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
                long posMs = intent.getLongExtra("position_ms", 0);
                long durMs = intent.getLongExtra("duration_ms", 0);
                payload.put("position_ms",     posMs);
                payload.put("currentPosition", posMs);
                payload.put("duration_ms",     durMs);
                payload.put("duration",        durMs);
                payload.put("pending_play",    intent.getBooleanExtra("pending_play", false));
                int queueIndex = intent.getIntExtra("queue_index", -1);
                payload.put("queue_index",  queueIndex);
                payload.put("currentIndex", queueIndex);
                payload.put("queue_length", intent.getIntExtra("queue_length", 0));
                boolean vm = intent.getBooleanExtra("video_mode", false);
                payload.put("video_mode",   vm);
                payload.put("videoMode",    vm);
                payload.put("thumbnailUrl", intent.getStringExtra("thumbnailUrl"));
                payload.put("event_ts",     intent.getLongExtra("event_ts",
                        System.currentTimeMillis()));

                if (durMs > 0 && posMs >= durMs && !playbackActive) {
                    dispatchToWeb("window.dispatchEvent(new CustomEvent('nativePlaybackCompleted'));");
                }
            } catch (JSONException ignored) {}

            updateNativePlayerUi(intent);
            dispatchToWeb("window.dispatchEvent(new CustomEvent("
                    + "'nativePlaybackState', { detail: " + payload + " }));");
            playbackViewModel.updateFromBroadcast(intent);
        }
    };

    private final BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null
                    || !PlaybackService.ACTION_MEDIA_CONTROL.equals(intent.getAction())) {
                return;
            }
            dispatchPendingMediaAction(intent.getStringExtra("action"));
        }
    };

    // -------------------------------------------------------------------------
    // onCreate
    // -------------------------------------------------------------------------
    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_app);
        debugToast("Android version: " + Build.VERSION.SDK_INT);

        webView              = findViewById(R.id.web_app_view);
        loadingIndicator     = findViewById(R.id.web_loading_indicator);
        seekOverlayIndicator = findViewById(R.id.seek_overlay_indicator);
        playerContainer      = findViewById(R.id.player_container);
        playbackViewModel    = new ViewModelProvider(this).get(PlaybackViewModel.class);

        initNativePlayerUi();

        if (NATIVE_PLAYER_UI_PREVIEW) {
            showNativePlayerPreview();
            return;
        }

        // FIX #3: Set up insets controller, show bars normally on launch.
        // Bars are only hidden when video mode is explicitly activated.
        insetsController = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        showNormalBars();

        configureVideoGestures();
        requestNotificationPermissionIfNeeded();
        requestLegacyStoragePermissionIfNeeded();

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
                .addPathHandler("/assets/",
                        new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/_next/",
                        new MultiPathAssetsHandler(new String[]{
                                "public/_next/", "_next/", "public/next/", "next/",
                                "public/harmonystream/_next/", "harmonystream/_next/",
                                "public/harmonystream/next/", "harmonystream/next/"}))
                .addPathHandler("/harmonystream/_next/",
                        new MultiPathAssetsHandler(new String[]{
                                "public/_next/", "_next/", "public/next/", "next/",
                                "public/harmonystream/_next/", "harmonystream/_next/",
                                "public/harmonystream/next/", "harmonystream/next/"}))
                .addPathHandler("/harmonystream/",
                        new PublicRoutesPathHandler("harmonystream/"))
                .addPathHandler("/",
                        new PublicRoutesPathHandler())
                .build();

        NativePlaybackBridge bridge = new NativePlaybackBridge();
        webView.addJavascriptInterface(bridge, "HarmonyNative");
        webView.addJavascriptInterface(bridge, "AndroidNative");
        PlaybackService.attachWebView(webView);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                if (msg != null) {
                    String line = "JS " + msg.messageLevel()
                            + " " + msg.sourceId()
                            + ":" + msg.lineNumber()
                            + " " + msg.message();
                    Log.d(TAG, line);
                    Log.d(WEB_CONSOLE_TAG, line);
                    if (msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e(PLAYER_DEBUG_TAG, "JS ERROR: " + line);
                    }
                    logContentInfo(line);
                }
                return super.onConsoleMessage(msg);
            }
        });
        webView.setWebViewClient(new HarmonyWebViewClient(assetLoader));

        logStartupDiagnostics(getIntent());
        logBundledEntrySummary();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            String startUrl = getIntent().getStringExtra(EXTRA_START_URL);
            if (startUrl != null
                    && startUrl.startsWith(
                            "https://appassets.androidplatform.net/assets/")) {
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

        dispatchPendingMediaAction(
                getIntent().getStringExtra(PlaybackService.EXTRA_PENDING_MEDIA_ACTION));
        playbackActive = PlaybackService.readSnapshot(this).playing;
        playbackViewModel.setSnapshot(PlaybackService.readSnapshot(this));

        playbackViewModel.getState().observe(this, state -> {
            if (state != null) playbackActive = state.playing;
        });

        bindService(new Intent(this, PlaybackService.class),
                playbackServiceConnection, Context.BIND_AUTO_CREATE);
    }

    // -------------------------------------------------------------------------
    // FIX #3: System bar helpers.
    // showNormalBars() is the default (audio mode / app launch).
    // hideSystemBars() is only called when videoModeEnabled == true.
    // -------------------------------------------------------------------------
    private void showNormalBars() {
        if (insetsController == null) return;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        insetsController.show(
                WindowInsetsCompat.Type.statusBars()
                | WindowInsetsCompat.Type.navigationBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH);
    }

    private void hideSystemBars() {
        if (!videoModeEnabled) return; // Safety guard – never hide outside video mode
        if (insetsController == null) return;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        insetsController.hide(
                WindowInsetsCompat.Type.statusBars()
                | WindowInsetsCompat.Type.navigationBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    // -------------------------------------------------------------------------
    // PiP
    // -------------------------------------------------------------------------
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        maybeEnterPictureInPicture();
    }

    private void maybeEnterPictureInPicture() {
        if (!playbackActive
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || isInPictureInPictureMode()) {
            return;
        }
        PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .build();
        try {
            enterPictureInPictureMode(params);
        } catch (IllegalStateException ignored) {}
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPiP, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPiP, newConfig);
        dispatchToWeb("window.dispatchEvent(new CustomEvent("
                + "'nativePictureInPictureChanged',"
                + "{ detail: { isInPictureInPictureMode: " + isInPiP + " } }));");
    }

    // -------------------------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------------------------
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) return;
        dispatchPendingMediaAction(
                intent.getStringExtra(PlaybackService.EXTRA_PENDING_MEDIA_ACTION));
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
        mainHandler.removeCallbacks(controlsAutoHideRunnable);
        if (webView != null) {
            webView.onResume();
            dispatchToWeb(
                    "window.dispatchEvent(new CustomEvent('nativeHostResumed'));");
        }
        // FIX #3: Only enter immersive mode on resume if video mode is active.
        if (videoModeEnabled) {
            hideSystemBars();
        } else {
            showNormalBars();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;
        // FIX #3: Respect video mode state when window regains focus.
        if (videoModeEnabled) {
            hideSystemBars();
        } else {
            showNormalBars();
        }
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            if (nativePlayerView != null) nativePlayerView.setPlayer(null);
            unbindService(playbackServiceConnection);
            serviceBound = false;
        }
        try { unregisterReceiver(serviceStateReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(mediaActionReceiver);  } catch (Exception ignored) {}
        mainHandler.removeCallbacks(controlsAutoHideRunnable);
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

    // -------------------------------------------------------------------------
    // Dispatch helpers
    // -------------------------------------------------------------------------
    private void dispatchPendingMediaAction(String action) {
        if (action == null || action.isEmpty()) return;
        JSONObject payload = new JSONObject();
        try { payload.put("action", action); } catch (JSONException ignored) {}
        dispatchToWeb("window.dispatchEvent(new CustomEvent("
                + "'nativePlaybackCommand', { detail: " + payload + " }));");
        dispatchToWeb("window.__harmonyNativeApplyCommand"
                + "&&window.__harmonyNativeApplyCommand("
                + JSONObject.quote(action) + ");");
        clearPendingMediaAction();
    }

    private void clearPendingMediaAction() {
        Intent i = new Intent(this, PlaybackService.class);
        i.setAction(PlaybackService.ACTION_CLEAR_PENDING_MEDIA_ACTION);
        startPlaybackService(i);
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

    private void initNativePlayerUi() {

    if (playerContainer == null) {
        debugToast("Player inflation failed: playerContainer is null");
        return;
    }

    debugToast("Player layout inflation start");

    try {

        playerContainer.removeAllViews();

        // Correct inflation (attachToRoot = true)
        getLayoutInflater().inflate(
                R.layout.view_native_player,
                playerContainer,
                true
        );

        playerContainer.setVisibility(View.VISIBLE);
        playerContainer.setAlpha(1f);

        debugToast("Player layout inflation success");

        playerContainer.post(() -> {
            int height = playerContainer.getHeight();
            debugToast("Player height: " + height);
            Log.d(PLAYER_DEBUG_TAG, "Player height = " + height);
        });

        // ✅ Bind REAL IDs from player_portrait.xml
        nativePlayerTitle = playerContainer.findViewById(R.id.title);
        nativePlayerArtist = playerContainer.findViewById(R.id.artist);
        nativePlayerTimeCurrent = playerContainer.findViewById(R.id.timeCurrent);
        nativePlayerTimeDuration = playerContainer.findViewById(R.id.timeDuration);

        btnPlay = playerContainer.findViewById(R.id.btnPlay);
        btnNext = playerContainer.findViewById(R.id.btnNext);
        btnPrev = playerContainer.findViewById(R.id.btnPrev);
        btnMode = playerContainer.findViewById(R.id.btnMode);
        btnQueue = playerContainer.findViewById(R.id.btnQueue);
        btnAdd = playerContainer.findViewById(R.id.btnAdd);
        seekBar = playerContainer.findViewById(R.id.seekBar);
        volumeBar = playerContainer.findViewById(R.id.volumeBar);

        // Default placeholder state
        if (nativePlayerTitle != null)
            nativePlayerTitle.setText("No song selected");

        if (nativePlayerArtist != null)
            nativePlayerArtist.setText("-");

        if (btnPlay != null) {
            btnPlay.setEnabled(false);
            btnPlay.setAlpha(0.5f);
        }

        if (btnNext != null) {
            btnNext.setEnabled(false);
            btnNext.setAlpha(0.5f);
        }

        if (btnPrev != null) {
            btnPrev.setEnabled(false);
            btnPrev.setAlpha(0.5f);
        }

        if (seekBar != null) {
            seekBar.setEnabled(false);
        }

        setupNativeControlListeners();
        debugToast("Player UI initialized successfully");

    } catch (Throwable t) {
        debugToast("Player layout inflation failure: " + t.getMessage());
        Log.e(PLAYER_DEBUG_TAG, "Failed to initialize native player UI", t);
    }
    }

    private void updateNativePlayerUi(Intent intent) {
        if (playerContainer == null) return;
        String title = intent.getStringExtra("title");
        String artist = intent.getStringExtra("artist");
        if (nativePlayerTitle != null && title != null && !title.trim().isEmpty()) {
            nativePlayerTitle.setText(title);
        }
        if (nativePlayerArtist != null) {
            nativePlayerArtist.setText(artist != null && !artist.trim().isEmpty() ? artist : "-");
        }

        boolean isPlaying = intent.getBooleanExtra("playing", false);
        boolean hasSong = title != null && !title.trim().isEmpty();
        if (!hasSong && nativePlayerTitle != null) {
            nativePlayerTitle.setText("No song selected");
        }
        if (btnPlay != null) {
            btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            btnPlay.setEnabled(hasSong);
            btnPlay.setAlpha(hasSong ? 1f : 0.5f);
        }
        if (btnNext != null) {
            btnNext.setEnabled(hasSong);
            btnNext.setAlpha(hasSong ? 1f : 0.5f);
        }
        if (btnPrev != null) {
            btnPrev.setEnabled(hasSong);
            btnPrev.setAlpha(hasSong ? 1f : 0.5f);
        }
        if (seekBar != null) {
            seekBar.setEnabled(hasSong);
        }

        playerContainer.setVisibility(View.VISIBLE);
        boolean videoMode = intent.getBooleanExtra("video_mode", false);
        lastKnownVideoMode = videoMode;
        applyPlayerLayoutMode(videoMode);

        long progress = intent.getLongExtra("position_ms", 0L);
        long duration = Math.max(1L, intent.getLongExtra("duration_ms", 0L));
        if (seekBar != null) {
            seekBar.setMax((int) Math.min(Integer.MAX_VALUE, duration));
            if (!isSeeking) {
                seekBar.setProgress((int) Math.min(Integer.MAX_VALUE, progress));
            }
        }
        if (!isSeeking && nativePlayerTimeCurrent != null) {
            nativePlayerTimeCurrent.setText(formatTime(progress));
        }
        if (nativePlayerTimeDuration != null) {
            nativePlayerTimeDuration.setText(formatTime(duration));
        }
        if (progress != lastProgressMs) {
            lastProgressMs = progress;
            dispatchToWeb("window.dispatchEvent(new CustomEvent('nativePlaybackProgress',"
                    + "{ detail: { position_ms: " + progress + " } }));");
        }
    }

    private void applyPlayerLayoutMode(boolean videoMode) {
        if (playerContainer == null || webView == null) return;

        ViewGroup.LayoutParams playerLayoutParams = playerContainer.getLayoutParams();
        if (!(playerLayoutParams instanceof FrameLayout.LayoutParams)) {
            FrameLayout.LayoutParams fallbackParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            fallbackParams.gravity = Gravity.BOTTOM;
            playerContainer.setLayoutParams(fallbackParams);
            playerLayoutParams = fallbackParams;
        }

        FrameLayout.LayoutParams bottomParams = (FrameLayout.LayoutParams) playerLayoutParams;
        bottomParams.gravity = Gravity.BOTTOM;

        if (videoMode) {
    webView.setVisibility(View.VISIBLE);
    playerContainer.setVisibility(View.VISIBLE);
    playerContainer.setAlpha(1f);
    resetOverlayAutoHideTimer();
} else {
    webView.setVisibility(View.GONE);
    playerContainer.setVisibility(View.VISIBLE);
    playerContainer.setAlpha(1f);
    mainHandler.removeCallbacks(controlsAutoHideRunnable);
        }
        playerContainer.setLayoutParams(bottomParams);
    }

    private void setupNativeControlListeners() {
        if (btnPlay != null) {
            btnPlay.setOnClickListener(v -> {
                if (playbackService != null) {
                    Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
                    intent.setAction(playbackService.getCurrentSnapshot().playing
                            ? PlaybackService.ACTION_PAUSE
                            : PlaybackService.ACTION_PLAY);
                    startPlaybackService(intent);
                } else {
                    Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
                    intent.setAction(PlaybackService.ACTION_PLAY_PAUSE);
                    startPlaybackService(intent);
                }
            });
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
                intent.setAction(PlaybackService.ACTION_NEXT);
                startPlaybackService(intent);
            });
        }

        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
                intent.setAction(PlaybackService.ACTION_PREVIOUS);
                startPlaybackService(intent);
            });
        }

        if (btnMode != null) {
            btnMode.setOnClickListener(v -> setVideoMode(!videoModeEnabled));
        }

        if (btnQueue != null) {
            btnQueue.setOnClickListener(v -> dispatchToWeb(
                    "window.dispatchEvent(new CustomEvent('nativeOpenQueue'));"));
        }

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> dispatchToWeb(
                    "window.dispatchEvent(new CustomEvent('nativeAddToPlaylist'));"));
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && nativePlayerTimeCurrent != null) {
                        nativePlayerTimeCurrent.setText(formatTime(progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    isSeeking = false;
                    Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
                    intent.setAction(PlaybackService.ACTION_SEEK);
                    intent.putExtra("position_ms", (long) seekBar.getProgress());
                    startPlaybackService(intent);
                }
            });
        }

        if (volumeBar != null) {
            volumeBar.setMax(100);
            volumeBar.setProgress(100);
            volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
                    intent.setAction(PlaybackService.ACTION_SET_VOLUME);
                    intent.putExtra("volume", progress / 100f);
                    startPlaybackService(intent);
                    dispatchToWeb("window.dispatchEvent(new CustomEvent('nativeVolumeChanged', { detail: { volume: " + progress + " } }));");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) { }
            });
        }
    }

    private void resetOverlayAutoHideTimer() {
        mainHandler.removeCallbacks(controlsAutoHideRunnable);
        if (!videoModeEnabled || playerContainer == null) return;
        mainHandler.postDelayed(controlsAutoHideRunnable, CONTROLS_AUTO_HIDE_DELAY_MS);
    }

    private void toggleOverlayVisibility() {
        if (playerContainer == null || !videoModeEnabled) return;
        if (playerContainer.getVisibility() == View.VISIBLE && playerContainer.getAlpha() > 0.1f) {
            playerContainer.animate().alpha(0f).setDuration(180L)
                    .withEndAction(() -> playerContainer.setVisibility(View.GONE))
                    .start();
            mainHandler.removeCallbacks(controlsAutoHideRunnable);
        } else {
            playerContainer.setVisibility(View.VISIBLE);
            playerContainer.animate().alpha(1f).setDuration(180L).start();
            resetOverlayAutoHideTimer();
        }
    }

    private void autoHidePlayerOverlayIfNeeded() {
        if (!videoModeEnabled || playerContainer == null || playerContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        playerContainer.animate().alpha(0f).setDuration(180L)
                .withEndAction(() -> playerContainer.setVisibility(View.GONE))
                .start();
    }

    private void debugVisibilityState() {
        if (playerContainer == null || webView == null) return;
        debugToast("playerContainer.visibility=" + playerContainer.getVisibility());
        debugToast("webView.visibility=" + webView.getVisibility());
    }

    private void debugToast(String msg) {
        runOnUiThread(() -> {
            Log.d(PLAYER_DEBUG_TAG, msg);
            Toast.makeText(WebAppActivity.this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    private String formatTime(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void injectBackgroundSyncScript(WebView view) {
        if (view == null) return;
        String js = "(function(){"
                + "if(window.__harmonyBgSyncInstalled){return;}"
                + "window.__harmonyBgSyncInstalled=true;"
                + "function sendBg(){"
                + "var body=window.getComputedStyle(document.body).backgroundColor;"
                + "var html=window.getComputedStyle(document.documentElement).backgroundColor;"
                + "var c=(body&&body!=='rgba(0, 0, 0, 0)'&&body!=='transparent')?body:html;"
                + "if(window.HarmonyNative&&window.HarmonyNative.setPlayerBackgroundColor){"
                + "window.HarmonyNative.setPlayerBackgroundColor(c||'#111827');}"
                + "}"
                + "sendBg();"
                + "var mo=new MutationObserver(sendBg);"
                + "mo.observe(document.documentElement,{attributes:true,attributeFilter:['class','style','data-theme']});"
                + "window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').addEventListener&&"
                + "window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change',sendBg);"
                + "setInterval(sendBg,1500);"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    private void disableWebAudioElements(WebView view) {
        if (view == null) return;
        String js = "(function(){"
                + "if(window.__harmonyWebAudioDisabled){return;}"
                + "window.__harmonyWebAudioDisabled=true;"
                + "function muteAll(){"
                + "var nodes=document.querySelectorAll('audio,video');"
                + "for(var i=0;i<nodes.length;i++){try{nodes[i].muted=true;nodes[i].volume=0;nodes[i].pause&&nodes[i].pause();}catch(e){}}"
                + "}"
                + "muteAll();"
                + "setInterval(muteAll,1000);"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    private int parseCssColor(String color) {
        if (color == null) return Color.rgb(17, 24, 39);
        String trimmed = color.trim();
        try {
            if (trimmed.startsWith("rgb")) {
                String raw = trimmed.replace("rgba(", "")
                        .replace("rgb(", "")
                        .replace(")", "");
                String[] parts = raw.split(",");
                if (parts.length >= 3) {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return Color.rgb(r, g, b);
                }
            }
            return Color.parseColor(trimmed);
        } catch (IllegalArgumentException ignored) {
            return Color.rgb(17, 24, 39);
        }
    }

    private void setVideoMode(boolean enabled) {
    videoModeEnabled = enabled;
    lastKnownVideoMode = enabled;

    applyPlayerLayoutMode(enabled);

    if (enabled) {
        hideSystemBars();

        if (playerContainer != null) {
            playerContainer.setVisibility(View.VISIBLE);
            playerContainer.setAlpha(1f);
        }

        resetOverlayAutoHideTimer();

        dispatchToWeb("window.dispatchEvent(new Event('nativeVideoMode'))");

    } else {
        showNormalBars();

        if (playerContainer != null) {
            playerContainer.setVisibility(View.VISIBLE);
            playerContainer.setAlpha(1f);
        }

        mainHandler.removeCallbacks(controlsAutoHideRunnable);

        dispatchToWeb("window.dispatchEvent(new Event('nativeAudioMode'))");
    }
    }
        Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_SET_MODE);
        intent.putExtra("video_mode", enabled);
        startPlaybackService(intent);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        CharSequence currentTitle = nativePlayerTitle != null ? nativePlayerTitle.getText() : null;
        boolean wasVisible = playerContainer != null && playerContainer.getVisibility() == View.VISIBLE;
        initNativePlayerUi();
        if (nativePlayerTitle != null && currentTitle != null) {
            nativePlayerTitle.setText(currentTitle);
        }
        if (wasVisible && playerContainer != null) {
            playerContainer.setVisibility(View.VISIBLE);
        }
        attachNativePlayer();
        applyPlayerLayoutMode(videoModeEnabled);
    }

    // -------------------------------------------------------------------------
    // Video gesture detector (double-tap to seek ±10 s in video mode)
    // -------------------------------------------------------------------------
    private void showNativePlayerPreview() {
        if (loadingIndicator != null) loadingIndicator.setVisibility(View.GONE);
        if (seekOverlayIndicator != null) seekOverlayIndicator.setVisibility(View.GONE);
        if (webView != null) webView.setVisibility(View.GONE);

        FrameLayout previewContainer = findViewById(R.id.player_container);
        if (previewContainer == null) return;
        previewContainer.setVisibility(View.VISIBLE);
        getLayoutInflater().inflate(R.layout.player_native_preview, previewContainer, true);
    }

    private void configureVideoGestures() {
        gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!videoModeEnabled) return false;
                toggleOverlayVisibility();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!videoModeEnabled) return false;
                float x     = e.getX();
                float midX  = webView != null ? webView.getWidth() / 2f : 0;
                long deltaMs = (x < midX) ? -10_000L : 10_000L;
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

        mainHandler.removeCallbacks(controlsAutoHideRunnable);
        if (webView != null) {
            webView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return false;
            });
        }
    }

    // -------------------------------------------------------------------------
    // Notification permission (Android 13+)
    // -------------------------------------------------------------------------
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }
    }

    private void requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_READ_EXTERNAL_STORAGE);
        }
    }

    // -------------------------------------------------------------------------
    // Main-frame load timeout / fallback
    // -------------------------------------------------------------------------
    private void scheduleMainFrameTimeout() {
        mainHandler.removeCallbacks(mainFrameTimeoutRunnable);
        mainHandler.postDelayed(mainFrameTimeoutRunnable, MAIN_FRAME_TIMEOUT_MS);
    }

    private void clearMainFrameTimeout() {
        mainHandler.removeCallbacks(mainFrameTimeoutRunnable);
    }

    private void handleMainFrameTimeout() {
        if (webView == null || !mainFrameLoading) return;
        String currentUrl = webView.getUrl();
        String startedUrl = mainFrameStartedUrl != null ? mainFrameStartedUrl : "unknown";
        if (currentUrl == null || currentUrl.isEmpty()
                || "about:blank".equals(currentUrl)) {
            Log.w(TAG, "Main-frame load timed out on empty URL. startedUrl=" + startedUrl);
            loadFallbackShell(webView);
            return;
        }
        if (currentUrl.contains(
                "appassets.androidplatform.net/assets/web/offline_shell.html")) {
            Log.w(TAG, "Fallback shell timed out; rendering embedded HTML");
            loadingFallbackShell = true;
            webView.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/assets/web/",
                    EMBEDDED_FALLBACK_HTML, "text/html", "UTF-8", null);
            return;
        }
        Log.w(TAG, "Main-frame timed out on: " + currentUrl
                + " startedUrl=" + startedUrl);
        loadFallbackShell(webView);
    }

    private void loadFallbackShell(WebView view) {
        if (view == null) return;
        if (loadingFallbackShell) {
            view.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/assets/web/",
                    EMBEDDED_FALLBACK_HTML, "text/html", "UTF-8", null);
            return;
        }
        loadingFallbackShell = true;
        view.loadUrl(FALLBACK_SHELL_URL);
    }

    // -------------------------------------------------------------------------
    // Asset URL resolution
    // -------------------------------------------------------------------------
    private String resolveBundledEntryUrl() {
        if (assetExists(BUNDLED_HOME_ASSET_PATH_BASE_PATH))
            return BUNDLED_HOME_URL_BASE_PATH;
        if (assetExists(BUNDLED_HOME_ASSET_PATH))
            return BUNDLED_HOME_URL;
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

    // -------------------------------------------------------------------------
    // Logging helpers
    // -------------------------------------------------------------------------
    private String contentLogPrefix() { return "pkg=" + getPackageName() + " "; }

    private void logContentInfo(String msg) {
        Log.i(LOGCAT_HINT_TAG, contentLogPrefix() + msg);
    }

    private void logStartupLoadPlan(String selectedUrl, String requestedStartUrl) {
        logContentInfo("STARTUP_LOAD_REPORT_BEGIN");
        logContentInfo("startup.requestedStartUrl=" + requestedStartUrl);
        logContentInfo("startup.selectedMainFrameUrl=" + selectedUrl);
        logContentInfo("startup.fallbackShellUrl=" + FALLBACK_SHELL_URL);
        logContentInfo("startup.expectedAsset[1]=" + BUNDLED_HOME_ASSET_PATH);
        logContentInfo("startup.expectedAsset[2]=" + BUNDLED_HOME_ASSET_PATH_BASE_PATH);
        logContentInfo("startup.expectedAsset[3]=" + FALLBACK_SHELL_ASSET_PATH);
        logContentInfo("STARTUP_LOAD_REPORT_END");
    }

    private void logStartupDiagnostics(Intent launchIntent) {
        String requestedUrl = launchIntent == null
                ? null : launchIntent.getStringExtra(EXTRA_START_URL);
        logContentInfo("Startup diagnostics requestedStartUrl=" + requestedUrl);
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

    // -------------------------------------------------------------------------
    // WebViewClient
    // -------------------------------------------------------------------------
    private class HarmonyWebViewClient extends WebViewClientCompat {
        private final WebViewAssetLoader loader;

        HarmonyWebViewClient(WebViewAssetLoader loader) { this.loader = loader; }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view,
                                                          WebResourceRequest request) {
            WebResourceResponse response =
                    loader.shouldInterceptRequest(request.getUrl());
            if (response != null) return response;
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mainFrameLoading    = true;
            mainFrameStartedUrl = url;
            scheduleMainFrameTimeout();
            if (loadingIndicator != null)
                loadingIndicator.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mainFrameLoading = false;
            clearMainFrameTimeout();
            if (loadingIndicator != null)
                loadingIndicator.setVisibility(View.GONE);
            injectBackgroundSyncScript(view);
            disableWebAudioElements(view);
            applyPlayerLayoutMode(lastKnownVideoMode);
            debugToast("WebView page loaded");
            logContentInfo("onPageFinished url=" + url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request == null || request.getUrl() == null) {
                return false;
            }

            String url = request.getUrl().toString();
            boolean allowed = url.startsWith(BUNDLED_HOME_URL)
                    || url.startsWith("https://appassets.androidplatform.net/harmonystream/")
                    || url.startsWith("about:blank");

            if (!allowed) {
                Log.w(WEB_CONSOLE_TAG, "Blocked unexpected WebView navigation: " + url);
                return true;
            }
            return false;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceErrorCompat error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                debugToast("onReceivedError");
                logContentInfo("onReceivedError mainFrame url="
                        + request.getUrl());
                clearMainFrameTimeout();
                mainFrameLoading = false;
                loadFallbackShell(view);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Asset path handlers
    // -------------------------------------------------------------------------
    private class MultiPathAssetsHandler implements WebViewAssetLoader.PathHandler {
        private final String[] prefixes;

        MultiPathAssetsHandler(String[] prefixes) { this.prefixes = prefixes; }

        @Override
        public WebResourceResponse handle(String path) {
            AssetManager assets = getAssets();
            for (String prefix : prefixes) {
                try {
                    InputStream is = assets.open(prefix + path);
                    return new WebResourceResponse(guessMimeType(path), "UTF-8", is);
                } catch (IOException ignored) {}
            }
            return null;
        }
    }

    private class PublicRoutesPathHandler implements WebViewAssetLoader.PathHandler {
        private final String subPrefix;

        PublicRoutesPathHandler()               { this.subPrefix = ""; }
        PublicRoutesPathHandler(String prefix)  { this.subPrefix = prefix; }

        @Override
        public WebResourceResponse handle(String path) {
            AssetManager assets = getAssets();
            String[] candidates = {
                    "public/" + subPrefix + path,
                    "public/" + path,
                    subPrefix + path,
                    path,
            };
            for (String candidate : candidates) {
                try {
                    InputStream is = assets.open(candidate);
                    return new WebResourceResponse(guessMimeType(path), "UTF-8", is);
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
            case "js":    return "application/javascript";
            case "css":   return "text/css";
            case "html":  return "text/html";
            case "json":  return "application/json";
            case "png":   return "image/png";
            case "jpg":
            case "jpeg":  return "image/jpeg";
            case "svg":   return "image/svg+xml";
            case "webp":  return "image/webp";
            case "woff":  return "font/woff";
            case "woff2": return "font/woff2";
            case "ttf":   return "font/ttf";
            case "ico":   return "image/x-icon";
            case "mp4":   return "video/mp4";
            case "webm":  return "video/webm";
            case "mp3":   return "audio/mpeg";
            default:
                String fromMap = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(ext);
                return fromMap != null ? fromMap : "application/octet-stream";
        }
    }

    private boolean shouldSuppressDuplicatePlayRequest(String idOrUrl, String mediaType) {
        String key = (idOrUrl == null ? "" : idOrUrl.trim()) + "|"
                + (mediaType == null ? "" : mediaType.trim().toLowerCase());
        long now = SystemClock.elapsedRealtime();
        if (key.equals(lastBridgePlaySignature) && (now - lastBridgePlayRequestedAtMs) < 1200L) {
            return true;
        }
        lastBridgePlaySignature = key;
        lastBridgePlayRequestedAtMs = now;
        return false;
    }
private void attachNativePlayer() {

    if (!serviceBound || playbackService == null) {
        Log.w(PLAYER_DEBUG_TAG, "attachNativePlayer skipped: service not bound");
        return;
    }

    debugToast("attachNativePlayer called");

    // If later you re-add PlayerView support, you can attach player here.
    // For now, nothing is required since UI is custom buttons.

}
    // -------------------------------------------------------------------------
    // JavaScript bridge
    // -------------------------------------------------------------------------
    private class NativePlaybackBridge {

        @JavascriptInterface
        public void play(String videoId, String title, String artist,
                         double durationMs, String thumbnailUrl) {
            if (shouldSuppressDuplicatePlayRequest(videoId, "audio")) return;
            debugToast("Song clicked");
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
        public void loadMedia(String mediaUrl, String mediaType,
                              String title, String artist, String thumbnailUrl) {
            if (shouldSuppressDuplicatePlayRequest(mediaUrl, mediaType)) return;
            debugToast("Song clicked");
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PLAY);
            intent.putExtra("video_id", mediaUrl);
            intent.putExtra("media_type", mediaType);
            intent.putExtra("title", title);
            intent.putExtra("artist", artist);
            intent.putExtra("thumbnailUrl", thumbnailUrl);
            startPlaybackService(intent);
            if ("video".equalsIgnoreCase(mediaType)) {
                setVideoMode(true);
            }
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
        public void seek(double positionMs) {
            seekTo(positionMs);
        }

        @JavascriptInterface
        public void setQueue(String queueJson) {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SET_QUEUE);
            intent.putExtra("queue_json", queueJson);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void addToQueue(String queueJson) {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_ADD_TO_QUEUE);
            intent.putExtra("queue_json", queueJson);
            startPlaybackService(intent);
        }

        @JavascriptInterface
        public void addToPlaylist(String songJson) {
            dispatchToWeb("window.dispatchEvent(new CustomEvent('nativeAddSongToPlaylist', { detail: "
                    + (songJson == null || songJson.trim().isEmpty() ? "{}" : songJson)
                    + " }));");
        }

        @JavascriptInterface
        public void setVolume(double volumePercent) {
            Intent intent = new Intent(WebAppActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_SET_VOLUME);
            intent.putExtra("volume", (float) Math.max(0.0, Math.min(1.0, volumePercent / 100.0)));
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
            // FIX #3 + FIX #2: JS-triggered mode switch updates bars and notifies service.
            WebAppActivity.this.setVideoMode(enabled);
        }

        @JavascriptInterface
        public void setPlayerBackgroundColor(String cssColor) {
            int parsed = parseCssColor(cssColor);
            runOnUiThread(() -> {
                if (playerContainer != null) {
                    playerContainer.setBackgroundColor(parsed);
                }
            });
        }

        @JavascriptInterface
        public void updateState(String title, String artist, boolean playing,
                                double positionMs, double durationMs,
                                String thumbnailUrl) {
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
