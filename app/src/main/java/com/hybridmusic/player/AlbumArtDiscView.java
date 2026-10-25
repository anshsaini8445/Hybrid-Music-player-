package com.hybridmusic.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class AlbumArtDiscView extends View {

    public interface OnArtGestureListener {
        void onSwipeRightToLeft(); // Opens Lyrics or Next action
        void onSwipeLeftToRight(); // Returns to Art or Previous action
        void onCenterTap();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();
    private final Matrix shaderMatrix = new Matrix();

    private Bitmap songBitmap = null;
    private BitmapShader bitmapShader = null;
    private boolean isPlaying = false;
    private boolean isVinylDiscMode = false; // false = Square Box with 2 flat dots, true = Vinyl Disc + Top Danda
    private boolean isLyricsPageSelected = false;

    private float discRotation = 0f;
    private float tonearmAngle = 0f; // 0 = Resting, 26 = On Spinning Disc
    private float beatPulse = 0f;
    private int accentColor = Color.parseColor("#00E5FF");

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
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (gestureListener != null) {
                    gestureListener.onCenterTap();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 70) {
                    if (diffX < 0) {
                        // Right-to-Left Swipe
                        if (gestureListener != null) gestureListener.onSwipeRightToLeft();
                    } else {
                        // Left-to-Right Swipe
                        if (gestureListener != null) gestureListener.onSwipeLeftToRight();
                    }
                    return true;
                }
                return false;
            }
        });
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
        invalidate();
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        invalidate();
    }

    public void setVinylDiscMode(boolean vinylMode) {
        this.isVinylDiscMode = vinylMode;
        invalidate();
    }

    public boolean isVinylDiscMode() {
        return isVinylDiscMode;
    }

    public void setLyricsPageSelected(boolean selected) {
        this.isLyricsPageSelected = selected;
        invalidate();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = (h - 28f) / 2f;
        float minDim = Math.min(w, h - 36f);

        if (isPlaying) {
            discRotation = (discRotation + 1.35f) % 360f;
            beatPulse += 0.15f;
            if (tonearmAngle < 25f) tonearmAngle += 1.8f;
        } else {
            if (tonearmAngle > 0f) tonearmAngle = Math.max(0f, tonearmAngle - 1.8f);
        }

        if (isVinylDiscMode) {
            drawVinylDiscWithDanda(canvas, cx, cy + 10f, minDim * 0.42f);
        } else {
            drawSquareAlbumBox(canvas, cx, cy, minDim * 0.82f);
        }

        // Draw 2 Flat Indicator Dots (. .) at the bottom for Album vs Lyrics
        drawBottomFlatDots(canvas, cx, h - 12f);

        if (isPlaying || (tonearmAngle > 0f && !isPlaying)) {
            postInvalidateDelayed(24);
        }
    }

    private void drawSquareAlbumBox(Canvas canvas, float cx, float cy, float boxSize) {
        float pulse = isPlaying ? (float) Math.sin(beatPulse) * 4f : 0f;
        float half = (boxSize / 2f) + pulse;
        cardRect.set(cx - half, cy - half, cx + half, cy + half);
        float cornerRadius = 28f;

        // Outer Neon Glow Border
        borderPaint.setColor(accentColor);
        borderPaint.setStrokeWidth(5f);
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint);

        if (bitmapShader != null && songBitmap != null) {
            updateShaderMatrix(cardRect.width(), cardRect.height(), cardRect.left, cardRect.top);
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, imagePaint);
        } else {
            // Sleek Gradient Fallback Art with Musical Emblem
            paint.setShader(new LinearGradient(
                    cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                    new int[]{Color.parseColor("#1E293B"), Color.parseColor("#0F172A"), accentColor},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, paint);
            paint.setShader(null);

            paint.setColor(Color.WHITE);
            paint.setTextSize(boxSize * 0.30f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("♫", cx, cy + (boxSize * 0.10f), paint);
        }
    }

    private void drawVinylDiscWithDanda(Canvas canvas, float cx, float cy, float discRadius) {
        // Outer Vinyl Disc Shadow & Ring
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#090D16"));
        canvas.drawCircle(cx, cy, discRadius + 8f, paint);

        borderPaint.setColor(accentColor);
        borderPaint.setStrokeWidth(3.5f);
        canvas.drawCircle(cx, cy, discRadius + 8f, borderPaint);

        // Rotating Vinyl Record
        canvas.save();
        canvas.rotate(discRotation, cx, cy);

        paint.setColor(Color.parseColor("#141923"));
        canvas.drawCircle(cx, cy, discRadius, paint);

        // Realistic Vinyl Grooves
        borderPaint.setColor(Color.argb(55, 255, 255, 255));
        borderPaint.setStrokeWidth(1.5f);
        canvas.drawCircle(cx, cy, discRadius * 0.88f, borderPaint);
        canvas.drawCircle(cx, cy, discRadius * 0.76f, borderPaint);
        canvas.drawCircle(cx, cy, discRadius * 0.65f, borderPaint);

        // Center Song Photo inside the Disc
        float photoRadius = discRadius * 0.54f;
        cardRect.set(cx - photoRadius, cy - photoRadius, cx + photoRadius, cy + photoRadius);
        if (bitmapShader != null && songBitmap != null) {
            updateShaderMatrix(cardRect.width(), cardRect.height(), cardRect.left, cardRect.top);
            canvas.drawOval(cardRect, imagePaint);
        } else {
            paint.setColor(accentColor);
            canvas.drawCircle(cx, cy, photoRadius, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(photoRadius * 0.7f);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("♪", cx, cy + (photoRadius * 0.25f), paint);
        }

        // Center Spindle Hole
        paint.setColor(Color.parseColor("#0B0F19"));
        canvas.drawCircle(cx, cy, discRadius * 0.09f, paint);
        canvas.restore();

        // Top Tonearm ("Danda") that moves over the disc and sways gently while playing
        float pivotX = cx + discRadius * 0.72f;
        float pivotY = cy - discRadius * 1.05f;
        float sway = isPlaying ? (float) Math.sin(beatPulse * 0.7f) * 1.8f : 0f;

        canvas.save();
        canvas.rotate(tonearmAngle + sway, pivotX, pivotY);

        // Tonearm Base
        paint.setColor(Color.parseColor("#334155"));
        canvas.drawCircle(pivotX, pivotY, 20f, paint);
        paint.setColor(accentColor);
        canvas.drawCircle(pivotX, pivotY, 10f, paint);

        // Tonearm Rod ("Danda")
        paint.setColor(Color.parseColor("#E2E8F0"));
        paint.setStrokeWidth(9f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float armEndX = pivotX - discRadius * 0.62f;
        float armEndY = pivotY + discRadius * 0.92f;
        canvas.drawLine(pivotX, pivotY, armEndX, armEndY, paint);

        // Stylus Needle Head with Photo/Glow Accent
        paint.setColor(accentColor);
        cardRect.set(armEndX - 16f, armEndY - 10f, armEndX + 12f, armEndY + 14f);
        canvas.drawRoundRect(cardRect, 6f, 6f, paint);

        canvas.restore();
    }

    private void drawBottomFlatDots(Canvas canvas, float cx, float bottomY) {
        paint.setStyle(Paint.Style.FILL);
        // Dot 1: Album Art View (Flat Pill)
        paint.setColor(!isLyricsPageSelected ? accentColor : Color.argb(90, 203, 213, 225));
        float leftWidth = !isLyricsPageSelected ? 28f : 14f;
        cardRect.set(cx - 8f - leftWidth, bottomY - 4f, cx - 8f, bottomY + 4f);
        canvas.drawRoundRect(cardRect, 4f, 4f, paint);

        // Dot 2: Lyrics View (Flat Pill)
        paint.setColor(isLyricsPageSelected ? accentColor : Color.argb(90, 203, 213, 225));
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
