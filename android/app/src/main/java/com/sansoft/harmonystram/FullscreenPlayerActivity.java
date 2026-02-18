package com.sansoft.harmonystram;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FullscreenPlayerActivity extends AppCompatActivity {

    private ImageView artworkView;
    private TextView artworkFallback;
    private ExecutorService artworkExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_player);

        artworkView = findViewById(R.id.fullscreen_artwork);
        artworkFallback = findViewById(R.id.fullscreen_artwork_fallback);
        TextView titleText = findViewById(R.id.fullscreen_title);
        TextView artistText = findViewById(R.id.fullscreen_artist);
        TextView queueText = findViewById(R.id.fullscreen_queue);
        TextView playbackBadge = findViewById(R.id.fullscreen_badge_playback);
        TextView repeatBadge = findViewById(R.id.fullscreen_badge_repeat);
        TextView bufferingBadge = findViewById(R.id.fullscreen_badge_buffering);
        TextView sourceBadge = findViewById(R.id.fullscreen_badge_source);

        titleText.setText(valueOrFallback(getIntent().getStringExtra("track_title"), "No active track"));
        artistText.setText(valueOrFallback(getIntent().getStringExtra("track_artist"), "Unknown artist"));

        int queuePosition = Math.max(0, getIntent().getIntExtra("queue_position", 0));
        int queueSize = Math.max(0, getIntent().getIntExtra("queue_size", 0));
        queueText.setText("Queue: " + queuePosition + " / " + queueSize);

        playbackBadge.setText("State: " + valueOrFallback(getIntent().getStringExtra("playback_state"), "Paused"));
        repeatBadge.setText("Repeat: " + valueOrFallback(getIntent().getStringExtra("repeat_label"), "Off"));
        bufferingBadge.setText("Buffer: " + valueOrFallback(getIntent().getStringExtra("buffering_state"), "idle"));
        sourceBadge.setText("Source: " + valueOrFallback(getIntent().getStringExtra("source_type"), "Native queue"));

        findViewById(R.id.fullscreen_close_button).setOnClickListener(v -> finish());

        String thumbnailUrl = getIntent().getStringExtra("track_thumbnail");
        artworkExecutor = Executors.newSingleThreadExecutor();
        loadArtworkAsync(thumbnailUrl);
    }

    private String valueOrFallback(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value;
    }

    private void loadArtworkAsync(String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) {
            artworkFallback.setVisibility(View.VISIBLE);
            return;
        }

        artworkExecutor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            InputStream stream = null;
            try {
                URL url = new URL(thumbnailUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setDoInput(true);
                connection.connect();
                stream = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(stream);
            } catch (Exception ignored) {
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception ignored) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }

            Bitmap finalBitmap = bitmap;
            runOnUiThread(() -> {
                if (finalBitmap != null) {
                    artworkView.setImageBitmap(finalBitmap);
                    artworkFallback.setVisibility(View.GONE);
                } else {
                    artworkFallback.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (artworkExecutor != null) {
            artworkExecutor.shutdownNow();
        }
    }
}
