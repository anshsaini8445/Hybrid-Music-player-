package com.hybridmusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class DancingPuppyView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ovalRect = new RectF();
    private final Path earPath = new Path();

    private boolean isPlaying = false;
    private float phase = 0f;
    private float superJumpBoost = 0f;
    private int accentColor = Color.parseColor("#00E5FF");

    public DancingPuppyView(Context context) {
        super(context);
        init();
    }

    public DancingPuppyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setOnClickListener(v -> {
            // Extra happy jump when user taps the puppy!
            superJumpBoost = 18f;
            invalidate();
        });
    }

    public void setPlayingState(boolean playing) {
        this.isPlaying = playing;
        invalidate();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float scale = Math.min(w, h) / 110f;

        if (isPlaying) {
            phase += 0.22f;
        } else {
            phase += 0.04f;
        }

        if (superJumpBoost > 0.2f) {
            superJumpBoost *= 0.85f;
        } else {
            superJumpBoost = 0f;
        }

        float bounceY = isPlaying
                ? (float) Math.abs(Math.sin(phase)) * (-14f * scale) - (superJumpBoost * scale)
                : (float) Math.sin(phase) * (-2f * scale) - (superJumpBoost * scale);

        float headTilt = isPlaying ? (float) Math.sin(phase * 0.8f) * 10f : 0f;
        float tailWag = isPlaying ? (float) Math.sin(phase * 2.2f) * 22f : (float) Math.sin(phase) * 5f;
        float earFlop = isPlaying ? (float) Math.abs(Math.cos(phase)) * 8f * scale : 0f;

        // Soft glowing shadow on floor
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(70, 0, 229, 255));
        float shadowRadius = (26f - ( -bounceY / (2f * scale))) * scale;
        ovalRect.set(cx - shadowRadius, h - 14f * scale, cx + shadowRadius, h - 5f * scale);
        canvas.drawOval(ovalRect, paint);

        canvas.save();
        canvas.translate(0, bounceY);

        // Wagging Tail
        canvas.save();
        canvas.rotate(tailWag, cx + 16f * scale, cy + 18f * scale);
        paint.setColor(Color.parseColor("#D97706"));
        ovalRect.set(cx + 14f * scale, cy + 4f * scale, cx + 32f * scale, cy + 16f * scale);
        canvas.drawRoundRect(ovalRect, 8f * scale, 8f * scale, paint);
        canvas.restore();

        // Puppy Body
        paint.setColor(Color.parseColor("#F59E0B"));
        ovalRect.set(cx - 20f * scale, cy + 4f * scale, cx + 20f * scale, cy + 36f * scale);
        canvas.drawRoundRect(ovalRect, 14f * scale, 14f * scale, paint);

        // White Tummy Patch
        paint.setColor(Color.parseColor("#FEF3C7"));
        ovalRect.set(cx - 11f * scale, cy + 10f * scale, cx + 11f * scale, cy + 33f * scale);
        canvas.drawOval(ovalRect, paint);

        // Dancing Paws
        float leftPawLift = isPlaying ? (float) Math.max(0, Math.sin(phase)) * 6f * scale : 0f;
        float rightPawLift = isPlaying ? (float) Math.max(0, -Math.sin(phase)) * 6f * scale : 0f;
        paint.setColor(Color.parseColor("#FFFFFF"));
        canvas.drawCircle(cx - 12f * scale, cy + 35f * scale - leftPawLift, 6.5f * scale, paint);
        canvas.drawCircle(cx + 12f * scale, cy + 35f * scale - rightPawLift, 6.5f * scale, paint);

        // Neon Headphones Collar
        paint.setColor(accentColor);
        ovalRect.set(cx - 16f * scale, cy + 3f * scale, cx + 16f * scale, cy + 9f * scale);
        canvas.drawRoundRect(ovalRect, 4f * scale, 4f * scale, paint);

        // Puppy Head (Tilts to the rhythm)
        canvas.save();
        canvas.rotate(headTilt, cx, cy - 8f * scale);

        // Floppy Ears
        paint.setColor(Color.parseColor("#92400E"));
        earPath.reset();
        ovalRect.set(cx - 33f * scale, cy - 26f * scale - earFlop, cx - 17f * scale, cy + 2f * scale - earFlop);
        canvas.drawRoundRect(ovalRect, 9f * scale, 9f * scale, paint);

        ovalRect.set(cx + 17f * scale, cy - 26f * scale - earFlop, cx + 33f * scale, cy + 2f * scale - earFlop);
        canvas.drawRoundRect(ovalRect, 9f * scale, 9f * scale, paint);

        // Main Head Circle
        paint.setColor(Color.parseColor("#FBBF24"));
        ovalRect.set(cx - 24f * scale, cy - 32f * scale, cx + 24f * scale, cy + 8f * scale);
        canvas.drawRoundRect(ovalRect, 18f * scale, 18f * scale, paint);

        // Cute Snout / Muzzle
        paint.setColor(Color.parseColor("#FFFBEB"));
        ovalRect.set(cx - 12f * scale, cy - 11f * scale, cx + 12f * scale, cy + 5f * scale);
        canvas.drawOval(ovalRect, paint);

        // Expressive Eyes
        paint.setColor(Color.parseColor("#1E293B"));
        boolean blink = ((int) (phase * 3)) % 23 == 0;
        if (blink) {
            paint.setStrokeWidth(2.5f * scale);
            canvas.drawLine(cx - 14f * scale, cy - 15f * scale, cx - 6f * scale, cy - 15f * scale, paint);
            canvas.drawLine(cx + 6f * scale, cy - 15f * scale, cx + 14f * scale, cy - 15f * scale, paint);
        } else {
            canvas.drawCircle(cx - 10f * scale, cy - 16f * scale, 4.2f * scale, paint);
            canvas.drawCircle(cx + 10f * scale, cy - 16f * scale, 4.2f * scale, paint);
            // Eye Sparkle
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx - 11.2f * scale, cy - 17.2f * scale, 1.5f * scale, paint);
            canvas.drawCircle(cx + 8.8f * scale, cy - 17.2f * scale, 1.5f * scale, paint);
        }

        // Nose
        paint.setColor(Color.parseColor("#1E293B"));
        ovalRect.set(cx - 4.5f * scale, cy - 9f * scale, cx + 4.5f * scale, cy - 3.5f * scale);
        canvas.drawOval(ovalRect, paint);

        // Happy Pink Tongue when dancing
        if (isPlaying) {
            paint.setColor(Color.parseColor("#FB7185"));
            ovalRect.set(cx - 3.5f * scale, cy - 2f * scale, cx + 3.5f * scale, cy + 6f * scale);
            canvas.drawRoundRect(ovalRect, 4f * scale, 4f * scale, paint);
        }

        canvas.restore(); // End head tilt
        canvas.restore(); // End bounce

        // Floating Musical Notes when playing
        if (isPlaying) {
            paint.setColor(accentColor);
            paint.setTextSize(15f * scale);
            paint.setFakeBoldText(true);
            float noteOffset = (phase * 6f) % (24f * scale);
            canvas.drawText("♪", cx - 38f * scale, cy - 12f * scale - noteOffset, paint);
            canvas.drawText("♫", cx + 28f * scale, cy - 18f * scale - (noteOffset * 0.7f), paint);
        }

        postInvalidateDelayed(isPlaying ? 32 : 120);
    }
}
