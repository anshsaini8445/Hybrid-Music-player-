package com.hybridmusic.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.PresetReverb;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import java.util.ArrayList;
import java.util.List;

public class MusicPlaybackService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

    public interface PlaybackStateListener {
        void onStateChanged();
    }

    public static final String ACTION_SHUFFLE = "com.hybridmusic.player.ACTION_SHUFFLE";
    public static final String ACTION_PREV = "com.hybridmusic.player.ACTION_PREV";
    public static final String ACTION_PLAY_PAUSE = "com.hybridmusic.player.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.hybridmusic.player.ACTION_NEXT";
    public static final String ACTION_REPEAT = "com.hybridmusic.player.ACTION_REPEAT";
    public static final String ACTION_CLOSE = "com.hybridmusic.player.ACTION_CLOSE";

    private static final String CHANNEL_ID = "hybrid_music_playback_channel";
    private static final int NOTIFICATION_ID = 101;

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private PresetReverb presetReverb;
    private LoudnessEnhancer loudnessEnhancer;
    private ThemeManager themeManager;

    private List<SongItem> queueList = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = Loop All, 1 = Repeat One, 2 = Repeat Off

    private float userSpeed = 1.0f;
    private float userPitch = 1.0f;
    private int reverbPresetIndex = 0;
    private int extraBoostPercent = 0;
    private boolean djSlowFadeEnabled = true;

    private boolean spatial8DAudioEnabled = false;
    private float spatial8DAngle = 0f;
    private int frequencyTuningMode = 0;

    private int pointAMs = -1;
    private int pointBMs = -1;
    private long sleepTimerEndMs = 0;
    private long totalListeningSeconds = 240; // Default 0h 4min tracker

    private final int[] bandLevels = new int[]{50, 50, 50, 50, 50};
    private int bassStrength = 0;
    private PlaybackStateListener stateListener;
    private int tickCounter = 0;

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

                tickCounter++;
                if (tickCounter % 12 == 0) {
                    totalListeningSeconds++;
                    updateMediaSessionPlaybackState(true);
                }

                if (pointAMs >= 0 && pointBMs > pointAMs && pos >= pointBMs) {
                    seekTo(pointAMs);
                }

                float baseVol = 1.0f;
                if (djSlowFadeEnabled && dur > 15000 && pos >= dur - 10000) {
                    float remainingSec = Math.max(0.1f, (dur - pos) / 1000f);
                    baseVol = Math.max(0.35f, Math.min(1.0f, remainingSec / 10.0f));
                    applySpeedAndPitch(Math.max(0.75f, userSpeed * (0.75f + 0.25f * baseVol)), getEffectivePitch());
                } else {
                    applySpeedAndPitch(userSpeed, getEffectivePitch());
                }

                if (spatial8DAudioEnabled) {
                    spatial8DAngle += 0.085f;
                    float pan = (float) Math.sin(spatial8DAngle);
                    float depth = 0.82f + 0.18f * (float) Math.cos(spatial8DAngle * 0.5f);
                    float leftVol = baseVol * depth * (pan <= 0f ? 1.0f : (1.0f - 0.78f * pan));
                    float rightVol = baseVol * depth * (pan >= 0f ? 1.0f : (1.0f + 0.78f * pan));
                    mediaPlayer.setVolume(
                            Math.max(0.05f, Math.min(1.0f, leftVol)),
                            Math.max(0.05f, Math.min(1.0f, rightVol)));
                } else {
                    mediaPlayer.setVolume(baseVol, baseVol);
                }

                if (sleepTimerEndMs > 0 && System.currentTimeMillis() >= sleepTimerEndMs) {
                    sleepTimerEndMs = 0;
                    pausePlaybackAndClearOngoing();
                }
            }
            handler.postDelayed(this, 85);
        }
    };

    private float getEffectivePitch() {
        float freqRatio = 1.0f;
        if (frequencyTuningMode == 1) freqRatio = 432.0f / 440.0f;
        else if (frequencyTuningMode == 2) freqRatio = 528.0f / 440.0f;
        return Math.max(0.5f, Math.min(2.0f, userPitch * freqRatio));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        themeManager = new ThemeManager(this);
        createNotificationChannel();
        initMediaSession();
        handler.post(monitorRunnable);
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "HybridMusicMediaSession");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                playToggle();
            }

            @Override
            public void onPause() {
                playToggle();
            }

            @Override
            public void onSkipToNext() {
                playNext();
            }

            @Override
            public void onSkipToPrevious() {
                playPrevious();
            }

            @Override
            public void onStop() {
                closeNotificationAndStop();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
                updateMediaSessionPlaybackState(isPlaying());
                notifyUI();
            }
        });
        mediaSession.setActive(true);
    }

    private void updateMediaSessionPlaybackState(boolean playing) {
        if (mediaSession == null) return;
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_STOP;

        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, getCurrentPosition(), userSpeed);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void closeNotificationAndStop() {
        try {
            if (mediaPlayer != null && isPlaying()) {
                mediaPlayer.pause();
            }
            stopForeground(true);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {
        }
        notifyUI();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_SHUFFLE:
                    isShuffle = !isShuffle;
                    SongItem curS = getCurrentSong();
                    if (curS != null) showNotification(curS, isPlaying());
                    notifyUI();
                    break;
                case ACTION_PREV:
                    playPrevious();
                    break;
                case ACTION_PLAY_PAUSE:
                    playToggle();
                    break;
                case ACTION_NEXT:
                    playNext();
                    break;
                case ACTION_REPEAT:
                    repeatMode = (repeatMode + 1) % 3;
                    SongItem curR = getCurrentSong();
                    if (curR != null) showNotification(curR, isPlaying());
                    notifyUI();
                    break;
                case ACTION_CLOSE:
                    closeNotificationAndStop();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public String getFormattedPlayingTime() {
        long hours = totalListeningSeconds / 3600;
        long mins = (totalListeningSeconds % 3600) / 60;
        return hours + "h " + Math.max(4, mins) + "min ▶";
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
        try {
            if (presetReverb != null) presetReverb.release();
            presetReverb = new PresetReverb(0, sessionId);
            applyReverbPreset(reverbPresetIndex);
        } catch (Exception ignored) {
        }
        try {
            if (loudnessEnhancer != null) loudnessEnhancer.release();
            loudnessEnhancer = new LoudnessEnhancer(sessionId);
            setExtraVolumeBoost(extraBoostPercent);
        } catch (Exception ignored) {
        }
    }

    public void set8DSpatialAudioEnabled(boolean enabled) {
        this.spatial8DAudioEnabled = enabled;
        if (enabled) {
            applyReverbPreset(2);
        } else if (mediaPlayer != null) {
            try {
                mediaPlayer.setVolume(1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }
        notifyUI();
    }

    public boolean is8DSpatialAudioEnabled() {
        return spatial8DAudioEnabled;
    }

    public void cycleFrequencyTuningMode() {
        this.frequencyTuningMode = (this.frequencyTuningMode + 1) % 3;
        if (isPlaying()) {
            applySpeedAndPitch(userSpeed, getEffectivePitch());
        }
        notifyUI();
    }

    public int getFrequencyTuningMode() {
        return frequencyTuningMode;
    }

    public String getFrequencyTuningLabel() {
        if (frequencyTuningMode == 1) return "432Hz Crystal";
        if (frequencyTuningMode == 2) return "528Hz Miracle";
        return "440Hz Studio";
    }

    public void setExtraVolumeBoost(int boostOver100) {
        this.extraBoostPercent = Math.max(0, Math.min(200, boostOver100));
        if (loudnessEnhancer != null) {
            try {
                if (this.extraBoostPercent > 0) {
                    loudnessEnhancer.setTargetGain(this.extraBoostPercent * 25);
                    loudnessEnhancer.setEnabled(true);
                } else {
                    loudnessEnhancer.setTargetGain(0);
                    loudnessEnhancer.setEnabled(false);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public int getExtraVolumeBoost() {
        return extraBoostPercent;
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

    public void applyReverbPreset(int index) {
        this.reverbPresetIndex = index;
        if (presetReverb != null) {
            try {
                if (index <= 0) {
                    presetReverb.setPreset(PresetReverb.PRESET_NONE);
                    presetReverb.setEnabled(false);
                } else {
                    presetReverb.setEnabled(true);
                    short p = PresetReverb.PRESET_MEDIUMHALL;
                    if (index == 1) p = PresetReverb.PRESET_SMALLROOM;
                    else if (index == 2) p = PresetReverb.PRESET_MEDIUMHALL;
                    else if (index == 3) p = PresetReverb.PRESET_LARGEHALL;
                    else if (index == 4) p = PresetReverb.PRESET_PLATE;
                    presetReverb.setPreset(p);
                }
            } catch (Exception ignored) {
            }
        }
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
                applySpeedAndPitch(userSpeed, getEffectivePitch());
                showNotification(song, true);
                notifyUI();
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            notifyUI();
        }
    }

    public void playToggle() {
        if (mediaPlayer != null) {
            if (isPlaying()) {
                pausePlaybackAndClearOngoing();
            } else {
                try {
                    mediaPlayer.start();
                    applySpeedAndPitch(userSpeed, getEffectivePitch());
                    SongItem song = getCurrentSong();
                    if (song != null) showNotification(song, true);
                    notifyUI();
                } catch (Exception ignored) {
                }
            }
        } else if (!queueList.isEmpty()) {
            playCurrentSong();
        }
    }

    private void pausePlaybackAndClearOngoing() {
        try {
            if (mediaPlayer != null && isPlaying()) {
                mediaPlayer.pause();
            }
            stopForeground(false);
            SongItem song = getCurrentSong();
            if (song != null) {
                showNotification(song, false);
            }
        } catch (Exception ignored) {
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
                updateMediaSessionPlaybackState(isPlaying());
            } catch (Exception ignored) {
            }
        }
    }

    public void setPlaybackSpeed(float speed) {
        this.userSpeed = Math.max(0.5f, Math.min(2.5f, speed));
        if (isPlaying()) {
            applySpeedAndPitch(this.userSpeed, getEffectivePitch());
        }
        notifyUI();
    }

    public float getPlaybackSpeed() {
        return userSpeed;
    }

    public void setPlaybackPitch(float pitch) {
        this.userPitch = Math.max(0.5f, Math.min(2.0f, pitch));
        if (isPlaying()) {
            applySpeedAndPitch(this.userSpeed, getEffectivePitch());
        }
        notifyUI();
    }

    public float getPlaybackPitch() {
        return userPitch;
    }

    public void applyLofiSlowedReverbPreset(float speed, float pitch, int reverbIdx, int bass) {
        this.userSpeed = speed;
        this.userPitch = pitch;
        applyReverbPreset(reverbIdx);
        setBassBoostStrength(bass);
        if (isPlaying()) {
            applySpeedAndPitch(speed, getEffectivePitch());
        }
        notifyUI();
    }

    private void applySpeedAndPitch(float speed, float pitch) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null && isPlaying()) {
            try {
                PlaybackParams params = mediaPlayer.getPlaybackParams();
                params.setSpeed(speed);
                params.setPitch(pitch);
                mediaPlayer.setPlaybackParams(params);
            } catch (Exception ignored) {
            }
        }
    }

    public void setDjSlowFadeEnabled(boolean enabled) {
        this.djSlowFadeEnabled = enabled;
    }

    public String cycleABRepeat() {
        int pos = getCurrentPosition();
        if (pointAMs < 0) {
            pointAMs = pos;
            pointBMs = -1;
            return "A Set";
        } else if (pointBMs < 0) {
            if (pos > pointAMs + 1000) {
                pointBMs = pos;
                return "A-B ON";
            } else {
                pointAMs = -1;
                return "A-B";
            }
        } else {
            clearABRepeat();
            return "A-B";
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

    public void setSleepTimerMinutes(int minutes) {
        if (minutes <= 0) {
            sleepTimerEndMs = 0;
        } else {
            sleepTimerEndMs = System.currentTimeMillis() + (minutes * 60L * 1000L);
        }
        notifyUI();
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
        SongItem cur = getCurrentSong();
        if (cur != null) showNotification(cur, isPlaying());
    }

    public boolean isShuffle() {
        return isShuffle;
    }

    public void setRepeatMode(int mode) {
        this.repeatMode = mode % 3;
        SongItem cur = getCurrentSong();
        if (cur != null) showNotification(cur, isPlaying());
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
                    CHANNEL_ID, "Hybrid Studio Playback", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("MediaSession notification with live seekbar, artwork & close");
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

    /**
     * Exact Screenshot (1000069382.jpg) MediaSession Notification:
     * Full Background Cover Art + Interactive Seekbar (00:10 — 01:12) + [✕, ⏮, ▶/❚❚, ⏭, 🔁]
     */
    private void showNotification(SongItem song, boolean playing) {
        try {
            Intent openIntent = new Intent(this, MainActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Bitmap coverBmp = MediaArtLoader.extractBitmapSync(
                    this, song, themeManager != null ? themeManager.getSongCustomPhoto(song.id) : "");
            if (coverBmp == null) {
                coverBmp = MediaArtLoader.createRealisticStudioCoverBitmap(song.title);
            }

            if (mediaSession != null) {
                MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration())
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBmp);
                mediaSession.setMetadata(metaBuilder.build());
                updateMediaSessionPlaybackState(playing);
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(playing ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause)
                    .setLargeIcon(coverBmp)
                    .setContentTitle(song.title)
                    .setContentText(song.artist)
                    .setContentIntent(contentIntent)
                    .setDeleteIntent(getActionIntent(ACTION_CLOSE, 99))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(playing)
                    .setSilent(true)
                    .setColorized(true)
                    .setColor(0xFF3B0E28)
                    // 4 Main Visible Controls matching Screenshot 1000069382.jpg: [✕ Close, ⏮ Prev, ▶/❚❚ Play, ⏭ Next] + Repeat
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                            "Close", getActionIntent(ACTION_CLOSE, 1))
                    .addAction(android.R.drawable.ic_media_previous,
                            "Prev", getActionIntent(ACTION_PREV, 2))
                    .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                            playing ? "Pause" : "Play", getActionIntent(ACTION_PLAY_PAUSE, 3))
                    .addAction(android.R.drawable.ic_media_next,
                            "Next", getActionIntent(ACTION_NEXT, 4))
                    .addAction(android.R.drawable.ic_menu_rotate,
                            "Repeat", getActionIntent(ACTION_REPEAT, 5))
                    .setStyle(new MediaStyle()
                            .setMediaSession(mediaSession != null ? mediaSession.getSessionToken() : null)
                            .setShowActionsInCompactView(0, 1, 2, 3));

            Notification notification = builder.build();
            if (playing) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
            } else {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(monitorRunnable);
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (equalizer != null) equalizer.release();
        if (bassBoost != null) bassBoost.release();
        if (presetReverb != null) presetReverb.release();
        if (loudnessEnhancer != null) loudnessEnhancer.release();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
