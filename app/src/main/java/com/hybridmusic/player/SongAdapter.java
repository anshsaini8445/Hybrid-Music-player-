package com.hybridmusic.player;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(int position);
        void onThreeDotClick(SongItem song, int position);
    }

    private final Context context;
    private final List<SongItem> songList;
    private final ThemeManager themeManager;
    private final OnSongClickListener listener;
    private final boolean isCompactQueueMode;
    private long activeSongId = -1;

    public SongAdapter(Context context, List<SongItem> songList, ThemeManager themeManager,
                       boolean isCompactQueueMode, OnSongClickListener listener) {
        this.context = context;
        this.songList = songList;
        this.themeManager = themeManager;
        this.isCompactQueueMode = isCompactQueueMode;
        this.listener = listener;
    }

    public void setActiveSongId(long songId) {
        this.activeSongId = songId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        SongItem song = songList.get(position);
        ThemeManager.ThemePreset theme = themeManager.getCurrentTheme();

        boolean isCurrent = (song.id == activeSongId);
        int primaryColor = isCurrent ? theme.accentPrimary : themeManager.getPrimaryTextColor();
        int secondaryColor = themeManager.getSecondaryTextColor();

        String prefix = isCompactQueueMode ? (position + 1) + ". " : "";
        String badge = song.isVideoAudio ? " [VIDEO-AUDIO]" : "";
        holder.titleText.setText(prefix + song.title + badge);
        holder.artistText.setText(song.artist + " • " + song.getFormattedDuration() + " • " + song.getFormattedSize());

        holder.titleText.setTextColor(primaryColor);
        holder.artistText.setTextColor(secondaryColor);
        holder.threeDotBtn.setColorFilter(primaryColor);

        // Rounded glowing background for thumbnail
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(theme.bgCard);
        thumbBg.setCornerRadius(18f);
        thumbBg.setStroke(2, theme.accentPrimary);
        holder.artImage.setBackground(thumbBg);

        // Check custom saved photo for song first, then album art
        String customPhoto = themeManager.getSongCustomPhoto(song.id);
        boolean photoLoaded = false;
        try {
            if (customPhoto != null && !customPhoto.isEmpty()) {
                holder.artImage.setImageURI(Uri.parse(customPhoto));
                photoLoaded = (holder.artImage.getDrawable() != null);
            }
            if (!photoLoaded && !song.isVideoAudio) {
                holder.artImage.setImageURI(song.getAlbumArtUri());
                photoLoaded = (holder.artImage.getDrawable() != null);
            }
        } catch (Exception ignored) {
        }

        if (!photoLoaded) {
            holder.artImage.setImageResource(song.isVideoAudio
                    ? android.R.drawable.ic_media_ff
                    : android.R.drawable.ic_media_play);
            holder.artImage.setColorFilter(theme.accentPrimary);
        } else {
            holder.artImage.clearColorFilter();
        }

        holder.threeDotBtn.setVisibility(isCompactQueueMode ? View.GONE : View.VISIBLE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSongClick(position);
        });

        holder.threeDotBtn.setOnClickListener(v -> {
            if (listener != null) listener.onThreeDotClick(song, position);
        });
    }

    // Double-Confirmation Permanent Phone Storage Delete
    public static void showPermanentDeleteDialog(Context context, SongItem song, Runnable onDeleteSuccess) {
        new AlertDialog.Builder(context)
                .setTitle("⚠️ Delete Song (Step 1 of 2)")
                .setMessage("Kya aap '" + song.title + "' ko delete karna chahte hain?")
                .setPositiveButton("OK (Next)", (d1, w1) -> {
                    new AlertDialog.Builder(context)
                            .setTitle("🚨 FINAL WARNING (Step 2 of 2)")
                            .setMessage("Dobara Confirm Karein: OK dabane par '" + song.title + "' aapke poore phone se hamesha ke liye delete ho jayega!")
                            .setPositiveButton("OK, Delete Forever", (d2, w2) -> {
                                boolean deleted = false;
                                try {
                                    Uri baseUri = song.isVideoAudio
                                            ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                            : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                                    Uri itemUri = ContentUris.withAppendedId(baseUri, song.id);
                                    int rows = context.getContentResolver().delete(itemUri, null, null);
                                    if (rows > 0) deleted = true;
                                } catch (Exception ignored) {
                                }
                                try {
                                    File f = new File(song.path);
                                    if (f.exists() && f.delete()) {
                                        deleted = true;
                                    }
                                } catch (Exception ignored) {
                                }
                                Toast.makeText(context, deleted
                                        ? "Song poore phone se permanently delete ho gaya!"
                                        : "File delete request sent!", Toast.LENGTH_SHORT).show();
                                if (onDeleteSuccess != null) onDeleteSuccess.run();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView artImage;
        TextView titleText, artistText;
        ImageView threeDotBtn;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            artImage = itemView.findViewById(R.id.songThumbnail);
            titleText = itemView.findViewById(R.id.songTitle);
            artistText = itemView.findViewById(R.id.songArtist);
            threeDotBtn = itemView.findViewById(R.id.threeDotButton);
        }
    }
}
