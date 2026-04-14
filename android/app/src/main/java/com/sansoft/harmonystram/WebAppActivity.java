package com.sansoft.harmonystram;

import android.Manifest;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.webkit.WebView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class WebAppActivity extends AppCompatActivity
        implements PlaybackObserver.Listener, PlayerUiController.Actions,
        WebViewManager.BridgeActions, GestureController.Callbacks {

    public static final String EXTRA_START_URL = "start_url";
    private static final boolean FORCE_WEB_PLAYER_UI_FOR_TEST = true;

    private WebView webView;
    private FrameLayout playerContainer;
    private TextView seekOverlayIndicator;

    private WindowInsetsControllerCompat insetsController;

    private WebViewManager webViewManager;
    private PlayerUiController playerUiController;
    private PlaybackObserver playbackObserver;
    private GestureController gestureController;

    private boolean playbackActive;
    private boolean videoModeEnabled;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_app);

        webView = findViewById(R.id.web_app_view);
        playerContainer = findViewById(R.id.player_container);
        seekOverlayIndicator = findViewById(R.id.seek_overlay_indicator);
        ProgressBar loadingIndicator = findViewById(R.id.web_loading_indicator);
        if (loadingIndicator != null) loadingIndicator.setVisibility(ProgressBar.GONE);

        webView.setClipToPadding(false);
        playerContainer.setClickable(false);
        playerContainer.setFocusable(false);
        playerContainer.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                   oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top != oldBottom - oldTop) {
                updateWebViewBottomInset();
            }
        });

        insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        showNormalBars();

        playerUiController = new PlayerUiController(this, playerContainer, this);
        playerUiController.init();
        playerContainer.post(this::updateWebViewBottomInset);

        webViewManager = new WebViewManager(this, webView, this);
        webViewManager.initialize();
        if (savedInstanceState == null) {
            webViewManager.loadInitialUrl(getIntent().getStringExtra(EXTRA_START_URL));
        } else {
            webView.restoreState(savedInstanceState);
        }

        gestureController = new GestureController(this, webView, seekOverlayIndicator, this);
        gestureController.attach();

        playbackObserver = new PlaybackObserver(this, this);
        playbackObserver.start();

        requestNotificationPermissionIfNeeded();
        requestInitialPlaybackState();
        playbackActive = PlaybackService.readSnapshot(this).playing;
    }

    private void requestInitialPlaybackState() {
        Intent stateIntent = new Intent(this, PlaybackService.class);
        stateIntent.setAction(PlaybackService.ACTION_GET_STATE);
        sendServiceIntent(stateIntent);
    }

    @Override
    public void onPlaybackStateChanged(@NonNull Intent stateIntent) {
        playbackActive = stateIntent.getBooleanExtra("playing", false);
        videoModeEnabled = stateIntent.getBooleanExtra("video_mode", false);
        playerUiController.updateFromState(stateIntent);
        applyModeUi(videoModeEnabled);

        JSONObject payload = new JSONObject();
        try {
            payload.put("title", stateIntent.getStringExtra("title"));
            payload.put("artist", stateIntent.getStringExtra("artist"));
            payload.put("playing", playbackActive);
            payload.put("isPlaying", playbackActive);
            payload.put("position_ms", stateIntent.getLongExtra("position_ms", 0L));
            payload.put("duration_ms", stateIntent.getLongExtra("duration_ms", 0L));
            payload.put("thumbnailUrl", stateIntent.getStringExtra("thumbnailUrl"));
            payload.put("video_mode", videoModeEnabled);
            payload.put("queue_index", stateIntent.getIntExtra("queue_index", -1));
            payload.put("queue_length", stateIntent.getIntExtra("queue_length", 0));
            payload.put("last_error", stateIntent.getStringExtra("last_error"));
            payload.put("event_ts", stateIntent.getLongExtra("event_ts", System.currentTimeMillis()));
        } catch (JSONException ignored) {
        }
        dispatchToWeb("window.dispatchEvent(new CustomEvent('nativePlaybackState', { detail: " + payload + " }));");
    }

    @Override
    public void onServiceConnected(@NonNull PlaybackService.PlaybackSnapshot snapshot) {
        playbackActive = snapshot.playing;
        videoModeEnabled = snapshot.videoMode;
        playerUiController.updateFromSnapshot(snapshot);
        applyModeUi(videoModeEnabled);
    }

    @Override
    public void onMediaAction(@NonNull Intent mediaIntent) {
        webViewManager.dispatchPendingMediaAction(mediaIntent);
        clearPendingMediaAction();
    }

    @Override
    public void sendServiceIntent(@NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void dispatchToWeb(@NonNull String js) {
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    @Override
    public void onModeToggleRequested(boolean enabled) {
        setVideoMode(enabled);
    }

    @Override
    public void setVideoMode(boolean enabled) {
        videoModeEnabled = enabled;
        applyModeUi(enabled);
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_SET_MODE);
        intent.putExtra("video_mode", enabled);
        sendServiceIntent(intent);
    }

    private void applyModeUi(boolean enabled) {
        boolean showNativePlayerUi = !FORCE_WEB_PLAYER_UI_FOR_TEST && !enabled;
        if (enabled) {
            hideSystemBars();
            webView.setVisibility(WebView.VISIBLE);
            webView.setClickable(true);
            webView.setLongClickable(true);
            playerContainer.setVisibility(FrameLayout.GONE);
            updateWebViewBottomInset();
        } else {
            showNormalBars();
            webView.setVisibility(WebView.VISIBLE);
            webView.setClickable(true);
            webView.setLongClickable(true);
            playerContainer.setVisibility(showNativePlayerUi ? FrameLayout.VISIBLE : FrameLayout.GONE);
            updateWebViewBottomInset();
        }
    }

    private void updateWebViewBottomInset() {
        int bottomInset = playerContainer.getVisibility() == View.VISIBLE
                ? playerContainer.getHeight()
                : 0;
        if (webView.getPaddingBottom() == bottomInset) {
            return;
        }
        webView.setPadding(
                webView.getPaddingLeft(),
                webView.getPaddingTop(),
                webView.getPaddingRight(),
                bottomInset
        );
    }

    @Override
    public boolean isVideoModeEnabled() {
        return videoModeEnabled;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) return;
        String action = intent.getStringExtra(PlaybackService.EXTRA_PENDING_MEDIA_ACTION);
        if (action != null && !action.isEmpty()) {
            Intent mediaIntent = new Intent(PlaybackService.ACTION_MEDIA_CONTROL);
            mediaIntent.putExtra("action", action);
            webViewManager.dispatchPendingMediaAction(mediaIntent);
            clearPendingMediaAction();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webViewManager.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webViewManager.onResume();
        applyModeUi(videoModeEnabled);
    }

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
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyModeUi(videoModeEnabled);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        playerUiController.init();
        requestInitialPlaybackState();
    }

    @Override
    protected void onDestroy() {
        if (playbackObserver != null) playbackObserver.stop();
        if (webViewManager != null) webViewManager.destroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    private void clearPendingMediaAction() {
        Intent i = new Intent(this, PlaybackService.class);
        i.setAction(PlaybackService.ACTION_CLEAR_PENDING_MEDIA_ACTION);
        sendServiceIntent(i);
    }

    private void showNormalBars() {
        if (insetsController == null) return;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        insetsController.show(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
    }

    private void hideSystemBars() {
        if (insetsController == null) return;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        insetsController.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
