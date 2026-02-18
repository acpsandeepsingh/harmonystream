package com.sansoft.harmonystram;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class LibraryActivity extends AppCompatActivity {

    public static final String EXTRA_PLAYLIST_ID = "extra_playlist_id";
    public static final String EXTRA_SONG_INDEX = "extra_song_index";

    private PlaylistStorageRepository playlistStorageRepository;

    private ListView playlistsList;
    private ListView songsList;
    private TextView selectedPlaylistTitle;
    private TextView emptySongsState;
    private Button playAllButton;
    private Button removeTrackButton;
    private Button deletePlaylistButton;

    private final List<Playlist> playlists = new ArrayList<>();
    private int selectedPlaylistIndex = -1;
    private int selectedSongIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        playlistStorageRepository = new PlaylistStorageRepository(this);

        playlistsList = findViewById(R.id.library_playlists_list);
        songsList = findViewById(R.id.library_songs_list);
        selectedPlaylistTitle = findViewById(R.id.library_selected_playlist_title);
        emptySongsState = findViewById(R.id.library_empty_songs_state);
        playAllButton = findViewById(R.id.btn_library_play_all);
        removeTrackButton = findViewById(R.id.btn_library_remove_track);
        deletePlaylistButton = findViewById(R.id.btn_library_delete_playlist);
        Button closeButton = findViewById(R.id.btn_library_close);

        playlistsList.setOnItemClickListener((parent, view, position, id) -> {
            selectedPlaylistIndex = position;
            selectedSongIndex = -1;
            renderSongs();
        });

        songsList.setOnItemClickListener((parent, view, position, id) -> {
            selectedSongIndex = position;
            sendPlaybackSelection(position);
        });

        playAllButton.setOnClickListener(v -> sendPlaybackSelection(-1));

        removeTrackButton.setOnClickListener(v -> {
            Playlist selectedPlaylist = getSelectedPlaylist();
            if (selectedPlaylist == null) {
                Toast.makeText(this, "Select a playlist", Toast.LENGTH_SHORT).show();
                return;
            }
            List<Song> songs = selectedPlaylist.getSongs();
            if (selectedSongIndex < 0 || selectedSongIndex >= songs.size()) {
                Toast.makeText(this, "Select a track to remove", Toast.LENGTH_SHORT).show();
                return;
            }

            Song selectedSong = songs.get(selectedSongIndex);
            playlistStorageRepository.removeSongFromPlaylist(selectedPlaylist.getId(), selectedSong);
            Toast.makeText(this, "Track removed", Toast.LENGTH_SHORT).show();
            reloadPlaylists(selectedPlaylist.getId());
        });

        deletePlaylistButton.setOnClickListener(v -> {
            Playlist selectedPlaylist = getSelectedPlaylist();
            if (selectedPlaylist == null) {
                Toast.makeText(this, "Select a playlist", Toast.LENGTH_SHORT).show();
                return;
            }

            playlistStorageRepository.deletePlaylist(selectedPlaylist.getId());
            Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
            reloadPlaylists(null);
        });

        closeButton.setOnClickListener(v -> finish());

        reloadPlaylists(null);
    }

    private void reloadPlaylists(String preferredPlaylistId) {
        playlists.clear();
        playlists.addAll(playlistStorageRepository.getPlaylists());

        if (playlists.isEmpty()) {
            playlistsList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new String[]{"No playlists yet"}));
            selectedPlaylistIndex = -1;
            selectedSongIndex = -1;
            renderSongs();
            return;
        }

        if (preferredPlaylistId != null) {
            int preferredIndex = findPlaylistIndexById(preferredPlaylistId);
            selectedPlaylistIndex = preferredIndex >= 0 ? preferredIndex : 0;
        } else if (selectedPlaylistIndex < 0 || selectedPlaylistIndex >= playlists.size()) {
            selectedPlaylistIndex = 0;
        }

        String[] playlistItems = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            Playlist playlist = playlists.get(i);
            playlistItems[i] = playlist.getName() + " (" + playlist.getSongs().size() + ")";
        }

        playlistsList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, playlistItems));
        playlistsList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        if (selectedPlaylistIndex >= 0) {
            playlistsList.setItemChecked(selectedPlaylistIndex, true);
        }

        selectedSongIndex = -1;
        renderSongs();
    }

    private void renderSongs() {
        Playlist selectedPlaylist = getSelectedPlaylist();
        if (selectedPlaylist == null) {
            selectedPlaylistTitle.setText("Select a playlist");
            songsList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new String[]{}));
            emptySongsState.setVisibility(View.VISIBLE);
            emptySongsState.setText("No playlist selected.");
            return;
        }

        selectedPlaylistTitle.setText(selectedPlaylist.getName());
        List<Song> songs = selectedPlaylist.getSongs();

        if (songs.isEmpty()) {
            songsList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new String[]{}));
            emptySongsState.setVisibility(View.VISIBLE);
            emptySongsState.setText("Playlist is empty.");
            return;
        }

        String[] songItems = new String[songs.size()];
        for (int i = 0; i < songs.size(); i++) {
            Song song = songs.get(i);
            songItems[i] = song.getTitle() + " • " + song.getArtist();
        }

        songsList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, songItems));
        songsList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        emptySongsState.setVisibility(View.GONE);
    }

    private void sendPlaybackSelection(int songIndex) {
        Playlist selectedPlaylist = getSelectedPlaylist();
        if (selectedPlaylist == null) {
            Toast.makeText(this, "Select a playlist", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent result = new Intent();
        result.putExtra(EXTRA_PLAYLIST_ID, selectedPlaylist.getId());
        result.putExtra(EXTRA_SONG_INDEX, songIndex);
        setResult(RESULT_OK, result);
        finish();
    }

    private Playlist getSelectedPlaylist() {
        if (selectedPlaylistIndex < 0 || selectedPlaylistIndex >= playlists.size()) {
            return null;
        }
        return playlists.get(selectedPlaylistIndex);
    }

    private int findPlaylistIndexById(String playlistId) {
        for (int i = 0; i < playlists.size(); i++) {
            if (playlists.get(i).getId().equals(playlistId)) {
                return i;
            }
        }
        return -1;
    }
}
