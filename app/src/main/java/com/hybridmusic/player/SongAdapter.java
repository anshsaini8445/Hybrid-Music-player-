package com.hybridmusic.player;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
        default void onSongHoldTwoSeconds(SongItem song, int position) {
            onSongClick(position);
        }
    }

    private final Context context;
    private final List<SongItem> songList;
    private final ThemeManager themeManager;
    private final OnSongClickListener listener;
    private final boolean isCompactQueueMode;
    private long activeSongId = -1;
    private final Handler holdHandler = new Handler(Looper.getMainLooper());

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
        final SongItem song = songList.get(position);
        final int pos = position;
        ThemeManager.ThemePreset theme = themeManager.getCurrentTheme();

        boolean isCurrent = (song.id == activeSongId);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(theme.bgCard);
        cardBg.setCornerRadius(32f);
        if (isCurrent) {
            cardBg.setStroke(3, Color.WHITE);
        }
        holder.cardContainer.setBackground(cardBg);

        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(theme.accentPrimary);
        thumbBg.setCornerRadius(22f);
        holder.artImage.setBackground(thumbBg);
        holder.artImage.setClipToOutline(true);

        // Pure White Circular 3-Dot Button
        GradientDrawable dotCircle = new GradientDrawable();
        dotCircle.setShape(GradientDrawable.OVAL);
        dotCircle.setColor(Color.parseColor("#182230"));
        dotCircle.setStroke(2, Color.WHITE);
        holder.threeDotBtn.setBackground(dotCircle);
        holder.threeDotBtn.setTextColor(Color.WHITE);

        String prefix = isCompactQueueMode ? (position + 1) + ". " : "";
        holder.titleText.setText(prefix + song.title);
        holder.artistText.setText(song.artist + "  •  " + song.getFormattedDuration() + "  •  " + song.getFormattedSize());

        holder.titleText.setTextColor(Color.WHITE);
        holder.artistText.setTextColor(Color.parseColor("#CBD5E1"));

        // Load real embedded MP3 or Video-Audio thumbnail asynchronously so it ALWAYS appears!
        MediaArtLoader.loadSongArtInto(context, song, themeManager, holder.artImage);

        holder.threeDotBtn.setVisibility(isCompactQueueMode ? View.GONE : View.VISIBLE);

        // 2-Second Hold Detector: Holding for 2 seconds plays song & makes Gudda dance!
        final boolean[] holdTriggered = {false};
        final Runnable holdRunnable = () -> {
            holdTriggered[0] = true;
            if (listener != null) {
                listener.onSongHoldTwoSeconds(song, pos);
            }
        };

        holder.itemView.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                holdTriggered[0] = false;
                holdHandler.postDelayed(holdRunnable, 1800); // ~2 seconds hold
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                holdHandler.removeCallbacks(holdRunnable);
            }
            return false;
        });

        holder.itemView.setOnClickListener(v -> {
            if (!holdTriggered[0] && listener != null) {
                listener.onSongClick(pos);
            }
        });

        holder.threeDotBtn.setOnClickListener(v -> {
            if (listener != null) listener.onThreeDotClick(song, pos);
        });
    }

    public static void showPermanentDeleteDialog(Context context, SongItem song, Runnable onDeleteSuccess) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Song Permanently (1/2)")
                .setMessage("Kya aap '" + song.title + "' ko phone se delete karna chahte hain?")
                .setPositiveButton("OK (Continue)", (d1, w1) -> {
                    new AlertDialog.Builder(context)
                            .setTitle("Final Confirmation (2/2)")
                            .setMessage("Pakka delete karein? OK dabane par '" + song.title + "' poore phone se hamesha ke liye hat jayega!")
                            .setPositiveButton("OK, Delete Forever", (d2, w2) -> {
                                boolean deleted = false;
                                try {
                                    Uri baseUri = song.isVideoAudio
                                            ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                            : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                                    Uri itemUri = ContentUris.withAppendedId(baseUri, song.id);
                                    if (context.getContentResolver().delete(itemUri, null, null) > 0) {
                                        deleted = true;
                                    }
                                } catch (Exception ignored) {
                                }
                                try {
                                    File f = new File(song.path);
                                    if (f.exists() && f.delete()) deleted = true;
                                } catch (Exception ignored) {
                                }
                                Toast.makeText(context, deleted
                                        ? "Song poore phone se delete ho gaya!"
                                        : "Delete request completed!", Toast.LENGTH_SHORT).show();
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
        LinearLayout cardContainer;
        ImageView artImage;
        TextView titleText, artistText, threeDotBtn;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            cardContainer = itemView.findViewById(R.id.songCardContainer);
            artImage = itemView.findViewById(R.id.songThumbnail);
            titleText = itemView.findViewById(R.id.songTitle);
            artistText = itemView.findViewById(R.id.songArtist);
            threeDotBtn = itemView.findViewById(R.id.threeDotButton);
        }
    }
}
