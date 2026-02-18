package com.sansoft.harmonystram;

import java.util.ArrayList;
import java.util.List;

public class PlaylistSyncModels {
    public static class PlaylistRecord {
        public final Playlist playlist;
        public final long updatedAtMs;

        public PlaylistRecord(Playlist playlist, long updatedAtMs) {
            this.playlist = playlist;
            this.updatedAtMs = updatedAtMs;
        }
    }

    public static class PlaylistSnapshot {
        public final List<PlaylistRecord> playlists;
        public final List<String> deletedPlaylistIds;
        public final long generatedAtMs;

        public PlaylistSnapshot(List<PlaylistRecord> playlists, List<String> deletedPlaylistIds, long generatedAtMs) {
            this.playlists = playlists == null ? new ArrayList<>() : playlists;
            this.deletedPlaylistIds = deletedPlaylistIds == null ? new ArrayList<>() : deletedPlaylistIds;
            this.generatedAtMs = generatedAtMs;
        }
    }

    public static class SyncStatus {
        public final String state;
        public final String detail;

        public SyncStatus(String state, String detail) {
            this.state = state;
            this.detail = detail;
        }
    }
}
