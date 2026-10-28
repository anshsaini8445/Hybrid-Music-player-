package com.hybridmusic.player;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements
        SettingsSectionHelper.SettingsCallbacks,
        AudioThreeDotStudioHelper.StudioPlayerCallbacks {

    private MusicPlaybackService musicService;
    private boolean isBound = false;
    private ThemeManager themeManager;
    private PocketShakeManager pocketShakeManager;
    private AudioManager audioManager;

    private final List<SongItem> allSongs = new ArrayList<>();
    private final List<SongItem> displayedSongs = new ArrayList<>();
    private RecyclerView mainRecyclerView;
    private SongAdapter mainAdapter;

    private int currentTopTab = 1; // Default 1 = Songs (0=For You, 1=Songs, 2=Playlist, 3=Video, 4=Folders, 5=Favorites)
    private int currentBottomTab = 0; // 0=Songs, 1=Search, 2=Settings(ME)
    private long songIdForPhotoPick = -1;
    private int currentDisplayVolumePercent = 80;
    private boolean isSuperLoud200Boost = false;
    private String abRepeatLabel = "A-B";
    private boolean isMusicPetVisible = true;

    // Main Library Views
    private View rootLayout, topBrandBar, topHeaderBar, miniPlayerBar, miniTextContainer, bottomNavBar, fullPlayerOverlay;
    private ImageView customThemeBgImage, miniThumbnail, topSortMenuBtn;
    private ImageView miniPrevBtn, miniPlayPauseBtn, miniNextBtn;
    private TextView appTitleText, totalSongsCountText;
    private TextView miniTitle, miniArtist;
    private Button navSongsBtn, navSearchBtn, navSettingsBtn;
    private final TextView[] topTabPills = new TextView[6];
    private DancingPuppyView floatingGuddaView;
    private VolumeBannerView volumeBannerView;

    // Full Studio Player Views
    private AlbumArtDiscView fpAlbumArtDiscView;
    private View fpLyricsContainer, queueBackdropOverlay, queueSlidePanel;
    private ImageView fpCloseBtn, fpThreeDotBtn, fpFavoriteHeartBtn;
    private ImageView fpShuffleBtn, fpPrevBtn, fpPlayPauseBtn, fpNextBtn, fpRepeatBtn;
    private ImageView fpBottomThreeDotBtn, queueCloseCutBtn;
    private TextView fpQuickLyricsBtn, fpLyricsText, fpFindLyricsOnlineBtn, fpBackToArtBtn;
    private TextView fpSongTitle, fpSongArtist, fpCurrentTime, fpTotalTime;
    private WaveformSeekBarView fpWaveformSeekBar;
    private TextView fpPocketShakeBtn, fpQueueBtn, queueHeaderTitle;
    private Button fpLyricsFavBtn;
    private final TextView[] bottomSlotBtns = new TextView[4];
    private RecyclerView queueRecyclerView;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private GestureDetector tabSwipeDetector;
    private GestureDetector miniPlayerGestureDetector;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (musicService != null && isBound) {
                int pos = musicService.getCurrentPosition();
                int dur = musicService.getDuration();
                boolean playing = musicService.isPlaying();

                SongItem cur = musicService.getCurrentSong();
                if (cur != null && playing) {
                    int trimStartMs = themeManager.getSongTrimStartSec(cur.id) * 1000;
                    int trimEndMs = themeManager.getSongTrimEndSec(cur.id) * 1000;
                    if (trimStartMs > 0 && pos < trimStartMs) {
                        musicService.seekTo(trimStartMs);
                        pos = trimStartMs;
                    } else if (trimEndMs > trimStartMs && pos >= trimEndMs) {
                        musicService.playNext();
                    }
                }

                fpWaveformSeekBar.setProgressFraction((float) pos / Math.max(1, dur));
                fpWaveformSeekBar.setABMarkers(musicService.getFractionA(), musicService.getFractionB());
                fpWaveformSeekBar.setPlaying(playing);

                floatingGuddaView.setPlayingState(playing);
                fpAlbumArtDiscView.setPlaying(playing);

                fpCurrentTime.setText(formatMs(pos));
                fpTotalTime.setText(formatMs(dur));
            }
            uiHandler.postDelayed(this, 350);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicPlaybackService.LocalBinder) service).getService();
            isBound = true;
            musicService.setDjSlowFadeEnabled(themeManager.isDjSlowFadeEnabled());
            musicService.setPlaybackStateListener(() -> runOnUiThread(MainActivity.this::syncPlayerUI));
            syncPlayerUI();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themeManager = new ThemeManager(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setContentView(R.layout.activity_main);

        initSystemVolumePercent();
        bindViews();

        pocketShakeManager = new PocketShakeManager(this, () -> {
            if (musicService != null) {
                musicService.playNext();
                Toast.makeText(this, "📳 Double-Shake: Next Song Played!", Toast.LENGTH_SHORT).show();
            }
        });
        pocketShakeManager.setEnabled(false);

        Intent svcIntent = new Intent(this, MusicPlaybackService.class);
        startService(svcIntent);
        bindService(svcIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        setupInteractions();
        applyLanguageLabels();
        applyActiveThemeEverywhere();
        checkPermissionsAndLoadMedia();
        uiHandler.post(progressTicker);
    }

    private void initSystemVolumePercent() {
        if (audioManager != null) {
            int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            currentDisplayVolumePercent = Math.round((cur * 100f) / max);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            adjustHybridVolume(10);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            adjustHybridVolume(-10);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void adjustHybridVolume(int deltaPercent) {
        currentDisplayVolumePercent = Math.max(0, Math.min(200, currentDisplayVolumePercent + deltaPercent));
        applyVolumePercentToHardware(currentDisplayVolumePercent);
        if (volumeBannerView != null) {
            volumeBannerView.showVolume(currentDisplayVolumePercent);
        }
    }

    private void applyVolumePercentToHardware(int percent) {
        if (audioManager != null) {
            int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int sysPercent = Math.min(100, percent);
            int targetStreamVol = Math.round((sysPercent * max) / 100f);
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, 0);
        }
        if (musicService != null) {
            int extraBoost = Math.max(0, percent - 100);
            musicService.setExtraVolumeBoost(extraBoost);
            isSuperLoud200Boost = (extraBoost > 0);
        }
    }

    private void bindViews() {
        rootLayout = findViewById(R.id.rootLayout);
        customThemeBgImage = findViewById(R.id.customThemeBgImage);
        topBrandBar = findViewById(R.id.topBrandBar);
        topHeaderBar = findViewById(R.id.topHeaderBar);
        miniPlayerBar = findViewById(R.id.miniPlayerBar);
        miniTextContainer = findViewById(R.id.miniTextContainer);
        bottomNavBar = findViewById(R.id.bottomNavBar);
        fullPlayerOverlay = findViewById(R.id.fullPlayerOverlay);

        topTabPills[0] = findViewById(R.id.tabAllSongs);
        topTabPills[1] = findViewById(R.id.tabFolders);
        topTabPills[2] = findViewById(R.id.tabPlaylists);
        topTabPills[3] = findViewById(R.id.tabArtists);
        topTabPills[4] = findViewById(R.id.tabVideoAudio);
        topTabPills[5] = findViewById(R.id.tabFavorites);

        topSortMenuBtn = findViewById(R.id.topSortMenuBtn);
        totalSongsCountText = findViewById(R.id.totalSongsCountText);
        appTitleText = findViewById(R.id.appTitleText);
        floatingGuddaView = findViewById(R.id.floatingGuddaView);
        volumeBannerView = findViewById(R.id.volumeBannerView);

        navSongsBtn = findViewById(R.id.navSongsBtn);
        navSearchBtn = findViewById(R.id.navSearchBtn);
        navSettingsBtn = findViewById(R.id.navSettingsBtn);

        miniThumbnail = findViewById(R.id.miniThumbnail);
        miniTitle = findViewById(R.id.miniTitle);
        miniTitle.setSelected(true);
        miniArtist = findViewById(R.id.miniArtist);
        miniPrevBtn = findViewById(R.id.miniPrevBtn);
        miniPlayPauseBtn = findViewById(R.id.miniPlayPauseBtn);
        miniNextBtn = findViewById(R.id.miniNextBtn);

        fpCloseBtn = findViewById(R.id.fpCloseBtn);
        fpThreeDotBtn = findViewById(R.id.fpThreeDotBtn);
        fpFavoriteHeartBtn = findViewById(R.id.fpFavoriteHeartBtn);
        fpQuickLyricsBtn = findViewById(R.id.fpQuickLyricsBtn);
        fpAlbumArtDiscView = findViewById(R.id.fpAlbumArtDiscView);
        fpLyricsContainer = findViewById(R.id.fpLyricsContainer);
        fpLyricsText = findViewById(R.id.fpLyricsText);
        fpFindLyricsOnlineBtn = findViewById(R.id.fpFindLyricsOnlineBtn);
        fpBackToArtBtn = findViewById(R.id.fpBackToArtBtn);
        fpLyricsFavBtn = findViewById(R.id.fpLyricsFavBtn);
        fpSongTitle = findViewById(R.id.fpSongTitle);
        fpSongTitle.setSelected(true);
        fpSongArtist = findViewById(R.id.fpSongArtist);
        fpCurrentTime = findViewById(R.id.fpCurrentTime);
        fpTotalTime = findViewById(R.id.fpTotalTime);
        fpWaveformSeekBar = findViewById(R.id.fpWaveformSeekBar);

        fpPlayPauseBtn = findViewById(R.id.fpPlayPauseBtn);
        fpPrevBtn = findViewById(R.id.fpPrevBtn);
        fpNextBtn = findViewById(R.id.fpNextBtn);
        fpShuffleBtn = findViewById(R.id.fpShuffleBtn);
        fpRepeatBtn = findViewById(R.id.fpRepeatBtn);
        fpPocketShakeBtn = findViewById(R.id.fpPocketShakeBtn);
        fpQueueBtn = findViewById(R.id.fpQueueBtn);
        fpBottomThreeDotBtn = findViewById(R.id.fpBottomThreeDotBtn);

        bottomSlotBtns[0] = findViewById(R.id.fpSlot0Btn);
        bottomSlotBtns[1] = findViewById(R.id.fpSlot1Btn);
        bottomSlotBtns[2] = findViewById(R.id.fpSlot2Btn);
        bottomSlotBtns[3] = findViewById(R.id.fpSlot3Btn);

        queueBackdropOverlay = findViewById(R.id.queueBackdropOverlay);
        queueSlidePanel = findViewById(R.id.queueSlidePanel);
        queueHeaderTitle = findViewById(R.id.queueHeaderTitle);
        queueCloseCutBtn = findViewById(R.id.queueCloseCutBtn);
        queueRecyclerView = findViewById(R.id.queueRecyclerView);
        queueRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mainRecyclerView = new RecyclerView(this);
        mainRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupInteractions() {
        for (int i = 0; i < 6; i++) {
            final int tabIdx = i;
            AudioThreeDotStudioHelper.attachBouncySpring(topTabPills[i], () -> selectTopCategoryTab(tabIdx));
        }

        AudioThreeDotStudioHelper.attachBouncySpring(topSortMenuBtn, this::showAudioStudioSortAndToolsMenu);
        appTitleText.setOnClickListener(v -> showCustomizeHeaderDialog());

        tabSwipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy) * 1.6f && Math.abs(dx) > 130) {
                    if (dx < 0) {
                        if (currentBottomTab == 0) {
                            if (currentTopTab < 5) selectTopCategoryTab(currentTopTab + 1);
                            else showBottomSection(1);
                        } else if (currentBottomTab == 1) {
                            showBottomSection(2);
                        }
                    } else {
                        if (currentBottomTab == 2) showBottomSection(1);
                        else if (currentBottomTab == 1) showBottomSection(0);
                        else if (currentTopTab > 0) selectTopCategoryTab(currentTopTab - 1);
                    }
                    return true;
                }
                return false;
            }
        });

        mainRecyclerView.setOnTouchListener((v, event) -> {
            tabSwipeDetector.onTouchEvent(event);
            return false;
        });

        // Instant 1-Click Open on Mini-Player Bar + Left/Right Swipe to Change Song!
        miniPlayerGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                openFullPlayer();
                return true;
            }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null || musicService == null) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) > 65) {
                    if (dx < 0) musicService.playNext();
                    else musicService.playPrevious();
                    return true;
                }
                return false;
            }
        });

        View.OnTouchListener instantMiniTouch = (v, event) -> miniPlayerGestureDetector.onTouchEvent(event);
        miniPlayerBar.setOnTouchListener(instantMiniTouch);
        miniTextContainer.setOnTouchListener(instantMiniTouch);
        miniThumbnail.setOnTouchListener(instantMiniTouch);

        navSongsBtn.setOnClickListener(v -> showBottomSection(0));
        navSearchBtn.setOnClickListener(v -> showBottomSection(1));
        navSettingsBtn.setOnClickListener(v -> showBottomSection(2));

        AudioThreeDotStudioHelper.attachBouncySpring(miniPrevBtn, () -> {
            if (musicService != null) musicService.playPrevious();
        });
        AudioThreeDotStudioHelper.attachBouncySpring(miniPlayPauseBtn, () -> {
            if (musicService != null) musicService.playToggle();
        });
        AudioThreeDotStudioHelper.attachBouncySpring(miniNextBtn, () -> {
            if (musicService != null) musicService.playNext();
        });

        AudioThreeDotStudioHelper.attachBouncySpring(fpCloseBtn, this::closeFullPlayerSmoothly);

        Runnable openStudioMenu = () ->
                AudioThreeDotStudioHelper.showAudioThreeDotStudioDialog(MainActivity.this, MainActivity.this);
        AudioThreeDotStudioHelper.attachBouncySpring(fpThreeDotBtn, openStudioMenu);
        AudioThreeDotStudioHelper.attachBouncySpring(fpBottomThreeDotBtn, openStudioMenu);

        AudioThreeDotStudioHelper.attachBouncySpring(fpFavoriteHeartBtn, this::onToggleFavorite);
        AudioThreeDotStudioHelper.attachBouncySpring(fpQueueBtn, this::openRightSlideQueue);

        queueBackdropOverlay.setOnClickListener(v -> closeRightSlideQueue());
        AudioThreeDotStudioHelper.attachBouncySpring(queueCloseCutBtn, this::closeRightSlideQueue);

        fpAlbumArtDiscView.setOnArtGestureListener(new AlbumArtDiscView.OnArtGestureListener() {
            @Override
            public void onSwipeRightToLeft() {
                if (musicService != null) musicService.playNext();
            }
            @Override
            public void onSwipeLeftToRight() {
                if (musicService != null) musicService.playPrevious();
            }
            @Override
            public void onCenterTap() {
                onToggleVinylDiscOrBox();
            }
        });

        AudioThreeDotStudioHelper.attachBouncySpring(fpQuickLyricsBtn, () -> openLyricsPanel(true));

        AudioThreeDotStudioHelper.attachBouncySpring(fpBackToArtBtn, () -> {
            fpLyricsContainer.setVisibility(View.GONE);
            fpAlbumArtDiscView.setLyricsPageSelected(false);
        });
        AudioThreeDotStudioHelper.attachBouncySpring(fpFindLyricsOnlineBtn, this::fetchOnlineLyricsForCurrentSong);
        fpLyricsFavBtn.setOnClickListener(v -> onToggleFavorite());

        AudioThreeDotStudioHelper.attachBouncySpring(fpPocketShakeBtn, () -> {
            boolean nextState = !pocketShakeManager.isEnabled();
            pocketShakeManager.setEnabled(nextState);
            updatePocketShakeButtonUI();
            Toast.makeText(this, nextState
                    ? "Pocket Shake ON (2x Hand Shake for Next Song)"
                    : "Pocket Shake OFF", Toast.LENGTH_SHORT).show();
        });

        fpWaveformSeekBar.setOnWaveformSeekListener(fraction -> {
            if (musicService != null) {
                int target = Math.round(fraction * musicService.getDuration());
                musicService.seekTo(target);
            }
        });

        AudioThreeDotStudioHelper.attachBouncySpring(fpPlayPauseBtn, () -> {
            if (musicService != null) musicService.playToggle();
        });
        AudioThreeDotStudioHelper.attachBouncySpring(fpPrevBtn, () -> {
            if (musicService != null) musicService.playPrevious();
        });
        AudioThreeDotStudioHelper.attachBouncySpring(fpNextBtn, () -> {
            if (musicService != null) musicService.playNext();
        });

        // SHUFFLE BUTTON: Completely Transforms & Spins 360° on Click!
        AudioThreeDotStudioHelper.attachBouncySpring(fpShuffleBtn, () -> {
            if (musicService != null) {
                boolean next = !musicService.isShuffle();
                musicService.setShuffle(next);
                fpShuffleBtn.animate().rotationBy(360f).setDuration(260).start();
                syncPlayerUI();
                Toast.makeText(this, next ? "🔀 Shuffle Mode: ON" : "Shuffle Mode: OFF", Toast.LENGTH_SHORT).show();
            }
        });

        // REPEAT BUTTON: Completely Transforms & Spins 360° on Click!
        AudioThreeDotStudioHelper.attachBouncySpring(fpRepeatBtn, () -> {
            if (musicService != null) {
                int nextMode = (musicService.getRepeatMode() + 1) % 3;
                musicService.setRepeatMode(nextMode);
                fpRepeatBtn.animate().rotationBy(360f).setDuration(260).start();
                syncPlayerUI();
                String[] labels = {"🔁 Loop All Songs: ON", "🔂 Repeat Current Song (1): ON", "➡️ Repeat Mode: OFF"};
                Toast.makeText(this, labels[nextMode], Toast.LENGTH_SHORT).show();
            }
        });

        refreshBottomSwappableButtons();
    }

    private void applyLanguageLabels() {
        String lang = themeManager.getAppLanguage();
        if ("Hindi".equalsIgnoreCase(lang)) {
            topTabPills[0].setText("फॉर यू");
            topTabPills[1].setText("गाने");
            topTabPills[2].setText("प्लेलिस्ट");
            topTabPills[3].setText("वीडियो");
            topTabPills[4].setText("फ़ोल्डर");
            topTabPills[5].setText("पसंदीदा");
            navSongsBtn.setText("🎵 गाने");
            navSearchBtn.setText("🔍 खोजें");
            navSettingsBtn.setText("✨ ME स्टूडियो");
        } else if ("Punjabi".equalsIgnoreCase(lang)) {
            topTabPills[0].setText("For you");
            topTabPills[1].setText("ਗਾਣੇ");
            topTabPills[2].setText("ਪਲੇਲਿਸਟ");
            topTabPills[3].setText("ਵੀਡੀਓ");
            topTabPills[4].setText("ਫੋਲਡਰ");
            topTabPills[5].setText("ਪਸੰਦੀਦਾ");
            navSongsBtn.setText("🎵 ਗਾਣੇ");
            navSearchBtn.setText("🔍 ਖੋਜ");
            navSettingsBtn.setText("✨ ME ਸਟੂਡੀਓ");
        } else {
            topTabPills[0].setText("For you");
            topTabPills[1].setText("Songs");
            topTabPills[2].setText("Playlist");
            topTabPills[3].setText("Video");
            topTabPills[4].setText("Folders");
            topTabPills[5].setText("Favorites");
            navSongsBtn.setText("🎵 Songs");
            navSearchBtn.setText("🔍 Search");
            navSettingsBtn.setText("✨ ME Studio");
        }
    }

    private void showSongRowThreeDotMenu(final SongItem song, final int position) {
        if (song == null) return;
        final boolean isFav = themeManager.isFavorite(song.id);
        String[] options = new String[]{
                "Play Song Now (Hi-Res Studio Player)",
                "Cut / Play Half Song (Aadha Gaana Chalayein)",
                "Enable Free 8D Spatial Surround Audio",
                isFav ? "Remove from Liked (❤️)" : "Add to Liked (❤️)",
                "5-Band Dolby Equalizer & Speed Dial",
                "Set Custom Photo from Gallery (Save Permanently)",
                "Set Song as Phone Ringtone",
                "Share Song via Other Apps",
                "Song Details & File Path",
                "Delete Song Permanently (From Phone)"
        };

        SongAdapter.showPureWhiteAudioMenuDialog(this, song.title, options, which -> {
            switch (which) {
                case 0:
                    if (musicService != null && position >= 0 && position < displayedSongs.size()) {
                        themeManager.setSongTrimSeconds(song.id, 0, 0);
                        musicService.setQueue(displayedSongs, position);
                        openFullPlayer();
                    }
                    break;
                case 1:
                    int halfSec = Math.max(10, (int) (song.duration / 2000L));
                    onPlayTrimmedSong(song, 0, halfSec);
                    Toast.makeText(this, "✂️ Playing Half Song (0s to " + halfSec + "s)", Toast.LENGTH_LONG).show();
                    break;
                case 2:
                    onToggle8DSpatialAudio();
                    break;
                case 3:
                    boolean nowFav = themeManager.toggleFavorite(song.id);
                    Toast.makeText(this, nowFav ? "❤️ Added to Liked Songs" : "Removed from Liked Songs", Toast.LENGTH_SHORT).show();
                    syncPlayerUI();
                    break;
                case 4:
                    onOpenEqualizerAndSoundLab();
                    break;
                case 5:
                    onPickCustomPhotoForSong(song);
                    break;
                case 6:
                    AudioThreeDotStudioHelper.setSongAsPhoneRingtone(this, song);
                    break;
                case 7:
                    AudioThreeDotStudioHelper.shareSongFile(this, song);
                    break;
                case 8:
                    new AlertDialog.Builder(this)
                            .setTitle("Audio Track Details")
                            .setMessage("Title: " + song.title
                                    + "\n\nArtist: " + song.artist
                                    + "\n\nDuration: " + song.getFormattedDuration()
                                    + "\n\nSize: " + song.getFormattedSize()
                                    + "\n\nPath: " + song.path)
                            .setPositiveButton("OK", null)
                            .show();
                    break;
                case 9:
                    onDeleteSongPermanently(song);
                    break;
            }
        });
    }

    private void showAudioStudioSortAndToolsMenu() {
        String[] items = new String[]{
                "Sort: Newest Downloaded Songs",
                "Sort: Song Name (A to Z)",
                "Sort: Artist / Singer Name",
                "Sort: Longest Duration Songs",
                "Sort: Largest File Size",
                "🦁 Choose 15 Cute Baby Animals & 15 Colors",
                "🎧 Toggle 8D Spatial Surround Audio (Free Pro)",
                "🎼 Switch 432Hz / 528Hz Frequency Tuner (Free Pro)",
                "🎚️ Open Buttonless Equalizer & Speed/Timer Dial",
                "🎨 Open 60 Studio Themes & VIP Store"
        };
        SongAdapter.showPureWhiteAudioMenuDialog(this, "Hybrid Studio Tools & Sort", items, which -> {
            if (which == 0) {
                Collections.sort(displayedSongs, (a, b) -> Long.compare(b.dateAdded, a.dateAdded));
                refreshMainListAdapter();
            } else if (which == 1) {
                Collections.sort(displayedSongs, (a, b) -> a.title.compareToIgnoreCase(b.title));
                refreshMainListAdapter();
            } else if (which == 2) {
                Collections.sort(displayedSongs, (a, b) -> a.artist.compareToIgnoreCase(b.artist));
                refreshMainListAdapter();
            } else if (which == 3) {
                Collections.sort(displayedSongs, (a, b) -> Long.compare(b.duration, a.duration));
                refreshMainListAdapter();
            } else if (which == 4) {
                Collections.sort(displayedSongs, (a, b) -> Long.compare(b.size, a.size));
                refreshMainListAdapter();
            } else if (which == 5) {
                SettingsSectionHelper.showCutePuppyStudioFolderDialog(this, themeManager, this);
            } else if (which == 6) {
                onToggle8DSpatialAudio();
            } else if (which == 7) {
                onCycleFrequencyTuning();
            } else if (which == 8) {
                onOpenEqualizerAndSoundLab();
            } else if (which == 9) {
                showThemePickerDialog();
            }
        });
    }

    // =========================================================================
    // AudioThreeDotStudioHelper.StudioPlayerCallbacks Implementation
    // =========================================================================
    @Override
    public SongItem getCurrentSong() {
        return musicService != null ? musicService.getCurrentSong() : null;
    }

    @Override
    public boolean isFavoriteSong() {
        SongItem cur = getCurrentSong();
        return cur != null && themeManager.isFavorite(cur.id);
    }

    @Override
    public void onToggleFavorite() {
        SongItem cur = getCurrentSong();
        if (cur != null) {
            boolean fav = themeManager.toggleFavorite(cur.id);
            fpFavoriteHeartBtn.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120)
                    .withEndAction(() -> fpFavoriteHeartBtn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start())
                    .start();
            syncPlayerUI();
            Toast.makeText(this, fav ? "❤️ Liked Song!" : "Removed from Liked", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean isSuperLoud200BoostActive() {
        return isSuperLoud200Boost;
    }

    @Override
    public void onToggleSuperLoud200Boost() {
        isSuperLoud200Boost = !isSuperLoud200Boost;
        currentDisplayVolumePercent = isSuperLoud200Boost ? 200 : 100;
        applyVolumePercentToHardware(currentDisplayVolumePercent);
        if (volumeBannerView != null) {
            volumeBannerView.showVolume(currentDisplayVolumePercent);
        }
        syncPlayerUI();
        Toast.makeText(this, isSuperLoud200Boost ? "200% Super Loud Boost ON" : "Normal 100% Volume", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean is8DSpatialAudioActive() {
        return musicService != null && musicService.is8DSpatialAudioEnabled();
    }

    @Override
    public void onToggle8DSpatialAudio() {
        if (musicService != null) {
            boolean next = !musicService.is8DSpatialAudioEnabled();
            musicService.set8DSpatialAudioEnabled(next);
            syncPlayerUI();
            Toast.makeText(this, next
                    ? "🎧 8D Spatial Surround ON (360° Rotating Audio!)"
                    : "8D Spatial Surround OFF", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public String getFrequencyTuningLabel() {
        return musicService != null ? musicService.getFrequencyTuningLabel() : "440Hz Studio";
    }

    @Override
    public void onCycleFrequencyTuning() {
        if (musicService != null) {
            musicService.cycleFrequencyTuningMode();
            String label = musicService.getFrequencyTuningLabel();
            syncPlayerUI();
            Toast.makeText(this, "🎼 Studio Tuning: " + label, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public float getPlaybackSpeed() {
        return musicService != null ? musicService.getPlaybackSpeed() : 1.0f;
    }

    @Override
    public void onChangePlaybackSpeed(float newSpeed) {
        if (musicService != null) {
            musicService.setPlaybackSpeed(newSpeed);
            syncPlayerUI();
        }
    }

    @Override
    public int getLoopMode() {
        if (musicService == null) return 2;
        if (musicService.isShuffle()) return 0;
        if (musicService.getRepeatMode() == 1) return 1;
        return 2;
    }

    @Override
    public void onChangeLoopMode(int newMode) {
        if (musicService == null) return;
        if (newMode == 0) {
            musicService.setShuffle(true);
            musicService.setRepeatMode(0);
        } else if (newMode == 1) {
            musicService.setShuffle(false);
            musicService.setRepeatMode(1);
        } else {
            musicService.setShuffle(false);
            musicService.setRepeatMode(0);
        }
        syncPlayerUI();
    }

    @Override
    public void onTriggerAbRepeat() {
        if (musicService != null) {
            abRepeatLabel = musicService.cycleABRepeat();
            refreshBottomSwappableButtons();
            Toast.makeText(this, "A-B Loop: " + abRepeatLabel, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public String getAbRepeatStatusText() {
        return abRepeatLabel;
    }

    @Override
    public void onOpenEqualizerAndSoundLab() {
        if (musicService != null) {
            new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
        }
    }

    @Override
    public void onOpenSleepTimer() {
        onOpenEqualizerAndSoundLab();
    }

    @Override
    public void onToggleVinylDiscOrBox() {
        boolean next = !themeManager.isVinylDiscMode();
        themeManager.setVinylDiscMode(next);
        fpAlbumArtDiscView.setVinylDiscMode(next);
        Toast.makeText(this, next ? "Rotating Vinyl Disc + Laser Tonearm View" : "3D Holographic Square Cover View", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onToggleWaveformOrLine() {
        boolean next = !themeManager.isWaveformMode();
        themeManager.setWaveformMode(next);
        fpWaveformSeekBar.setWaveformStyle(next);
        Toast.makeText(this, next ? "60FPS Liquid Mirror Waveform Bars" : "Laser Straight Line Seekbar", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPickCustomPhotoForSong(SongItem song) {
        if (song != null) {
            songIdForPhotoPick = song.id;
            Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(pick, 902);
        }
    }

    @Override
    public void onSwapBottomControlsOrder() {
        themeManager.rotateBottomControlOrder();
        refreshBottomSwappableButtons();
        Toast.makeText(this, "Studio Controls Order Swapped!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDeleteSongPermanently(SongItem song) {
        if (song != null) {
            SongAdapter.showPermanentDeleteDialog(this, song, this::loadAllAudioAndVideoSongs);
        }
    }

    @Override
    public void onOpenVipPlansOrWatchAd() {
        showThemePickerDialog();
    }

    // =========================================================================
    // Media Loading & Top/Bottom Tab Navigation
    // =========================================================================
    private void checkPermissionsAndLoadMedia() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_AUDIO);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_VIDEO);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), 2026);
        } else {
            loadAllAudioAndVideoSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        loadAllAudioAndVideoSongs();
    }

    private void loadAllAudioAndVideoSongs() {
        allSongs.clear();
        ContentResolver resolver = getContentResolver();
        queryAudioMedia(resolver);
        queryVideoAsAudioMedia(resolver);
        selectTopCategoryTab(currentTopTab);
    }

    private void queryAudioMedia(ContentResolver resolver) {
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.ALBUM_ID
        };
        try (Cursor c = resolver.query(uri, proj, null, null, MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            if (c != null && c.moveToFirst()) {
                do {
                    long id = c.getLong(0);
                    String title = c.getString(1);
                    String artist = c.getString(2);
                    String album = c.getString(3);
                    String path = c.getString(4);
                    long duration = c.getLong(5);
                    long size = c.getLong(6);
                    long dateAdded = c.getLong(7);
                    long albumId = c.getLong(8);

                    if (duration < 2500) continue;
                    String folder = "Music";
                    if (path != null) {
                        File parent = new File(path).getParentFile();
                        if (parent != null) folder = parent.getName();
                    }
                    String itemUri = Uri.withAppendedPath(uri, String.valueOf(id)).toString();
                    SongItem item = new SongItem(id, title, artist, album, path, itemUri, albumId, duration, size, dateAdded, false, folder);
                    item.customArtUri = themeManager.getSongCustomPhoto(id);
                    allSongs.add(item);
                } while (c.moveToNext());
            }
        } catch (Exception ignored) {
        }
    }

    private void queryVideoAsAudioMedia(ContentResolver resolver) {
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.ARTIST,
                MediaStore.Video.Media.ALBUM,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
        };
        try (Cursor c = resolver.query(uri, proj, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (c != null && c.moveToFirst()) {
                do {
                    long id = c.getLong(0);
                    String title = c.getString(1);
                    String artist = c.getString(2);
                    String album = c.getString(3);
                    String path = c.getString(4);
                    long duration = c.getLong(5);
                    long size = c.getLong(6);
                    long dateAdded = c.getLong(7);

                    if (duration < 3000) continue;
                    String folder = "Videos";
                    if (path != null) {
                        File parent = new File(path).getParentFile();
                        if (parent != null) folder = parent.getName();
                    }
                    String itemUri = Uri.withAppendedPath(uri, String.valueOf(id)).toString();
                    SongItem item = new SongItem(id, title, artist, album, path, itemUri, id, duration, size, dateAdded, true, folder);
                    item.customArtUri = themeManager.getSongCustomPhoto(id);
                    allSongs.add(item);
                } while (c.moveToNext());
            }
        } catch (Exception ignored) {
        }
    }

    private void selectTopCategoryTab(int tabIndex) {
        this.currentTopTab = tabIndex;
        this.currentBottomTab = 0;
        topHeaderBar.setVisibility(View.VISIBLE);

        navSongsBtn.setTextColor(Color.WHITE);
        navSearchBtn.setTextColor(Color.parseColor("#94A3B8"));
        navSettingsBtn.setTextColor(Color.parseColor("#94A3B8"));

        styleTopCategoryPills();

        FrameLayout container = findViewById(R.id.mainContentContainer);
        container.removeAllViews();

        // Tab 0 = "For you" (Matches Screenshot 1000069379.jpg)
        if (tabIndex == 0) {
            totalSongsCountText.setText(allSongs.size() + " Songs");
            container.addView(SettingsSectionHelper.buildMusoForYouView(this, allSongs, themeManager, this));
            return;
        }

        // Tab 2 = "Playlist" (Matches Screenshot 1000069372.jpg)
        if (tabIndex == 2) {
            totalSongsCountText.setText("Playlists");
            container.addView(SettingsSectionHelper.buildMusoPlaylistView(this, allSongs, themeManager, this));
            return;
        }

        displayedSongs.clear();
        if (tabIndex == 1) {
            // "Songs" Tab (Matches Screenshot 1000069378.jpg)
            for (SongItem s : allSongs) if (!s.isVideoAudio) displayedSongs.add(s);
        } else if (tabIndex == 3) {
            // "Video" (Video-to-Audio) Tab
            for (SongItem s : allSongs) if (s.isVideoAudio) displayedSongs.add(s);
        } else if (tabIndex == 4) {
            // "Folders" Tab
            List<SongItem> copy = new ArrayList<>(allSongs);
            Collections.sort(copy, (a, b) -> a.folderName.compareToIgnoreCase(b.folderName));
            displayedSongs.addAll(copy);
        } else if (tabIndex == 5) {
            // "Favorites" Tab
            for (SongItem s : allSongs) if (themeManager.isFavorite(s.id)) displayedSongs.add(s);
        }

        totalSongsCountText.setText(displayedSongs.size() + " Songs");
        refreshMainListAdapter();
        container.addView(mainRecyclerView);
    }

    private void styleTopCategoryPills() {
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 6; i++) {
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(18f * d);
            if (i == currentTopTab) {
                pill.setColor(Color.WHITE);
                topTabPills[i].setTextColor(Color.parseColor("#0A0A10"));
            } else {
                pill.setColor(Color.parseColor("#24FFFFFF"));
                pill.setStroke(Math.max(1, (int) (1.1f * d)), Color.WHITE);
                topTabPills[i].setTextColor(Color.WHITE);
            }
            topTabPills[i].setBackground(pill);
        }
    }

    private void refreshMainListAdapter() {
        mainAdapter = new SongAdapter(this, displayedSongs, themeManager, false, new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(int position) {
                if (musicService != null && position >= 0 && position < displayedSongs.size()) {
                    musicService.setQueue(displayedSongs, position);
                    openFullPlayer();
                }
            }
            @Override
            public void onThreeDotClick(SongItem song, int position) {
                showSongRowThreeDotMenu(song, position);
            }
            @Override
            public void onSongHoldTwoSeconds(SongItem song, int position) {
                if (musicService != null && position >= 0 && position < displayedSongs.size()) {
                    musicService.setQueue(displayedSongs, position);
                    floatingGuddaView.setPlayingState(true);
                    floatingGuddaView.triggerGravityJump();
                    syncPlayerUI();
                    Toast.makeText(MainActivity.this, "🐶 2s Hold: Playing " + song.title, Toast.LENGTH_SHORT).show();
                }
            }
        });
        if (musicService != null && musicService.getCurrentSong() != null) {
            mainAdapter.setActiveSongId(musicService.getCurrentSong().id);
        }
        mainRecyclerView.setAdapter(mainAdapter);
    }

    private void showBottomSection(int sectionIndex) {
        if (sectionIndex == 0) {
            selectTopCategoryTab(currentTopTab);
            applyActiveThemeEverywhere();
            return;
        }

        this.currentBottomTab = sectionIndex;
        FrameLayout container = findViewById(R.id.mainContentContainer);
        container.removeAllViews();

        topHeaderBar.setVisibility(View.GONE);

        navSongsBtn.setTextColor(Color.parseColor("#94A3B8"));
        navSearchBtn.setTextColor(sectionIndex == 1 ? Color.WHITE : Color.parseColor("#94A3B8"));
        navSettingsBtn.setTextColor(sectionIndex == 2 ? Color.WHITE : Color.parseColor("#94A3B8"));

        View targetView;
        if (sectionIndex == 1) {
            targetView = SettingsSectionHelper.buildModernSearchScreen(this, allSongs, themeManager, this);
        } else {
            targetView = SettingsSectionHelper.buildModernSettingsScreen(
                    this, themeManager, pocketShakeManager, currentDisplayVolumePercent, this);
        }

        targetView.setAlpha(0f);
        targetView.setTranslationY(24f);
        container.addView(targetView);
        targetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        applyActiveThemeEverywhere();
    }

    // --- SettingsSectionHelper.SettingsCallbacks Implementation ---
    @Override public void onOpenThemePicker() { showThemePickerDialog(); }
    @Override public void onPickGalleryThemePhoto() {
        songIdForPhotoPick = -1;
        showThemePickerDialog();
    }
    @Override public void onResetThemePhoto() {
        themeManager.setCustomBgUri("");
        applyActiveThemeEverywhere();
        showBottomSection(2);
    }
    @Override public void onOpenEqualizerLab() { onOpenEqualizerAndSoundLab(); }
    @Override public void onTogglePocketShake() {
        boolean next = !pocketShakeManager.isEnabled();
        pocketShakeManager.setEnabled(next);
        showBottomSection(2);
    }
    @Override public void onToggleDiscMode() {
        onToggleVinylDiscOrBox();
        showBottomSection(2);
    }
    @Override public void onToggleWaveformMode() {
        onToggleWaveformOrLine();
        showBottomSection(2);
    }
    @Override public void onToggleDjSlowFade() {
        boolean next = !themeManager.isDjSlowFadeEnabled();
        themeManager.setDjSlowFadeEnabled(next);
        if (musicService != null) musicService.setDjSlowFadeEnabled(next);
        showBottomSection(2);
    }
    @Override public void onCustomizeHeader() { showCustomizeHeaderDialog(); }
    @Override public void onVolumeChangedFromSettings(int newPercent) {
        currentDisplayVolumePercent = newPercent;
        applyVolumePercentToHardware(newPercent);
        if (volumeBannerView != null) volumeBannerView.showVolume(newPercent);
    }
    @Override public void onPlaySearchedSong(List<SongItem> list, int position) {
        if (musicService != null) {
            musicService.setQueue(list, position);
            openFullPlayer();
        }
    }
    @Override public void onOpenSongOptions(SongItem song) {
        showSongRowThreeDotMenu(song, 0);
    }
    @Override public void onPuppyOrLanguageUpdated() {
        floatingGuddaView.refreshFromThemeManager();
        floatingGuddaView.triggerGravityJump();
        applyLanguageLabels();
        applyActiveThemeEverywhere();
        if (currentBottomTab == 2) {
            showBottomSection(2);
        }
    }
    @Override public List<SongItem> getAllSongsForConverter() {
        return allSongs;
    }
    @Override public void onPlayTrimmedSong(SongItem song, int startSec, int endSec) {
        if (musicService != null && song != null) {
            themeManager.setSongTrimSeconds(song.id, startSec, endSec);
            int idx = allSongs.indexOf(song);
            if (idx >= 0) {
                musicService.setQueue(allSongs, idx);
            } else {
                List<SongItem> single = new ArrayList<>();
                single.add(song);
                musicService.setQueue(single, 0);
            }
            openFullPlayer();
            uiHandler.postDelayed(() -> {
                if (musicService != null && startSec > 0) {
                    musicService.seekTo(startSec * 1000);
                }
            }, 350);
        }
    }
    @Override public void onToggleMusicPetVisibility(boolean visible) {
        isMusicPetVisible = visible;
        floatingGuddaView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    @Override public boolean isMusicPetVisible() { return isMusicPetVisible; }
    @Override public String getPlayingTimeText() {
        return musicService != null ? musicService.getFormattedPlayingTime() : "0h 4min ▶";
    }
    @Override public void onRefreshAndFixSongs() {
        loadAllAudioAndVideoSongs();
        Toast.makeText(this, "🔧 All songs & album art repaired successfully!", Toast.LENGTH_SHORT).show();
    }

    private void openFullPlayer() {
        if (fullPlayerOverlay.getVisibility() != View.VISIBLE) {
            fullPlayerOverlay.setVisibility(View.VISIBLE);
            fullPlayerOverlay.setAlpha(0f);
            fullPlayerOverlay.setTranslationY(120f);
            fullPlayerOverlay.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        fpAlbumArtDiscView.setVinylDiscMode(themeManager.isVinylDiscMode());
        fpWaveformSeekBar.setWaveformStyle(themeManager.isWaveformMode());
        syncPlayerUI();
    }

    private void closeFullPlayerSmoothly() {
        fullPlayerOverlay.animate()
                .alpha(0f)
                .translationY(120f)
                .setDuration(190)
                .withEndAction(() -> {
                    fullPlayerOverlay.setVisibility(View.GONE);
                    fullPlayerOverlay.setAlpha(1f);
                    fullPlayerOverlay.setTranslationY(0f);
                })
                .start();
    }

    private void openRightSlideQueue() {
        if (musicService == null) return;
        float d = getResources().getDisplayMetrics().density;
        List<SongItem> q = musicService.getQueueList();
        int curIdx = musicService.getCurrentIndex();
        queueHeaderTitle.setText("Playing Queue (" + (q.isEmpty() ? 0 : curIdx + 1) + "/" + q.size() + ")");

        GradientDrawable qBg = new GradientDrawable();
        qBg.setColor(Color.parseColor("#F50A0A10"));
        qBg.setCornerRadius(24f * d);
        qBg.setStroke(Math.max(2, (int) (1.8f * d)), Color.WHITE);
        queueSlidePanel.setBackground(qBg);

        SongAdapter qAdapter = new SongAdapter(this, q, themeManager, true, new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(int pos) {
                musicService.playSongAtQueueIndex(pos);
                syncPlayerUI();
            }
            @Override
            public void onThreeDotClick(SongItem song, int pos) {}
        });
        if (musicService.getCurrentSong() != null) {
            qAdapter.setActiveSongId(musicService.getCurrentSong().id);
        }
        queueRecyclerView.setAdapter(qAdapter);
        queueRecyclerView.scrollToPosition(Math.max(0, curIdx - 2));

        queueBackdropOverlay.setVisibility(View.VISIBLE);
        queueSlidePanel.setTranslationX(320f);
        queueSlidePanel.animate().translationX(0f).setDuration(220).start();
    }

    private void closeRightSlideQueue() {
        queueSlidePanel.animate().translationX(320f).setDuration(180)
                .withEndAction(() -> queueBackdropOverlay.setVisibility(View.GONE)).start();
    }

    private void openLyricsPanel(boolean autoSearchIfEmpty) {
        fpLyricsContainer.setVisibility(View.VISIBLE);
        fpAlbumArtDiscView.setLyricsPageSelected(true);
        if (musicService != null && musicService.getCurrentSong() != null) {
            SongItem s = musicService.getCurrentSong();
            fpLyricsFavBtn.setText(themeManager.isFavorite(s.id) ? "❤️ Liked" : "♡ Like");
            String saved = themeManager.getSavedLyrics(s.id);
            if (saved != null && !saved.isEmpty()) {
                fpLyricsText.setText(saved);
            } else if (autoSearchIfEmpty) {
                fetchOnlineLyricsForCurrentSong();
            } else {
                fpLyricsText.setText("🎵 " + s.title + "\n" + s.artist + "\n\nTap 'Sync Online' below to load studio lyrics.");
            }
        }
    }

    private void fetchOnlineLyricsForCurrentSong() {
        if (musicService == null || musicService.getCurrentSong() == null) return;
        final SongItem song = musicService.getCurrentSong();
        fpLyricsText.setText("✨ Syncing studio lyrics online for:\n" + song.title + "...");

        new Thread(() -> {
            String resultLyrics = null;
            try {
                String cleanTitle = song.title.replaceAll("[_\\-+]+", " ").replaceAll("\\(.*?\\)|\\[.*?\\]", "").trim();
                String urlStr = "https://lrclib.net/api/search?q=" + URLEncoder.encode(cleanTitle, "UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    org.json.JSONArray arr = new org.json.JSONArray(sb.toString());
                    if (arr.length() > 0) {
                        JSONObject first = arr.getJSONObject(0);
                        resultLyrics = first.optString("plainLyrics", "");
                    }
                }
            } catch (Exception ignored) {
            }

            final String finalLyrics = (resultLyrics != null && !resultLyrics.trim().isEmpty())
                    ? resultLyrics
                    : "🎵 " + song.title + "\n" + song.artist + "\n\n(No online lyrics found for this track)";

            if (resultLyrics != null && !resultLyrics.trim().isEmpty()) {
                themeManager.saveCustomLyrics(song.id, resultLyrics);
            }
            runOnUiThread(() -> fpLyricsText.setText(finalLyrics));
        }).start();
    }

    private void refreshBottomSwappableButtons() {
        float d = getResources().getDisplayMetrics().density;
        int[] order = themeManager.getBottomControlOrder();
        float spd = musicService != null ? musicService.getPlaybackSpeed() : 1.0f;
        int remMins = musicService != null ? musicService.getRemainingSleepMinutes() : 0;

        String timerLabel = remMins > 0 ? remMins + "m" : "Timer";
        String speedLabel = String.format(Locale.US, "%.1fx", spd);
        String eqLabel = is8DSpatialAudioActive() ? "8D ON" : "Equali";

        String[] labels = {timerLabel, abRepeatLabel, eqLabel, speedLabel};
        String[] iconTypes = {"TIMER", "AB_REPEAT", "EQUALIZER", "SPEED_GAUGE"};

        int iconPx = (int) (14 * d);
        for (int slot = 0; slot < 4; slot++) {
            final int currentSlot = slot;
            final int actionType = order[currentSlot];

            AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable vecIcon =
                    new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable(iconTypes[actionType], d * 0.75f);
            vecIcon.setBounds(0, 0, iconPx, iconPx);

            bottomSlotBtns[currentSlot].setCompoundDrawables(vecIcon, null, null, null);
            bottomSlotBtns[currentSlot].setCompoundDrawablePadding((int) (4 * d));
            bottomSlotBtns[currentSlot].setText(labels[actionType]);
            bottomSlotBtns[currentSlot].setTextColor(Color.WHITE);
            bottomSlotBtns[currentSlot].setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));

            AudioThreeDotStudioHelper.attachBouncySpring(bottomSlotBtns[currentSlot], () -> {
                if (musicService == null) return;
                if (actionType == 1) {
                    onTriggerAbRepeat();
                } else {
                    onOpenEqualizerAndSoundLab();
                }
            });
        }
    }

    /**
     * Opens the Complete 60-Theme Store + User Custom Gallery Theme with 3-Ad Lock & Paid VIP Unlock!
     */
    private void showThemePickerDialog() {
        themeManager.show60ThemeStoreDialog(this, 901, () -> {
            applyActiveThemeEverywhere();
            if (currentBottomTab == 2) showBottomSection(2);
        });
    }

    private void showCustomizeHeaderDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 20, 32, 10);

        final EditText nameIn = new EditText(this);
        nameIn.setText(themeManager.getHeaderTitle());
        box.addView(nameIn);

        final EditText starIn = new EditText(this);
        starIn.setText(themeManager.getHeaderStar());
        box.addView(starIn);

        new AlertDialog.Builder(this)
                .setTitle("Customize App Name & Star Badge")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    themeManager.setHeaderTitle(nameIn.getText().toString().trim());
                    themeManager.setHeaderStar(starIn.getText().toString().trim());
                    applyActiveThemeEverywhere();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyActiveThemeEverywhere() {
        float d = getResources().getDisplayMetrics().density;
        ThemeManager.ThemePreset t = themeManager.getCurrentTheme();

        rootLayout.setBackgroundColor(t.bgPrimary);
        fullPlayerOverlay.setBackgroundColor(t.bgPrimary);

        // Programmatic Muso Sunset-Pink-Purple Gradient on Top Header Bars (No External XML Drawable Required!)
        GradientDrawable musoTopGradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor("#E05A2B"),
                        Color.parseColor("#D92682"),
                        Color.parseColor("#4F26A6"),
                        t.bgPrimary
                });
        topBrandBar.setBackground(musoTopGradient);

        GradientDrawable musoTabsGradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.parseColor("#664F26A6"),
                        t.bgPrimary
                });
        topHeaderBar.setBackground(musoTabsGradient);

        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(Color.parseColor("#101724"));
        navBg.setCornerRadius(22f * d);
        navBg.setStroke(Math.max(1, (int) (1.2f * d)), Color.parseColor("#44FFFFFF"));
        bottomNavBar.setBackground(navBg);

        appTitleText.setText(themeManager.getHeaderTitle() + " " + themeManager.getHeaderStar());
        appTitleText.setTextColor(Color.WHITE);

        topSortMenuBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        topSortMenuBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        GradientDrawable miniBg = new GradientDrawable();
        miniBg.setColor(Color.parseColor("#121C2E"));
        miniBg.setCornerRadius(20f * d);
        miniBg.setStroke(Math.max(2, (int) (1.6f * d)), Color.WHITE);
        miniPlayerBar.setBackground(miniBg);

        miniPrevBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("PREV", d));
        miniPrevBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        miniNextBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("NEXT", d));
        miniNextBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        fpCloseBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("DOWN_ARROW", d));
        fpCloseBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        fpThreeDotBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        fpThreeDotBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        fpFavoriteHeartBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
        fpQuickLyricsBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));

        GradientDrawable lyricsCardBg = new GradientDrawable();
        lyricsCardBg.setColor(Color.parseColor("#EE090E18"));
        lyricsCardBg.setCornerRadius(26f * d);
        lyricsCardBg.setStroke(Math.max(2, (int) (1.6f * d)), Color.WHITE);
        fpLyricsContainer.setBackground(lyricsCardBg);

        GradientDrawable syncBtnBg = new GradientDrawable();
        syncBtnBg.setColor(Color.WHITE);
        syncBtnBg.setCornerRadius(16f * d);
        fpFindLyricsOnlineBtn.setBackground(syncBtnBg);
        fpBackToArtBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));

        fpPrevBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("PREV", d));
        fpPrevBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        GradientDrawable bigPlayCircle = new GradientDrawable();
        bigPlayCircle.setShape(GradientDrawable.OVAL);
        bigPlayCircle.setColor(Color.parseColor("#38FFFFFF"));
        bigPlayCircle.setStroke(Math.max(2, (int) (2.2f * d)), Color.WHITE);
        fpPlayPauseBtn.setBackground(bigPlayCircle);

        fpNextBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("NEXT", d));
        fpNextBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        fpPocketShakeBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));
        fpPocketShakeBtn.setTextColor(Color.WHITE);

        fpQueueBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));
        fpQueueBtn.setTextColor(Color.WHITE);

        fpBottomThreeDotBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        fpBottomThreeDotBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        queueCloseCutBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        queueCloseCutBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        miniTitle.setTextColor(Color.WHITE);
        miniArtist.setTextColor(Color.WHITE);
        fpSongTitle.setTextColor(Color.WHITE);
        fpSongArtist.setTextColor(Color.WHITE);

        floatingGuddaView.setAccentColor(Color.WHITE);
        fpAlbumArtDiscView.setAccentColor(Color.WHITE);
        fpWaveformSeekBar.setAccentColor(Color.WHITE);

        styleTopCategoryPills();

        if (themeManager.hasCustomBgPhoto()) {
            try {
                customThemeBgImage.setVisibility(View.VISIBLE);
                customThemeBgImage.setImageURI(Uri.parse(themeManager.getCustomBgUri()));
            } catch (Exception e) {
                customThemeBgImage.setVisibility(View.GONE);
            }
        } else {
            customThemeBgImage.setVisibility(View.GONE);
        }

        if (mainAdapter != null) mainAdapter.notifyDataSetChanged();
    }

    private void updatePocketShakeButtonUI() {
        boolean on = pocketShakeManager != null && pocketShakeManager.isEnabled();
        fpPocketShakeBtn.setText(on ? "📳 Shake: ON" : "📳 Shake: OFF");
        fpPocketShakeBtn.setTextColor(Color.WHITE);
    }

    private void syncPlayerUI() {
        float d = getResources().getDisplayMetrics().density;
        if (musicService == null) return;
        final SongItem cur = musicService.getCurrentSong();
        boolean playing = musicService.isPlaying();

        floatingGuddaView.setPlayingState(playing);
        fpAlbumArtDiscView.setPlaying(playing);
        fpWaveformSeekBar.setPlaying(playing);

        String playIconType = playing ? "PAUSE" : "PLAY";
        miniPlayPauseBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable(playIconType, d));
        miniPlayPauseBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
        fpPlayPauseBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable(playIconType, d));

        boolean shufOn = musicService.isShuffle();
        if (shufOn) {
            fpShuffleBtn.setBackground(AudioThreeDotStudioHelper.makeActiveSolidWhiteCircle(d));
            fpShuffleBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("SHUFFLE_ON_DARK", d));
        } else {
            fpShuffleBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
            fpShuffleBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("SHUFFLE", d));
        }

        int rep = musicService.getRepeatMode();
        if (rep == 1) {
            fpRepeatBtn.setBackground(AudioThreeDotStudioHelper.makeActiveSolidWhiteCircle(d));
            fpRepeatBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("REPEAT_ONE_DARK", d));
        } else if (rep == 0) {
            fpRepeatBtn.setBackground(AudioThreeDotStudioHelper.makeActiveSolidWhiteCircle(d));
            fpRepeatBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("REPEAT_ALL_DARK", d));
        } else {
            fpRepeatBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
            fpRepeatBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("REPEAT_OFF", d));
        }

        boolean fav = isFavoriteSong();
        fpFavoriteHeartBtn.setImageDrawable(
                new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable(fav ? "HEART_ON" : "HEART_OFF", d));

        List<SongItem> q = musicService.getQueueList();
        fpQueueBtn.setText("🎵 Queue (" + (q.isEmpty() ? 0 : musicService.getCurrentIndex() + 1) + "/" + q.size() + ")");

        updatePocketShakeButtonUI();
        refreshBottomSwappableButtons();

        if (cur != null) {
            miniTitle.setText(cur.title);
            String modeBadge = "";
            if (is8DSpatialAudioActive()) modeBadge += " • 8D Surround";
            if (musicService.getFrequencyTuningMode() != 0) modeBadge += " • " + musicService.getFrequencyTuningLabel();
            miniArtist.setText(cur.artist + modeBadge);
            fpSongTitle.setText(cur.title);
            fpSongArtist.setText(cur.artist + modeBadge);
            if (mainAdapter != null) mainAdapter.setActiveSongId(cur.id);

            MediaArtLoader.loadSongArtInto(this, cur, themeManager, miniThumbnail);
            new Thread(() -> {
                Bitmap extracted = MediaArtLoader.extractBitmapSync(
                        MainActivity.this, cur, themeManager.getSongCustomPhoto(cur.id));
                final Bitmap finalBmp = (extracted != null)
                        ? extracted
                        : MediaArtLoader.createRealisticStudioCoverBitmap(cur.title);
                runOnUiThread(() -> fpAlbumArtDiscView.setSongBitmap(finalBmp));
            }).start();
        } else {
            miniThumbnail.setImageBitmap(MediaArtLoader.createRealisticStudioCoverBitmap("Hybrid Studio"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == 901) {
                themeManager.setCustomBgUri(uri.toString());
                applyActiveThemeEverywhere();
                Toast.makeText(this, "Custom Theme Wallpaper Applied!", Toast.LENGTH_SHORT).show();
            } else if (requestCode == 902 && songIdForPhotoPick != -1) {
                themeManager.setSongCustomPhoto(songIdForPhotoPick, uri.toString());
                syncPlayerUI();
                if (mainAdapter != null) mainAdapter.notifyDataSetChanged();
                Toast.makeText(this, "Song Photo Permanently Saved!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String formatMs(int ms) {
        int totalSec = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60);
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(progressTicker);
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        if (pocketShakeManager != null) {
            pocketShakeManager.setEnabled(false);
        }
        super.onDestroy();
    }
}
