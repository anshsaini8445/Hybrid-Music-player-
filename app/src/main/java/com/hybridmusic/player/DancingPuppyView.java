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

        // If inside a small dialog preview, just jump on tap
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
                float newY = Math.max(40f, Math.min(parentH - getHeight() - 80f, event.getRawY() + dragOffsetY));
                velX = (newX - getX()) * 0.4f;
                setX(newX);
                setY(newY);
                postInvalidateOnAnimation();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                if (Math.abs(event.getRawX() - downRawX) < 18f && Math.abs(event.getRawY() - downRawY) < 18f) {
                    // Single Tap: Jump up high with real gravity & switch dance style!
                    danceStyle = (danceStyle + 1) % 3;
                    velY = -20f;
                    internalVelY = -12f;
                    gravityPhysicsActive = true;
                    String[] names = {
                            "🦁 Cute Mascot: Gravity Jump & Wave!",
                            "🐧 Cute Mascot: Happy Belly Dance!",
                            "🐻‍❄️ Cute Mascot: Beat Bounce!"
                    };
                    Toast.makeText(getContext(), names[danceStyle], Toast.LENGTH_SHORT).show();
                } else {
                    // Released after dragging up -> falls down with real gravity!
                    gravityPhysicsActive = true;
                }
                postInvalidateOnAnimation();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateGravityPhysics() {
        // 1. Internal jump physics inside canvas
        if (internalBounceY < 0f || internalVelY != 0f) {
            internalBounceY += internalVelY;
            internalVelY += 1.15f; // Gravity acceleration
            if (internalBounceY >= 0f) {
                internalBounceY = 0f;
                if (Math.abs(internalVelY) > 3.5f) {
                    internalVelY = -internalVelY * 0.52f; // Elastic bounce
                } else {
                    internalVelY = 0f;
                }
            }
        } else if (isPlaying && Math.sin(phase) > 0.92f) {
            // Automatic rhythmic gravity hop on music beats!
            internalVelY = -7.5f;
        }

        // 2. Screen-level gravity fall & floor bounce above Mini-Player dock
        if (gravityPhysicsActive && !isDragging && getParent() instanceof View) {
            View parentView = (View) getParent();
            int pH = parentView.getHeight();
            int pW = parentView.getWidth();
            if (pH > 400) {
                float d = getResources().getDisplayMetrics().density;
                float floorY = pH - getHeight() - (118f * d);
                float nextY = getY() + velY;
                float nextX = getX() + velX;

                velY += 1.35f; // Real downward gravity acceleration
                velX *= 0.94f; // Air friction

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
                        velY = -velY * 0.58f; // Bounces off the floor!
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
                : (themeManager != null ? themeManager.getSelectedPuppyColor() : Color.WHITE);

        // White animals (Polar Cub #2, White Samoyed #8, Snow Bunny #9, Snowy Owl #10) default to crisp snow white if index 0
        int coatColor = userCoatColor;
        if (breed == 1) {
            // Emperor Penguin uses sleek tuxedo dark navy-black + white belly
            coatColor = Color.parseColor("#1E293B");
        }

        int accentManeOrEar = ColorUtils.blendARGB(coatColor, Color.parseColor("#78350F"), 0.45f);
        if (breed == 2 || breed == 8 || breed == 9) {
            accentManeOrEar = Color.parseColor("#E2E8F0");
        } else if (breed == 6) {
            accentManeOrEar = Color.parseColor("#0F172A"); // Panda black ears/patches
        }

        float cx = w / 2f;
        float cy = h * 0.54f;
        float scale = Math.min(w, h) / 110f;

        phase += isPlaying ? 0.22f : 0.06f;

        float swayX = isPlaying ? (float) Math.sin(phase * 0.8f) * (4.5f * scale) : 0f;
        float headTilt = isPlaying ? (float) Math.sin(phase) * 9f : (float) Math.sin(phase * 0.5f) * 3f;
        float rightPawWave = (float) Math.sin(phase * 1.8f) * 14f;

        // 1. Glowing Floor Shadow (Shrinks when jumping high with gravity!)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(85, 255, 255, 255));
        float jumpFactor = Math.max(0.45f, 1.0f + (internalBounceY / 35f));
        float shadowRx = 26f * scale * jumpFactor;
        rect.set(cx - shadowRx, h - 10f * scale, cx + shadowRx, h - 3f * scale);
        canvas.drawOval(rect, paint);

        canvas.save();
        canvas.translate(swayX, internalBounceY * scale);

        // 2. Cute Tail Wagging Behind Body
        float tailAngle = (float) Math.sin(phase * 2.5f) * 24f;
        canvas.save();
        canvas.rotate(tailAngle, cx + 14f * scale, cy + 16f * scale);
        paint.setColor(accentManeOrEar);
        rect.set(cx + 12f * scale, cy + 6f * scale, cx + 31f * scale, cy + 15f * scale);
        canvas.drawRoundRect(rect, 7f * scale, 7f * scale, paint);
        if (breed == 0) {
            // Lion Cub fluffy tail tuft!
            paint.setColor(Color.parseColor("#78350F"));
            canvas.drawCircle(cx + 31f * scale, cy + 10.5f * scale, 5.5f * scale, paint);
        }
        canvas.restore();

        // 3. Standing Cute Chubby Body
        paint.setColor(coatColor);
        rect.set(cx - 20f * scale, cy - 2f * scale, cx + 20f * scale, cy + 32f * scale);
        canvas.drawRoundRect(rect, 16f * scale, 16f * scale, paint);

        // Pure White / Cream Belly Tuxedo Patch
        paint.setColor(Color.WHITE);
        rect.set(cx - 13f * scale, cy + 3f * scale, cx + 13f * scale, cy + 29f * scale);
        canvas.drawOval(rect, paint);

        // Tiger / Snow Leopard Cute Stripes on Body
        if (breed == 4 || breed == 11) {
            paint.setColor(Color.parseColor("#1E293B"));
            canvas.drawRoundRect(new RectF(cx - 19f * scale, cy + 8f * scale, cx - 11f * scale, cy + 12f * scale), 3f, 3f, paint);
            canvas.drawRoundRect(new RectF(cx + 11f * scale, cy + 8f * scale, cx + 19f * scale, cy + 12f * scale), 3f, 3f, paint);
        }

        // Cute Standing Hind Feet with Pink Toe Beans
        float leftStep = isPlaying ? (float) Math.max(0, Math.sin(phase)) * 5f * scale : 0f;
        float rightStep = isPlaying ? (float) Math.max(0, -Math.sin(phase)) * 5f * scale : 0f;
        paint.setColor(breed == 1 ? Color.parseColor("#FB923C") : Color.WHITE);
        canvas.drawCircle(cx - 11f * scale, cy + 32f * scale - leftStep, 7f * scale, paint);
        canvas.drawCircle(cx + 11f * scale, cy + 32f * scale - rightStep, 7f * scale, paint);
        if (breed != 1) {
            paint.setColor(Color.parseColor("#FDA4AF"));
            canvas.drawCircle(cx - 11f * scale, cy + 32f * scale - leftStep, 3f * scale, paint);
            canvas.drawCircle(cx + 11f * scale, cy + 32f * scale - rightStep, 3f * scale, paint);
        }

        // Left Arm Resting Cutely + Right Arm Raised High Waving ("Sidhe Haath Khade Ho")!
        paint.setColor(coatColor);
        rect.set(cx - 28f * scale, cy + 4f * scale, cx - 16f * scale, cy + 19f * scale);
        canvas.drawRoundRect(rect, 7f * scale, 7f * scale, paint);

        // Raised Waving Right Paw!
        canvas.save();
        canvas.rotate(rightPawWave, cx + 18f * scale, cy + 4f * scale);
        rect.set(cx + 15f * scale, cy - 16f * scale, cx + 28f * scale, cy + 8f * scale);
        canvas.drawRoundRect(rect, 7f * scale, 7f * scale, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx + 21.5f * scale, cy - 14f * scale, 5.5f * scale, paint);
        paint.setColor(Color.parseColor("#FDA4AF"));
        canvas.drawCircle(cx + 21.5f * scale, cy - 14f * scale, 2.5f * scale, paint);
        canvas.restore();

        // 4. Oversized Super-Cute Head
        canvas.save();
        canvas.rotate(headTilt, cx, cy - 12f * scale);

        // Breed #0: Cute Lion Cub Fluffy Mane Scallops Around Head!
        if (breed == 0) {
            paint.setColor(Color.parseColor("#B45309"));
            for (int a = 0; a < 12; a++) {
                double rad = Math.toRadians(a * 30);
                float mx = cx + (float) Math.cos(rad) * 25f * scale;
                float my = (cy - 16f * scale) + (float) Math.sin(rad) * 23f * scale;
                canvas.drawCircle(mx, my, 9.5f * scale, paint);
            }
        }

        // Cute Ears Based on Animal Breed
        paint.setColor(accentManeOrEar);
        if (breed == 9) {
            // Long Fluffy Snow Bunny Ears
            rect.set(cx - 20f * scale, cy - 52f * scale, cx - 8f * scale, cy - 22f * scale);
            canvas.drawRoundRect(rect, 8f * scale, 8f * scale, paint);
            rect.set(cx + 8f * scale, cy - 52f * scale, cx + 20f * scale, cy - 22f * scale);
            canvas.drawRoundRect(rect, 8f * scale, 8f * scale, paint);
            paint.setColor(Color.parseColor("#FDA4AF"));
            rect.set(cx - 17f * scale, cy - 48f * scale, cx - 11f * scale, cy - 26f * scale);
            canvas.drawRoundRect(rect, 5f * scale, 5f * scale, paint);
            rect.set(cx + 11f * scale, cy - 48f * scale, cx + 17f * scale, cy - 26f * scale);
            canvas.drawRoundRect(rect, 5f * scale, 5f * scale, paint);
        } else if (breed == 5) {
            // Big Fluffy Koala Round Ears
            canvas.drawCircle(cx - 26f * scale, cy - 26f * scale, 12f * scale, paint);
            canvas.drawCircle(cx + 26f * scale, cy - 26f * scale, 12f * scale, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx - 26f * scale, cy - 26f * scale, 6f * scale, paint);
            canvas.drawCircle(cx + 26f * scale, cy - 26f * scale, 6f * scale, paint);
        } else if (breed != 1 && breed != 10) {
            // Cute Round Cub Ears (Lion Cub, Polar Cub, Tiger Cub, Panda, Red Panda)
            canvas.drawCircle(cx - 21f * scale, cy - 33f * scale, 9.5f * scale, paint);
            canvas.drawCircle(cx + 21f * scale, cy - 33f * scale, 9.5f * scale, paint);
            paint.setColor(Color.parseColor("#FDA4AF"));
            canvas.drawCircle(cx - 21f * scale, cy - 33f * scale, 4.8f * scale, paint);
            canvas.drawCircle(cx + 21f * scale, cy - 33f * scale, 4.8f * scale, paint);
        }

        // Main Chubby Head Circle
        paint.setColor(coatColor);
        rect.set(cx - 25f * scale, cy - 36f * scale, cx + 25f * scale, cy + 4f * scale);
        canvas.drawRoundRect(rect, 22f * scale, 22f * scale, paint);

        // Penguin White Face Mask OR Tiger Forehead Stripes
        if (breed == 1) {
            paint.setColor(Color.WHITE);
            rect.set(cx - 19f * scale, cy - 28f * scale, cx + 19f * scale, cy + 2f * scale);
            canvas.drawOval(rect, paint);
        } else if (breed == 4) {
            paint.setColor(Color.parseColor("#1E293B"));
            canvas.drawLine(cx - 8f * scale, cy - 33f * scale, cx + 8f * scale, cy - 33f * scale, paint);
            canvas.drawLine(cx - 6f * scale, cy - 29f * scale, cx + 6f * scale, cy - 29f * scale, paint);
        }

        // Cute Cream Snout / Muzzle
        paint.setColor(Color.WHITE);
        rect.set(cx - 12f * scale, cy - 14f * scale, cx + 12f * scale, cy + 1f * scale);
        canvas.drawOval(rect, paint);

        // Rosy Pink Kawaii Blushing Cheeks
        paint.setColor(Color.argb(165, 255, 46, 147));
        rect.set(cx - 21f * scale, cy - 13f * scale, cx - 12f * scale, cy - 7f * scale);
        canvas.drawOval(rect, paint);
        rect.set(cx + 12f * scale, cy - 13f * scale, cx + 21f * scale, cy - 7f * scale);
        canvas.drawOval(rect, paint);

        // Panda Dark Eye Patches
        if (breed == 6) {
            paint.setColor(Color.parseColor("#0F172A"));
            canvas.drawCircle(cx - 11f * scale, cy - 19f * scale, 7.5f * scale, paint);
            canvas.drawCircle(cx + 11f * scale, cy - 19f * scale, 7.5f * scale, paint);
        }

        // Giant Sparkling Kawaii Eyes
        paint.setColor(Color.parseColor("#0F172A"));
        boolean blink = ((int) (phase * 2.1f)) % 25 == 0;
        if (blink) {
            paint.setStrokeWidth(2.6f * scale);
            canvas.drawLine(cx - 15f * scale, cy - 19f * scale, cx - 7f * scale, cy - 19f * scale, paint);
            canvas.drawLine(cx + 7f * scale, cy - 19f * scale, cx + 15f * scale, cy - 19f * scale, paint);
        } else {
            canvas.drawCircle(cx - 11f * scale, cy - 19f * scale, 5.5f * scale, paint);
            canvas.drawCircle(cx + 11f * scale, cy - 19f * scale, 5.5f * scale, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx - 12.6f * scale, cy - 20.8f * scale, 2.2f * scale, paint);
            canvas.drawCircle(cx - 9.4f * scale, cy - 17.2f * scale, 1.1f * scale, paint);
            canvas.drawCircle(cx + 9.4f * scale, cy - 20.8f * scale, 2.2f * scale, paint);
            canvas.drawCircle(cx + 12.6f * scale, cy - 17.2f * scale, 1.1f * scale, paint);
        }

        // Cute Nose / Penguin Beak & Happy Smile
        if (breed == 1 || breed == 10) {
            // Cute Orange Penguin / Owl Beak
            paint.setColor(Color.parseColor("#FB923C"));
            rect.set(cx - 5.5f * scale, cy - 13f * scale, cx + 5.5f * scale, cy - 6.5f * scale);
            canvas.drawOval(rect, paint);
        } else {
            // Cute Pink/Black Cub Button Nose & Happy Tongue
            paint.setColor(breed == 0 || breed == 4 ? Color.parseColor("#FB7185") : Color.parseColor("#0F172A"));
            rect.set(cx - 4.5f * scale, cy - 13f * scale, cx + 4.5f * scale, cy - 7.5f * scale);
            canvas.drawOval(rect, paint);

            paint.setColor(Color.parseColor("#FF2E93"));
            rect.set(cx - 3.8f * scale, cy - 6.5f * scale, cx + 3.8f * scale, cy - 0.5f * scale);
            canvas.drawRoundRect(rect, 4f * scale, 4f * scale, paint);
        }

        canvas.restore(); // End head tilt
        canvas.restore(); // End bounce

        if (isPlaying || gravityPhysicsActive || internalBounceY < 0f || Math.abs(internalVelY) > 0.1f) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(120);
        }
    }
}
