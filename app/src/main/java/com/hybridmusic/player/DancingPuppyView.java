package com.hybridmusic.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.Toast;
import androidx.core.graphics.ColorUtils;

public class DancingPuppyView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private ThemeManager themeManager;
    private boolean isPlaying = false;
    private int danceStyle = 0;
    private int previewBreedOverride = -1;
    private int previewColorOverride = -1;

    private float phase = 0f;
    private int accentColor = Color.WHITE;

    // Real Gravity & Bounce Physics State
    private boolean isDragging = false;
    private boolean gravityPhysicsActive = false;
    private float velY = 0f;
    private float velX = 0f;
    private float internalBounceY = 0f;
    private float internalVelY = 0f;
    private float dragOffsetX, dragOffsetY, downRawX, downRawY;

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
    }

    public void triggerGravityJump() {
        internalVelY = -14f;
        velY = -16f;
        gravityPhysicsActive = true;
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        ViewParent parent = getParent();
        if (!(parent instanceof View)) return super.onTouchEvent(event);
        View parentView = (View) parent;
        int parentW = parentView.getWidth();
        int parentH = parentView.getHeight();

        if (parentH < 400) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                internalVelY = -12f;
                postInvalidateOnAnimation();
            }
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                gravityPhysicsActive = false;
                velY = 0f;
                velX = 0f;
                dragOffsetX = getX() - event.getRawX();
                dragOffsetY = getY() - event.getRawY();
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                postInvalidateOnAnimation();
                return true;

            case MotionEvent.ACTION_MOVE:
                float newX = Math.max(8f, Math.min(parentW - getWidth() - 8f, event.getRawX() + dragOffsetX));
                float newY = Math.max(40f, Math.min(parentH - getHeight() - 70f, event.getRawY() + dragOffsetY));
                velX = (newX - getX()) * 0.4f;
                setX(newX);
                setY(newY);
                postInvalidateOnAnimation();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                if (Math.abs(event.getRawX() - downRawX) < 18f && Math.abs(event.getRawY() - downRawY) < 18f) {
                    danceStyle = (danceStyle + 1) % 3;
                    velY = -20f;
                    internalVelY = -12f;
                    gravityPhysicsActive = true;
                    String[] names = {
                            "🐶 Muso Pet: Gravity Jump & Dance!",
                            "🦁 Cute Mascot: Happy Belly Wave!",
                            "🐧 Cute Mascot: Beat Bounce!"
                    };
                    Toast.makeText(getContext(), names[danceStyle], Toast.LENGTH_SHORT).show();
                } else {
                    gravityPhysicsActive = true;
                }
                postInvalidateOnAnimation();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateGravityPhysics() {
        if (internalBounceY < 0f || internalVelY != 0f) {
            internalBounceY += internalVelY;
            internalVelY += 1.15f;
            if (internalBounceY >= 0f) {
                internalBounceY = 0f;
                if (Math.abs(internalVelY) > 3.5f) {
                    internalVelY = -internalVelY * 0.52f;
                } else {
                    internalVelY = 0f;
                }
            }
        } else if (isPlaying && Math.sin(phase) > 0.92f) {
            internalVelY = -7.0f;
        }

        if (gravityPhysicsActive && !isDragging && getParent() instanceof View) {
            View parentView = (View) getParent();
            int pH = parentView.getHeight();
            int pW = parentView.getWidth();
            if (pH > 400) {
                float d = getResources().getDisplayMetrics().density;
                // Lands right on top of the Mini-Player bar like Screenshot 1000069378.jpg!
                float floorY = pH - getHeight() - (92f * d);
                float nextY = getY() + velY;
                float nextX = getX() + velX;

                velY += 1.35f;
                velX *= 0.94f;

                if (nextX < 10f) {
                    nextX = 10f;
                    velX = -velX * 0.6f;
                } else if (nextX > pW - getWidth() - 10f) {
                    nextX = pW - getWidth() - 10f;
                    velX = -velX * 0.6f;
                }

                if (nextY >= floorY) {
                    nextY = floorY;
                    if (Math.abs(velY) > 3.2f) {
                        velY = -velY * 0.58f;
                    } else {
                        velY = 0f;
                        velX = 0f;
                        gravityPhysicsActive = false;
                    }
                }
                setX(nextX);
                setY(nextY);
            }
        }
    }

    public void setPreviewBreedAndColor(int breedIdx, int colorIdx) {
        this.previewBreedOverride = breedIdx;
        this.previewColorOverride = colorIdx;
        postInvalidateOnAnimation();
    }

    public void refreshFromThemeManager() {
        this.previewBreedOverride = -1;
        this.previewColorOverride = -1;
        postInvalidateOnAnimation();
    }

    public void setPlayingState(boolean playing) {
        this.isPlaying = playing;
        postInvalidateOnAnimation();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        updateGravityPhysics();

        int breed = previewBreedOverride >= 0
                ? previewBreedOverride
                : (themeManager != null ? themeManager.getPuppyBreedIndex() : 0);

        int userCoatColor = previewColorOverride >= 0
                ? ThemeManager.PUPPY_COLOR_HEX[previewColorOverride % ThemeManager.PUPPY_COLOR_HEX.length]
                : (themeManager != null ? themeManager.getSelectedPuppyColor() : Color.parseColor("#FBBF24"));

        int coatColor = userCoatColor;
        if (breed == 1) coatColor = Color.parseColor("#1E293B"); // Emperor Penguin Tuxedo

        int earColor = ColorUtils.blendARGB(coatColor, Color.parseColor("#8B4513"), 0.55f);
        if (breed == 2 || breed == 8 || breed == 9) earColor = Color.parseColor("#E2E8F0");
        else if (breed == 6) earColor = Color.parseColor("#0F172A");

        float cx = w / 2f;
        float cy = h * 0.55f;
        float scale = Math.min(w, h) / 110f;

        phase += isPlaying ? 0.22f : 0.05f;

        float swayX = isPlaying ? (float) Math.sin(phase * 0.8f) * (4.5f * scale) : 0f;
        float headTilt = isPlaying ? (float) Math.sin(phase) * 9f : (float) Math.sin(phase * 0.5f) * 3f;
        float rightPawWave = (float) Math.sin(phase * 1.8f) * 14f;

        // 1. Floor Shadow
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(80, 0, 0, 0));
        float jumpFactor = Math.max(0.45f, 1.0f + (internalBounceY / 35f));
        float shadowRx = 24f * scale * jumpFactor;
        rect.set(cx - shadowRx, h - 9f * scale, cx + shadowRx, h - 2f * scale);
        canvas.drawOval(rect, paint);

        canvas.save();
        canvas.translate(swayX, internalBounceY * scale);

        // 2. Cute Wagging Tail
        float tailAngle = (float) Math.sin(phase * 2.5f) * 24f;
        canvas.save();
        canvas.rotate(tailAngle, cx + 14f * scale, cy + 16f * scale);
        paint.setColor(earColor);
        rect.set(cx + 12f * scale, cy + 6f * scale, cx + 30f * scale, cy + 15f * scale);
        canvas.drawRoundRect(rect, 7f * scale, 7f * scale, paint);
        canvas.restore();

        // 3. MUSO BLUE HOODIE JACKET BODY (Matches Screenshots 1000069380.jpg & 1000069372.jpg!)
        paint.setColor(Color.parseColor("#3B82F6")); // Muso Sky-Blue Hoodie Jacket
        rect.set(cx - 20f * scale, cy - 2f * scale, cx + 20f * scale, cy + 31f * scale);
        canvas.drawRoundRect(rect, 15f * scale, 15f * scale, paint);

        // White Inner T-Shirt / Belly Center Strip
        paint.setColor(Color.WHITE);
        rect.set(cx - 8f * scale, cy - 1f * scale, cx + 8f * scale, cy + 29f * scale);
        canvas.drawRoundRect(rect, 6f * scale, 6f * scale, paint);

        // Cute White Hoodie Drawstrings
        paint.setStrokeWidth(2.2f * scale);
        canvas.drawLine(cx - 5f * scale, cy + 2f * scale, cx - 5f * scale, cy + 13f * scale, paint);
        canvas.drawLine(cx + 5f * scale, cy + 2f * scale, cx + 5f * scale, cy + 13f * scale, paint);

        // Standing Feet with Cute Brown/Pink Toe Beans
        float leftStep = isPlaying ? (float) Math.max(0, Math.sin(phase)) * 5f * scale : 0f;
        float rightStep = isPlaying ? (float) Math.max(0, -Math.sin(phase)) * 5f * scale : 0f;
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx - 11f * scale, cy + 32f * scale - leftStep, 7f * scale, paint);
        canvas.drawCircle(cx + 11f * scale, cy + 32f * scale - rightStep, 7f * scale, paint);
        paint.setColor(Color.parseColor("#A16207"));
        canvas.drawCircle(cx - 11f * scale, cy + 32f * scale - leftStep, 3f * scale, paint);
        canvas.drawCircle(cx + 11f * scale, cy + 32f * scale - rightStep, 3f * scale, paint);

        // Blue Hoodie Sleeves + Raised Waving Right Paw
        paint.setColor(Color.parseColor("#2563EB"));
        rect.set(cx - 27f * scale, cy + 2f * scale, cx - 16f * scale, cy + 18f * scale);
        canvas.drawRoundRect(rect, 7f * scale, 7f * scale, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx - 21.5f * scale, cy + 18f * scale, 5f * scale, paint);

        canvas.save();
        canvas.rotate(rightPawWave, cx + 18f * scale, cy + 4f * scale);
        paint.setColor(Color.parseColor("#2563EB"));
        rect.set(cx + 15f * scale, cy - 14f * scale, cx + 27f * scale, cy + 8f * scale);
        canvas.drawRoundRect(rect, 7f * scale, 7f * scale, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx + 21f * scale, cy - 14f * scale, 5.2f * scale, paint);
        canvas.restore();

        // 4. OVERSIZED CUTE 3D HEAD + RED/ORANGE STUDIO HEADPHONES
        canvas.save();
        canvas.rotate(headTilt, cx, cy - 12f * scale);

        // Lion Cub Mane Scallops if Breed #0
        if (breed == 0) {
            paint.setColor(Color.parseColor("#B45309"));
            for (int a = 0; a < 12; a++) {
                double rad = Math.toRadians(a * 30);
                float mx = cx + (float) Math.cos(rad) * 25f * scale;
                float my = (cy - 16f * scale) + (float) Math.sin(rad) * 23f * scale;
                canvas.drawCircle(mx, my, 9f * scale, paint);
            }
        }

        // Red/Orange Studio Headphone Band Over Head (Matches Screenshot 1000069372.jpg!)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4.5f * scale);
        paint.setColor(Color.parseColor("#EF4444"));
        rect.set(cx - 27f * scale, cy - 39f * scale, cx + 27f * scale, cy - 1f * scale);
        canvas.drawArc(rect, 180f, 180f, false, paint);
        paint.setStyle(Paint.Style.FILL);

        // Floppy Brown-and-Cream Ears
        paint.setColor(earColor);
        rect.set(cx - 33f * scale, cy - 28f * scale, cx - 18f * scale, cy - 1f * scale);
        canvas.drawRoundRect(rect, 10f * scale, 10f * scale, paint);
        rect.set(cx + 18f * scale, cy - 28f * scale, cx + 33f * scale, cy - 1f * scale);
        canvas.drawRoundRect(rect, 10f * scale, 10f * scale, paint);

        // Red/Orange Headphone Earcups on Sides
        paint.setColor(Color.parseColor("#F97316"));
        rect.set(cx - 31f * scale, cy - 22f * scale, cx - 21f * scale, cy - 7f * scale);
        canvas.drawRoundRect(rect, 6f * scale, 6f * scale, paint);
        rect.set(cx + 21f * scale, cy - 22f * scale, cx + 31f * scale, cy - 7f * scale);
        canvas.drawRoundRect(rect, 6f * scale, 6f * scale, paint);

        // Main Head Base (White/Cream with Golden-Brown Forehead Patches like Muso Puppy!)
        paint.setColor(Color.WHITE);
        rect.set(cx - 24f * scale, cy - 35f * scale, cx + 24f * scale, cy + 3f * scale);
        canvas.drawRoundRect(rect, 21f * scale, 21f * scale, paint);

        // Cute Brown/Golden Eye-Forehead Patches
        paint.setColor(coatColor);
        rect.set(cx - 23f * scale, cy - 34f * scale, cx - 5f * scale, cy - 12f * scale);
        canvas.drawOval(rect, paint);
        rect.set(cx + 5f * scale, cy - 34f * scale, cx + 23f * scale, cy - 12f * scale);
        canvas.drawOval(rect, paint);

        // Cute Eyebrows
        paint.setColor(Color.parseColor("#78350F"));
        canvas.drawRoundRect(new RectF(cx - 16f * scale, cy - 28f * scale, cx - 7f * scale, cy - 25f * scale), 3f, 3f, paint);
        canvas.drawRoundRect(new RectF(cx + 7f * scale, cy - 28f * scale, cx + 16f * scale, cy - 25f * scale), 3f, 3f, paint);

        // Rosy Pink Blushing Cheeks
        paint.setColor(Color.argb(150, 255, 46, 147));
        rect.set(cx - 21f * scale, cy - 12f * scale, cx - 12f * scale, cy - 6f * scale);
        canvas.drawOval(rect, paint);
        rect.set(cx + 12f * scale, cy - 12f * scale, cx + 21f * scale, cy - 6f * scale);
        canvas.drawOval(rect, paint);

        // Big Glossy 3D Eyes
        paint.setColor(Color.parseColor("#1E1B18"));
        boolean blink = ((int) (phase * 2.1f)) % 25 == 0;
        if (blink) {
            paint.setStrokeWidth(2.6f * scale);
            canvas.drawLine(cx - 15f * scale, cy - 18f * scale, cx - 7f * scale, cy - 18f * scale, paint);
            canvas.drawLine(cx + 7f * scale, cy - 18f * scale, cx + 15f * scale, cy - 18f * scale, paint);
        } else {
            canvas.drawCircle(cx - 11f * scale, cy - 18f * scale, 5.6f * scale, paint);
            canvas.drawCircle(cx + 11f * scale, cy - 18f * scale, 5.6f * scale, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx - 12.6f * scale, cy - 19.8f * scale, 2.2f * scale, paint);
            canvas.drawCircle(cx + 9.4f * scale, cy - 19.8f * scale, 2.2f * scale, paint);
        }

        // Cute Button Nose & Open Happy / Yawning Mouth (Matches Screenshot 1000069372.jpg!)
        paint.setColor(Color.parseColor("#1E1B18"));
        rect.set(cx - 4.2f * scale, cy - 13f * scale, cx + 4.2f * scale, cy - 8f * scale);
        canvas.drawOval(rect, paint);

        paint.setColor(Color.parseColor("#991B1B"));
        rect.set(cx - 4.5f * scale, cy - 6.5f * scale, cx + 4.5f * scale, cy + 0.5f * scale);
        canvas.drawOval(rect, paint);
        paint.setColor(Color.parseColor("#FB7185"));
        rect.set(cx - 3.2f * scale, cy - 3.5f * scale, cx + 3.2f * scale, cy + 0.5f * scale);
        canvas.drawOval(rect, paint);

        canvas.restore(); // End head tilt
        canvas.restore(); // End bounce

        if (isPlaying || gravityPhysicsActive || internalBounceY < 0f || Math.abs(internalVelY) > 0.1f) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(120);
        }
    }
}
