package com.sansoft.harmonystram;

import android.content.Intent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class GestureController {

    interface Callbacks {
        boolean isVideoModeEnabled();
        void sendServiceIntent(@NonNull Intent intent);
    }

    private final WebAppActivity activity;
    private final View webView;
    private final @Nullable TextView seekOverlay;
    private final Callbacks callbacks;

    private GestureDetector detector;

    GestureController(@NonNull WebAppActivity activity,
                      @NonNull View webView,
                      @Nullable TextView seekOverlay,
                      @NonNull Callbacks callbacks) {
        this.activity = activity;
        this.webView = webView;
        this.seekOverlay = seekOverlay;
        this.callbacks = callbacks;
    }

    void attach() {
        detector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!callbacks.isVideoModeEnabled()) return false;
                float midX = webView.getWidth() / 2f;
                long deltaMs = e.getX() < midX ? -10_000L : 10_000L;
                if (seekOverlay != null) {
                    seekOverlay.setText(deltaMs < 0 ? "-10s" : "+10s");
                    seekOverlay.setVisibility(View.VISIBLE);
                    seekOverlay.postDelayed(() -> seekOverlay.setVisibility(View.GONE), 800);
                }
                Intent i = new Intent(activity, PlaybackService.class);
                i.setAction(PlaybackService.ACTION_SEEK_RELATIVE);
                i.putExtra("delta_ms", deltaMs);
                callbacks.sendServiceIntent(i);
                return true;
            }
        });

        webView.setOnTouchListener((v, event) -> {
            if (detector != null) detector.onTouchEvent(event);
            return false;
        });
    }
}
