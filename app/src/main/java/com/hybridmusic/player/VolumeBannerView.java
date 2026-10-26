package com.hybridmusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

public class VolumeBannerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path speakerPath = new Path();
    private final Handler hideHandler = new Handler(Looper.getMainLooper());

    private int volumePercent = 80; // 0 to 200% (100% bar + 4 boost dots)
    private final Runnable hideRunnable = () -> animate().alpha(0f).setDuration(220)
            .withEndAction(() -> setVisibility(View.GONE)).start();

    public VolumeBannerView(Context context) {
        super(context);
        init();
    }

    public VolumeBannerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setVisibility(View.GONE);
        setAlpha(0f);
    }

    public void showVolume(int percent) {
        this.volumePercent = Math.max(0, Math.min(200, percent));
        hideHandler.removeCallbacks(hideRunnable);
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
            setAlpha(1f);
        } else {
            animate().cancel();
            setAlpha(1f);
        }
        invalidate();
        hideHandler.postDelayed(hideRunnable, 1800);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. Deep Pitch-Black Rounded Pill Background (Exact Screenshot 1000061267.jpg)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F505070A"));
        rect.set(0, 0, w, h);
        float radius = h * 0.24f;
        canvas.drawRoundRect(rect, radius, radius, paint);

        // Subtle dark border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.parseColor("#33FFFFFF"));
        canvas.drawRoundRect(rect, radius, radius, paint);

        // 2. Top Center Speaker Icon + Bold White "80%" Text
        float centerYTop = h * 0.40f;
        float centerX = w * 0.50f;

        // Speaker Cone Icon in Soft Grey/White
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#C8CDD4"));
        float spkX = centerX - 95f;
        speakerPath.reset();
        speakerPath.moveTo(spkX - 24f, centerYTop - 10f);
        speakerPath.lineTo(spkX - 10f, centerYTop - 10f);
        speakerPath.lineTo(spkX + 6f, centerYTop - 24f);
        speakerPath.lineTo(spkX + 6f, centerYTop + 24f);
        speakerPath.lineTo(spkX - 10f, centerYTop + 10f);
        speakerPath.lineTo(spkX - 24f, centerYTop + 10f);
        speakerPath.close();
        canvas.drawPath(speakerPath, paint);

        // Speaker Sound Waves
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (volumePercent > 0) {
            rect.set(spkX + 2f, centerYTop - 12f, spkX + 18f, centerYTop + 12f);
            canvas.drawArc(rect, -45f, 90f, false, paint);
        }
        if (volumePercent >= 50) {
            rect.set(spkX - 4f, centerYTop - 22f, spkX + 28f, centerYTop + 22f);
            canvas.drawArc(rect, -45f, 90f, false, paint);
        }

        // Bold Pure White Percentage Text ("80%")
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(h * 0.25f);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(volumePercent + "%", spkX + 46f, centerYTop + (h * 0.09f), paint);

        // 3. Bottom Thick White Bar + Dark Track + 4 Boost Dots (Exact Screenshot Match)
        float barY = h * 0.76f;
        float barHeight = h * 0.125f;
        float leftPad = w * 0.06f;
        float rightPad = w * 0.06f;

        // Reserve space on the right for the 4 circular dots
        float dotsAreaWidth = w * 0.22f;
        float trackLeft = leftPad;
        float trackRight = w - rightPad - dotsAreaWidth;
        float trackWidth = trackRight - trackLeft;

        // Dark Grey Unfilled Track
        paint.setColor(Color.parseColor("#45484F"));
        rect.set(trackLeft, barY - barHeight / 2f, trackRight, barY + barHeight / 2f);
        canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, paint);

        // Pure White Filled Progress Bar (0% to 100%)
        float baseFrac = Math.min(1.0f, volumePercent / 100f);
        if (baseFrac > 0f) {
            paint.setColor(Color.WHITE);
            float fillRight = Math.max(trackLeft + barHeight, trackLeft + trackWidth * baseFrac);
            rect.set(trackLeft, barY - barHeight / 2f, fillRight, barY + barHeight / 2f);
            canvas.drawRoundRect(rect, barHeight / 2f, barHeight / 2f, paint);
        }

        // 4 Circular Boost Dots on the Right (Lights up above 100% or shows grey capsules)
        float dotStart = trackRight + (dotsAreaWidth * 0.16f);
        float dotGap = (dotsAreaWidth * 0.84f) / 4f;
        float dotRadius = barHeight * 0.50f;

        for (int i = 0; i < 4; i++) {
            int threshold = 100 + (i + 1) * 25;
            paint.setColor(volumePercent >= threshold ? Color.WHITE : Color.parseColor("#45484F"));
            float dx = dotStart + i * dotGap;
            rect.set(dx - dotRadius * 1.15f, barY - dotRadius, dx + dotRadius * 1.15f, barY + dotRadius);
            canvas.drawRoundRect(rect, dotRadius, dotRadius, paint);
        }
    }
}
