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

    private int currentTopTab = 0; // 0=Songs, 1=Folders, 2=Albums, 3=Artists, 4=VideoAudio, 5=Favorites
    private int currentBottomTab = 0; // 0=Songs, 1=Search, 2=Settings(MI)
    private long songIdForPhotoPick = -1;
    private int currentDisplayVolumePercent = 80; // 0% to 200%
    private boolean isSuperLoud200Boost = false;
    private String abRepeatLabel = "AB Repeat";

    // Main Library Views
    private View rootLayout, topHeaderBar, subShuffleHeaderRow, miniPlayerBar, bottomNavBar, fullPlayerOverlay;
    private ImageView customThemeBgImage, miniThumbnail, profileThemeButton, topSortMenuBtn;
    private ImageView miniPrevBtn, miniPlayPauseBtn, miniNextBtn, miniThreeDotBtn;
    private TextView appTitleText, playShuffleAllBtn, randomPlayBtn, totalSongsCountText;
    private TextView miniTitle, miniArtist;
    private final TextView[] topTabPills = new TextView[6];
    private DancingPuppyView headerPuppyView, fpPuppyView, floatingGuddaView;
    private VolumeBannerView volumeBannerView;

    // Full Studio Player Views
    private AlbumArtDiscView fpAlbumArtDiscView;
    private View fpLyricsContainer, queueBackdropOverlay, queueSlidePanel;
    private ImageView fpCloseBtn, fpTopEqualizerBtn, fpThreeDotBtn, fpFavoriteHeartBtn;
    private ImageView fpShuffleBtn, fpPrevBtn, fpPlayPauseBtn, fpNextBtn, fpRepeatBtn;
    private ImageView fpBottomThreeDotBtn, queueCloseCutBtn;
    private TextView fpQuickLyricsBtn, fpLyricsText, fpSongTitle, fpSongArtist, fpCurrentTime, fpTotalTime;
    private WaveformSeekBarView fpWaveformSeekBar;
    private TextView fpPocketShakeBtn, fpQueueBtn, queueHeaderTitle;
    private Button fpLyricsFavBtn;
    private final TextView[] bottomSlotBtns = new TextView[4];
    private RecyclerView queueRecyclerView;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private GestureDetector tabSwipeDetector;
    private GestureDetector miniPlayerSwipeDetector;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (musicService != null && isBound) {
                int pos = musicService.getCurrentPosition();
                int dur = musicService.getDuration();
                boolean playing = musicService.isPlaying();

                fpWaveformSeekBar.setProgressFraction((float) pos / Math.max(1, dur));
                fpWaveformSeekBar.setABMarkers(musicService.getFractionA(), musicService.getFractionB());
                fpWaveformSeekBar.setPlaying(playing);

                headerPuppyView.setPlayingState(playing);
                fpPuppyView.setPlayingState(playing);
                floatingGuddaView.setPlayingState(playing);
                fpAlbumArtDiscView.setPlaying(playing);

                fpCurrentTime.setText(formatMs(pos));
                fpTotalTime.setText(formatMs(dur));
            }
            uiHandler.postDelayed(this, 400);
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

        // Pocket Shake Default OFF when app opens
        pocketShakeManager = new PocketShakeManager(this, () -> {
            if (musicService != null) {
                musicService.playNext();
                Toast.makeText(this, "Double-Shake: Next Song Played!", Toast.LENGTH_SHORT).show();
            }
        });
        pocketShakeManager.setEnabled(false);

        Intent svcIntent = new Intent(this, MusicPlaybackService.class);
        startService(svcIntent);
        bindService(svcIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        setupInteractions();
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
        topHeaderBar = findViewById(R.id.topHeaderBar);
        subShuffleHeaderRow = findViewById(R.id.subShuffleHeaderRow);
        miniPlayerBar = findViewById(R.id.miniPlayerBar);
        bottomNavBar = findViewById(R.id.bottomNavBar);
        fullPlayerOverlay = findViewById(R.id.fullPlayerOverlay);

        topTabPills[0] = findViewById(R.id.tabAllSongs);
        topTabPills[1] = findViewById(R.id.tabFolders);
        topTabPills[2] = findViewById(R.id.tabPlaylists);
        topTabPills[3] = findViewById(R.id.tabArtists);
        topTabPills[4] = findViewById(R.id.tabVideoAudio);
        topTabPills[5] = findViewById(R.id.tabFavorites);

        profileThemeButton = findViewById(R.id.profileThemeButton);
        topSortMenuBtn = findViewById(R.id.topSortMenuBtn);
        playShuffleAllBtn = findViewById(R.id.playShuffleAllBtn);
        randomPlayBtn = findViewById(R.id.randomPlayBtn);
        totalSongsCountText = findViewById(R.id.totalSongsCountText);
        appTitleText = findViewById(R.id.appTitleText);
        headerPuppyView = findViewById(R.id.headerPuppyView);
        floatingGuddaView = findViewById(R.id.floatingGuddaView);
        volumeBannerView = findViewById(R.id.volumeBannerView);

        miniThumbnail = findViewById(R.id.miniThumbnail);
        miniTitle = findViewById(R.id.miniTitle);
        miniTitle.setSelected(true);
        miniArtist = findViewById(R.id.miniArtist);
        miniPrevBtn = findViewById(R.id.miniPrevBtn);
        miniPlayPauseBtn = findViewById(R.id.miniPlayPauseBtn);
        miniNextBtn = findViewById(R.id.miniNextBtn);
        miniThreeDotBtn = findViewById(R.id.miniThreeDotBtn);

        fpCloseBtn = findViewById(R.id.fpCloseBtn);
        fpTopEqualizerBtn = findViewById(R.id.fpTopEqualizerBtn);
        fpThreeDotBtn = findViewById(R.id.fpThreeDotBtn);
        fpFavoriteHeartBtn = findViewById(R.id.fpFavoriteHeartBtn);
        fpQuickLyricsBtn = findViewById(R.id.fpQuickLyricsBtn);
        fpPuppyView = findViewById(R.id.fpPuppyView);
        fpAlbumArtDiscView = findViewById(R.id.fpAlbumArtDiscView);
        fpLyricsContainer = findViewById(R.id.fpLyricsContainer);
        fpLyricsText = findViewById(R.id.fpLyricsText);
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
            topTabPills[i].setOnClickListener(v -> selectTopCategoryTab(tabIdx));
        }

        profileThemeButton.setOnClickListener(v -> onOpenEqualizerAndSoundLab());
        topSortMenuBtn.setOnClickListener(v -> showAudioStudioSortAndToolsMenu());
        appTitleText.setOnClickListener(v -> showCustomizeHeaderDialog());

        playShuffleAllBtn.setOnClickListener(v -> {
            if (musicService != null && !displayedSongs.isEmpty()) {
                musicService.setShuffle(true);
                musicService.setQueue(displayedSongs, 0);
                openFullPlayer();
            }
        });

        randomPlayBtn.setOnClickListener(v -> {
            if (musicService != null && !displayedSongs.isEmpty()) {
                int randIdx = (int) (Math.random() * displayedSongs.size());
                musicService.setQueue(displayedSongs, randIdx);
                openFullPlayer();
            }
        });

        setupDraggableFloatingGudda();

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

        miniPlayerSwipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                openFullPlayer();
                return true;
            }
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null || musicService == null) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) > 70) {
                    if (dx < 0) musicService.playNext();
                    else musicService.playPrevious();
                    return true;
                }
                return false;
            }
        });
        miniPlayerBar.setOnTouchListener((v, event) -> miniPlayerSwipeDetector.onTouchEvent(event));

        findViewById(R.id.navSongsBtn).setOnClickListener(v -> showBottomSection(0));
        findViewById(R.id.navSearchBtn).setOnClickListener(v -> showBottomSection(1));
        findViewById(R.id.navSettingsBtn).setOnClickListener(v -> showBottomSection(2));

        miniPrevBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playPrevious();
        });
        miniPlayPauseBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playToggle();
        });
        miniNextBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playNext();
        });
        miniThreeDotBtn.setOnClickListener(v ->
                AudioThreeDotStudioHelper.showAudioThreeDotStudioDialog(MainActivity.this, MainActivity.this));

        fpCloseBtn.setOnClickListener(v -> fullPlayerOverlay.setVisibility(View.GONE));
        fpTopEqualizerBtn.setOnClickListener(v -> onOpenEqualizerAndSoundLab());

        // Both Top-Right 3-Dot and Bottom-Right 3-Dot open the 12-Tile Pure White Studio Dialog
        View.OnClickListener openStudioMenu = v ->
                AudioThreeDotStudioHelper.showAudioThreeDotStudioDialog(MainActivity.this, MainActivity.this);
        fpThreeDotBtn.setOnClickListener(openStudioMenu);
        fpBottomThreeDotBtn.setOnClickListener(openStudioMenu);

        fpFavoriteHeartBtn.setOnClickListener(v -> onToggleFavorite());

        fpQueueBtn.setOnClickListener(v -> openRightSlideQueue());
        queueBackdropOverlay.setOnClickListener(v -> closeRightSlideQueue());
        queueCloseCutBtn.setOnClickListener(v -> closeRightSlideQueue());

        // Swipe Left/Right on Album Cover / Vinyl Disc changes song immediately!
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

        fpQuickLyricsBtn.setOnClickListener(v -> openLyricsPanel(true));

        findViewById(R.id.fpBackToArtBtn).setOnClickListener(v -> {
            fpLyricsContainer.setVisibility(View.GONE);
            fpAlbumArtDiscView.setLyricsPageSelected(false);
        });
        findViewById(R.id.fpFindLyricsOnlineBtn).setOnClickListener(v -> fetchOnlineLyricsForCurrentSong());

        fpLyricsFavBtn.setOnClickListener(v -> onToggleFavorite());

        fpPocketShakeBtn.setOnClickListener(v -> {
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

        fpPlayPauseBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playToggle();
        });
        fpPrevBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playPrevious();
        });
        fpNextBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playNext();
        });

        fpShuffleBtn.setOnClickListener(v -> {
            if (musicService != null) {
                boolean next = !musicService.isShuffle();
                musicService.setShuffle(next);
                syncPlayerUI();
                Toast.makeText(this, next ? "Shuffle Play Active" : "Shuffle OFF", Toast.LENGTH_SHORT).show();
            }
        });

        fpRepeatBtn.setOnClickListener(v -> {
            if (musicService != null) {
                int nextMode = (musicService.getRepeatMode() + 1) % 3;
                musicService.setRepeatMode(nextMode);
                syncPlayerUI();
                String[] labels = {"Loop All Songs Active", "Repeat Current Song Active", "Play 1 Song (Repeat OFF)"};
                Toast.makeText(this, labels[nextMode], Toast.LENGTH_SHORT).show();
            }
        });

        refreshBottomSwappableButtons();
    }

    private void setupDraggableFloatingGudda() {
        floatingGuddaView.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY, downRawX, downRawY;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        view.animate().x(event.getRawX() + dX).y(event.getRawY() + dY).setDuration(0).start();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (Math.abs(event.getRawX() - downRawX) < 18 && Math.abs(event.getRawY() - downRawY) < 18) {
                            view.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void showSongRowThreeDotMenu(final SongItem song, final int position) {
        if (song == null) return;
        final boolean isFav = themeManager.isFavorite(song.id);
        String[] options = new String[]{
                "Play Song Now (Hi-Res Studio Player)",
                isFav ? "Remove from Favorites" : "Add to Favorites",
                "5-Band Dolby Equalizer & Bass Boost",
                "Sound & Pitch Lab (Lofi / Slowed + Reverb)",
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
                        musicService.setQueue(displayedSongs, position);
                        openFullPlayer();
                    }
                    break;
                case 1:
                    boolean nowFav = themeManager.toggleFavorite(song.id);
                    Toast.makeText(this, nowFav ? "Added to Favorites" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
                    syncPlayerUI();
                    break;
                case 2:
                case 3:
                    onOpenEqualizerAndSoundLab();
                    break;
                case 4:
                    onPickCustomPhotoForSong(song);
                    break;
                case 5:
                    AudioThreeDotStudioHelper.setSongAsPhoneRingtone(this, song);
                    break;
                case 6:
                    AudioThreeDotStudioHelper.shareSongFile(this, song);
                    break;
                case 7:
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
                case 8:
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
                "Open 5-Band Dolby Equalizer & Bass Boost",
                "Open Sound & Pitch Lab (Lofi / Slowed + Reverb)",
                "Choose 20+ Studio Themes & Gallery Wallpaper"
        };
        SongAdapter.showPureWhiteAudioMenuDialog(this, "Audio Studio Tools & Sort", items, which -> {
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
            } else if (which == 5 || which == 6) {
                onOpenEqualizerAndSoundLab();
            } else if (which == 7) {
                showThemePickerDialog();
            }
        });
    }

    // --- AudioThreeDotStudioHelper.StudioPlayerCallbacks Implementation ---

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
            syncPlayerUI();
            Toast.makeText(this, fav ? "Added to Favorite Songs" : "Removed from Favorite Songs", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Shuffle Play Active", Toast.LENGTH_SHORT).show();
        } else if (newMode == 1) {
            musicService.setShuffle(false);
            musicService.setRepeatMode(1);
            Toast.makeText(this, "Repeat Current Song Active", Toast.LENGTH_SHORT).show();
        } else {
            musicService.setShuffle(false);
            musicService.setRepeatMode(0);
            Toast.makeText(this, "Loop All Songs Active", Toast.LENGTH_SHORT).show();
        }
        syncPlayerUI();
    }

    @Override
    public void onTriggerAbRepeat() {
        if (musicService != null) {
            abRepeatLabel = musicService.cycleABRepeat();
            refreshBottomSwappableButtons();
            Toast.makeText(this, "Repeat Mode: " + abRepeatLabel, Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, next ? "Rotating Vinyl Disc + Top Danda View" : "Studio Square Cover Photo View", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onToggleWaveformOrLine() {
        boolean next = !themeManager.isWaveformMode();
        themeManager.setWaveformMode(next);
        fpWaveformSeekBar.setWaveformStyle(next);
        Toast.makeText(this, next ? "Waveform Bars (Lambe Dande) View" : "Simple Straight Line View", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "Bottom 4 Controls Order Swapped!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDeleteSongPermanently(SongItem song) {
        if (song != null) {
            SongAdapter.showPermanentDeleteDialog(this, song, this::loadAllAudioAndVideoSongs);
        }
    }

    // --- Media Loading & Navigation ---

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
        showBottomSection(0);
        displayedSongs.clear();

        if (tabIndex == 0) {
            for (SongItem s : allSongs) if (!s.isVideoAudio) displayedSongs.add(s);
        } else if (tabIndex == 1) {
            List<SongItem> copy = new ArrayList<>(allSongs);
            Collections.sort(copy, (a, b) -> a.folderName.compareToIgnoreCase(b.folderName));
            displayedSongs.addAll(copy);
        } else if (tabIndex == 2) {
            List<SongItem> copy = new ArrayList<>(allSongs);
            Collections.sort(copy, (a, b) -> a.album.compareToIgnoreCase(b.album));
            displayedSongs.addAll(copy);
        } else if (tabIndex == 3) {
            List<SongItem> copy = new ArrayList<>(allSongs);
            Collections.sort(copy, (a, b) -> a.artist.compareToIgnoreCase(b.artist));
            displayedSongs.addAll(copy);
        } else if (tabIndex == 4) {
            for (SongItem s : allSongs) if (s.isVideoAudio) displayedSongs.add(s);
        } else if (tabIndex == 5) {
            for (SongItem s : allSongs) if (themeManager.isFavorite(s.id)) displayedSongs.add(s);
        }

        totalSongsCountText.setText(displayedSongs.size() + " Songs");
        styleTopCategoryPills();
        refreshMainListAdapter();
    }

    private void styleTopCategoryPills() {
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 6; i++) {
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(20f * d);
            if (i == currentTopTab) {
                pill.setColor(Color.WHITE);
                topTabPills[i].setTextColor(Color.parseColor("#0A0A10"));
            } else {
                pill.setColor(Color.parseColor("#22FFFFFF"));
                pill.setStroke(Math.max(1, (int) (1.2f * d)), Color.WHITE);
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
                    headerPuppyView.setPlayingState(true);
                    floatingGuddaView.setPlayingState(true);
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
        this.currentBottomTab = sectionIndex;
        FrameLayout container = findViewById(R.id.mainContentContainer);
        container.removeAllViews();

        topHeaderBar.setVisibility(sectionIndex == 0 ? View.VISIBLE : View.GONE);
        subShuffleHeaderRow.setVisibility(sectionIndex == 0 ? View.VISIBLE : View.GONE);

        if (sectionIndex == 0) {
            container.addView(mainRecyclerView);
        } else if (sectionIndex == 1) {
            container.addView(SettingsSectionHelper.buildModernSearchScreen(this, allSongs, themeManager, this));
        } else if (sectionIndex == 2) {
            container.addView(SettingsSectionHelper.buildModernSettingsScreen(
                    this, themeManager, pocketShakeManager, currentDisplayVolumePercent, this));
        }
        applyActiveThemeEverywhere();
    }

    // --- SettingsSectionHelper.SettingsCallbacks Implementation ---
    @Override public void onOpenThemePicker() { showThemePickerDialog(); }
    @Override public void onPickGalleryThemePhoto() {
        songIdForPhotoPick = -1;
        Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pick, 901);
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

    private void openFullPlayer() {
        fullPlayerOverlay.setVisibility(View.VISIBLE);
        fpAlbumArtDiscView.setVinylDiscMode(themeManager.isVinylDiscMode());
        fpWaveformSeekBar.setWaveformStyle(themeManager.isWaveformMode());
        syncPlayerUI();
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
            fpLyricsFavBtn.setText(themeManager.isFavorite(s.id) ? "Favorited" : "Favorite");
            String saved = themeManager.getSavedLyrics(s.id);
            if (saved != null && !saved.isEmpty()) {
                fpLyricsText.setText(saved);
            } else if (autoSearchIfEmpty) {
                fetchOnlineLyricsForCurrentSong();
            } else {
                fpLyricsText.setText(s.title + "\n" + s.artist + "\n\nTap 'Find Online' below to search lyrics.");
            }
        }
    }

    private void fetchOnlineLyricsForCurrentSong() {
        if (musicService == null || musicService.getCurrentSong() == null) return;
        final SongItem song = musicService.getCurrentSong();
        fpLyricsText.setText("Searching lyrics online for:\n" + song.title + "...");

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
                    : song.title + "\nSinger: " + song.artist + "\n\n(No online lyrics found for this track)";

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
        String speedLabel = String.format(Locale.US, "%.1fx Speed", spd);
        String[] labels = {"Sleep Timer", abRepeatLabel, "Equalizer", speedLabel};

        for (int slot = 0; slot < 4; slot++) {
            final int currentSlot = slot;
            final int actionType = order[currentSlot];
            bottomSlotBtns[currentSlot].setText(labels[actionType]);
            bottomSlotBtns[currentSlot].setTextColor(Color.WHITE);
            bottomSlotBtns[currentSlot].setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));
            bottomSlotBtns[currentSlot].setOnClickListener(v -> {
                if (musicService == null) return;
                if (actionType == 1) {
                    onTriggerAbRepeat();
                } else {
                    onOpenEqualizerAndSoundLab();
                }
            });
        }
    }

    private void showThemePickerDialog() {
        ThemeManager.ThemePreset[] presets = themeManager.getAllThemes();
        String[] names = new String[presets.length + 1];
        for (int i = 0; i < presets.length; i++) names[i] = presets[i].name;
        names[presets.length] = "Pick Custom Gallery Photo as Theme Wallpaper";

        SongAdapter.showPureWhiteAudioMenuDialog(this, "Choose Studio Theme (20+ Themes)", names, which -> {
            if (which < presets.length) {
                themeManager.setThemeIndex(which);
                applyActiveThemeEverywhere();
            } else {
                onPickGalleryThemePhoto();
            }
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

    /**
     * Applies user's selected theme background while keeping ALL Audio & Full Player icons/controls
     * 100% Pure White (#FFFFFF) and larger realistic studio size!
     */
    private void applyActiveThemeEverywhere() {
        float d = getResources().getDisplayMetrics().density;
        ThemeManager.ThemePreset t = themeManager.getCurrentTheme();

        rootLayout.setBackgroundColor(t.bgPrimary);
        fullPlayerOverlay.setBackgroundColor(t.bgPrimary);
        bottomNavBar.setBackgroundColor(t.bgSurface);

        // Top Header Equalizer & 3-Dot Vector Icons in Pure White
        profileThemeButton.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("EQUALIZER", d));
        profileThemeButton.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        topSortMenuBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        topSortMenuBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        // Shuffle All Pill (Solid Pure White Pill with Dark Text) & Random Pill (Frosted White Pill)
        GradientDrawable shufflePill = new GradientDrawable();
        shufflePill.setColor(Color.WHITE);
        shufflePill.setCornerRadius(20f * d);
        playShuffleAllBtn.setBackground(shufflePill);
        playShuffleAllBtn.setTextColor(Color.parseColor("#0A0A10"));

        randomPlayBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));
        randomPlayBtn.setTextColor(Color.WHITE);

        // Mini-Player Card & Pure-White Vector Icons
        GradientDrawable miniBg = new GradientDrawable();
        miniBg.setColor(Color.parseColor("#121C2E"));
        miniBg.setCornerRadius(22f * d);
        miniBg.setStroke(Math.max(2, (int) (1.8f * d)), Color.WHITE);
        miniPlayerBar.setBackground(miniBg);

        miniPrevBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("PREV", d));
        miniPrevBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        miniNextBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("NEXT", d));
        miniNextBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        miniThreeDotBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        miniThreeDotBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        // Full Player Top Row Pure-White Vector Icons
        fpCloseBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("DOWN_ARROW", d));
        fpCloseBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        fpTopEqualizerBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("EQUALIZER", d));
        fpTopEqualizerBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        fpThreeDotBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        fpThreeDotBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        fpFavoriteHeartBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
        fpQuickLyricsBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));
        fpQuickLyricsBtn.setTextColor(Color.WHITE);

        // Full Player Larger Transport Controls (All Pure White #FFFFFF)
        fpShuffleBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("SHUFFLE", d));
        fpShuffleBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        fpPrevBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("PREV", d));
        fpPrevBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        GradientDrawable bigPlayCircle = new GradientDrawable();
        bigPlayCircle.setShape(GradientDrawable.OVAL);
        bigPlayCircle.setColor(Color.parseColor("#38FFFFFF"));
        bigPlayCircle.setStroke(Math.max(3, (int) (2.4f * d)), Color.WHITE);
        fpPlayPauseBtn.setBackground(bigPlayCircle);

        fpNextBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("NEXT", d));
        fpNextBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        fpRepeatBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        // Bottom Footer Pills & Bottom 3-Dot Button (Replaces Cross 'X')
        fpPocketShakeBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));
        fpPocketShakeBtn.setTextColor(Color.WHITE);

        fpQueueBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));
        fpQueueBtn.setTextColor(Color.WHITE);

        fpBottomThreeDotBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        fpBottomThreeDotBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        queueCloseCutBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable("THREE_DOTS", d));
        queueCloseCutBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));

        appTitleText.setText(themeManager.getHeaderStar());
        appTitleText.setTextColor(Color.WHITE);
        miniTitle.setTextColor(Color.WHITE);
        miniArtist.setTextColor(Color.WHITE);
        fpSongTitle.setTextColor(Color.WHITE);
        fpSongArtist.setTextColor(Color.WHITE);

        headerPuppyView.setAccentColor(Color.WHITE);
        fpPuppyView.setAccentColor(Color.WHITE);
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
        fpPocketShakeBtn.setText(on ? "Pocket Shake: ON" : "Pocket Shake: OFF");
        fpPocketShakeBtn.setTextColor(Color.WHITE);
    }

    private void syncPlayerUI() {
        float d = getResources().getDisplayMetrics().density;
        if (musicService == null) return;
        final SongItem cur = musicService.getCurrentSong();
        boolean playing = musicService.isPlaying();

        headerPuppyView.setPlayingState(playing);
        fpPuppyView.setPlayingState(playing);
        floatingGuddaView.setPlayingState(playing);
        fpAlbumArtDiscView.setPlaying(playing);
        fpWaveformSeekBar.setPlaying(playing);

        String playIconType = playing ? "PAUSE" : "PLAY";
        miniPlayPauseBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable(playIconType, d));
        miniPlayPauseBtn.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
        fpPlayPauseBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable(playIconType, d));

        int rep = musicService.getRepeatMode();
        String repType = (rep == 1) ? "REPEAT_ONE" : "REPEAT_ALL";
        fpRepeatBtn.setImageDrawable(new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable(repType, d));

        boolean fav = isFavoriteSong();
        fpFavoriteHeartBtn.setImageDrawable(
                new AudioThreeDotStudioHelper.PureWhiteAudioVectorDrawable(fav ? "HEART_ON" : "HEART_OFF", d));

        List<SongItem> q = musicService.getQueueList();
        fpQueueBtn.setText("Queue (" + (q.isEmpty() ? 0 : musicService.getCurrentIndex() + 1) + "/" + q.size() + ")");

        updatePocketShakeButtonUI();
        refreshBottomSwappableButtons();

        if (cur != null) {
            miniTitle.setText(cur.title);
            miniArtist.setText(cur.artist);
            fpSongTitle.setText(cur.title);
            fpSongArtist.setText(cur.artist);
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
