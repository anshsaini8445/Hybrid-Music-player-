package com.hybridmusic.player;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
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

    public interface OnSongMenuActionCallback {
        void onSelectOption(int which);
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

    /**
     * Unified Pure-White (#FFFFFF) Menu Dialog for Audio 3-Dot Actions:
     * Uses a 3-Dot ("...") button in the header instead of any Cross ("X").
     */
    public static void showPureWhiteAudioMenuDialog(
            final Context context,
            String headerTitle,
            String[] items,
            final OnSongMenuActionCallback callback) {

        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout rootCard = new LinearLayout(context);
        rootCard.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * d);
        rootCard.setPadding(pad, pad, pad, (int) (14 * d));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F50A0A10"));
        cardBg.setCornerRadius(24f * d);
        cardBg.setStroke(Math.max(2, (int) (1.6f * d)), Color.WHITE);
        rootCard.setBackground(cardBg);

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(rootCard).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding((int) (8 * d), (int) (2 * d), (int) (4 * d), (int) (12 * d));

        TextView tvHeader = new TextView(context);
        tvHeader.setText(headerTitle);
        tvHeader.setTextColor(Color.WHITE);
        tvHeader.setTextSize(16.5f);
        tvHeader.setTypeface(null, Typeface.BOLD);
        tvHeader.setSingleLine(true);
        tvHeader.setEllipsize(TextUtils.TruncateAt.END);
        tvHeader.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnDone = new TextView(context);
        btnDone.setText("⋮");
        btnDone.setTextColor(Color.WHITE);
        btnDone.setTextSize(18f);
        btnDone.setTypeface(null, Typeface.BOLD);
        btnDone.setGravity(Gravity.CENTER);
        btnDone.setLayoutParams(new LinearLayout.LayoutParams((int) (38 * d), (int) (38 * d)));
        btnDone.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
        btnDone.setOnClickListener(v -> dialog.dismiss());

        headerRow.addView(tvHeader);
        headerRow.addView(btnDone);
        rootCard.addView(headerRow);

        View topDivider = new View(context);
        topDivider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) d)));
        topDivider.setBackgroundColor(Color.parseColor("#55FFFFFF"));
        rootCard.addView(topDivider);

        ScrollView scroll = new ScrollView(context);
        LinearLayout listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(0, (int) (6 * d), 0, (int) (4 * d));

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (12 * d), (int) (13 * d), (int) (12 * d), (int) (13 * d));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = (int) (3 * d);
            rlp.bottomMargin = (int) (3 * d);
            row.setLayoutParams(rlp);

            GradientDrawable rBg = new GradientDrawable();
            rBg.setColor(Color.parseColor("#18FFFFFF"));
            rBg.setCornerRadius(14f * d);
            rBg.setStroke(Math.max(1, (int) d), Color.WHITE);
            row.setBackground(rBg);

            View bulletDot = new View(context);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams((int) (8 * d), (int) (8 * d));
            blp.rightMargin = (int) (12 * d);
            bulletDot.setLayoutParams(blp);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(Color.WHITE);
            bulletDot.setBackground(dotBg);

            TextView tvItem = new TextView(context);
            tvItem.setText(items[i]);
            tvItem.setTextColor(Color.WHITE);
            tvItem.setTextSize(15f);
            tvItem.setTypeface(null, Typeface.BOLD);
            tvItem.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(bulletDot);
            row.addView(tvItem);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (callback != null) callback.onSelectOption(idx);
            });
            listLayout.addView(row);
        }

        scroll.addView(listLayout);
        rootCard.addView(scroll);
        dialog.show();
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
        float d = context.getResources().getDisplayMetrics().density;
        ThemeManager.ThemePreset theme = themeManager.getCurrentTheme();

        boolean isCurrent = (song.id == activeSongId);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(theme.bgCard);
        cardBg.setCornerRadius(24f * d);
        if (isCurrent) {
            cardBg.setStroke(Math.max(2, (int) (2 * d)), Color.WHITE);
        } else {
            cardBg.setStroke(Math.max(1, (int) d), Color.parseColor("#33FFFFFF"));
        }
        holder.cardContainer.setBackground(cardBg);

        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(Color.parseColor("#182234"));
        thumbBg.setCornerRadius(16f * d);
        holder.artImage.setBackground(thumbBg);
        holder.artImage.setClipToOutline(true);

        // Larger Pure-White Frosted Circle 3-Dot Button
        holder.threeDotBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
        holder.threeDotBtn.setTextColor(Color.WHITE);
        holder.threeDotBtn.setText("⋮");

        String prefix = isCompactQueueMode ? (position + 1) + ". " : "";
        holder.titleText.setText(prefix + song.title);
        holder.artistText.setText(song.artist + "  •  " + song.getFormattedDuration() + "  •  " + song.getFormattedSize());

        holder.titleText.setTextColor(Color.WHITE);
        holder.artistText.setTextColor(Color.parseColor("#D8E2F0"));

        // Guaranteed Instant Cover Photo or Studio Vinyl Cover
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
                holdHandler.postDelayed(holdRunnable, 1700);
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
