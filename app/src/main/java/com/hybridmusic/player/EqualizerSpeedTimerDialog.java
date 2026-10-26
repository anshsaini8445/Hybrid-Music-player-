package com.hybridmusic.player;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
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

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Cyan-bordered rounded box like Screenshot 3
        View root = findViewById(R.id.eqDialogRoot);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(Color.parseColor("#0D141E"));
        boxBg.setCornerRadius(36f);
        boxBg.setStroke(3, themeManager.getCurrentTheme().accentPrimary);
        root.setBackground(boxBg);

        SeekBar speedSeek = findViewById(R.id.speedSeekBar);
        TextView speedText = findViewById(R.id.speedTextValue);
        SeekBar pitchSeek = findViewById(R.id.pitchSeekBar);
        TextView pitchText = findViewById(R.id.pitchTextValue);
        SeekBar bassBar = findViewById(R.id.bassBoostSeekBar);

        int[] bandIds = {R.id.eqBand1, R.id.eqBand2, R.id.eqBand3, R.id.eqBand4, R.id.eqBand5};
        final SeekBar[] bandBars = new SeekBar[5];

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            bandBars[i] = findViewById(bandIds[i]);
            if (musicService != null) {
                bandBars[i].setProgress(musicService.getEqualizerBand(idx));
            }
            bandBars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && musicService != null) {
                        musicService.setEqualizerBand(idx, progress);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Initialize Speed Slider (0.50x to 2.50x)
        float curSpeed = musicService != null ? musicService.getPlaybackSpeed() : 1.0f;
        speedSeek.setProgress(Math.round((curSpeed - 0.5f) * 25f));
        speedText.setText(String.format(Locale.US, "Playback Speed: %.2fx", curSpeed));

        speedSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float speed = 0.5f + (progress / 25.0f);
                speedText.setText(String.format(Locale.US, "Playback Speed: %.2fx", speed));
                if (fromUser && musicService != null) {
                    musicService.setPlaybackSpeed(speed);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Initialize Pitch Slider (0.50x to 2.00x)
        float curPitch = musicService != null ? musicService.getPlaybackPitch() : 1.0f;
        pitchSeek.setProgress(Math.round((curPitch - 0.5f) * 20f));
        pitchText.setText(String.format(Locale.US, "Voice Pitch Shift: %.2fx", curPitch));

        pitchSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float pitch = 0.5f + (progress / 20.0f);
                pitchText.setText(String.format(Locale.US, "Voice Pitch Shift: %.2fx", pitch));
                if (fromUser && musicService != null) {
                    musicService.setPlaybackPitch(pitch);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.resetSpeedBtn).setOnClickListener(v -> {
            speedSeek.setProgress(12); // ~1.0x
            pitchSeek.setProgress(10); // 1.0x
            if (musicService != null) {
                musicService.applyLofiSlowedReverbPreset(1.0f, 1.0f, 0, musicService.getBassBoostStrength());
            }
        });

        // Real Working Sound & Pitch Lab Presets (Original, Slowed + Reverb, Lofi Chill, Nightcore)
        findViewById(R.id.lofiNormalBtn).setOnClickListener(v -> {
            applySoundLabMode(speedSeek, pitchSeek, bassBar, 1.0f, 1.0f, 0, 0, "Original Studio Sound Active");
        });
        findViewById(R.id.lofiSlowedReverbBtn).setOnClickListener(v -> {
            applySoundLabMode(speedSeek, pitchSeek, bassBar, 0.84f, 0.88f, 3, 75, "Slowed + Hall Reverb Active 🎧");
        });
        findViewById(R.id.lofiChillBtn).setOnClickListener(v -> {
            applySoundLabMode(speedSeek, pitchSeek, bassBar, 0.90f, 0.92f, 2, 65, "Lofi Chill Warm Bass Active 🌙");
            applyPreset(bandBars, 82, 74, 55, 42, 35);
        });
        findViewById(R.id.lofiNightcoreBtn).setOnClickListener(v -> {
            applySoundLabMode(speedSeek, pitchSeek, bassBar, 1.24f, 1.22f, 1, 50, "Nightcore Fast Pitch Active ⚡");
        });

        // 5-Band Equalizer Presets
        findViewById(R.id.presetFlatBtn).setOnClickListener(v -> applyPreset(bandBars, 50, 50, 50, 50, 50));
        findViewById(R.id.presetBassBtn).setOnClickListener(v -> applyPreset(bandBars, 90, 78, 54, 62, 74));
        findViewById(R.id.presetDjBtn).setOnClickListener(v -> applyPreset(bandBars, 94, 80, 46, 80, 92));
        findViewById(R.id.presetVocalBtn).setOnClickListener(v -> applyPreset(bandBars, 44, 60, 86, 84, 68));

        if (musicService != null) {
            bassBar.setProgress(musicService.getBassBoostStrength());
        }
        bassBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && musicService != null) {
                    musicService.setBassBoostStrength(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Smart Sleep Timer Buttons (Off, 15m, 30m, 60m, 90m + Custom Input)
        findViewById(R.id.timerOffBtn).setOnClickListener(v -> setTimer(0));
        findViewById(R.id.timer15Btn).setOnClickListener(v -> setTimer(15));
        findViewById(R.id.timer30Btn).setOnClickListener(v -> setTimer(30));
        findViewById(R.id.timer60Btn).setOnClickListener(v -> setTimer(60));
        findViewById(R.id.timer90Btn).setOnClickListener(v -> setTimer(90));

        EditText customMinInput = findViewById(R.id.customMinuteInput);
        Button setTimerBtn = findViewById(R.id.setCustomTimerButton);
        setTimerBtn.setOnClickListener(v -> {
            try {
                int mins = Integer.parseInt(customMinInput.getText().toString().trim());
                setTimer(mins);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Sahi minute likhein (jaise 2, 5, 75, 80)", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.closeDialogTopBtn).setOnClickListener(v -> dismiss());
        findViewById(R.id.closeDialogBtn).setOnClickListener(v -> dismiss());
    }

    private void applySoundLabMode(SeekBar speedBar, SeekBar pitchBar, SeekBar bassBar,
                                   float speed, float pitch, int reverbIdx, int bass, String msg) {
        speedBar.setProgress(Math.round((speed - 0.5f) * 25f));
        pitchBar.setProgress(Math.round((pitch - 0.5f) * 20f));
        bassBar.setProgress(bass);
        if (musicService != null) {
            musicService.applyLofiSlowedReverbPreset(speed, pitch, reverbIdx, bass);
        }
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void applyPreset(SeekBar[] bars, int b1, int b2, int b3, int b4, int b5) {
        int[] vals = {b1, b2, b3, b4, b5};
        for (int i = 0; i < 5; i++) {
            bars[i].setProgress(vals[i]);
            if (musicService != null) {
                musicService.setEqualizerBand(i, vals[i]);
            }
        }
    }

    private void setTimer(int mins) {
        if (musicService != null) {
            musicService.setSleepTimerMinutes(mins);
        }
        String msg = mins <= 0 ? "Sleep Timer OFF" : "Sleep Timer Set: " + mins + " Minutes";
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
