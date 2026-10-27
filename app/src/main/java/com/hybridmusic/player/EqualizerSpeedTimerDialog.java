package com.hybridmusic.player;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

public class EqualizerSpeedTimerDialog extends Dialog {

    private final MusicPlaybackService musicService;
    private final ThemeManager themeManager;

    public EqualizerSpeedTimerDialog(Context context, MusicPlaybackService service, ThemeManager tm) {
        super(context);
        this.musicService = service;
        this.themeManager = tm;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_equalizer_speed);

        float d = getContext().getResources().getDisplayMetrics().density;
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Outer Glass Card
        View root = findViewById(R.id.eqDialogRoot);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(Color.parseColor("#F5080C14"));
        boxBg.setCornerRadius(28f * d);
        boxBg.setStroke(Math.max(2, (int) (1.8f * d)), Color.WHITE);
        root.setBackground(boxBg);

        // Style Inner Bento Sub-Cards
        styleSubCard(findViewById(R.id.eqCardContainer), d);
        styleSubCard(findViewById(R.id.speedCardContainer), d);
        styleSubCard(findViewById(R.id.timerCardContainer), d);

        TextView closeBtn = findViewById(R.id.closeDialogTopBtn);
        closeBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
        AudioThreeDotStudioHelper.attachBouncySpring(closeBtn, this::dismiss);

        // 1. Buttonless 5-Band Equalizer with Live dB Readout
        int[] bandIds = {R.id.eqBand1, R.id.eqBand2, R.id.eqBand3, R.id.eqBand4, R.id.eqBand5};
        int[] valIds = {R.id.eqVal1, R.id.eqVal2, R.id.eqVal3, R.id.eqVal4, R.id.eqVal5};

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            SeekBar bar = findViewById(bandIds[i]);
            final TextView valTv = findViewById(valIds[i]);
            tintSeekBarWhite(bar);

            int currentPct = musicService != null ? musicService.getEqualizerBand(idx) : 50;
            bar.setProgress(currentPct);
            updateDbText(valTv, currentPct);

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateDbText(valTv, progress);
                    if (fromUser && musicService != null) {
                        musicService.setEqualizerBand(idx, progress);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Sub-Woofer Bass Boost Slider
        SeekBar bassBar = findViewById(R.id.bassBoostSeekBar);
        final TextView bassTv = findViewById(R.id.bassTextValue);
        tintSeekBarWhite(bassBar);
        int curBass = musicService != null ? musicService.getBassBoostStrength() : 0;
        bassBar.setProgress(curBass);
        bassTv.setText("🔊 Sub-Woofer Deep Bass: " + curBass + "%");

        bassBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                bassTv.setText("🔊 Sub-Woofer Deep Bass: " + progress + "%");
                if (fromUser && musicService != null) {
                    musicService.setBassBoostStrength(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 2. Smooth Speedometer Dial (0.50x to 2.50x)
        SeekBar speedSeek = findViewById(R.id.speedSeekBar);
        final TextView speedText = findViewById(R.id.speedTextValue);
        tintSeekBarWhite(speedSeek);

        float curSpeed = musicService != null ? musicService.getPlaybackSpeed() : 1.0f;
        speedSeek.setProgress(Math.round((curSpeed - 0.5f) * 25f));
        speedText.setText(String.format(Locale.US, "⚡ Tempo Speed Dial: %.2fx", curSpeed));

        speedSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float speed = 0.5f + (progress / 25.0f);
                speedText.setText(String.format(Locale.US, "⚡ Tempo Speed Dial: %.2fx", speed));
                if (fromUser && musicService != null) {
                    musicService.setPlaybackSpeed(speed);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Lofi Pitch & Reverb Dial
        SeekBar pitchSeek = findViewById(R.id.pitchSeekBar);
        final TextView pitchText = findViewById(R.id.pitchTextValue);
        tintSeekBarWhite(pitchSeek);

        float curPitch = musicService != null ? musicService.getPlaybackPitch() : 1.0f;
        pitchSeek.setProgress(Math.round((curPitch - 0.5f) * 20f));
        pitchText.setText(String.format(Locale.US, "🎛️ Lofi Pitch & Hall Reverb: %.2fx", curPitch));

        pitchSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float pitch = 0.5f + (progress / 20.0f);
                boolean hallOn = Math.abs(pitch - 1.0f) > 0.06f;
                pitchText.setText(String.format(Locale.US,
                        "🎛️ Lofi Pitch: %.2fx %s", pitch, hallOn ? "• Hall Reverb ON" : ""));
                if (fromUser && musicService != null) {
                    musicService.setPlaybackPitch(pitch);
                    musicService.applyReverbPreset(hallOn ? 3 : 0);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 3. Smooth Sleep Timer Dial (0 to 120 Minutes - Any Custom Minute!)
        SeekBar timerSeek = findViewById(R.id.timerSeekBar);
        final TextView timerText = findViewById(R.id.timerTextValue);
        tintSeekBarWhite(timerSeek);

        int remMins = musicService != null ? musicService.getRemainingSleepMinutes() : 0;
        timerSeek.setProgress(Math.min(120, remMins));
        timerText.setText(remMins <= 0
                ? "⏳ Smart Sleep Timer Dial: OFF (Slide to Set Minutes)"
                : "⏳ Smart Sleep Timer Active: " + remMins + " Minutes");

        timerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress <= 0) {
                    timerText.setText("⏳ Smart Sleep Timer Dial: OFF");
                } else {
                    timerText.setText("⏳ Smart Sleep Timer Active: " + progress + " Minutes");
                }
                if (fromUser && musicService != null) {
                    musicService.setSleepTimerMinutes(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateDbText(TextView tv, int percent) {
        int db = Math.round(((percent - 50) / 50f) * 15f);
        tv.setText((db > 0 ? "+" : "") + db + "dB");
    }

    private void styleSubCard(View v, float d) {
        if (v == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#141E2E"));
        bg.setCornerRadius(18f * d);
        bg.setStroke(Math.max(1, (int) (1.2f * d)), Color.parseColor("#55FFFFFF"));
        v.setBackground(bg);
    }

    private void tintSeekBarWhite(SeekBar sb) {
        if (sb == null) return;
        if (sb.getProgressDrawable() != null) {
            sb.getProgressDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
        if (sb.getThumb() != null) {
            sb.getThumb().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
    }
}
