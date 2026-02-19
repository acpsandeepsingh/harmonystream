package com.sansoft.harmonystram;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    public interface OnTrackClickListener {
        void onTrackClick(int position);
    }

    private final List<Song> tracks = new ArrayList<>();
    private final OnTrackClickListener listener;

    public TrackAdapter(OnTrackClickListener listener) {
        this.listener = listener;
    }

    public void setTracks(List<Song> songs) {
        tracks.clear();
        tracks.addAll(songs);
        notifyDataSetChanged();
    }

    public Song getItem(int position) {
        return tracks.get(position);
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track_modern, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Song item = tracks.get(position);
        holder.title.setText(item.getTitle());
        holder.subtitle.setText(item.getArtist());
        holder.itemView.setOnClickListener(v -> listener.onTrackClick(position));
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.track_title);
            subtitle = itemView.findViewById(R.id.track_subtitle);
        }
    }
}
