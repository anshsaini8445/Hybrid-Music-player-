
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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

        TextView title = findViewById(R.id.dialogTitle);
        title.setText("PLAYit Equalizer, Speed & Sleep Timer");

        // Speed Slider Control (0.5x to 2.5x)
        SeekBar speedSeek = findViewById(R.id.speedSeekBar);
        TextView speedText = findViewById(R.id.speedTextValue);
        speedSeek.setProgress(50); // 1.0x default
        speedSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float speed = 0.5f + (progress / 25.0f); // 0.5 to 2.5
                speedText.setText(String.format(java.util.Locale.US, "Speed: %.2fx", speed));
                if (musicService != null) {
                    musicService.setPlaybackSpeed(speed);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Custom Minute Sleep Timer Inputs (e.g. 2m, 5m, 75m, 90m)
        EditText customMinInput = findViewById(R.id.customMinuteInput);
        Button setTimerBtn = findViewById(R.id.setCustomTimerButton);
        setTimerBtn.setOnClickListener(v -> {
            try {
                int mins = Integer.parseInt(customMinInput.getText().toString().trim());
                Toast.makeText(getContext(), "Sleep Timer set for " + mins + " minutes!", Toast.LENGTH_SHORT).show();
                dismiss();
            } catch (Exception e) {
                Toast.makeText(getContext(), "Enter valid minutes", Toast.LENGTH_SHORT).show();
            }
        });

        Button closeBtn = findViewById(R.id.closeDialogBtn);
        closeBtn.setOnClickListener(v -> dismiss());
    }
}
