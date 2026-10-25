package com.hybridmusic.player;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
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

        // Preset Buttons (Flat, Bass Boost, DJ Party, Vocal Clear)
        findViewById(R.id.presetFlatBtn).setOnClickListener(v -> applyPreset(bandBars, 50, 50, 50, 50, 50));
        findViewById(R.id.presetBassBtn).setOnClickListener(v -> applyPreset(bandBars, 88, 76, 52, 60, 72));
        findViewById(R.id.presetDjBtn).setOnClickListener(v -> applyPreset(bandBars, 92, 78, 45, 78, 90));
        findViewById(R.id.presetVocalBtn).setOnClickListener(v -> applyPreset(bandBars, 42, 58, 85, 82, 65));

        // Sub-Woofer Bass Boost Slider
        SeekBar bassBar = findViewById(R.id.bassBoostSeekBar);
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

        // Smooth Playback Speed Slider (0.50x to 2.50x)
        SeekBar speedSeek = findViewById(R.id.speedSeekBar);
        TextView speedText = findViewById(R.id.speedTextValue);
        float currentSpeed = musicService != null ? musicService.getPlaybackSpeed() : 1.0f;
        speedSeek.setProgress(Math.round((currentSpeed - 0.5f) * 25f));
        speedText.setText(String.format(Locale.US, "Playback Speed: %.2fx (1.0x Normal)", currentSpeed));

        speedSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float speed = 0.5f + (progress / 25.0f);
                speedText.setText(String.format(Locale.US, "Playback Speed: %.2fx", speed));
                if (musicService != null) {
                    musicService.setPlaybackSpeed(speed);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.resetSpeedBtn).setOnClickListener(v -> speedSeek.setProgress(12)); // ~1.0x

        // Pre-set Sleep Timer Buttons (Off, 15m, 30m, 60m, 90m)
        findViewById(R.id.timerOffBtn).setOnClickListener(v -> setTimer(0));
        findViewById(R.id.timer15Btn).setOnClickListener(v -> setTimer(15));
        findViewById(R.id.timer30Btn).setOnClickListener(v -> setTimer(30));
        findViewById(R.id.timer60Btn).setOnClickListener(v -> setTimer(60));
        findViewById(R.id.timer90Btn).setOnClickListener(v -> setTimer(90));

        // Custom Minute Input (2m, 5m, 75m, 80m, etc.)
        EditText customMinInput = findViewById(R.id.customMinuteInput);
        Button setTimerBtn = findViewById(R.id.setCustomTimerButton);
        setTimerBtn.setOnClickListener(v -> {
            try {
                int mins = Integer.parseInt(customMinInput.getText().toString().trim());
                setTimer(mins);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Sahi minute enter karein (jaise 2, 5, 75, 80)", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.closeDialogBtn).setOnClickListener(v -> dismiss());
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
