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

    private static final int BAR_COUNT = 48;
    private final float[] baseHeights = new float[BAR_COUNT];
    private final float[] liveHeights = new float[BAR_COUNT];

    private float progressFraction = 0f;
    private float markerAFraction = -1f;
    private float markerBFraction = -1f;
    private boolean isWaveformStyle = true;
    private boolean isPlaying = false;
    private boolean isUserScrubbing = false;
    private float animPhase = 0f;
    private int accentColor = Color.WHITE;

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
            float wave = (float) (0.28 + 0.68 * Math.abs(Math.sin(i * 0.44f) * Math.cos(i * 0.21f)));
            baseHeights[i] = Math.min(1.0f, Math.max(0.22f, wave));
            liveHeights[i] = baseHeights[i];
        }
    }

    public void setOnWaveformSeekListener(OnWaveformSeekListener listener) {
        this.seekListener = listener;
    }

    public void setProgressFraction(float fraction) {
        if (!isUserScrubbing) {
            this.progressFraction = Math.max(0f, Math.min(1f, fraction));
            postInvalidateOnAnimation();
        }
    }

    public void setWaveformStyle(boolean waveformStyle) {
        this.isWaveformStyle = waveformStyle;
        postInvalidateOnAnimation();
    }

    public boolean isWaveformStyle() {
        return isWaveformStyle;
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        postInvalidateOnAnimation();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        postInvalidateOnAnimation();
    }

    public void setABMarkers(float fractionA, float fractionB) {
        this.markerAFraction = fractionA;
        this.markerBFraction = fractionB;
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            isUserScrubbing = (action != MotionEvent.ACTION_UP);
            float w = getWidth();
            if (w > 0) {
                float frac = Math.max(0f, Math.min(1f, event.getX() / w));
                this.progressFraction = frac;
                if (seekListener != null) {
                    seekListener.onSeekToFraction(frac);
                }
                postInvalidateOnAnimation();
            }
            return true;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            isUserScrubbing = false;
            postInvalidateOnAnimation();
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
            animPhase += 0.24f;
        }

        float cy = h * 0.46f;

        if (isWaveformStyle) {
            float totalSpacing = w * 0.30f;
            float barWidth = (w - totalSpacing) / BAR_COUNT;
            float gap = totalSpacing / (BAR_COUNT + 1);
            int activeBarLimit = Math.round(progressFraction * BAR_COUNT);

            for (int i = 0; i < BAR_COUNT; i++) {
                float targetH = baseHeights[i];
                if (isPlaying) {
                    float beatWave = (float) Math.abs(Math.sin(animPhase + i * 0.38f) * Math.cos(animPhase * 0.6f - i * 0.2f));
                    targetH = 0.22f + 0.76f * beatWave;
                }
                // Smooth 60FPS liquid interpolation
                liveHeights[i] += (targetH - liveHeights[i]) * 0.28f;

                float left = gap + i * (barWidth + gap);
                float right = left + barWidth;
                float mainBarH = liveHeights[i] * (h * 0.66f);
                float top = cy - (mainBarH / 2f);
                float bottom = cy + (mainBarH / 2f);

                // Main Waveform Bar (Pure White for played, soft frosted for upcoming)
                if (i <= activeBarLimit) {
                    paint.setColor(Color.WHITE);
                } else {
                    paint.setColor(Color.argb(80, 203, 213, 225));
                }
                barRect.set(left, top, right, bottom);
                canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, paint);

                // 3D Glass Mirror Reflection below each bar
                float reflectH = mainBarH * 0.26f;
                paint.setColor(i <= activeBarLimit
                        ? Color.argb(55, 255, 255, 255)
                        : Color.argb(22, 203, 213, 225));
                barRect.set(left, bottom + 3f, right, Math.min(h - 1f, bottom + 3f + reflectH));
                canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, paint);
            }

            // Glowing Laser Playhead Cursor
            float playheadX = Math.max(8f, Math.min(w - 8f, progressFraction * w));
            paint.setColor(Color.argb(95, 255, 255, 255));
            canvas.drawCircle(playheadX, cy, isUserScrubbing ? 15f : 10f, paint);
            paint.setColor(Color.WHITE);
            barRect.set(playheadX - 2.2f, 2f, playheadX + 2.2f, h - 4f);
            canvas.drawRoundRect(barRect, 2f, 2f, paint);
        } else {
            // Mode 2: Sleek Laser Straight Player Line
            float lineHeight = 7f;
            paint.setColor(Color.argb(85, 148, 163, 184));
            barRect.set(12f, cy - lineHeight / 2f, w - 12f, cy + lineHeight / 2f);
            canvas.drawRoundRect(barRect, 4f, 4f, paint);

            float thumbX = 12f + progressFraction * (w - 24f);
            paint.setColor(Color.WHITE);
            barRect.set(12f, cy - lineHeight / 2f, thumbX, cy + lineHeight / 2f);
            canvas.drawRoundRect(barRect, 4f, 4f, paint);

            paint.setColor(Color.argb(95, 255, 255, 255));
            canvas.drawCircle(thumbX, cy, isUserScrubbing ? 18f : 13f, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(thumbX, cy, 7f, paint);
        }

        // A-B Repeat Markers
        if (markerAFraction >= 0f) {
            float ax = markerAFraction * w;
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(3.5f);
            canvas.drawLine(ax, 2f, ax, h - 2f, paint);
            paint.setTextSize(18f);
            paint.setFakeBoldText(true);
            canvas.drawText("A", ax + 4f, 18f, paint);
        }
        if (markerBFraction >= 0f) {
            float bx = markerBFraction * w;
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(3.5f);
            canvas.drawLine(bx, 2f, bx, h - 2f, paint);
            paint.setTextSize(18f);
            paint.setFakeBoldText(true);
            canvas.drawText("B", bx + 4f, 18f, paint);
        }

        if (isPlaying) {
            postInvalidateOnAnimation();
        }
    }
}
