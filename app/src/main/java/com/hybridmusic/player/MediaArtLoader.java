package com.hybridmusic.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

    private static final LruCache<String, Bitmap> MEMORY_CACHE = new LruCache<String, Bitmap>(60) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return 1;
        }
    };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void loadSongArtInto(final Context context, final SongItem song,
                                       final ThemeManager themeManager, final ImageView targetView) {
        if (song == null || targetView == null) return;

        final String customUri = themeManager.getSongCustomPhoto(song.id);
        final String cacheKey = song.id + "_" + (customUri != null ? customUri : "");
        targetView.setTag(cacheKey);

        Bitmap cached = MEMORY_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            targetView.clearColorFilter();
            targetView.setImageBitmap(cached);
            return;
        }

        // Default clean white musical icon while loading
        targetView.setImageResource(android.R.drawable.ic_media_play);
        targetView.setColorFilter(0xFFFFFFFF);

        EXECUTOR.execute(() -> {
            final Bitmap bmp = extractBitmapSync(context, song, customUri);
            if (bmp != null) {
                MEMORY_CACHE.put(cacheKey, bmp);
                MAIN_HANDLER.post(() -> {
                    if (cacheKey.equals(targetView.getTag())) {
                        targetView.clearColorFilter();
                        targetView.setImageBitmap(bmp);
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

        // 2. Modern Android 10+ ContentResolver Thumbnail API (Works for Audio & Video!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Bitmap thumb = context.getContentResolver().loadThumbnail(
                        song.getMediaUri(), new Size(256, 256), null);
                if (thumb != null) return thumb;
            } catch (Exception ignored) {
            }
        }

        // 3. Embedded MP3 / M4A / Video Frame via MediaMetadataRetriever
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
                if (b != null) return b;
            }
            if (song.isVideoAudio) {
                Bitmap frame = mmr.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame != null) return frame;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                mmr.release();
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
