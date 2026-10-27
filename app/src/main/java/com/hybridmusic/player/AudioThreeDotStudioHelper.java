package com.hybridmusic.player;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class AudioThreeDotStudioHelper {

    public interface StudioPlayerCallbacks {
        SongItem getCurrentSong();
        boolean isFavoriteSong();
        void onToggleFavorite();
        boolean isSuperLoud200BoostActive();
        void onToggleSuperLoud200Boost();
        boolean is8DSpatialAudioActive();
        void onToggle8DSpatialAudio();
        String getFrequencyTuningLabel();
        void onCycleFrequencyTuning();
        float getPlaybackSpeed();
        void onChangePlaybackSpeed(float newSpeed);
        int getLoopMode(); // 0=Shuffle, 1=Repeat1, 2=LoopAll
        void onChangeLoopMode(int newMode);
        void onTriggerAbRepeat();
        String getAbRepeatStatusText();
        void onOpenEqualizerAndSoundLab();
        void onOpenSleepTimer();
        void onToggleVinylDiscOrBox();
        void onToggleWaveformOrLine();
        void onPickCustomPhotoForSong(SongItem song);
        void onSwapBottomControlsOrder();
        void onDeleteSongPermanently(SongItem song);
        void onOpenVipPlansOrWatchAd();
    }

    /**
     * Attaches a buttery-smooth 60FPS 3D Spring Bounce animation to any button or tile!
     */
    public static void attachBouncySpring(final View view, final Runnable onClickAction) {
        if (view == null) return;
        view.setClickable(true);
        view.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).start();
            } else if (action == MotionEvent.ACTION_UP) {
                v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(240)
                        .setInterpolator(new OvershootInterpolator(2.6f))
                        .start();
                if (onClickAction != null) {
                    onClickAction.run();
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
            return true;
        });
    }

    public static GradientDrawable makeFrostedWhiteCircle(float d) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor("#28FFFFFF"));
        gd.setStroke(Math.max(2, (int) (1.5f * d)), Color.WHITE);
        return gd;
    }

    public static GradientDrawable makeFrostedWhitePill(float d) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(20f * d);
        gd.setColor(Color.parseColor("#26FFFFFF"));
        gd.setStroke(Math.max(2, (int) (1.4f * d)), Color.WHITE);
        return gd;
    }

    /**
     * 100% Pure-White (#FFFFFF) 3-Dot Studio Panel:
     * - 12 Animated Studio Tiles (including FREE 8D Spatial Surround & FREE 432Hz/528Hz Tuner!)
     * - VIP / Rewarded Ad Banner at the bottom for 300% Extreme Turbo Bass & Ad-Free Crown Mode
     * - Uses a 3-Dot ("⋮") button in the header instead of any Cross ("X").
     */
    public static void showAudioThreeDotStudioDialog(
            final Context context,
            final StudioPlayerCallbacks callbacks) {

        final float d = context.getResources().getDisplayMetrics().density;
        final SongItem currentSong = callbacks.getCurrentSong();
        final String songTitle = currentSong != null ? currentSong.title : "Hybrid Studio Audio";

        LinearLayout rootCard = new LinearLayout(context);
        rootCard.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * d);
        rootCard.setPadding(pad, pad, pad, (int) (14 * d));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F5090D16"));
        cardBg.setCornerRadius(26f * d);
        cardBg.setStroke(Math.max(2, (int) (1.8f * d)), Color.WHITE);
        rootCard.setBackground(cardBg);

        // Entrance Pop Animation for the Studio Card
        rootCard.setScaleX(0.90f);
        rootCard.setScaleY(0.90f);
        rootCard.setAlpha(0f);
        rootCard.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(240)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(rootCard).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Header Row with 3-Dot Dismiss Button (No Cross 'X')
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding((int) (4 * d), 0, (int) (4 * d), (int) (10 * d));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvHeader = new TextView(context);
        tvHeader.setText("✨ Hybrid Pro Studio (Free Unlocked)");
        tvHeader.setTextColor(Color.WHITE);
        tvHeader.setTextSize(16.5f);
        tvHeader.setTypeface(null, Typeface.BOLD);

        TextView tvSubSong = new TextView(context);
        tvSubSong.setText(songTitle);
        tvSubSong.setTextColor(Color.parseColor("#D8E2F0"));
        tvSubSong.setTextSize(12f);
        tvSubSong.setSingleLine(true);
        tvSubSong.setEllipsize(TextUtils.TruncateAt.END);
        tvSubSong.setPadding(0, (int) (2 * d), 0, 0);

        titleCol.addView(tvHeader);
        titleCol.addView(tvSubSong);

        ImageView btnThreeDotDismiss = new ImageView(context);
        btnThreeDotDismiss.setLayoutParams(new LinearLayout.LayoutParams((int) (40 * d), (int) (40 * d)));
        int iconPad = (int) (9 * d);
        btnThreeDotDismiss.setPadding(iconPad, iconPad, iconPad, iconPad);
        btnThreeDotDismiss.setImageDrawable(new PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        btnThreeDotDismiss.setBackground(makeFrostedWhiteCircle(d));
        attachBouncySpring(btnThreeDotDismiss, dialog::dismiss);

        headerRow.addView(titleCol);
        headerRow.addView(btnThreeDotDismiss);
        rootCard.addView(headerRow);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setOnTouchListener((v, event) -> {
            if (v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });

        LinearLayout contentCol = new LinearLayout(context);
        contentCol.setOrientation(LinearLayout.VERTICAL);

        // 12 Pure-White Pro Studio Tiles (Including FREE 8D Surround & FREE 432Hz/528Hz Tuner!)
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(4);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.bottomMargin = (int) (12 * d);
        grid.setLayoutParams(gridLp);

        final String[] tileLabels = new String[]{
                "Equalizer",
                "Lofi Lab",
                callbacks.is8DSpatialAudioActive() ? "8D: ON" : "8D Audio",
                callbacks.getFrequencyTuningLabel(),
                callbacks.isSuperLoud200BoostActive() ? "200% ON" : "200% Boost",
                "Sleep Timer",
                callbacks.getAbRepeatStatusText(),
                "Disc / Box",
                "Line / Bars",
                "Song Photo",
                "Ringtone",
                "Delete Song"
        };

        final String[] tileIcons = new String[]{
                "EQUALIZER",
                "PITCH_LAB",
                "SPATIAL_8D",
                "FREQ_HZ",
                "BOOST",
                "TIMER",
                "AB_REPEAT",
                "VINYL_DISC",
                "WAVEFORM",
                "PHOTO",
                "RINGTONE",
                "DELETE"
        };

        for (int i = 0; i < 12; i++) {
            final int tileIdx = i;
            LinearLayout tileLayout = new LinearLayout(context);
            tileLayout.setOrientation(LinearLayout.VERTICAL);
            tileLayout.setGravity(Gravity.CENTER);
            tileLayout.setPadding((int) (5 * d), (int) (9 * d), (int) (5 * d), (int) (9 * d));

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = (int) (78 * d);
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            int m = (int) (3.5f * d);
            glp.setMargins(m, m, m, m);
            tileLayout.setLayoutParams(glp);

            boolean isHighlighted = (i == 2 && callbacks.is8DSpatialAudioActive())
                    || (i == 4 && callbacks.isSuperLoud200BoostActive());

            GradientDrawable tileBg = new GradientDrawable();
            tileBg.setColor(isHighlighted ? Color.parseColor("#38FFFFFF") : Color.parseColor("#1AFFFFFF"));
            tileBg.setCornerRadius(15f * d);
            tileBg.setStroke(Math.max(1, (int) (1.2f * d)), Color.WHITE);
            tileLayout.setBackground(tileBg);

            ImageView iconView = new ImageView(context);
            iconView.setLayoutParams(new LinearLayout.LayoutParams((int) (28 * d), (int) (28 * d)));
            iconView.setImageDrawable(new PureWhiteAudioVectorDrawable(tileIcons[i], d));

            TextView tvLabel = new TextView(context);
            tvLabel.setText(tileLabels[i]);
            tvLabel.setTextColor(Color.WHITE);
            tvLabel.setTextSize(11f);
            tvLabel.setTypeface(null, Typeface.BOLD);
            tvLabel.setGravity(Gravity.CENTER);
            tvLabel.setSingleLine(true);
            tvLabel.setEllipsize(TextUtils.TruncateAt.END);
            tvLabel.setPadding(0, (int) (5 * d), 0, 0);

            tileLayout.addView(iconView);
            tileLayout.addView(tvLabel);

            // Cascading entrance animation for each tile
            tileLayout.setAlpha(0f);
            tileLayout.setTranslationY(20f);
            tileLayout.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 18L)
                    .setDuration(200)
                    .start();

            attachBouncySpring(tileLayout, () -> {
                dialog.dismiss();
                switch (tileIdx) {
                    case 0:
                    case 1:
                        callbacks.onOpenEqualizerAndSoundLab();
                        break;
                    case 2:
                        callbacks.onToggle8DSpatialAudio();
                        break;
                    case 3:
                        callbacks.onCycleFrequencyTuning();
                        break;
                    case 4:
                        callbacks.onToggleSuperLoud200Boost();
                        break;
                    case 5:
                        callbacks.onOpenSleepTimer();
                        break;
                    case 6:
                        callbacks.onTriggerAbRepeat();
                        break;
                    case 7:
                        callbacks.onToggleVinylDiscOrBox();
                        break;
                    case 8:
                        callbacks.onToggleWaveformOrLine();
                        break;
                    case 9:
                        if (currentSong != null) {
                            callbacks.onPickCustomPhotoForSong(currentSong);
                        }
                        break;
                    case 10:
                        if (currentSong != null) {
                            setSongAsPhoneRingtone(context, currentSong);
                        }
                        break;
                    case 11:
                        if (currentSong != null) {
                            callbacks.onDeleteSongPermanently(currentSong);
                        }
                        break;
                }
            });

            grid.addView(tileLayout);
        }
        contentCol.addView(grid);

        // Pure-White Playback Speed Control Row (0.50x to 2.50x)
        LinearLayout speedRow = new LinearLayout(context);
        speedRow.setOrientation(LinearLayout.VERTICAL);
        speedRow.setPadding((int) (8 * d), (int) (8 * d), (int) (8 * d), (int) (10 * d));
        GradientDrawable speedBg = new GradientDrawable();
        speedBg.setColor(Color.parseColor("#18FFFFFF"));
        speedBg.setCornerRadius(16f * d);
        speedBg.setStroke(Math.max(1, (int) (1.2f * d)), Color.WHITE);
        speedRow.setBackground(speedBg);

        LinearLayout speedHeader = new LinearLayout(context);
        speedHeader.setOrientation(LinearLayout.HORIZONTAL);
        speedHeader.setGravity(Gravity.CENTER_VERTICAL);
        speedHeader.setPadding((int) (6 * d), 0, (int) (6 * d), (int) (4 * d));

        final TextView tvSpeedTitle = new TextView(context);
        tvSpeedTitle.setText(String.format(Locale.US, "Playback Speed: %.2fx", callbacks.getPlaybackSpeed()));
        tvSpeedTitle.setTextColor(Color.WHITE);
        tvSpeedTitle.setTextSize(13.5f);
        tvSpeedTitle.setTypeface(null, Typeface.BOLD);
        tvSpeedTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnResetSpeed = new TextView(context);
        btnResetSpeed.setText("Reset 1.0x");
        btnResetSpeed.setTextColor(Color.parseColor("#0A0A10"));
        btnResetSpeed.setTextSize(11f);
        btnResetSpeed.setTypeface(null, Typeface.BOLD);
        btnResetSpeed.setPadding((int) (12 * d), (int) (5 * d), (int) (12 * d), (int) (5 * d));
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setColor(Color.WHITE);
        resetBg.setCornerRadius(12f * d);
        btnResetSpeed.setBackground(resetBg);

        final SeekBar sbSpeed = new SeekBar(context);
        sbSpeed.setMax(200);
        int initProgress = Math.max(0, Math.min(200, Math.round((callbacks.getPlaybackSpeed() - 0.5f) * 100f)));
        sbSpeed.setProgress(initProgress);
        if (sbSpeed.getProgressDrawable() != null) {
            sbSpeed.getProgressDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
        if (sbSpeed.getThumb() != null) {
            sbSpeed.getThumb().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float sp = 0.5f + (progress / 100f);
                    tvSpeedTitle.setText(String.format(Locale.US, "Playback Speed: %.2fx", sp));
                    callbacks.onChangePlaybackSpeed(sp);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        attachBouncySpring(btnResetSpeed, () -> {
            sbSpeed.setProgress(50);
            tvSpeedTitle.setText("Playback Speed: 1.00x");
            callbacks.onChangePlaybackSpeed(1.0f);
        });

        speedHeader.addView(tvSpeedTitle);
        speedHeader.addView(btnResetSpeed);
        speedRow.addView(speedHeader);
        speedRow.addView(sbSpeed);
        contentCol.addView(speedRow);

        // Pure-White Loop / Shuffle / Swap Icons Row
        LinearLayout loopRow = new LinearLayout(context);
        loopRow.setOrientation(LinearLayout.HORIZONTAL);
        loopRow.setWeightSum(4f);
        LinearLayout.LayoutParams loopLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loopLp.topMargin = (int) (10 * d);
        loopRow.setLayoutParams(loopLp);

        final String[] loopLabels = new String[]{"Shuffle", "Repeat 1", "Loop All", "Swap Icons"};
        for (int i = 0; i < 4; i++) {
            final int modeIdx = i;
            TextView btn = new TextView(context);
            btn.setText(loopLabels[i]);
            btn.setTextSize(12f);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, (int) (38 * d), 1f);
            int bm = (int) (3 * d);
            blp.setMargins(bm, 0, bm, 0);
            btn.setLayoutParams(blp);

            boolean isSelected = (i < 3 && callbacks.getLoopMode() == i);
            GradientDrawable lBg = new GradientDrawable();
            lBg.setColor(isSelected ? Color.WHITE : Color.parseColor("#18FFFFFF"));
            lBg.setCornerRadius(14f * d);
            lBg.setStroke(Math.max(1, (int) (1.2f * d)), Color.WHITE);
            btn.setBackground(lBg);
            btn.setTextColor(isSelected ? Color.parseColor("#0A0A10") : Color.WHITE);

            attachBouncySpring(btn, () -> {
                if (modeIdx == 3) {
                    callbacks.onSwapBottomControlsOrder();
                    dialog.dismiss();
                } else {
                    callbacks.onChangeLoopMode(modeIdx);
                    dialog.dismiss();
                }
            });
            loopRow.addView(btn);
        }
        contentCol.addView(loopRow);

        // VIP & Rewarded Ad Banner (Monetization Card: Watch Ad or Get ₹50/₹60/₹99 VIP)
        LinearLayout vipBanner = new LinearLayout(context);
        vipBanner.setOrientation(LinearLayout.HORIZONTAL);
        vipBanner.setGravity(Gravity.CENTER_VERTICAL);
        vipBanner.setPadding((int) (12 * d), (int) (10 * d), (int) (12 * d), (int) (10 * d));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = (int) (10 * d);
        vipBanner.setLayoutParams(vlp);

        GradientDrawable vipBg = new GradientDrawable();
        vipBg.setColor(Color.parseColor("#24FFFFFF"));
        vipBg.setCornerRadius(16f * d);
        vipBg.setStroke(Math.max(1, (int) (1.4f * d)), Color.WHITE);
        vipBanner.setBackground(vipBg);

        TextView tvVipInfo = new TextView(context);
        tvVipInfo.setText("👑 Unlock 300% Extreme Bass & Golden VIP\nWatch 1 Short Ad (Free) or Get ₹50 / ₹99 VIP Pass");
        tvVipInfo.setTextColor(Color.WHITE);
        tvVipInfo.setTextSize(11.5f);
        tvVipInfo.setTypeface(null, Typeface.BOLD);
        tvVipInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnGoVip = new TextView(context);
        btnGoVip.setText("Unlock ⚡");
        btnGoVip.setTextColor(Color.parseColor("#0A0A10"));
        btnGoVip.setTextSize(11.5f);
        btnGoVip.setTypeface(null, Typeface.BOLD);
        btnGoVip.setPadding((int) (12 * d), (int) (7 * d), (int) (12 * d), (int) (7 * d));
        GradientDrawable goBg = new GradientDrawable();
        goBg.setColor(Color.WHITE);
        goBg.setCornerRadius(12f * d);
        btnGoVip.setBackground(goBg);

        vipBanner.addView(tvVipInfo);
        vipBanner.addView(btnGoVip);
        attachBouncySpring(vipBanner, () -> {
            dialog.dismiss();
            callbacks.onOpenVipPlansOrWatchAd();
        });
        contentCol.addView(vipBanner);

        scroll.addView(contentCol);
        rootCard.addView(scroll);
        dialog.show();
    }

    public static void setSongAsPhoneRingtone(Context context, SongItem song) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Toast.makeText(context, "Allow Modify System Settings to set Ringtone", Toast.LENGTH_LONG).show();
                return;
            }
            Uri ringtoneUri = song.id > 0
                    ? ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                    : null;
            if (ringtoneUri == null && song.path != null && !song.path.isEmpty()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DATA, song.path);
                values.put(MediaStore.MediaColumns.TITLE, song.title);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3");
                values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
                ringtoneUri = context.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            }
            if (ringtoneUri != null) {
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, ringtoneUri);
                Toast.makeText(context, "Phone Ringtone Set: " + song.title, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Ringtone set request sent", Toast.LENGTH_SHORT).show();
        }
    }

    public static void shareSongFile(Context context, SongItem song) {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("audio/*");
            share.putExtra(Intent.EXTRA_STREAM, song.getMediaUri());
            share.putExtra(Intent.EXTRA_TEXT, "Listening to " + song.title + " on Hybrid Music Player");
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(share, "Share Song"));
        } catch (Exception ignored) {
        }
    }

    /**
     * Unified 100% Pure-White (#FFFFFF) Vector Icon Engine for the entire Audio Player & 3-Dot Studio.
     */
    public static class PureWhiteAudioVectorDrawable extends Drawable {

        private final String type;
        private final float d;
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        public PureWhiteAudioVectorDrawable(String type, float density) {
            this.type = type;
            this.d = density;

            strokePaint.setColor(Color.WHITE);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2.4f * d);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);

            fillPaint.setColor(Color.WHITE);
            fillPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float w = b.width();
            float h = b.height();
            if (w <= 0f || h <= 0f) return;
            float cx = b.exactCenterX();
            float cy = b.exactCenterY();
            float r = Math.min(w, h) * 0.39f;
            path.reset();

            switch (type) {
                case "BACK_ARROW":
                case "DOWN_ARROW":
                    canvas.drawLine(cx - r * 0.62f, cy - r * 0.25f, cx, cy + r * 0.38f, strokePaint);
                    canvas.drawLine(cx, cy + r * 0.38f, cx + r * 0.62f, cy - r * 0.25f, strokePaint);
                    break;
                case "THREE_DOTS":
                    canvas.drawCircle(cx, cy - r * 0.66f, 2.6f * d, fillPaint);
                    canvas.drawCircle(cx, cy, 2.6f * d, fillPaint);
                    canvas.drawCircle(cx, cy + r * 0.66f, 2.6f * d, fillPaint);
                    break;
                case "PLAY":
                    path.moveTo(cx - r * 0.32f, cy - r * 0.58f);
                    path.lineTo(cx + r * 0.64f, cy);
                    path.lineTo(cx - r * 0.32f, cy + r * 0.58f);
                    path.close();
                    canvas.drawPath(path, fillPaint);
                    break;
                case "PAUSE":
                    float bw = r * 0.32f;
                    float bh = r * 1.12f;
                    rect.set(cx - r * 0.48f, cy - bh / 2f, cx - r * 0.48f + bw, cy + bh / 2f);
                    canvas.drawRoundRect(rect, 2.5f * d, 2.5f * d, fillPaint);
                    rect.set(cx + r * 0.16f, cy - bh / 2f, cx + r * 0.16f + bw, cy + bh / 2f);
                    canvas.drawRoundRect(rect, 2.5f * d, 2.5f * d, fillPaint);
                    break;
                case "PREV":
                    canvas.drawLine(cx - r * 0.55f, cy - r * 0.52f, cx - r * 0.55f, cy + r * 0.52f, strokePaint);
                    path.moveTo(cx + r * 0.55f, cy - r * 0.52f);
                    path.lineTo(cx - r * 0.38f, cy);
                    path.lineTo(cx + r * 0.55f, cy + r * 0.52f);
                    path.close();
                    canvas.drawPath(path, fillPaint);
                    break;
                case "NEXT":
                    canvas.drawLine(cx + r * 0.55f, cy - r * 0.52f, cx + r * 0.55f, cy + r * 0.52f, strokePaint);
                    path.moveTo(cx - r * 0.55f, cy - r * 0.52f);
                    path.lineTo(cx + r * 0.38f, cy);
                    path.lineTo(cx - r * 0.55f, cy + r * 0.52f);
                    path.close();
                    canvas.drawPath(path, fillPaint);
                    break;
                case "SHUFFLE":
                    canvas.drawLine(cx - r * 0.72f, cy - r * 0.45f, cx + r * 0.72f, cy + r * 0.45f, strokePaint);
                    canvas.drawLine(cx - r * 0.72f, cy + r * 0.45f, cx + r * 0.72f, cy - r * 0.45f, strokePaint);
                    canvas.drawLine(cx + r * 0.35f, cy - r * 0.45f, cx + r * 0.72f, cy - r * 0.45f, strokePaint);
                    canvas.drawLine(cx + r * 0.35f, cy + r * 0.45f, cx + r * 0.72f, cy + r * 0.45f, strokePaint);
                    break;
                case "REPEAT_ALL":
                case "REPEAT_ONE":
                case "AB_REPEAT":
                    rect.set(cx - r * 0.75f, cy - r * 0.52f, cx + r * 0.75f, cy + r * 0.52f);
                    canvas.drawRoundRect(rect, 5f * d, 5f * d, strokePaint);
                    if ("REPEAT_ONE".equals(type)) {
                        canvas.drawLine(cx, cy - r * 0.25f, cx, cy + r * 0.25f, strokePaint);
                    }
                    break;
                case "EQUALIZER":
                    canvas.drawLine(cx - r * 0.58f, cy - r * 0.72f, cx - r * 0.58f, cy + r * 0.72f, strokePaint);
                    canvas.drawLine(cx, cy - r * 0.72f, cx, cy + r * 0.72f, strokePaint);
                    canvas.drawLine(cx + r * 0.58f, cy - r * 0.72f, cx + r * 0.58f, cy + r * 0.72f, strokePaint);
                    canvas.drawCircle(cx - r * 0.58f, cy - r * 0.2f, 2.8f * d, fillPaint);
                    canvas.drawCircle(cx, cy + r * 0.25f, 2.8f * d, fillPaint);
                    canvas.drawCircle(cx + r * 0.58f, cy - r * 0.25f, 2.8f * d, fillPaint);
                    break;
                case "SPATIAL_8D":
                    rect.set(cx - r * 0.85f, cy - r * 0.48f, cx + r * 0.85f, cy + r * 0.48f);
                    canvas.drawOval(rect, strokePaint);
                    canvas.drawCircle(cx, cy, r * 0.26f, fillPaint);
                    break;
                case "FREQ_HZ":
                    path.moveTo(cx - r * 0.8f, cy);
                    path.lineTo(cx - r * 0.35f, cy - r * 0.65f);
                    path.lineTo(cx + r * 0.25f, cy + r * 0.65f);
                    path.lineTo(cx + r * 0.8f, cy);
                    canvas.drawPath(path, strokePaint);
                    break;
                case "QUEUE_LIST":
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.55f, cx + r * 0.75f, cy - r * 0.55f, strokePaint);
                    canvas.drawLine(cx - r * 0.75f, cy, cx + r * 0.75f, cy, strokePaint);
                    canvas.drawLine(cx - r * 0.75f, cy + r * 0.55f, cx + r * 0.35f, cy + r * 0.55f, strokePaint);
                    break;
                case "HEART_ON":
                case "HEART_OFF":
                    canvas.drawCircle(cx, cy, r * 0.76f, strokePaint);
                    if ("HEART_ON".equals(type)) {
                        canvas.drawCircle(cx, cy, r * 0.42f, fillPaint);
                    }
                    break;
                case "TIMER":
                    canvas.drawCircle(cx, cy, r * 0.78f, strokePaint);
                    canvas.drawLine(cx, cy, cx, cy - r * 0.45f, strokePaint);
                    canvas.drawLine(cx, cy, cx + r * 0.35f, cy, strokePaint);
                    break;
                case "PITCH_LAB":
                case "BOOST":
                case "WAVEFORM":
                    canvas.drawLine(cx - r * 0.6f, cy - r * 0.35f, cx - r * 0.6f, cy + r * 0.35f, strokePaint);
                    canvas.drawLine(cx - r * 0.2f, cy - r * 0.75f, cx - r * 0.2f, cy + r * 0.75f, strokePaint);
                    canvas.drawLine(cx + r * 0.2f, cy - r * 0.55f, cx + r * 0.2f, cy + r * 0.55f, strokePaint);
                    canvas.drawLine(cx + r * 0.6f, cy - r * 0.25f, cx + r * 0.6f, cy + r * 0.25f, strokePaint);
                    break;
                case "RINGTONE":
                    rect.set(cx - r * 0.62f, cy - r * 0.65f, cx + r * 0.62f, cy + r * 0.45f);
                    canvas.drawRoundRect(rect, 6f * d, 6f * d, strokePaint);
                    canvas.drawLine(cx - r * 0.78f, cy + r * 0.45f, cx + r * 0.78f, cy + r * 0.45f, strokePaint);
                    canvas.drawCircle(cx, cy + r * 0.68f, 2.5f * d, fillPaint);
                    break;
                case "VINYL_DISC":
                    canvas.drawCircle(cx, cy, r * 0.8f, strokePaint);
                    canvas.drawCircle(cx, cy, r * 0.26f, fillPaint);
                    break;
                case "PHOTO":
                    rect.set(cx - r * 0.75f, cy - r * 0.6f, cx + r * 0.75f, cy + r * 0.6f);
                    canvas.drawRoundRect(rect, 4f * d, 4f * d, strokePaint);
                    canvas.drawCircle(cx - r * 0.25f, cy - r * 0.15f, 2.5f * d, fillPaint);
                    break;
                case "SHARE":
                    canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy - r * 0.5f, strokePaint);
                    canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy + r * 0.5f, strokePaint);
                    canvas.drawCircle(cx - r * 0.5f, cy, 3f * d, fillPaint);
                    canvas.drawCircle(cx + r * 0.5f, cy - r * 0.5f, 3f * d, fillPaint);
                    canvas.drawCircle(cx + r * 0.5f, cy + r * 0.5f, 3f * d, fillPaint);
                    break;
                case "DELETE":
                    canvas.drawLine(cx - r * 0.75f, cy - r * 0.52f, cx + r * 0.75f, cy - r * 0.52f, strokePaint);
                    rect.set(cx - r * 0.55f, cy - r * 0.52f, cx + r * 0.55f, cy + r * 0.78f);
                    canvas.drawRoundRect(rect, 3f * d, 3f * d, strokePaint);
                    break;
                default:
                    canvas.drawCircle(cx, cy, r * 0.72f, strokePaint);
                    break;
            }
        }

        @Override
        public void setAlpha(int alpha) {
            strokePaint.setAlpha(alpha);
            fillPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            strokePaint.setColorFilter(colorFilter);
            fillPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
