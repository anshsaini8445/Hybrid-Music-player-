package com.hybridmusic.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import java.util.ArrayList;
import java.util.List;

public class MusicPlaybackService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

    public interface PlaybackStateListener {
        void onStateChanged();
    }

    public static final String ACTION_PLAY_PAUSE = "com.hybridmusic.player.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.hybridmusic.player.ACTION_NEXT";
    public static final String ACTION_PREV = "com.hybridmusic.player.ACTION_PREV";
    public static final String ACTION_CLOSE = "com.hybridmusic.player.ACTION_CLOSE";

    private static final String CHANNEL_ID = "hybrid_music_playback_channel";
    private static final int NOTIFICATION_ID = 101;

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;
    private Equalizer equalizer;
    private BassBoost bassBoost;

    private List<SongItem> queueList = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = Loop All, 1 = Repeat One, 2 = Off
    private float userSpeed = 1.0f;
    private boolean djSlowFadeEnabled = true;

    // A-B Repeat positions in ms (-1 means inactive)
    private int pointAMs = -1;
    private int pointBMs = -1;

    // Sleep Timer
    private long sleepTimerEndMs = 0;

    private final int[] bandLevels = new int[]{50, 50, 50, 50, 50};
    private int bassStrength = 0;
    private PlaybackStateListener stateListener;

    public class LocalBinder extends Binder {
        public MusicPlaybackService getService() {
            return MusicPlaybackService.this;
        }
    }

    private final Runnable monitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying()) {
                int pos = getCurrentPosition();
                int dur = getDuration();

                // 1. A-B Repeat check
                if (pointAMs >= 0 && pointBMs > pointAMs && pos >= pointBMs) {
                    seekTo(pointAMs);
                }

                // 2. 10-Second DJ Slow Fade & Speed-Down at end of song
                if (djSlowFadeEnabled && dur > 15000 && pos >= dur - 10000) {
                    float remainingSec = Math.max(0.1f, (dur - pos) / 1000f);
                    float factor = Math.max(0.35f, Math.min(1.0f, remainingSec / 10.0f));
                    mediaPlayer.setVolume(factor, factor);
                    applyInternalSpeed(Math.max(0.75f, userSpeed * (0.75f + 0.25f * factor)));
                } else {
                    mediaPlayer.setVolume(1.0f, 1.0f);
                    applyInternalSpeed(userSpeed);
                }

                // 3. Sleep Timer check
                if (sleepTimerEndMs > 0 && System.currentTimeMillis() >= sleepTimerEndMs) {
                    sleepTimerEndMs = 0;
                    pausePlaybackAndClearOngoing();
                }
            }
            handler.postDelayed(this, 400);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        handler.post(monitorRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_PLAY_PAUSE.equals(action)) {
                playToggle();
            } else if (ACTION_NEXT.equals(action)) {
                playNext();
            } else if (ACTION_PREV.equals(action)) {
                playPrevious();
            } else if (ACTION_CLOSE.equals(action)) {
                // One-tap instant notification close!
                if (mediaPlayer != null && isPlaying()) {
                    mediaPlayer.pause();
                }
                stopForeground(true);
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.cancel(NOTIFICATION_ID);
                notifyUI();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setPlaybackStateListener(PlaybackStateListener listener) {
        this.stateListener = listener;
    }

    private void notifyUI() {
        if (stateListener != null) {
            stateListener.onStateChanged();
        }
    }

    private void initMediaPlayer() {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener(this);
        }
    }

    private void setupAudioEffects(int sessionId) {
        try {
            if (equalizer != null) equalizer.release();
            equalizer = new Equalizer(0, sessionId);
            equalizer.setEnabled(true);
            for (int i = 0; i < 5; i++) {
                setEqualizerBand(i, bandLevels[i]);
            }
        } catch (Exception ignored) {
        }
        try {
            if (bassBoost != null) bassBoost.release();
            bassBoost = new BassBoost(0, sessionId);
            bassBoost.setEnabled(true);
            setBassBoostStrength(bassStrength);
        } catch (Exception ignored) {
        }
    }

    public void setEqualizerBand(int bandIdx, int percent) {
        if (bandIdx >= 0 && bandIdx < 5) {
            bandLevels[bandIdx] = percent;
        }
        if (equalizer != null) {
            try {
                short bands = equalizer.getNumberOfBands();
                if (bandIdx < bands) {
                    short[] range = equalizer.getBandLevelRange();
                    short min = range[0];
                    short max = range[1];
                    short level = (short) (min + ((max - min) * percent) / 100);
                    equalizer.setBandLevel((short) bandIdx, level);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public int getEqualizerBand(int bandIdx) {
        return (bandIdx >= 0 && bandIdx < 5) ? bandLevels[bandIdx] : 50;
    }

    public void setBassBoostStrength(int percent) {
        this.bassStrength = Math.max(0, Math.min(100, percent));
        if (bassBoost != null) {
            try {
                bassBoost.setStrength((short) (this.bassStrength * 10));
            } catch (Exception ignored) {
            }
        }
    }

    public int getBassBoostStrength() {
        return bassStrength;
    }

    public void setQueue(List<SongItem> songs, int startIndex) {
        if (songs == null || songs.isEmpty()) return;
        this.queueList = new ArrayList<>(songs);
        this.currentIndex = Math.max(0, Math.min(startIndex, queueList.size() - 1));
        clearABRepeat();
        playCurrentSong();
    }

    public void playSongAtQueueIndex(int index) {
        if (index >= 0 && index < queueList.size()) {
            this.currentIndex = index;
            clearABRepeat();
            playCurrentSong();
        }
    }

    public void playCurrentSong() {
        if (queueList.isEmpty()) return;
        initMediaPlayer();
        try {
            mediaPlayer.reset();
            SongItem song = queueList.get(currentIndex);
            mediaPlayer.setDataSource(getApplicationContext(), song.getMediaUri());
            mediaPlayer.setOnPreparedListener(mp -> {
                setupAudioEffects(mp.getAudioSessionId());
                mp.setVolume(1.0f, 1.0f);
                mp.start();
                applyInternalSpeed(userSpeed);
                showNotification(song, true);
                notifyUI();
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            playNext();
        }
    }

    public void playToggle() {
        if (mediaPlayer != null) {
            if (isPlaying()) {
                pausePlaybackAndClearOngoing();
            } else {
                mediaPlayer.start();
                applyInternalSpeed(userSpeed);
                SongItem song = getCurrentSong();
                if (song != null) showNotification(song, true);
                notifyUI();
            }
        } else if (!queueList.isEmpty()) {
            playCurrentSong();
        }
    }

    private void pausePlaybackAndClearOngoing() {
        if (mediaPlayer != null && isPlaying()) {
            mediaPlayer.pause();
        }
        // Detach ongoing foreground lock so user can swipe away notification in 1 tap!
        stopForeground(false);
        SongItem song = getCurrentSong();
        if (song != null) {
            showNotification(song, false);
        }
        notifyUI();
    }

    public void playNext() {
        if (queueList.isEmpty()) return;
        clearABRepeat();
        if (repeatMode == 1) {
            playCurrentSong();
            return;
        }
        if (isShuffle) {
            currentIndex = (int) (Math.random() * queueList.size());
        } else {
            currentIndex = (currentIndex + 1) % queueList.size();
        }
        playCurrentSong();
    }

    public void playPrevious() {
        if (queueList.isEmpty()) return;
        clearABRepeat();
        currentIndex = (currentIndex - 1 + queueList.size()) % queueList.size();
        playCurrentSong();
    }

    public boolean isPlaying() {
        try {
            return mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public int getCurrentPosition() {
        try {
            return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getDuration() {
        try {
            return mediaPlayer != null ? Math.max(1, mediaPlayer.getDuration()) : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    public void seekTo(int positionMs) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(Math.max(0, positionMs));
            } catch (Exception ignored) {
            }
        }
    }

    public void setPlaybackSpeed(float speed) {
        this.userSpeed = Math.max(0.5f, Math.min(2.5f, speed));
        if (isPlaying()) {
            applyInternalSpeed(this.userSpeed);
        }
    }

    public float getPlaybackSpeed() {
        return userSpeed;
    }

    private void applyInternalSpeed(float speed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null && isPlaying()) {
            try {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            } catch (Exception ignored) {
            }
        }
    }

    public void setDjSlowFadeEnabled(boolean enabled) {
        this.djSlowFadeEnabled = enabled;
    }

    // A-B Repeat Controls
    public String cycleABRepeat() {
        int pos = getCurrentPosition();
        if (pointAMs < 0) {
            pointAMs = pos;
            pointBMs = -1;
            return "A Set";
        } else if (pointBMs < 0) {
            if (pos > pointAMs + 1000) {
                pointBMs = pos;
                return "A-B Loop ON";
            } else {
                pointAMs = -1;
                return "A-B Off";
            }
        } else {
            clearABRepeat();
            return "A-B Off";
        }
    }

    public void clearABRepeat() {
        pointAMs = -1;
        pointBMs = -1;
    }

    public float getFractionA() {
        return pointAMs >= 0 ? (float) pointAMs / getDuration() : -1f;
    }

    public float getFractionB() {
        return pointBMs >= 0 ? (float) pointBMs / getDuration() : -1f;
    }

    // Sleep Timer Controls
    public void setSleepTimerMinutes(int minutes) {
        if (minutes <= 0) {
            sleepTimerEndMs = 0;
        } else {
            sleepTimerEndMs = System.currentTimeMillis() + (minutes * 60L * 1000L);
        }
    }

    public int getRemainingSleepMinutes() {
        if (sleepTimerEndMs <= 0) return 0;
        long diff = sleepTimerEndMs - System.currentTimeMillis();
        return diff > 0 ? (int) Math.ceil(diff / 60000.0) : 0;
    }

    public SongItem getCurrentSong() {
        if (!queueList.isEmpty() && currentIndex >= 0 && currentIndex < queueList.size()) {
            return queueList.get(currentIndex);
        }
        return null;
    }

    public List<SongItem> getQueueList() {
        return queueList;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setShuffle(boolean shuffle) {
        this.isShuffle = shuffle;
    }

    public boolean isShuffle() {
        return isShuffle;
    }

    public void setRepeatMode(int mode) {
        this.repeatMode = mode % 3;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (repeatMode == 2 && currentIndex == queueList.size() - 1 && !isShuffle) {
            pausePlaybackAndClearOngoing();
        } else {
            playNext();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return true;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Hybrid Music Player Controls", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Music playback notification with one-tap close");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent getActionIntent(String action, int reqCode) {
        Intent intent = new Intent(this, MusicPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void showNotification(SongItem song, boolean playing) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(playing ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSubText("Hybrid Music Player")
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(playing)
                .setSilent(true)
                .addAction(android.R.drawable.ic_media_previous, "Prev", getActionIntent(ACTION_PREV, 1))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play", getActionIntent(ACTION_PLAY_PAUSE, 2))
                .addAction(android.R.drawable.ic_media_next, "Next", getActionIntent(ACTION_NEXT, 3))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close ✕", getActionIntent(ACTION_CLOSE, 4));

        Notification notification = builder.build();
        if (playing) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(monitorRunnable);
        if (equalizer != null) equalizer.release();
        if (bassBoost != null) bassBoost.release();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopForeground(true);
        super.onDestroy();
    }
}
