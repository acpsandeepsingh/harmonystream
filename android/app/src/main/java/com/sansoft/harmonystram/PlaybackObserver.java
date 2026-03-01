package com.sansoft.harmonystram;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;

final class PlaybackObserver {

    interface Listener {
        void onPlaybackStateChanged(@NonNull Intent stateIntent);
        void onServiceConnected(@NonNull PlaybackService.PlaybackSnapshot snapshot);
        void onMediaAction(@NonNull String action);
    }

    private final Context context;
    private final Listener listener;

    private PlaybackService playbackService;
    private boolean bound;

    PlaybackObserver(@NonNull Context context, @NonNull Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent == null) return;
            if (!PlaybackService.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
            listener.onPlaybackStateChanged(intent);
        }
    };

    private final BroadcastReceiver mediaActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (intent == null) return;
            if (!PlaybackService.ACTION_MEDIA_CONTROL.equals(intent.getAction())) return;
            String action = intent.getStringExtra("action");
            if (action != null && !action.isEmpty()) {
                listener.onMediaAction(action);
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!(service instanceof PlaybackService.LocalBinder)) return;
            playbackService = ((PlaybackService.LocalBinder) service).getService();
            bound = true;
            listener.onServiceConnected(playbackService.getCurrentSnapshot());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            playbackService = null;
        }
    };

    void start() {
        IntentFilter stateFilter = new IntentFilter(PlaybackService.ACTION_STATE_CHANGED);
        IntentFilter mediaFilter = new IntentFilter(PlaybackService.ACTION_MEDIA_CONTROL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(stateReceiver, stateFilter, Context.RECEIVER_NOT_EXPORTED);
            context.registerReceiver(mediaActionReceiver, mediaFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(stateReceiver, stateFilter);
            context.registerReceiver(mediaActionReceiver, mediaFilter);
        }

        context.bindService(new Intent(context, PlaybackService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    void stop() {
        try {
            context.unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {
        }
        try {
            context.unregisterReceiver(mediaActionReceiver);
        } catch (Exception ignored) {
        }
        if (bound) {
            context.unbindService(serviceConnection);
            bound = false;
        }
        playbackService = null;
    }
}
