package com.hybridmusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class WaveformSeekBarView extends View {

    public interface OnWaveformSeekListener {
        void onSeekToFraction(float fraction);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private static final int BAR_COUNT = 46;
    private final float[] barHeights = new float[BAR_COUNT];

    private float progressFraction = 0f; // 0.0 to 1.0
    private float markerAFraction = -1f; // A-B Repeat Start
    private float markerBFraction = -1f; // A-B Repeat End
    private boolean isWaveformStyle = true; // true = Lambe Dande, false = Simple Player Line
    private boolean isPlaying = false;
    private float animPhase = 0f;
    private int accentColor = Color.parseColor("#00E5FF");

    private OnWaveformSeekListener seekListener;

    public WaveformSeekBarView(Context context) {
        super(context);
        initBars();
    }

    public WaveformSeekBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initBars();
    }

    private void initBars() {
        for (int i = 0; i < BAR_COUNT; i++) {
            // Musical wave pattern for the tall bars ("Lambe-Lambe Dande")
            float wave = (float) (0.30 + 0.65 * Math.abs(Math.sin(i * 0.42f) * Math.cos(i * 0.19f)));
            barHeights[i] = Math.min(1.0f, Math.max(0.22f, wave));
        }
    }

    public void setOnWaveformSeekListener(OnWaveformSeekListener listener) {
        this.seekListener = listener;
    }

    public void setProgressFraction(float fraction) {
        this.progressFraction = Math.max(0f, Math.min(1f, fraction));
        invalidate();
    }

    public void setWaveformStyle(boolean waveformStyle) {
        this.isWaveformStyle = waveformStyle;
        invalidate();
    }

    public boolean isWaveformStyle() {
        return isWaveformStyle;
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        invalidate();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        invalidate();
    }

    public void setABMarkers(float fractionA, float fractionB) {
        this.markerAFraction = fractionA;
        this.markerBFraction = fractionB;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            float w = getWidth();
            if (w > 0) {
                float frac = Math.max(0f, Math.min(1f, event.getX() / w));
                this.progressFraction = frac;
                if (seekListener != null) {
                    seekListener.onSeekToFraction(frac);
                }
                invalidate();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (isPlaying) {
            animPhase += 0.25f;
        }

        float cy = h / 2f;

        if (isWaveformStyle) {
            // Mode 1: Tall Waveform Bars ("Lambe-Lambe Dande")
            float totalSpacing = w * 0.32f;
            float barWidth = (w - totalSpacing) / BAR_COUNT;
            float gap = totalSpacing / (BAR_COUNT + 1);
            int activeBarLimit = Math.round(progressFraction * BAR_COUNT);

            for (int i = 0; i < BAR_COUNT; i++) {
                float left = gap + i * (barWidth + gap);
                float right = left + barWidth;

                float dynamicBoost = 0f;
                if (isPlaying && Math.abs(i - activeBarLimit) <= 2) {
                    dynamicBoost = (float) Math.abs(Math.sin(animPhase + i)) * 0.20f;
                }

                float barH = Math.min(1f, barHeights[i] + dynamicBoost) * (h * 0.82f);
                float top = cy - (barH / 2f);
                float bottom = cy + (barH / 2f);

                if (i <= activeBarLimit) {
                    paint.setColor(accentColor);
                } else {
                    paint.setColor(Color.argb(85, 148, 163, 184));
                }

                barRect.set(left, top, right, bottom);
                canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, paint);
            }
        } else {
            // Mode 2: Simple Clean Player Line
            float lineHeight = 8f;
            paint.setColor(Color.argb(85, 148, 163, 184));
            barRect.set(12f, cy - lineHeight / 2f, w - 12f, cy + lineHeight / 2f);
            canvas.drawRoundRect(barRect, 4f, 4f, paint);

            float thumbX = 12f + progressFraction * (w - 24f);
            paint.setColor(accentColor);
            barRect.set(12f, cy - lineHeight / 2f, thumbX, cy + lineHeight / 2f);
            canvas.drawRoundRect(barRect, 4f, 4f, paint);

            // Glowing Scrubber Thumb
            canvas.drawCircle(thumbX, cy, 13f, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(thumbX, cy, 6f, paint);
        }

        // Draw A-B Repeat Markers if active
        if (markerAFraction >= 0f) {
            float ax = markerAFraction * w;
            paint.setColor(Color.parseColor("#FFD700"));
            paint.setStrokeWidth(4f);
            canvas.drawLine(ax, 4f, ax, h - 4f, paint);
            paint.setTextSize(20f);
            canvas.drawText("A", ax + 4f, 20f, paint);
        }
        if (markerBFraction >= 0f) {
            float bx = markerBFraction * w;
            paint.setColor(Color.parseColor("#FF2E93"));
            paint.setStrokeWidth(4f);
            canvas.drawLine(bx, 4f, bx, h - 4f, paint);
            paint.setTextSize(20f);
            canvas.drawText("B", bx + 4f, 20f, paint);
        }

        if (isPlaying && isWaveformStyle) {
            postInvalidateDelayed(45);
        }
    }
}
