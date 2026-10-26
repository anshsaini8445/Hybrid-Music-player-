package com.hybridmusic.player;

import android.Manifest;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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

public class MainActivity extends AppCompatActivity implements SettingsSectionHelper.SettingsCallbacks {

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

    // Main Library Views
    private View rootLayout, topHeaderBar, subShuffleHeaderRow, miniPlayerBar, bottomNavBar, fullPlayerOverlay;
    private ImageView customThemeBgImage, miniThumbnail;
    private TextView appTitleText, playShuffleAllBtn, totalSongsCountText, topSortMenuBtn;
    private TextView miniTitle, miniArtist, miniPlayPauseBtn, miniNextBtn;
    private final TextView[] topTabPills = new TextView[6];
    private DancingPuppyView headerPuppyView, fpPuppyView, floatingGuddaView;
    private VolumeBannerView volumeBannerView;

    // Full Player Views
    private AlbumArtDiscView fpAlbumArtDiscView;
    private View fpLyricsContainer, queueBackdropOverlay, queueSlidePanel;
    private TextView fpCloseBtn, fpShareBtn, fpQuickLyricsBtn, fpLyricsText, fpSongTitle, fpSongArtist, fpCurrentTime, fpTotalTime;
    private WaveformSeekBarView fpWaveformSeekBar;
    private TextView fpPlayPauseBtn, fpPrevBtn, fpNextBtn, fpShuffleBtn, fpRepeatBtn;
    private TextView fpPocketShakeBtn, fpQueueBtn, fpThreeDotBtn, fpBottomThreeDotBtn, queueHeaderTitle, queueCloseCutBtn;
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

                // All 3 Muso Guddas dance in sync whenever music plays!
                headerPuppyView.setPlayingState(playing);
                fpPuppyView.setPlayingState(playing);
                floatingGuddaView.setPlayingState(playing);
                fpAlbumArtDiscView.setPlaying(playing);

