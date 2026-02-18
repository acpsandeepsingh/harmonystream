package com.sansoft.harmonystram;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlaylistSyncManager {
    private final PlaylistStorageRepository playlistRepository;
    private final FirestorePlaylistRemoteDataSource remoteDataSource;
    private final NativeUserSessionStore userSessionStore;

    public PlaylistSyncManager(Context context) {
        Context appContext = context.getApplicationContext();
        this.playlistRepository = new PlaylistStorageRepository(appContext);
        this.remoteDataSource = new FirestorePlaylistRemoteDataSource(appContext);
        this.userSessionStore = new NativeUserSessionStore(appContext);
    }

    public synchronized PlaylistSyncModels.SyncStatus syncNow() {
        NativeUserSessionStore.UserSession session = userSessionStore.getSession();
        if (session == null || !session.isSignedIn()) {
            return new PlaylistSyncModels.SyncStatus("offline", "Guest mode: local library only");
        }

        String accountKey = playlistRepository.getCurrentAccountKeyForSync();
        PlaylistSyncModels.PlaylistSnapshot local = playlistRepository.pullLocalSnapshot();
        PlaylistSyncModels.PlaylistSnapshot remote = remoteDataSource.pull(accountKey);

        PlaylistSyncModels.PlaylistSnapshot merged = resolveConflicts(local, remote);
        playlistRepository.applyResolvedSnapshot(merged);
        remoteDataSource.push(accountKey, merged);
        playlistRepository.markSyncSuccess();

        return new PlaylistSyncModels.SyncStatus("conflict-resolved", "Synced " + merged.playlists.size() + " playlists");
    }

    public synchronized PlaylistSyncModels.SyncStatus getLastStatus() {
        return playlistRepository.getLastSyncStatus();
    }

    public synchronized PlaylistSyncModels.PlaylistSnapshot resolveConflicts(
            PlaylistSyncModels.PlaylistSnapshot local,
            PlaylistSyncModels.PlaylistSnapshot remote
    ) {
        Map<String, PlaylistSyncModels.PlaylistRecord> mergedMap = new HashMap<>();
        Set<String> tombstones = new HashSet<>();

        Map<String, Long> deleteTimes = new HashMap<>();
        for (String id : local.deletedPlaylistIds) deleteTimes.put(id, local.generatedAtMs);
        for (String id : remote.deletedPlaylistIds) deleteTimes.put(id, Math.max(deleteTimes.getOrDefault(id, 0L), remote.generatedAtMs));

        for (PlaylistSyncModels.PlaylistRecord record : local.playlists) {
            if (record == null || record.playlist == null) continue;
            mergedMap.put(record.playlist.getId(), record);
        }

        for (PlaylistSyncModels.PlaylistRecord record : remote.playlists) {
            if (record == null || record.playlist == null) continue;
            PlaylistSyncModels.PlaylistRecord existing = mergedMap.get(record.playlist.getId());
            if (existing == null || record.updatedAtMs >= existing.updatedAtMs) {
                mergedMap.put(record.playlist.getId(), record);
            }
        }

        List<PlaylistSyncModels.PlaylistRecord> mergedPlaylists = new ArrayList<>();
        for (Map.Entry<String, PlaylistSyncModels.PlaylistRecord> entry : mergedMap.entrySet()) {
            String id = entry.getKey();
            PlaylistSyncModels.PlaylistRecord record = entry.getValue();
            long deletedAt = deleteTimes.getOrDefault(id, -1L);
            if (deletedAt >= record.updatedAtMs) {
                tombstones.add(id);
                continue;
            }
            mergedPlaylists.add(record);
        }

        tombstones.addAll(local.deletedPlaylistIds);
        tombstones.addAll(remote.deletedPlaylistIds);

        return new PlaylistSyncModels.PlaylistSnapshot(mergedPlaylists, new ArrayList<>(tombstones), System.currentTimeMillis());
    }
}
