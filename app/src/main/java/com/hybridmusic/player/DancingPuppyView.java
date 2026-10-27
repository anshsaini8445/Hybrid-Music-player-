package com.hybridmusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Toast;
import androidx.core.graphics.ColorUtils;

public class DancingPuppyView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private ThemeManager themeManager;
    private boolean isPlaying = false;
    private int danceStyle = 0; // 0 = Cute Bounce, 1 = DJ Party Jump, 2 = Happy Sway
    private int previewBreedOverride = -1;
    private int previewColorOverride = -1;

    private float phase = 0f;
    private float tapJumpBoost = 0f;
    private int accentColor = Color.WHITE;

    public DancingPuppyView(Context context) {
        super(context);
        init(context);
    }

    public DancingPuppyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(final Context context) {
        this.themeManager = new ThemeManager(context);
        setClickable(true);
        setOnClickListener(v -> {
            danceStyle = (danceStyle + 1) % 3;
            tapJumpBoost = 18f;
            String[] styleNames = {
                    "🐶 Cute Puppy: Kawaii Bounce!",
                    "🎧 Cute Puppy: Super DJ Dance!",
                    "💖 Cute Puppy: Happy Heart Sway!"
            };
            Toast.makeText(context, styleNames[danceStyle], Toast.LENGTH_SHORT).show();
            invalidate();
        });
    }

    public void setPreviewBreedAndColor(int breedIdx, int colorIdx) {
        this.previewBreedOverride = breedIdx;
        this.previewColorOverride = colorIdx;
        invalidate();
    }

    public void refreshFromThemeManager() {
        this.previewBreedOverride = -1;
        this.previewColorOverride = -1;
        invalidate();
    }

    public void setPlayingState(boolean playing) {
        this.isPlaying = playing;
        invalidate();
    }

    public void setDanceStyle(int style) {
        this.danceStyle = Math.abs(style) % 3;
        invalidate();
    }

    public int getDanceStyle() {
        return danceStyle;
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

        int breed = previewBreedOverride >= 0
                ? previewBreedOverride
                : (themeManager != null ? themeManager.getPuppyBreedIndex() : 0);

        int coatColor = previewColorOverride >= 0
                ? ThemeManager.PUPPY_COLOR_HEX[previewColorOverride % ThemeManager.PUPPY_COLOR_HEX.length]
                : (themeManager != null ? themeManager.getSelectedPuppyColor() : Color.parseColor("#FBBF24"));

        int darkEarColor = ColorUtils.blendARGB(coatColor, Color.parseColor("#3E1F08"), 0.45f);

        float cx = w / 2f;
        float cy = h / 2f;
        float scale = Math.min(w, h) / 115f;

        float speedStep = isPlaying ? (danceStyle == 1 ? 0.34f : 0.22f) : 0.06f;
        phase += speedStep;

        if (tapJumpBoost > 0.3f) tapJumpBoost *= 0.84f;
        else tapJumpBoost = 0f;

        float bounceY = isPlaying
                ? (float) Math.abs(Math.sin(phase)) * (-14f * scale) - (tapJumpBoost * scale)
                : (float) Math.sin(phase) * (-2.5f * scale) - (tapJumpBoost * scale);

        float swayX = isPlaying ? (float) Math.sin(phase * 0.7f) * (5.5f * scale) : 0f;
        float headTilt = isPlaying ? (float) Math.sin(phase * 0.9f) * 11f : (float) Math.sin(phase * 0.5f) * 3f;
        float tailWag = isPlaying ? (float) Math.sin(phase * 2.6f) * 28f : (float) Math.sin(phase) * 8f;
        float earBounce = isPlaying ? (float) Math.abs(Math.cos(phase)) * 7f * scale : 0f;

        // 1. Glowing Music Aura Floor Shadow
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(coatColor, isPlaying ? 95 : 45));
        float shadowRx = (26f + (isPlaying ? (float) Math.abs(Math.sin(phase)) * 7f : 0f)) * scale;
        rect.set(cx - shadowRx, h - 13f * scale, cx + shadowRx, h - 4f * scale);
        canvas.drawOval(rect, paint);

        canvas.save();
        canvas.translate(swayX, bounceY);

        // 2. Cute Wagging Tail
        canvas.save();
        canvas.rotate(tailWag, cx + 14f * scale, cy + 18f * scale);
        paint.setColor(darkEarColor);
        rect.set(cx + 13f * scale, cy + 6f * scale, cx + 32f * scale, cy + 16f * scale);
        canvas.drawRoundRect(rect, 8f * scale, 8f * scale, paint);
        canvas.restore();

        // 3. Chibi Round Body
        paint.setColor(coatColor);
        rect.set(cx - 19f * scale, cy + 4f * scale, cx + 19f * scale, cy + 35f * scale);
        canvas.drawRoundRect(rect, 15f * scale, 15f * scale, paint);

        // Soft Cream Heart Belly Patch
        paint.setColor(Color.parseColor("#FFFBEB"));
        rect.set(cx - 11f * scale, cy + 9f * scale, cx + 11f * scale, cy + 32f * scale);
        canvas.drawOval(rect, paint);

        // Cute Red/Pink Bow-Tie on Collar
        paint.setColor(Color.parseColor("#FF2E93"));
        rect.set(cx - 9f * scale, cy + 4f * scale, cx + 9f * scale, cy + 10f * scale);
        canvas.drawRoundRect(rect, 4f * scale, 4f * scale, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy + 7f * scale, 2.5f * scale, paint);

        // Bouncing Tiny Paws
        float leftPaw = isPlaying ? (float) Math.max(0, Math.sin(phase)) * 7f * scale : 0f;
        float rightPaw = isPlaying ? (float) Math.max(0, -Math.sin(phase)) * 7f * scale : 0f;
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx - 11f * scale, cy + 34f * scale - leftPaw, 6.5f * scale, paint);
        canvas.drawCircle(cx + 11f * scale, cy + 34f * scale - rightPaw, 6.5f * scale, paint);
        // Pink Toe Beans
        paint.setColor(Color.parseColor("#FDA4AF"));
        canvas.drawCircle(cx - 11f * scale, cy + 34f * scale - leftPaw, 2.8f * scale, paint);
        canvas.drawCircle(cx + 11f * scale, cy + 34f * scale - rightPaw, 2.8f * scale, paint);

        // 4. Super-Cute Oversized Kawaii Head
        canvas.save();
        canvas.rotate(headTilt, cx, cy - 8f * scale);

        // Breed-Specific Ears
        paint.setColor(breed == 2 ? Color.parseColor("#1E293B") : darkEarColor);
        if (breed == 1 || breed == 3) {
            // Pointy Husky / Shiba Ears
            path.reset();
            path.moveTo(cx - 24f * scale, cy - 18f * scale);
            path.lineTo(cx - 30f * scale, cy - 42f * scale - earBounce);
            path.lineTo(cx - 10f * scale, cy - 28f * scale);
            path.close();
            canvas.drawPath(path, paint);

            path.reset();
            path.moveTo(cx + 24f * scale, cy - 18f * scale);
            path.lineTo(cx + 30f * scale, cy - 42f * scale - earBounce);
            path.lineTo(cx + 10f * scale, cy - 28f * scale);
            path.close();
            canvas.drawPath(path, paint);
        } else if (breed == 2) {
            // Round Chibi Panda Ears
            canvas.drawCircle(cx - 22f * scale, cy - 30f * scale - earBounce, 10f * scale, paint);
            canvas.drawCircle(cx + 22f * scale, cy - 30f * scale - earBounce, 10f * scale, paint);
        } else if (breed == 5) {
            // Long Cute Bunny-Pup Ears
            rect.set(cx - 22f * scale, cy - 48f * scale - earBounce, cx - 10f * scale, cy - 16f * scale);
            canvas.drawRoundRect(rect, 8f * scale, 8f * scale, paint);
            rect.set(cx + 10f * scale, cy - 48f * scale - earBounce, cx + 22f * scale, cy - 16f * scale);
            canvas.drawRoundRect(rect, 8f * scale, 8f * scale, paint);
        } else {
            // Classic Floppy Cute Puppy Ears
            rect.set(cx - 34f * scale, cy - 26f * scale - earBounce, cx - 17f * scale, cy + 3f * scale - earBounce);
            canvas.drawRoundRect(rect, 10f * scale, 10f * scale, paint);
            rect.set(cx + 17f * scale, cy - 26f * scale - earBounce, cx + 34f * scale, cy + 3f * scale - earBounce);
            canvas.drawRoundRect(rect, 10f * scale, 10f * scale, paint);
        }

        // Main Chubby Head
        paint.setColor(coatColor);
        rect.set(cx - 26f * scale, cy - 33f * scale, cx + 26f * scale, cy + 8f * scale);
        canvas.drawRoundRect(rect, 20f * scale, 20f * scale, paint);

        // Special Breed Accessories (Crown for Pug #4, Space Halo for Astro #6, DJ Headphones for others)
        if (breed == 4) {
            // Golden King Crown
            paint.setColor(Color.parseColor("#FFD700"));
            path.reset();
            path.moveTo(cx - 14f * scale, cy - 31f * scale);
            path.lineTo(cx - 16f * scale, cy - 44f * scale);
            path.lineTo(cx - 6f * scale, cy - 36f * scale);
            path.lineTo(cx, cy - 46f * scale);
            path.lineTo(cx + 6f * scale, cy - 36f * scale);
            path.lineTo(cx + 16f * scale, cy - 44f * scale);
            path.lineTo(cx + 14f * scale, cy - 31f * scale);
            path.close();
            canvas.drawPath(path, paint);
        } else if (breed == 6) {
            // Glowing Space Halo Ring
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * scale);
            paint.setColor(Color.parseColor("#00E5FF"));
            rect.set(cx - 22f * scale, cy - 43f * scale, cx + 22f * scale, cy - 33f * scale);
            canvas.drawOval(rect, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            // White & Neon Studio Headphones
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f * scale);
            paint.setColor(Color.WHITE);
            rect.set(cx - 27f * scale, cy - 36f * scale, cx + 27f * scale, cy + 3f * scale);
            canvas.drawArc(rect, 180f, 180f, false, paint);
            paint.setStyle(Paint.Style.FILL);
            rect.set(cx - 31f * scale, cy - 20f * scale, cx - 22f * scale, cy - 5f * scale);
            canvas.drawRoundRect(rect, 5f * scale, 5f * scale, paint);
            rect.set(cx + 22f * scale, cy - 20f * scale, cx + 31f * scale, cy - 5f * scale);
            canvas.drawRoundRect(rect, 5f * scale, 5f * scale, paint);
        }

        // Cute Cream Muzzle
        paint.setColor(Color.parseColor("#FFFBEB"));
        rect.set(cx - 13f * scale, cy - 11f * scale, cx + 13f * scale, cy + 5f * scale);
        canvas.drawOval(rect, paint);

        // Rosy Pink Kawaii Blushing Cheeks!
        paint.setColor(Color.argb(145, 255, 46, 147));
        rect.set(cx - 21f * scale, cy - 10f * scale, cx - 12f * scale, cy - 4f * scale);
        canvas.drawOval(rect, paint);
        rect.set(cx + 12f * scale, cy - 10f * scale, cx + 21f * scale, cy - 4f * scale);
        canvas.drawOval(rect, paint);

        // Big Sparkling Anime / Kawaii Eyes
        paint.setColor(Color.parseColor("#0F172A"));
        boolean blink = ((int) (phase * 2.2f)) % 24 == 0;
        if (blink) {
            paint.setStrokeWidth(2.6f * scale);
            canvas.drawLine(cx - 15f * scale, cy - 15f * scale, cx - 7f * scale, cy - 15f * scale, paint);
            canvas.drawLine(cx + 7f * scale, cy - 15f * scale, cx + 15f * scale, cy - 15f * scale, paint);
        } else {
            canvas.drawCircle(cx - 11f * scale, cy - 16f * scale, 5.2f * scale, paint);
            canvas.drawCircle(cx + 11f * scale, cy - 16f * scale, 5.2f * scale, paint);
            // Double Cute Eye Sparkles
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx - 12.5f * scale, cy - 17.8f * scale, 2.0f * scale, paint);
            canvas.drawCircle(cx - 9.5f * scale, cy - 14.5f * scale, 1.0f * scale, paint);
            canvas.drawCircle(cx + 9.5f * scale, cy - 17.8f * scale, 2.0f * scale, paint);
            canvas.drawCircle(cx + 12.5f * scale, cy - 14.5f * scale, 1.0f * scale, paint);
        }

        // Cute Button Nose & Happy Pink Tongue
        paint.setColor(Color.parseColor("#0F172A"));
        rect.set(cx - 4.5f * scale, cy - 9.5f * scale, cx + 4.5f * scale, cy - 4f * scale);
        canvas.drawOval(rect, paint);

        paint.setColor(Color.parseColor("#FF2E93"));
        rect.set(cx - 4f * scale, cy - 3f * scale, cx + 4f * scale, cy + 5.5f * scale);
        canvas.drawRoundRect(rect, 4f * scale, 4f * scale, paint);

        canvas.restore(); // End head tilt
        canvas.restore(); // End bounce

        // 5. Floating Hearts & Musical Notes when Dancing
        if (isPlaying) {
            paint.setColor(Color.WHITE);
            paint.setTextSize(14f * scale);
            paint.setFakeBoldText(true);
            float noteY = (phase * 5.5f) % (22f * scale);
            canvas.drawText("♪", cx - 39f * scale, cy - 12f * scale - noteY, paint);
            canvas.drawText("♥", cx + 27f * scale, cy - 16f * scale - (noteY * 0.75f), paint);
        }

        postInvalidateDelayed(isPlaying ? 30 : 140);
    }
}