                fpCurrentTime.setText(formatMs(pos));
                fpTotalTime.setText(formatMs(dur));
            }
            uiHandler.postDelayed(this, 450);
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
                Toast.makeText(this, "📳 Double-Shake: Next Song Played!", Toast.LENGTH_SHORT).show();
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

    // Intercept Hardware Volume Up / Down Keys to Show Screenshot (1000061267.jpg) Volume Patti!
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

        topSortMenuBtn = findViewById(R.id.topSortMenuBtn);
        playShuffleAllBtn = findViewById(R.id.playShuffleAllBtn);
        totalSongsCountText = findViewById(R.id.totalSongsCountText);
        appTitleText = findViewById(R.id.appTitleText);
        headerPuppyView = findViewById(R.id.headerPuppyView);
        floatingGuddaView = findViewById(R.id.floatingGuddaView);
        volumeBannerView = findViewById(R.id.volumeBannerView);

        miniThumbnail = findViewById(R.id.miniThumbnail);
        miniTitle = findViewById(R.id.miniTitle);
        miniTitle.setSelected(true);
        miniArtist = findViewById(R.id.miniArtist);
        miniPlayPauseBtn = findViewById(R.id.miniPlayPauseBtn);
        miniNextBtn = findViewById(R.id.miniNextBtn);

        fpCloseBtn = findViewById(R.id.fpCloseBtn);
        fpShareBtn = findViewById(R.id.fpShareBtn);
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
        fpThreeDotBtn = findViewById(R.id.fpThreeDotBtn);
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

        topSortMenuBtn.setOnClickListener(v -> showSortMenuDialog());
        appTitleText.setOnClickListener(v -> showCustomizeHeaderDialog());
        findViewById(R.id.profileThemeButton).setOnClickListener(v -> showThemePickerDialog());

        playShuffleAllBtn.setOnClickListener(v -> {
            if (musicService != null && !displayedSongs.isEmpty()) {
                musicService.setShuffle(true);
                int rand = (int) (Math.random() * displayedSongs.size());
                musicService.setQueue(displayedSongs, rand);
                openFullPlayer();
            }
        });

        // Draggable Floating Gudda: Drag anywhere on screen & tap to change dance mode!
        setupDraggableFloatingGudda();

        // Horizontal Swipe on Song List to switch tabs
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

        // Swipe Left / Right on Mini Player Bar to Change Songs Instantly!
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
                    if (dx < 0) {
                        musicService.playNext();
                        Toast.makeText(MainActivity.this, "⏭ Next Song", Toast.LENGTH_SHORT).show();
                    } else {
                        musicService.playPrevious();
                        Toast.makeText(MainActivity.this, "⏮ Previous Song", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
        });
        miniPlayerBar.setOnTouchListener((v, event) -> miniPlayerSwipeDetector.onTouchEvent(event));

        findViewById(R.id.navSongsBtn).setOnClickListener(v -> showBottomSection(0));
        findViewById(R.id.navSearchBtn).setOnClickListener(v -> showBottomSection(1));
        findViewById(R.id.navSettingsBtn).setOnClickListener(v -> showBottomSection(2));

        miniPlayPauseBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playToggle();
        });
        miniNextBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playNext();
        });

        fpCloseBtn.setOnClickListener(v -> fullPlayerOverlay.setVisibility(View.GONE));
        fpShareBtn.setOnClickListener(v -> shareCurrentSong());

        // Both Top-Right 3-Dot and Bottom-Right 3-Dot open the Pure White Audio Player Options
        View.OnClickListener open3DotMenu = v -> {
            SongItem cur = musicService != null ? musicService.getCurrentSong() : null;
            showScreenshot3AudioOptionsDialog(cur);
        };
        fpThreeDotBtn.setOnClickListener(open3DotMenu);
        fpBottomThreeDotBtn.setOnClickListener(open3DotMenu);

        fpQueueBtn.setOnClickListener(v -> openRightSlideQueue());
        queueBackdropOverlay.setOnClickListener(v -> closeRightSlideQueue());
        queueCloseCutBtn.setOnClickListener(v -> closeRightSlideQueue());

        // Swipe Left/Right on Audio Main Screen (Album Art / Disc) Changes Song! Center Tap or Lyrics Pill opens Lyrics!
        fpAlbumArtDiscView.setOnArtGestureListener(new AlbumArtDiscView.OnArtGestureListener() {
            @Override
            public void onSwipeRightToLeft() {
                if (musicService != null) {
                    musicService.playNext();
                    Toast.makeText(MainActivity.this, "⏭ Next Song", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onSwipeLeftToRight() {
                if (musicService != null) {
                    musicService.playPrevious();
                    Toast.makeText(MainActivity.this, "⏮ Previous Song", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onCenterTap() {
                openLyricsPanel(false);
            }
        });

        fpQuickLyricsBtn.setOnClickListener(v -> openLyricsPanel(true));

        findViewById(R.id.fpBackToArtBtn).setOnClickListener(v -> {
            fpLyricsContainer.setVisibility(View.GONE);
            fpAlbumArtDiscView.setLyricsPageSelected(false);
        });
        findViewById(R.id.fpFindLyricsOnlineBtn).setOnClickListener(v -> fetchOnlineLyricsForCurrentSong());

        fpLyricsFavBtn.setOnClickListener(v -> {
            if (musicService != null && musicService.getCurrentSong() != null) {
                SongItem cur = musicService.getCurrentSong();
                boolean fav = themeManager.toggleFavorite(cur.id);
                fpLyricsFavBtn.setText(fav ? "❤️ Favorited" : "♡ Favorite");
                Toast.makeText(this, fav ? "Added to Favorites ❤️" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
            }
        });

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
                Toast.makeText(this, next ? "Shuffle ON 🔀" : "Shuffle OFF", Toast.LENGTH_SHORT).show();
            }
        });

        fpRepeatBtn.setOnClickListener(v -> {
            if (musicService != null) {
                int nextMode = (musicService.getRepeatMode() + 1) % 3;
                musicService.setRepeatMode(nextMode);
                syncPlayerUI();
                String[] labels = {"Loop All Songs 🔁", "Repeat Current Song 🔂", "Repeat OFF ➡️"};
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

    // 3-DOT MENU: ALL ITEMS PURE WHITE (#FFFFFF), LARGER SIZE, AND "⋮" INSTEAD OF CROSS
    private void showScreenshot3AudioOptionsDialog(final SongItem targetSong) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(36, 32, 36, 28);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#0D131A"));
        cardBg.setCornerRadius(42f);
        cardBg.setStroke(3, Color.WHITE);
        card.setBackground(cardBg);

        // Top Row: "Audio Player Options" in Pure White + Circular Three-Dot "⋮" Button
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(8, 0, 8, 22);

        TextView titleTv = new TextView(this);
        titleTv.setText("Audio Player Options");
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(19f);
        titleTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleTv, titleLp);

        TextView menuCloseCircle = new TextView(this);
        menuCloseCircle.setText("⋮");
        menuCloseCircle.setTextColor(Color.WHITE);
        menuCloseCircle.setTextSize(20f);
        menuCloseCircle.setTypeface(null, Typeface.BOLD);
        menuCloseCircle.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(Color.parseColor("#263240"));
        closeBg.setStroke(2, Color.WHITE);
        menuCloseCircle.setBackground(closeBg);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(80, 80);
        menuCloseCircle.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(menuCloseCircle, closeLp);
        card.addView(headerRow);

        boolean isDisc = themeManager.isVinylDiscMode();
        boolean isWave = themeManager.isWaveformMode();

        addScreenshot3OptionRow(card, "🖼️  Set Custom Photo from Gallery (Save Permanently)", Color.WHITE, v -> {
            dialog.dismiss();
            if (targetSong != null) {
                songIdForPhotoPick = targetSong.id;
                Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pick, 902);
            }
        });

        String discLabel = isDisc
                ? "💿  Center Style: Switch to Square Album Art"
                : "💿  Center Style: Switch to Rotating Vinyl Disc";
        addScreenshot3OptionRow(card, discLabel, Color.WHITE, v -> {
            dialog.dismiss();
            boolean next = !themeManager.isVinylDiscMode();
            themeManager.setVinylDiscMode(next);
            fpAlbumArtDiscView.setVinylDiscMode(next);
        });

        String seekLabel = isWave
                ? "📏  Seekbar Style: Switch to Simple Straight Line"
                : "📏  Seekbar Style: Switch to Waveform Bars (Lambe Dande)";
        addScreenshot3OptionRow(card, seekLabel, Color.WHITE, v -> {
            dialog.dismiss();
            boolean next = !themeManager.isWaveformMode();
            themeManager.setWaveformMode(next);
            fpWaveformSeekBar.setWaveformStyle(next);
        });

        addScreenshot3OptionRow(card, "🎛️  Open Sound & Pitch Lab (Lofi / Slowed + Reverb)", Color.WHITE, v -> {
            dialog.dismiss();
            if (musicService != null) {
                new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
            }
        });

        addScreenshot3OptionRow(card, "🎚️  Open 5-Band Dolby Equalizer & Bass Boost", Color.WHITE, v -> {
            dialog.dismiss();
            if (musicService != null) {
                new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
            }
        });

        addScreenshot3OptionRow(card, "🔄  Swap Bottom 4 Controls (Equali, A-B, 1.0x, Timer)", Color.WHITE, v -> {
            dialog.dismiss();
            themeManager.rotateBottomControlOrder();
            refreshBottomSwappableButtons();
            Toast.makeText(this, "Bottom 4 Controls Order Swapped!", Toast.LENGTH_SHORT).show();
        });

        addScreenshot3OptionRow(card, "ℹ️  Song Details & File Path", Color.WHITE, v -> {
            dialog.dismiss();
            if (targetSong != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Song Details & File Path")
                        .setMessage("Title: " + targetSong.title
                                + "\nArtist: " + targetSong.artist
                                + "\nAlbum: " + targetSong.album
                                + "\nDuration: " + targetSong.getFormattedDuration()
                                + "\nFile Size: " + targetSong.getFormattedSize()
                                + "\nFile Path:\n" + targetSong.path)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        addScreenshot3OptionRow(card, "🗑️  Delete Song Permanently (From Phone)", Color.WHITE, v -> {
            dialog.dismiss();
            if (targetSong != null) {
                SongAdapter.showPermanentDeleteDialog(this, targetSong, this::loadAllAudioAndVideoSongs);
            }
        });

        dialog.setContentView(card);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void addScreenshot3OptionRow(LinearLayout parent, String text, int textColor, View.OnClickListener listener) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#263547"));
        parent.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));

        TextView item = new TextView(this);
        item.setText(text);
        item.setTextColor(textColor);
        item.setTextSize(15.5f);
        item.setTypeface(null, Typeface.BOLD);
        item.setPadding(12, 28, 12, 28);
        item.setOnClickListener(listener);
        parent.addView(item);
    }

    private void shareCurrentSong() {
        if (musicService == null || musicService.getCurrentSong() == null) return;
        SongItem s = musicService.getCurrentSong();
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("audio/*");
            share.putExtra(Intent.EXTRA_STREAM, s.getMediaUri());
            share.putExtra(Intent.EXTRA_TEXT, "Listening to " + s.title + " on Hybrid Music Player");
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share Audio Song"));
        } catch (Exception ignored) {
        }
    }

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
        int accent = themeManager.getCurrentTheme().accentPrimary;
        for (int i = 0; i < 6; i++) {
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(50f);
            if (i == currentTopTab) {
                pill.setColor(accent);
                topTabPills[i].setTextColor(Color.BLACK);
            } else {
                pill.setColor(Color.parseColor("#16202C"));
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
                showScreenshot3AudioOptionsDialog(song);
            }
            @Override
            public void onSongHoldTwoSeconds(SongItem song, int position) {
                // 2-Second Hold on Song: Plays immediately & makes Muso Gudda dance!
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

    // SettingsSectionHelper Callbacks
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
    @Override public void onOpenEqualizerLab() {
        if (musicService != null) new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
    }
    @Override public void onTogglePocketShake() {
        boolean next = !pocketShakeManager.isEnabled();
        pocketShakeManager.setEnabled(next);
        showBottomSection(2);
    }
    @Override public void onToggleDiscMode() {
        boolean next = !themeManager.isVinylDiscMode();
        themeManager.setVinylDiscMode(next);
        fpAlbumArtDiscView.setVinylDiscMode(next);
        showBottomSection(2);
    }
    @Override public void onToggleWaveformMode() {
        boolean next = !themeManager.isWaveformMode();
        themeManager.setWaveformMode(next);
        fpWaveformSeekBar.setWaveformStyle(next);
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
        showScreenshot3AudioOptionsDialog(song);
    }

    private void openFullPlayer() {
        fullPlayerOverlay.setVisibility(View.VISIBLE);
        fpAlbumArtDiscView.setVinylDiscMode(themeManager.isVinylDiscMode());
        fpWaveformSeekBar.setWaveformStyle(themeManager.isWaveformMode());
        syncPlayerUI();
    }

    private void openRightSlideQueue() {
        if (musicService == null) return;
        List<SongItem> q = musicService.getQueueList();
        int curIdx = musicService.getCurrentIndex();
        queueHeaderTitle.setText("Playing Queue (" + (q.isEmpty() ? 0 : curIdx + 1) + "/" + q.size() + ")");

        GradientDrawable qBg = new GradientDrawable();
        qBg.setColor(Color.parseColor("#0D131C"));
        qBg.setCornerRadius(36f);
        qBg.setStroke(3, Color.WHITE);
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
            fpLyricsFavBtn.setText(themeManager.isFavorite(s.id) ? "❤️ Favorited" : "♡ Favorite");
            String saved = themeManager.getSavedLyrics(s.id);
            if (saved != null && !saved.isEmpty()) {
                fpLyricsText.setText(saved);
            } else if (autoSearchIfEmpty) {
                fetchOnlineLyricsForCurrentSong();
            } else {
                fpLyricsText.setText("🎵 " + s.title + "\n" + s.artist + "\n\nTap 'Find Online' below to search lyrics.");
            }
        }
    }

    private void fetchOnlineLyricsForCurrentSong() {
        if (musicService == null || musicService.getCurrentSong() == null) return;
        final SongItem song = musicService.getCurrentSong();
        fpLyricsText.setText("🌐 Searching lyrics online for:\n" + song.title + "...");

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
                    : "🎶 " + song.title + "\nSinger: " + song.artist + "\n\n(No online lyrics found for this track)";

            if (resultLyrics != null && !resultLyrics.trim().isEmpty()) {
                themeManager.saveCustomLyrics(song.id, resultLyrics);
            }
            runOnUiThread(() -> fpLyricsText.setText(finalLyrics));
        }).start();
    }

    private void refreshBottomSwappableButtons() {
        int[] order = themeManager.getBottomControlOrder();
        float spd = musicService != null ? musicService.getPlaybackSpeed() : 1.0f;
        String speedLabel = String.format(Locale.US, "⚡ %.1fx", spd);
        String[] labels = {"🕒 Timer", "🔁 A-B", "🎚 Equali", speedLabel};

        for (int slot = 0; slot < 4; slot++) {
            final int actionType = order[slot];
            bottomSlotBtns[slot].setText(labels[actionType]);
            // Strictly Pure White (#FFFFFF) regardless of theme!
            bottomSlotBtns[slot].setTextColor(Color.WHITE);
            bottomSlotBtns[slot].setOnClickListener(v -> {
                if (musicService == null) return;
                if (actionType == 1) {
                    String status = musicService.cycleABRepeat();
                    bottomSlotBtns[slot].setText("🔁 " + status);
                    Toast.makeText(this, "Repeat Mode: " + status, Toast.LENGTH_SHORT).show();
                } else {
                    new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
                }
            });
        }
    }

    private void showSortMenuDialog() {
        String[] sorts = {"🔤 A to Z Songs", "🆕 New Downloaded Songs", "📏 Long Size / Duration Songs"};
        new AlertDialog.Builder(this)
                .setTitle("Sort Songs")
                .setItems(sorts, (d, which) -> {
                    if (which == 0) Collections.sort(displayedSongs, (a, b) -> a.title.compareToIgnoreCase(b.title));
                    else if (which == 1) Collections.sort(displayedSongs, (a, b) -> Long.compare(b.dateAdded, a.dateAdded));
                    else if (which == 2) Collections.sort(displayedSongs, (a, b) -> Long.compare(b.duration, a.duration));
                    refreshMainListAdapter();
                })
                .show();
    }

    private void showThemePickerDialog() {
        ThemeManager.ThemePreset[] presets = themeManager.getAllThemes();
        String[] names = new String[presets.length];
        for (int i = 0; i < presets.length; i++) names[i] = presets[i].name;

        new AlertDialog.Builder(this)
                .setTitle("Choose Hybrid Theme (20 Themes)")
                .setItems(names, (d, which) -> {
                    themeManager.setThemeIndex(which);
                    applyActiveThemeEverywhere();
                })
                .setPositiveButton("Gallery Theme Photo", (d, w) -> {
                    songIdForPhotoPick = -1;
                    Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(pick, 901);
                })
                .show();
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
        ThemeManager.ThemePreset t = themeManager.getCurrentTheme();

        rootLayout.setBackgroundColor(t.bgPrimary);
        fullPlayerOverlay.setBackgroundColor(t.bgPrimary);
        bottomNavBar.setBackgroundColor(t.bgSurface);

        GradientDrawable shufflePill = new GradientDrawable();
        shufflePill.setColor(t.accentPrimary);
        shufflePill.setCornerRadius(50f);
        playShuffleAllBtn.setBackground(shufflePill);

        GradientDrawable sortPill = new GradientDrawable();
        sortPill.setColor(Color.parseColor("#16202C"));
        sortPill.setCornerRadius(40f);
        sortPill.setStroke(2, Color.WHITE);
        topSortMenuBtn.setBackground(sortPill);
        topSortMenuBtn.setTextColor(Color.WHITE);

        // Mini Player Bar with Pure White Border & Pure White Controls
        GradientDrawable miniBg = new GradientDrawable();
        miniBg.setColor(Color.parseColor("#121A24"));
        miniBg.setCornerRadius(44f);
        miniBg.setStroke(3, Color.WHITE);
        miniPlayerBar.setBackground(miniBg);

        GradientDrawable miniPlayCircle = new GradientDrawable();
        miniPlayCircle.setShape(GradientDrawable.OVAL);
        miniPlayCircle.setColor(Color.WHITE);
        miniPlayPauseBtn.setBackground(miniPlayCircle);
        miniPlayPauseBtn.setTextColor(Color.BLACK);

        // Full Player Top Circular Buttons in Pure White Border
        GradientDrawable topCircle1 = new GradientDrawable();
        topCircle1.setShape(GradientDrawable.OVAL);
        topCircle1.setColor(Color.parseColor("#18222F"));
        topCircle1.setStroke(2, Color.WHITE);
        fpCloseBtn.setBackground(topCircle1);
        fpCloseBtn.setTextColor(Color.WHITE);

        GradientDrawable topCircle2 = new GradientDrawable();
        topCircle2.setShape(GradientDrawable.OVAL);
        topCircle2.setColor(Color.parseColor("#18222F"));
        topCircle2.setStroke(2, Color.WHITE);
        fpShareBtn.setBackground(topCircle2);
        fpShareBtn.setTextColor(Color.WHITE);

        GradientDrawable lyrPill = new GradientDrawable();
        lyrPill.setColor(Color.parseColor("#CC141E2B"));
        lyrPill.setCornerRadius(40f);
        lyrPill.setStroke(2, Color.WHITE);
        fpQuickLyricsBtn.setBackground(lyrPill);
        fpQuickLyricsBtn.setTextColor(Color.WHITE);

        // Full Player Giant Center Play/Pause Circle with Pure White Ring
        GradientDrawable bigPlayRing = new GradientDrawable();
        bigPlayRing.setShape(GradientDrawable.OVAL);
        bigPlayRing.setColor(Color.parseColor("#162432"));
        bigPlayRing.setStroke(6, Color.WHITE);
        fpPlayPauseBtn.setBackground(bigPlayRing);
        fpPlayPauseBtn.setTextColor(Color.WHITE);

        // Full Player 3-Dot Circles in Pure White
        GradientDrawable fpDotCircle = new GradientDrawable();
        fpDotCircle.setShape(GradientDrawable.OVAL);
        fpDotCircle.setColor(Color.parseColor("#162432"));
        fpDotCircle.setStroke(3, Color.WHITE);
        fpThreeDotBtn.setBackground(fpDotCircle);
        fpThreeDotBtn.setTextColor(Color.WHITE);

        GradientDrawable fpBotDotCircle = new GradientDrawable();
        fpBotDotCircle.setShape(GradientDrawable.OVAL);
        fpBotDotCircle.setColor(Color.parseColor("#162432"));
        fpBotDotCircle.setStroke(3, Color.WHITE);
        fpBottomThreeDotBtn.setBackground(fpBotDotCircle);
        fpBottomThreeDotBtn.setTextColor(Color.WHITE);

        // Playing Queue Pill in Pure White
        GradientDrawable queuePill = new GradientDrawable();
        queuePill.setColor(Color.parseColor("#141E2B"));
        queuePill.setCornerRadius(50f);
        queuePill.setStroke(3, Color.WHITE);
        fpQueueBtn.setBackground(queuePill);
        fpQueueBtn.setTextColor(Color.WHITE);

        // Keep ALL Full Player Texts & Icons Pure White (#FFFFFF) regardless of theme!
        appTitleText.setText(themeManager.getHeaderStar());
        appTitleText.setTextColor(Color.WHITE);
        miniTitle.setTextColor(Color.WHITE);
        miniArtist.setTextColor(Color.WHITE);
        fpSongTitle.setTextColor(Color.WHITE);
        fpSongArtist.setTextColor(Color.WHITE);
        fpShuffleBtn.setTextColor(Color.WHITE);
        fpPrevBtn.setTextColor(Color.WHITE);
        fpNextBtn.setTextColor(Color.WHITE);
        fpRepeatBtn.setTextColor(Color.WHITE);
        fpPocketShakeBtn.setTextColor(Color.WHITE);

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
        fpPocketShakeBtn.setText(on ? "📳 Pocket Shake: ON" : "📳 Pocket Shake: OFF");
        fpPocketShakeBtn.setTextColor(Color.WHITE);
    }

    private void syncPlayerUI() {
        if (musicService == null) return;
        final SongItem cur = musicService.getCurrentSong();
        boolean playing = musicService.isPlaying();

        headerPuppyView.setPlayingState(playing);
        fpPuppyView.setPlayingState(playing);
        floatingGuddaView.setPlayingState(playing);
        fpAlbumArtDiscView.setPlaying(playing);
        fpWaveformSeekBar.setPlaying(playing);

        miniPlayPauseBtn.setText(playing ? "❚❚" : "▶");
        fpPlayPauseBtn.setText(playing ? "❚❚" : "▶");

        List<SongItem> q = musicService.getQueueList();
        fpQueueBtn.setText("🎵 Playing Queue (" + (q.isEmpty() ? 0 : musicService.getCurrentIndex() + 1) + "/" + q.size() + ")");

        int rep = musicService.getRepeatMode();
        fpRepeatBtn.setText(rep == 1 ? "🔂" : (rep == 0 ? "🔁" : "➡️"));

        // Keep all icons strictly Pure White (#FFFFFF)
        fpShuffleBtn.setTextColor(Color.WHITE);
        fpRepeatBtn.setTextColor(Color.WHITE);
        updatePocketShakeButtonUI();
        refreshBottomSwappableButtons();

        if (cur != null) {
            miniTitle.setText(cur.title);
            miniArtist.setText(cur.artist);
            fpSongTitle.setText(cur.title);
            fpSongArtist.setText(cur.artist);
            if (mainAdapter != null) mainAdapter.setActiveSongId(cur.id);

            // Load real photo for Mini Player & Full Player (Works for both Audio & Video-to-Audio!)
            MediaArtLoader.loadSongArtInto(this, cur, themeManager, miniThumbnail);
            new Thread(() -> {
                final Bitmap bmp = MediaArtLoader.extractBitmapSync(
                        MainActivity.this, cur, themeManager.getSongCustomPhoto(cur.id));
                runOnUiThread(() -> fpAlbumArtDiscView.setSongBitmap(bmp));
            }).start();
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
                Toast.makeText(this, "Custom Theme Photo Applied!", Toast.LENGTH_SHORT).show();
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
        return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60);
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
