package com.hybridmusic.player;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaArtLoader {

    private static final LruCache<String, Bitmap> MEMORY_CACHE = new LruCache<String, Bitmap>(150) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return 1;
        }
    };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /**
     * Guaranteed Instant Song Photo Loader:
     * 1. Instant RAM LruCache lookup (< 1ms) so switching tabs shows photos immediately.
     * 2. Shows a realistic HD Studio Vinyl Disc Cover immediately while extracting embedded ID3 art.
     */
    public static void loadSongArtInto(final Context context, final SongItem song,
                                       final ThemeManager themeManager, final ImageView targetView) {
        if (song == null || targetView == null) return;

        final String customUri = themeManager.getSongCustomPhoto(song.id);
        final String cacheKey = song.id + "_" + (customUri != null ? customUri : "");
        targetView.setTag(cacheKey);
        targetView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        targetView.clearColorFilter();

        Bitmap cached = MEMORY_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            targetView.setImageBitmap(cached);
            return;
        }

        // Immediately display a crisp generated Studio Vinyl Cover so the box is never blank!
        final Bitmap fallbackStudioBmp = createRealisticStudioCoverBitmap(song.title);
        targetView.setImageBitmap(fallbackStudioBmp);

        EXECUTOR.execute(() -> {
            Bitmap extracted = extractBitmapSync(context, song, customUri);
            final Bitmap finalBmp = (extracted != null) ? extracted : fallbackStudioBmp;
            MEMORY_CACHE.put(cacheKey, finalBmp);

            if (extracted != null) {
                MAIN_HANDLER.post(() -> {
                    if (cacheKey.equals(targetView.getTag())) {
                        targetView.clearColorFilter();
                        targetView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        targetView.setImageBitmap(extracted);
                    }
                });
            }
        });
    }

    public static Bitmap extractBitmapSync(Context context, SongItem song, String customUri) {
        if (song == null) return null;

        // 1. Check Custom Saved Gallery Photo First
        if (customUri != null && !customUri.isEmpty()) {
            Bitmap b = decodeUri(context, Uri.parse(customUri));
            if (b != null) return b;
        }

        // 2. Embedded MP3 / M4A / Video Frame via MediaMetadataRetriever
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            if (song.path != null && !song.path.isEmpty() && new File(song.path).exists()) {
                mmr.setDataSource(song.path);
            } else {
                mmr.setDataSource(context, song.getMediaUri());
            }
            byte[] pic = mmr.getEmbeddedPicture();
            if (pic != null && pic.length > 0) {
                Bitmap b = BitmapFactory.decodeByteArray(pic, 0, pic.length);
                if (b != null) return Bitmap.createScaledBitmap(b, 250, 250, true);
            }
            if (song.isVideoAudio) {
                Bitmap frame = mmr.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame != null) return Bitmap.createScaledBitmap(frame, 250, 250, true);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {
            }
        }

        // 3. Modern Android 10+ ContentResolver Thumbnail API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Bitmap thumb = context.getContentResolver().loadThumbnail(
                        song.getMediaUri(), new Size(250, 250), null);
                if (thumb != null) return thumb;
            } catch (Exception ignored) {
            }
        }

        // 4. Fallback Album Art URI or Video Thumbnail
        if (!song.isVideoAudio) {
            Bitmap b = decodeUri(context, song.getAlbumArtUri());
            if (b != null) return b;
        } else if (song.path != null && !song.path.isEmpty()) {
            try {
                return ThumbnailUtils.createVideoThumbnail(song.path, MediaStore.Video.Thumbnails.MINI_KIND);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static Bitmap createRealisticStudioCoverBitmap(String title) {
        int size = 240;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        int[][] palettes = new int[][]{
                {Color.parseColor("#1D2B64"), Color.parseColor("#4A69BB")},
                {Color.parseColor("#0F2027"), Color.parseColor("#2C5364")},
                {Color.parseColor("#3A1C71"), Color.parseColor("#D76D77")},
                {Color.parseColor("#134E5E"), Color.parseColor("#71B280")},
                {Color.parseColor("#232526"), Color.parseColor("#414345")},
                {Color.parseColor("#1A2980"), Color.parseColor("#26D0CE")}
        };
        int idx = Math.abs((title != null ? title : "Song").hashCode()) % palettes.length;
        int[] pair = palettes[idx];

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setShader(new LinearGradient(0f, 0f, size, size, pair[0], pair[1], Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0f, 0f, size, size), 28f, 28f, bgPaint);

        float cx = size / 2f;
        float cy = size / 2f;

        Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        discPaint.setStyle(Paint.Style.FILL);
        discPaint.setColor(Color.parseColor("#CC0A0A10"));
        canvas.drawCircle(cx, cy, size * 0.36f, discPaint);

        Paint groovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        groovePaint.setStyle(Paint.Style.STROKE);
        groovePaint.setStrokeWidth(2f);
        groovePaint.setColor(Color.parseColor("#44FFFFFF"));
        canvas.drawCircle(cx, cy, size * 0.28f, groovePaint);
        canvas.drawCircle(cx, cy, size * 0.20f, groovePaint);

        Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notePaint.setStyle(Paint.Style.STROKE);
        notePaint.setStrokeWidth(5.5f);
        notePaint.setStrokeCap(Paint.Cap.ROUND);
        notePaint.setStrokeJoin(Paint.Join.ROUND);
        notePaint.setColor(Color.WHITE);

        Paint noteFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        noteFill.setStyle(Paint.Style.FILL);
        noteFill.setColor(Color.WHITE);

        canvas.drawLine(cx - 12f, cy + 16f, cx - 12f, cy - 22f, notePaint);
        canvas.drawLine(cx + 18f, cy + 12f, cx + 18f, cy - 26f, notePaint);
        canvas.drawLine(cx - 12f, cy - 22f, cx + 18f, cy - 26f, notePaint);
        canvas.drawCircle(cx - 18f, cy + 18f, 8.5f, noteFill);
        canvas.drawCircle(cx + 12f, cy + 14f, 8.5f, noteFill);

        return bmp;
    }

    private static Bitmap decodeUri(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is != null) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                return BitmapFactory.decodeStream(is, null, opts);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
