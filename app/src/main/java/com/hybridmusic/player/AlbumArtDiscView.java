package com.hybridmusic.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class AlbumArtDiscView extends View {

    public interface OnArtGestureListener {
        void onSwipeRightToLeft();
        void onSwipeLeftToRight();
        void onCenterTap();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final Path shinePath = new Path();

    private Bitmap songBitmap = null;
    private BitmapShader bitmapShader = null;
    private boolean isPlaying = false;
    private boolean isVinylDiscMode = false;
    private boolean isLyricsPageSelected = false;

    private float discRotation = 0f;
    private float tonearmAngle = 0f;
    private float beatPhase = 0f;
    private float shockwaveProgress = 0f;
    private float touchScale = 1.0f;
    private int accentColor = Color.WHITE;

    // Floating Cosmic Particles
    private static final int PARTICLE_COUNT = 18;
    private final float[] px = new float[PARTICLE_COUNT];
    private final float[] py = new float[PARTICLE_COUNT];
    private final float[] pSpeed = new float[PARTICLE_COUNT];
    private final float[] pRadius = new float[PARTICLE_COUNT];
    private boolean particlesInitialized = false;

    private GestureDetector gestureDetector;
    private OnArtGestureListener gestureListener;

    public AlbumArtDiscView(Context context) {
        super(context);
        init(context);
    }

    public AlbumArtDiscView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        borderPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.STROKE);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                touchScale = 0.96f;
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                touchScale = 1.0f;
                if (gestureListener != null) {
                    gestureListener.onCenterTap();
                }
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                touchScale = 1.0f;
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 65) {
                    if (diffX < 0) {
                        if (gestureListener != null) gestureListener.onSwipeRightToLeft();
                    } else {
                        if (gestureListener != null) gestureListener.onSwipeLeftToRight();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void initParticles(int w, int h) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            px[i] = (float) (Math.random() * w);
            py[i] = (float) (Math.random() * h);
            pSpeed[i] = 1.2f + (float) (Math.random() * 2.5f);
            pRadius[i] = 2.0f + (float) (Math.random() * 4.5f);
        }
        particlesInitialized = true;
    }

    public void setOnArtGestureListener(OnArtGestureListener listener) {
        this.gestureListener = listener;
    }

    public void setSongBitmap(Bitmap bmp) {
        this.songBitmap = bmp;
        if (bmp != null && !bmp.isRecycled()) {
            bitmapShader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            imagePaint.setShader(bitmapShader);
        } else {
            bitmapShader = null;
            imagePaint.setShader(null);
        }
        postInvalidateOnAnimation();
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        postInvalidateOnAnimation();
    }

    public void setVinylDiscMode(boolean vinylMode) {
        this.isVinylDiscMode = vinylMode;
        postInvalidateOnAnimation();
    }

    public boolean isVinylDiscMode() {
        return isVinylDiscMode;
    }

    public void setLyricsPageSelected(boolean selected) {
        this.isLyricsPageSelected = selected;
        postInvalidateOnAnimation();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchScale = 1.0f;
            postInvalidateOnAnimation();
        }
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (!particlesInitialized) {
            initParticles(w, h);
        }

        float cx = w / 2f;
        float cy = (h - 26f) / 2f;
        float minDim = Math.min(w, h - 34f);

        if (isPlaying) {
            discRotation = (discRotation + 1.65f) % 360f;
            beatPhase += 0.16f;
            shockwaveProgress = (shockwaveProgress + 0.022f) % 1.0f;
            if (tonearmAngle < 25f) tonearmAngle = Math.min(25f, tonearmAngle + 1.8f);
        } else {
            if (tonearmAngle > 0f) tonearmAngle = Math.max(0f, tonearmAngle - 1.8f);
        }

        // 1. Draw Floating Cosmic Studio Particles in Background
        drawFloatingCosmicParticles(canvas, w, h);

        // 2. Smooth Spring Scale Effect on Touch
        canvas.save();
        canvas.scale(touchScale, touchScale, cx, cy);

        // 3. Draw Multi-Layer Reactive Bass Shockwave Rings when Playing
        if (isPlaying) {
            drawReactiveBassShockwaves(canvas, cx, cy, minDim * 0.40f);
        }

        // 4. Draw Either Rotating Vinyl Record + 3D Tonearm OR Holographic Square Album Box
        if (isVinylDiscMode) {
            drawVinylDiscWithDanda(canvas, cx, cy + 8f, minDim * 0.41f);
        } else {
            drawSquareAlbumBox(canvas, cx, cy, minDim * 0.78f);
        }

        canvas.restore();

        // 5. Draw 2 Flat Indicator Dots (. .) at the bottom
        drawBottomFlatDots(canvas, cx, h - 10f);

        if (isPlaying || tonearmAngle > 0f || touchScale != 1.0f) {
            postInvalidateOnAnimation();
        }
    }

    private void drawFloatingCosmicParticles(Canvas canvas, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            if (isPlaying) {
                py[i] -= pSpeed[i];
                px[i] += (float) Math.sin(beatPhase + i) * 0.8f;
                if (py[i] < 0) {
                    py[i] = h - 20f;
                    px[i] = (float) (Math.random() * w);
                }
            }
            int alpha = isPlaying ? (int) (70 + 110 * Math.abs(Math.sin(beatPhase * 0.5f + i))) : 35;
            paint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawCircle(px[i], py[i], pRadius[i], paint);
        }
    }

    private void drawReactiveBassShockwaves(Canvas canvas, float cx, float cy, float baseRadius) {
        for (int r = 0; r < 2; r++) {
            float prog = (shockwaveProgress + r * 0.5f) % 1.0f;
            float currentR = baseRadius + prog * (baseRadius * 0.42f);
            int alpha = (int) ((1.0f - prog) * 115);
            glowPaint.setColor(Color.argb(alpha, 255, 255, 255));
            glowPaint.setStrokeWidth(3.5f * (1.0f - prog) + 1f);
            canvas.drawCircle(cx, cy, currentR, glowPaint);
        }
    }

    private void drawSquareAlbumBox(Canvas canvas, float cx, float cy, float boxSize) {
        float beatBounce = isPlaying ? (float) Math.sin(beatPhase * 1.4f) * 5.5f : 0f;
        float half = (boxSize / 2f) + beatBounce;
        cardRect.set(cx - half, cy - half, cx + half, cy + half);
        float cornerRadius = 34f;

        // Outer Ambient Aura Glow
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                cx, cy, half * 1.25f,
                new int[]{Color.argb(isPlaying ? 85 : 35, 255, 255, 255), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, half * 1.22f, paint);
        paint.setShader(null);

        // Main Album Photo inside Rounded Box
        if (bitmapShader != null && songBitmap != null) {
            updateShaderMatrix(cardRect.width(), cardRect.height(), cardRect.left, cardRect.top);
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, imagePaint);
        } else {
            paint.setShader(new LinearGradient(
                    cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                    new int[]{Color.parseColor("#1E293B"), Color.parseColor("#0F172A"), Color.parseColor("#334155")},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint);
            paint.setShader(null);

            paint.setColor(Color.WHITE);
            paint.setTextSize(boxSize * 0.28f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("♫", cx, cy + (boxSize * 0.10f), paint);
        }

        // Animated Holographic Diagonal Glass Sheen
        if (isPlaying) {
            float sheenShift = (float) Math.sin(beatPhase * 0.6f) * (half * 0.6f);
            paint.setShader(new LinearGradient(
                    cardRect.left + sheenShift, cardRect.top,
                    cardRect.right + sheenShift, cardRect.bottom,
                    new int[]{Color.TRANSPARENT, Color.argb(38, 255, 255, 255), Color.TRANSPARENT},
                    new float[]{0.35f, 0.5f, 0.65f},
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint);
            paint.setShader(null);
        }

        // Crisp Pure White Neon Border
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(4.5f);
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint);
    }

    private void drawVinylDiscWithDanda(Canvas canvas, float cx, float cy, float discRadius) {
        // Outer Glowing Studio Platter Ring
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#080B12"));
        canvas.drawCircle(cx, cy, discRadius + 10f, paint);

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(3.5f);
        canvas.drawCircle(cx, cy, discRadius + 10f, borderPaint);

        // Rotating Vinyl Record
        canvas.save();
        canvas.rotate(discRotation, cx, cy);

        paint.setColor(Color.parseColor("#111622"));
        canvas.drawCircle(cx, cy, discRadius, paint);

        // Realistic Metallic Specular Shine Cones on Vinyl
        paint.setColor(Color.argb(32, 255, 255, 255));
        cardRect.set(cx - discRadius, cy - discRadius, cx + discRadius, cy + discRadius);
        canvas.drawArc(cardRect, 25f, 38f, true, paint);
        canvas.drawArc(cardRect, 205f, 38f, true, paint);

        // Vinyl Sound Grooves
        borderPaint.setColor(Color.argb(65, 255, 255, 255));
        borderPaint.setStrokeWidth(1.4f);
        canvas.drawCircle(cx, cy, discRadius * 0.90f, borderPaint);
        canvas.drawCircle(cx, cy, discRadius * 0.80f, borderPaint);
        canvas.drawCircle(cx, cy, discRadius * 0.70f, borderPaint);
        canvas.drawCircle(cx, cy, discRadius * 0.62f, borderPaint);

        // Center Song Photo inside the Spinning Disc
        float photoRadius = discRadius * 0.54f;
        cardRect.set(cx - photoRadius, cy - photoRadius, cx + photoRadius, cy + photoRadius);
        if (bitmapShader != null && songBitmap != null) {
            updateShaderMatrix(cardRect.width(), cardRect.height(), cardRect.left, cardRect.top);
            canvas.drawOval(cardRect, imagePaint);
        } else {
            paint.setColor(Color.parseColor("#1E293B"));
            canvas.drawCircle(cx, cy, photoRadius, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(photoRadius * 0.7f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("♪", cx, cy + (photoRadius * 0.25f), paint);
        }

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(2.5f);
        canvas.drawCircle(cx, cy, photoRadius, borderPaint);

        // Center Spindle Hole
        paint.setColor(Color.parseColor("#080B12"));
        canvas.drawCircle(cx, cy, discRadius * 0.09f, paint);
        canvas.restore();

        // Top 3D Metallic Tonearm ("Danda") with Glowing Laser Stylus Spark
        float pivotX = cx + discRadius * 0.74f;
        float pivotY = cy - discRadius * 1.04f;
        float sway = isPlaying ? (float) Math.sin(beatPhase * 0.9f) * 1.8f : 0f;

        canvas.save();
        canvas.rotate(tonearmAngle + sway, pivotX, pivotY);

        // Tonearm Pivot Base
        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawCircle(pivotX, pivotY, 22f, paint);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(2.5f);
        canvas.drawCircle(pivotX, pivotY, 22f, borderPaint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(pivotX, pivotY, 9f, paint);

        // Tonearm Rod ("Danda")
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(8.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float armEndX = pivotX - discRadius * 0.62f;
        float armEndY = pivotY + discRadius * 0.92f;
        canvas.drawLine(pivotX, pivotY, armEndX, armEndY, paint);

        // Stylus Cartridge Head
        paint.setColor(Color.WHITE);
        cardRect.set(armEndX - 15f, armEndY - 10f, armEndX + 12f, armEndY + 14f);
        canvas.drawRoundRect(cardRect, 6f, 6f, paint);

        // Glowing Laser Stylus Spark when Playing on Disc!
        if (isPlaying && tonearmAngle >= 20f) {
            float sparkRadius = 10f + (float) Math.abs(Math.sin(beatPhase * 2f)) * 8f;
            paint.setColor(Color.argb(130, 0, 229, 255));
            canvas.drawCircle(armEndX - 2f, armEndY + 10f, sparkRadius, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(armEndX - 2f, armEndY + 10f, 4.5f, paint);
        }

        canvas.restore();
    }

    private void drawBottomFlatDots(Canvas canvas, float cx, float bottomY) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(!isLyricsPageSelected ? Color.WHITE : Color.argb(90, 203, 213, 225));
        float leftWidth = !isLyricsPageSelected ? 28f : 14f;
        cardRect.set(cx - 8f - leftWidth, bottomY - 4f, cx - 8f, bottomY + 4f);
        canvas.drawRoundRect(cardRect, 4f, 4f, paint);

        paint.setColor(isLyricsPageSelected ? Color.WHITE : Color.argb(90, 203, 213, 225));
        float rightWidth = isLyricsPageSelected ? 28f : 14f;
        cardRect.set(cx + 8f, bottomY - 4f, cx + 8f + rightWidth, bottomY + 4f);
        canvas.drawRoundRect(cardRect, 4f, 4f, paint);
    }

    private void updateShaderMatrix(float targetW, float targetH, float left, float top) {
        if (songBitmap == null) return;
        shaderMatrix.reset();
        float scale;
        float dx = 0, dy = 0;
        int bWidth = songBitmap.getWidth();
        int bHeight = songBitmap.getHeight();

        if (bWidth * targetH > targetW * bHeight) {
            scale = targetH / (float) bHeight;
            dx = (targetW - bWidth * scale) * 0.5f;
        } else {
            scale = targetW / (float) bWidth;
            dy = (targetH - bHeight * scale) * 0.5f;
        }
        shaderMatrix.setScale(scale, scale);
        shaderMatrix.postTranslate(left + dx, top + dy);
        bitmapShader.setLocalMatrix(shaderMatrix);
    }
}
