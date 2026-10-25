
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicPlaybackService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

    private final IBinder binder = new LocalBinder();
    private MediaPlayer mediaPlayer;
    private List<SongItem> queueList = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isShuffle = false;
    private int repeatMode = 0; // 0 = Off, 1 = Repeat All, 2 = Repeat One

    private static final String CHANNEL_ID = "hybrid_music_channel";
    private static final int NOTIFICATION_ID = 101;

    public class LocalBinder extends Binder {
        public MusicPlaybackService getService() {
            return MusicPlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
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
            mediaPlayer.setOnCompletion(this);
            mediaPlayer.setOnError(this);
        }
    }

    public void setQueue(List<SongItem> songs, int startIndex) {
        this.queueList = new ArrayList<>(songs);
        this.currentIndex = Math.max(0, Math.min(startIndex, queueList.size() - 1));
        playCurrentSong();
    }

    public void playCurrentSong() {
        if (queueList.isEmpty()) return;
        initMediaPlayer();
        try {
            mediaPlayer.reset();
            SongItem song = queueList.get(currentIndex);
            mediaPlayer.setDataSource(getApplicationContext(), song.getMediaUri());
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                showForegroundNotification(song);
            });
        } catch (IOException e) {
            e.printStackTrace();
            playNext();
        }
    }

    public void playToggle() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                updateNotificationPaused();
            } else {
                mediaPlayer.start();
                if (!queueList.isEmpty()) {
                    showForegroundNotification(queueList.get(currentIndex));
                }
            }
        } else if (!queueList.isEmpty()) {
            playCurrentSong();
        }
    }

    public void playNext() {
        if (queueList.isEmpty()) return;
        if (repeatMode == 2) {
            playCurrentSong();
            return;
        }
        if (isShuffle) {
            currentIndex = (int) (Math.random() * queueList.size());
        } else {
            currentIndex++;
            if (currentIndex >= queueList.size()) {
                currentIndex = 0;
            }
        }
        playCurrentSong();
    }

    public void playPrevious() {
        if (queueList.isEmpty()) return;
        currentIndex--;
        if (currentIndex < 0) {
            currentIndex = queueList.size() - 1;
        }
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
            return mediaPlayer != null ? mediaPlayer.getDuration() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    public void setPlaybackSpeed(float speed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null) {
            try {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            } catch (Exception ignored) {
            }
        }
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
        this.repeatMode = mode;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        playNext();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        playNext();
        return true;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Hybrid Music Playback", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Controls for Hybrid Music Player");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showForegroundNotification(SongItem song) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void updateNotificationPaused() {
        SongItem song = getCurrentSong();
        if (song != null) {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(song.title + " (Paused)")
                    .setContentText(song.artist)
                    .setSmallIcon(android.R.drawable.ic_media_pause)
                    .setOngoing(false) // Allows instant single-tap dismissal!
                    .setSilent(true)
                    .build();
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopForeground(true);
    }
}
