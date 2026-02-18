package com.sansoft.harmonystram;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "harmonystream_playback";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_PREVIOUS = "com.sansoft.harmonystram.PREVIOUS";
    private static final String ACTION_PLAY_PAUSE = "com.sansoft.harmonystram.PLAY_PAUSE";
    private static final String ACTION_NEXT = "com.sansoft.harmonystram.NEXT";

    private static WeakReference<MainActivity> currentInstance = new WeakReference<>(null);

    private WebView webView;
    private String currentTitle = "HarmonyStream";
    private String currentArtist = "";
    private boolean isPlaying = false;
    private long currentPositionMs = 0;
    private long currentDurationMs = 0;
    private Bitmap artworkBitmap;

    private final BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MainActivity activity = currentInstance.get();
            if (activity == null) return;

            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case ACTION_PREVIOUS:
                    activity.dispatchToWeb("previous");
                    break;
                case ACTION_PLAY_PAUSE:
                    activity.dispatchToWeb("playpause");
                    break;
                case ACTION_NEXT:
                    activity.dispatchToWeb("next");
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

    private void applyImmersiveMode() {
        WindowInsetsControllerCompat windowInsetsController =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
            windowInsetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentInstance = new WeakReference<>(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveMode();

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

        createNotificationChannel();
        registerReceiver(mediaActionReceiver, new IntentFilter(ACTION_PREVIOUS));
        registerReceiver(mediaActionReceiver, new IntentFilter(ACTION_PLAY_PAUSE));
        registerReceiver(mediaActionReceiver, new IntentFilter(ACTION_NEXT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
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
        currentInstance = new WeakReference<>(null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("HarmonyStream playback controls");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent createActionIntent(String action) {
        Intent intent = new Intent(action);
        intent.setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(this, action.hashCode(), intent, flags);
    }

    private void updatePlaybackNotification() {
        NotificationCompat.Action prevAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "Previous",
                createActionIntent(ACTION_PREVIOUS)
        );

        NotificationCompat.Action playPauseAction = new NotificationCompat.Action(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Pause" : "Play",
                createActionIntent(ACTION_PLAY_PAUSE)
        );

        NotificationCompat.Action nextAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "Next",
                createActionIntent(ACTION_NEXT)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(new MediaStyle().setShowActionsInCompactView(0, 1, 2));

        if (artworkBitmap != null) {
            builder.setLargeIcon(artworkBitmap);
        }

        if (currentDurationMs > 0) {
            builder.setProgress((int) currentDurationMs, (int) Math.min(currentPositionMs, currentDurationMs), false);
        } else {
            builder.setProgress(0, 0, false);
        }

        Notification notification = builder.build();
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
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
            runOnUiThread(() -> {
                currentTitle = title != null && !title.isEmpty() ? title : "HarmonyStream";
                currentArtist = artist != null ? artist : "";
                isPlaying = playing;
                currentPositionMs = Math.max(0, (long) (positionSeconds * 1000));
                currentDurationMs = Math.max(0, (long) (durationSeconds * 1000));

                if (artworkBase64 != null && !artworkBase64.isEmpty()) {
                    try {
                        byte[] data = Base64.decode(artworkBase64, Base64.DEFAULT);
                        artworkBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    } catch (Exception ignored) {
                    }
                }

                updatePlaybackNotification();
            });
        }
    }
}
