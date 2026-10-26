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
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private MusicPlaybackService musicService;
    private boolean isBound = false;
    private ThemeManager themeManager;
    private PocketShakeManager pocketShakeManager;

    private final List<SongItem> allSongs = new ArrayList<>();
    private final List<SongItem> displayedSongs = new ArrayList<>();
    private RecyclerView mainRecyclerView;
    private SongAdapter mainAdapter;

    private int currentTopTab = 0; // 0=Songs, 1=Folders, 2=Albums, 3=Artists, 4=VideoAudio, 5=Favorites
    private int currentBottomTab = 0; // 0=Songs, 1=Search, 2=Settings(MI)
    private long songIdForPhotoPick = -1;

    // Main Library Views (Screenshot 1)
    private View rootLayout, topHeaderBar, subShuffleHeaderRow, categoryTabsScroll, miniPlayerBar, bottomNavBar, fullPlayerOverlay;
    private ImageView customThemeBgImage, miniThumbnail;
    private TextView appTitleText, playShuffleAllBtn, totalSongsCountText, topSortMenuBtn;
    private TextView miniTitle, miniArtist, miniPlayPauseBtn, miniNextBtn;
    private final TextView[] topTabPills = new TextView[6];
    private DancingPuppyView headerPuppyView, fpPuppyView;

    // Full Player Views (Screenshot 2)
    private AlbumArtDiscView fpAlbumArtDiscView;
    private View fpLyricsContainer, queueBackdropOverlay, queueSlidePanel;
    private TextView fpCloseBtn, fpShareBtn, fpLyricsText, fpSongTitle, fpSongArtist, fpCurrentTime, fpTotalTime;
    private WaveformSeekBarView fpWaveformSeekBar;
    private TextView fpPlayPauseBtn, fpPrevBtn, fpNextBtn, fpShuffleBtn, fpRepeatBtn;
    private TextView fpPocketShakeBtn, fpQueueBtn, fpThreeDotBtn, queueHeaderTitle, queueCloseCutBtn;
    private Button fpLyricsFavBtn;
    private final TextView[] bottomSlotBtns = new TextView[4];
    private RecyclerView queueRecyclerView;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private GestureDetector tabSwipeDetector;

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
        setContentView(R.layout.activity_main);

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

    private void bindViews() {
        rootLayout = findViewById(R.id.rootLayout);
        customThemeBgImage = findViewById(R.id.customThemeBgImage);
        topHeaderBar = findViewById(R.id.topHeaderBar);
        subShuffleHeaderRow = findViewById(R.id.subShuffleHeaderRow);
        categoryTabsScroll = findViewById(R.id.categoryTabsScroll);
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

        miniThumbnail = findViewById(R.id.miniThumbnail);
        miniTitle = findViewById(R.id.miniTitle);
        miniTitle.setSelected(true);
        miniArtist = findViewById(R.id.miniArtist);
        miniPlayPauseBtn = findViewById(R.id.miniPlayPauseBtn);
        miniNextBtn = findViewById(R.id.miniNextBtn);

        fpCloseBtn = findViewById(R.id.fpCloseBtn);
        fpShareBtn = findViewById(R.id.fpShareBtn);
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

        // Play / Shuffle All Pill Button (Screenshot 1)
        playShuffleAllBtn.setOnClickListener(v -> {
            if (musicService != null && !displayedSongs.isEmpty()) {
                musicService.setShuffle(true);
                int rand = (int) (Math.random() * displayedSongs.size());
                musicService.setQueue(displayedSongs, rand);
                openFullPlayer();
            }
        });

        // Horizontal Swipe Gesture across tabs (Songs -> Folders -> Albums -> Artists -> VideoAudio -> Fav -> Search -> Settings)
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

        findViewById(R.id.navSongsBtn).setOnClickListener(v -> showBottomSection(0));
        findViewById(R.id.navSearchBtn).setOnClickListener(v -> showBottomSection(1));
        findViewById(R.id.navSettingsBtn).setOnClickListener(v -> showBottomSection(2));

        miniPlayerBar.setOnClickListener(v -> openFullPlayer());
        miniPlayPauseBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playToggle();
        });
        miniNextBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playNext();
        });

        // Full Player Top Row (Screenshot 2)
        fpCloseBtn.setOnClickListener(v -> fullPlayerOverlay.setVisibility(View.GONE));
        fpShareBtn.setOnClickListener(v -> shareCurrentSong());

        // Screenshot 3 Exact 3-Dot Popup Menu
        fpThreeDotBtn.setOnClickListener(v -> {
            SongItem cur = musicService != null ? musicService.getCurrentSong() : null;
            showScreenshot3AudioOptionsDialog(cur);
        });

        fpQueueBtn.setOnClickListener(v -> openRightSlideQueue());
        queueBackdropOverlay.setOnClickListener(v -> closeRightSlideQueue());
        queueCloseCutBtn.setOnClickListener(v -> closeRightSlideQueue());

        // Square Art / Rotating Disc Gestures
        fpAlbumArtDiscView.setOnArtGestureListener(new AlbumArtDiscView.OnArtGestureListener() {
            @Override
            public void onSwipeRightToLeft() {
                openLyricsPanel(true);
            }
            @Override
            public void onSwipeLeftToRight() {
                if (musicService != null) musicService.playPrevious();
            }
            @Override
            public void onCenterTap() {
                openLyricsPanel(false);
            }
        });

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
                Toast.makeText(this, next ? "Shuffle ON" : "Shuffle OFF", Toast.LENGTH_SHORT).show();
            }
        });

        fpRepeatBtn.setOnClickListener(v -> {
            if (musicService != null) {
                int nextMode = (musicService.getRepeatMode() + 1) % 3;
                musicService.setRepeatMode(nextMode);
                syncPlayerUI();
                String[] labels = {"Loop All Songs", "Repeat Current Song", "Repeat OFF"};
                Toast.makeText(this, labels[nextMode], Toast.LENGTH_SHORT).show();
            }
        });

        refreshBottomSwappableButtons();
    }

    // SCREENSHOT 3 (1000067471.jpg) 100% EXACT CYAN-BORDERED POPUP MENU
    private void showScreenshot3AudioOptionsDialog(final SongItem targetSong) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(36, 32, 36, 28);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#0D131A"));
        cardBg.setCornerRadius(42f);
        cardBg.setStroke(3, themeManager.getCurrentTheme().accentPrimary);
        card.setBackground(cardBg);

        // Top Row: "Audio Player Options" in Cyan + Circular Close "✕" Button
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(8, 0, 8, 22);

        TextView titleTv = new TextView(this);
        titleTv.setText("Audio Player Options");
        titleTv.setTextColor(themeManager.getCurrentTheme().accentPrimary);
        titleTv.setTextSize(18f);
        titleTv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleTv, titleLp);

        TextView closeCircle = new TextView(this);
        closeCircle.setText("✕");
        closeCircle.setTextColor(Color.WHITE);
        closeCircle.setTextSize(16f);
        closeCircle.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.OVAL);
        closeBg.setColor(Color.parseColor("#263240"));
        closeCircle.setBackground(closeBg);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(76, 76);
        closeCircle.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(closeCircle, closeLp);
        card.addView(headerRow);

        boolean isDisc = themeManager.isVinylDiscMode();
        boolean isWave = themeManager.isWaveformMode();

        // 1. Set Custom Photo from Gallery (Save Permanently)
        addScreenshot3OptionRow(card, "🖼️  Set Custom Photo from Gallery (Save Permanently)", Color.WHITE, v -> {
            dialog.dismiss();
            if (targetSong != null) {
                songIdForPhotoPick = targetSong.id;
                Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(pick, 902);
            }
        });

        // 2. Center Style: Switch to Rotating Vinyl Disc / Square Box
        String discLabel = isDisc
                ? "💿  Center Style: Switch to Square Album Art"
                : "💿  Center Style: Switch to Rotating Vinyl Disc";
        addScreenshot3OptionRow(card, discLabel, Color.WHITE, v -> {
            dialog.dismiss();
            boolean next = !themeManager.isVinylDiscMode();
            themeManager.setVinylDiscMode(next);
            fpAlbumArtDiscView.setVinylDiscMode(next);
            Toast.makeText(this, next ? "Rotating Vinyl Disc + Top Danda ON" : "Square Album Box ON", Toast.LENGTH_SHORT).show();
        });

        // 3. Seekbar Style: Switch to Simple Straight Line / Tall Waveform Bars
        String seekLabel = isWave
                ? "📏  Seekbar Style: Switch to Simple Straight Line"
                : "📏  Seekbar Style: Switch to Waveform Bars (Lambe Dande)";
        addScreenshot3OptionRow(card, seekLabel, Color.WHITE, v -> {
            dialog.dismiss();
            boolean next = !themeManager.isWaveformMode();
            themeManager.setWaveformMode(next);
            fpWaveformSeekBar.setWaveformStyle(next);
            Toast.makeText(this, next ? "Waveform Bars (Lambe Dande) ON" : "Simple Straight Line ON", Toast.LENGTH_SHORT).show();
        });

        // 4. Open Sound & Pitch Lab (Lofi / Slowed + Reverb)
        addScreenshot3OptionRow(card, "🎛️  Open Sound & Pitch Lab (Lofi / Slowed + Reverb)", Color.WHITE, v -> {
            dialog.dismiss();
            if (musicService != null) {
                new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
            }
        });

        // 5. Open 5-Band Dolby Equalizer & Bass Boost
        addScreenshot3OptionRow(card, "🎚️  Open 5-Band Dolby Equalizer & Bass Boost", Color.WHITE, v -> {
            dialog.dismiss();
            if (musicService != null) {
                new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
            }
        });

        // 6. Song Details & File Path (+ Swap Bottom 4 Controls button inside details)
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
                        .setNeutralButton("Swap Bottom 4 Icons", (d, w) -> {
                            themeManager.rotateBottomControlOrder();
                            refreshBottomSwappableButtons();
                        })
                        .show();
            }
        });

        // 7. Delete Song Permanently (From Phone) in Red Color
        addScreenshot3OptionRow(card, "🗑️  Delete Song Permanently (From Phone)", Color.parseColor("#EF4444"), v -> {
            dialog.dismiss();
            if (targetSong != null) {
                SongAdapter.showPermanentDeleteDialog(this, targetSong, this::loadAllAudioAndVideoSongs);
            }
        });

        dialog.setContentView(card);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void addScreenshot3OptionRow(LinearLayout parent, String text, int textColor, View.OnClickListener listener) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#1E293B"));
        parent.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));

        TextView item = new TextView(this);
        item.setText(text);
        item.setTextColor(textColor);
        item.setTextSize(14.5f);
        item.setTypeface(null, android.graphics.Typeface.BOLD);
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
        queryMediaUri(resolver, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, false);
        queryMediaUri(resolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true);
        selectTopCategoryTab(currentTopTab);
    }

    private void queryMediaUri(ContentResolver resolver, Uri uri, boolean isVideo) {
        String[] proj = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED
        };
        try (Cursor c = resolver.query(uri, proj, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
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
                    String folder = "Music";
                    if (path != null) {
                        File parent = new File(path).getParentFile();
                        if (parent != null) folder = parent.getName();
                    }
                    String itemUri = Uri.withAppendedPath(uri, String.valueOf(id)).toString();
                    SongItem item = new SongItem(id, title, artist, album, path, itemUri, id, duration, size, dateAdded, isVideo, folder);
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
            // Songs Tab
            for (SongItem s : allSongs) if (!s.isVideoAudio) displayedSongs.add(s);
        } else if (tabIndex == 1) {
            // Folders Tab
            List<SongItem> copy = new ArrayList<>(allSongs);
            Collections.sort(copy, (a, b) -> a.folderName.compareToIgnoreCase(b.folderName));
            displayedSongs.addAll(copy);
        } else if (tabIndex == 2) {
            // Albums Tab
            List<SongItem> copy = new ArrayList<>(allSongs);
            Collections.sort(copy, (a, b) -> a.album.compareToIgnoreCase(b.album));
            displayedSongs.addAll(copy);
        } else if (tabIndex == 3) {
            // Artists Tab
            List<SongItem> copy = new ArrayList<>(allSongs);
            Collections.sort(copy, (a, b) -> a.artist.compareToIgnoreCase(b.artist));
            displayedSongs.addAll(copy);
        } else if (tabIndex == 4) {
            // Video-Audio Tab (Plays 8K/4K/MP4 Videos as Audio!)
            for (SongItem s : allSongs) if (s.isVideoAudio) displayedSongs.add(s);
        } else if (tabIndex == 5) {
            // Favorites Tab
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
            container.addView(buildBottomSearchScreen());
        } else if (sectionIndex == 2) {
            container.addView(buildMISettingsScreen());
        }
        applyActiveThemeEverywhere();
    }

    private View buildBottomSearchScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 8, 12, 8);

        RecyclerView searchRecycler = new RecyclerView(this);
        searchRecycler.setLayoutManager(new LinearLayoutManager(this));
        LinearLayout.LayoutParams rvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(searchRecycler, rvParams);

        final int[] filterMode = {0};
        final List<SongItem> filteredList = new ArrayList<>(allSongs);

        // Audio / Video Filter Pills at the BOTTOM right above the search bar
        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setGravity(Gravity.CENTER);

        Button pillAll = new Button(this);
        pillAll.setText("All (" + allSongs.size() + ")");
        pillAll.setTextSize(11f);

        Button pillAudio = new Button(this);
        pillAudio.setText("Music / Audio");
        pillAudio.setTextSize(11f);

        Button pillVideo = new Button(this);
        pillVideo.setText("Videos (Audio)");
        pillVideo.setTextSize(11f);

        pillRow.addView(pillAll);
        pillRow.addView(pillAudio);
        pillRow.addView(pillVideo);
        root.addView(pillRow);

        EditText searchBar = new EditText(this);
        searchBar.setHint("🔍 Type song or singer name (Instant Search)...");
        searchBar.setSingleLine(true);
        searchBar.setTextColor(themeManager.getPrimaryTextColor());
        searchBar.setHintTextColor(themeManager.getSecondaryTextColor());
        searchBar.setBackgroundColor(themeManager.getCardColorWithAlpha());
        searchBar.setPadding(24, 20, 24, 20);
        root.addView(searchBar);

        Runnable runFilter = () -> {
            String q = searchBar.getText().toString().toLowerCase(Locale.US).trim();
            filteredList.clear();
            for (SongItem s : allSongs) {
                if (filterMode[0] == 1 && s.isVideoAudio) continue;
                if (filterMode[0] == 2 && !s.isVideoAudio) continue;
                if (q.isEmpty() || s.title.toLowerCase(Locale.US).contains(q)
                        || s.artist.toLowerCase(Locale.US).contains(q)
                        || s.folderName.toLowerCase(Locale.US).contains(q)) {
                    filteredList.add(s);
                }
            }
            SongAdapter ad = new SongAdapter(this, filteredList, themeManager, false, new SongAdapter.OnSongClickListener() {
                @Override
                public void onSongClick(int pos) {
                    if (musicService != null) {
                        musicService.setQueue(filteredList, pos);
                        openFullPlayer();
                    }
                }
                @Override
                public void onThreeDotClick(SongItem song, int pos) {
                    showScreenshot3AudioOptionsDialog(song);
                }
            });
            searchRecycler.setAdapter(ad);
        };

        pillAll.setOnClickListener(v -> { filterMode[0] = 0; runFilter.run(); });
        pillAudio.setOnClickListener(v -> { filterMode[0] = 1; runFilter.run(); });
        pillVideo.setOnClickListener(v -> { filterMode[0] = 2; runFilter.run(); });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { runFilter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        runFilter.run();
        return root;
    }

    private View buildMISettingsScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(20, 16, 20, 24);

        addSettingButton(box, "🎨 Choose 20+ Themes (Global Theme Engine)", v -> showThemePickerDialog());
        addSettingButton(box, "🖼️ Set Custom Gallery Photo as App Theme", v -> {
            songIdForPhotoPick = -1;
            Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(pick, 901);
        });
        addSettingButton(box, "🔄 Reset Custom Theme Photo", v -> {
            themeManager.setCustomBgUri("");
            applyActiveThemeEverywhere();
            Toast.makeText(this, "Default Clean Theme Restored", Toast.LENGTH_SHORT).show();
        });
        addSettingButton(box, "🎛️ Open Sound & Pitch Lab (Lofi / Slowed + Reverb)", v -> {
            if (musicService != null) new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
        });
        addSettingButton(box, "📳 Pocket Shake (" + (pocketShakeManager.isEnabled() ? "ON" : "OFF - Default") + ")", v -> {
            boolean next = !pocketShakeManager.isEnabled();
            pocketShakeManager.setEnabled(next);
            showBottomSection(2);
        });
        addSettingButton(box, "💿 Toggle Center Style: Square Box vs Rotating Disc + Danda", v -> {
            boolean next = !themeManager.isVinylDiscMode();
            themeManager.setVinylDiscMode(next);
            fpAlbumArtDiscView.setVinylDiscMode(next);
            Toast.makeText(this, next ? "Rotating Disc + Danda ON" : "Square Box ON", Toast.LENGTH_SHORT).show();
        });
        addSettingButton(box, "📊 Toggle Seekbar: Lambe Dande (Waveform) vs Simple Line", v -> {
            boolean next = !themeManager.isWaveformMode();
            themeManager.setWaveformMode(next);
            fpWaveformSeekBar.setWaveformStyle(next);
            Toast.makeText(this, next ? "Lambe Dande (Waveform) ON" : "Simple Line ON", Toast.LENGTH_SHORT).show();
        });
        addSettingButton(box, "🎧 10-Second DJ Slow Fade Transition (" + (themeManager.isDjSlowFadeEnabled() ? "ON" : "OFF") + ")", v -> {
            boolean next = !themeManager.isDjSlowFadeEnabled();
            themeManager.setDjSlowFadeEnabled(next);
            if (musicService != null) musicService.setDjSlowFadeEnabled(next);
            showBottomSection(2);
        });
        addSettingButton(box, "⭐ Customize App Name & Star Badge", v -> showCustomizeHeaderDialog());

        scroll.addView(box);
        return scroll;
    }

    private void addSettingButton(LinearLayout parent, String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(themeManager.getPrimaryTextColor());
        b.setBackgroundColor(themeManager.getCardColorWithAlpha());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 14;
        b.setOnClickListener(click);
        parent.addView(b, lp);
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
        qBg.setStroke(3, themeManager.getCurrentTheme().accentPrimary);
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
            bottomSlotBtns[slot].setTextColor(actionType == 2
                    ? themeManager.getCurrentTheme().accentPrimary : Color.WHITE);
            bottomSlotBtns[slot].setOnClickListener(v -> {
                if (musicService == null) return;
                if (actionType == 1) {
                    String status = musicService.cycleABRepeat();
                    bottomSlotBtns[1].setText("🔁 " + status);
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
        int priText = themeManager.getPrimaryTextColor();

        rootLayout.setBackgroundColor(t.bgPrimary);
        fullPlayerOverlay.setBackgroundColor(t.bgPrimary);
        bottomNavBar.setBackgroundColor(t.bgSurface);

        // Style Play / Shuffle All Pill (Screenshot 1)
        GradientDrawable shufflePill = new GradientDrawable();
        shufflePill.setColor(t.accentPrimary);
        shufflePill.setCornerRadius(50f);
        playShuffleAllBtn.setBackground(shufflePill);

        // Style Sort Button Pill (Screenshot 1)
        GradientDrawable sortPill = new GradientDrawable();
        sortPill.setColor(Color.parseColor("#16202C"));
        sortPill.setCornerRadius(40f);
        topSortMenuBtn.setBackground(sortPill);

        // Style Floating Mini Player with Glowing Cyan Border (Screenshot 1)
        GradientDrawable miniBg = new GradientDrawable();
        miniBg.setColor(Color.parseColor("#121A24"));
        miniBg.setCornerRadius(44f);
        miniBg.setStroke(3, t.accentPrimary);
        miniPlayerBar.setBackground(miniBg);

        // Style Mini Player Cyan Circle Play Button (Screenshot 1)
        GradientDrawable miniPlayCircle = new GradientDrawable();
        miniPlayCircle.setShape(GradientDrawable.OVAL);
        miniPlayCircle.setColor(t.accentPrimary);
        miniPlayPauseBtn.setBackground(miniPlayCircle);

        // Style Full Player Top Circular Down & Share Buttons (Screenshot 2)
        GradientDrawable topCircle1 = new GradientDrawable();
        topCircle1.setShape(GradientDrawable.OVAL);
        topCircle1.setColor(Color.parseColor("#18222F"));
        fpCloseBtn.setBackground(topCircle1);

        GradientDrawable topCircle2 = new GradientDrawable();
        topCircle2.setShape(GradientDrawable.OVAL);
        topCircle2.setColor(Color.parseColor("#18222F"));
        fpShareBtn.setBackground(topCircle2);

        // Style Full Player Center Dark Circle with Glowing Cyan Ring (Screenshot 2)
        GradientDrawable bigPlayRing = new GradientDrawable();
        bigPlayRing.setShape(GradientDrawable.OVAL);
        bigPlayRing.setColor(Color.parseColor("#122634"));
        bigPlayRing.setStroke(5, t.accentPrimary);
        fpPlayPauseBtn.setBackground(bigPlayRing);

        // Style Full Player 3-Dot Circle (Screenshot 2)
        GradientDrawable fpDotCircle = new GradientDrawable();
        fpDotCircle.setShape(GradientDrawable.OVAL);
        fpDotCircle.setColor(Color.parseColor("#122230"));
        fpDotCircle.setStroke(3, t.accentPrimary);
        fpThreeDotBtn.setBackground(fpDotCircle);
        fpThreeDotBtn.setTextColor(t.accentPrimary);

        // Style Playing Queue Pill (Screenshot 2)
        GradientDrawable queuePill = new GradientDrawable();
        queuePill.setColor(Color.parseColor("#0E1D28"));
        queuePill.setCornerRadius(50f);
        queuePill.setStroke(2, Color.parseColor("#1E4E68"));
        fpQueueBtn.setBackground(queuePill);
        fpQueueBtn.setTextColor(t.accentPrimary);

        appTitleText.setText(themeManager.getHeaderStar());
        miniTitle.setTextColor(priText);
        miniArtist.setTextColor(t.accentPrimary);
        fpSongTitle.setTextColor(priText);
        fpSongArtist.setTextColor(t.accentPrimary);

        headerPuppyView.setAccentColor(t.accentPrimary);
        fpPuppyView.setAccentColor(t.accentPrimary);
        fpAlbumArtDiscView.setAccentColor(t.accentPrimary);
        fpWaveformSeekBar.setAccentColor(t.accentPrimary);

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
        fpPocketShakeBtn.setTextColor(on ? themeManager.getCurrentTheme().accentPrimary : Color.parseColor("#94A3B8"));
    }

    private void syncPlayerUI() {
        if (musicService == null) return;
        SongItem cur = musicService.getCurrentSong();
        boolean playing = musicService.isPlaying();

        headerPuppyView.setPlayingState(playing);
        fpPuppyView.setPlayingState(playing);
        fpAlbumArtDiscView.setPlaying(playing);
        fpWaveformSeekBar.setPlaying(playing);

        miniPlayPauseBtn.setText(playing ? "❚❚" : "▶");
        fpPlayPauseBtn.setText(playing ? "❚❚" : "▶");

        List<SongItem> q = musicService.getQueueList();
        fpQueueBtn.setText("🎵 Playing Queue (" + (q.isEmpty() ? 0 : musicService.getCurrentIndex() + 1) + "/" + q.size() + ")");

        fpShuffleBtn.setTextColor(musicService.isShuffle()
                ? themeManager.getCurrentTheme().accentPrimary : Color.parseColor("#94A3B8"));

        int rep = musicService.getRepeatMode();
        fpRepeatBtn.setText(rep == 1 ? "🔂" : "🔁");
        fpRepeatBtn.setTextColor(rep != 2
                ? themeManager.getCurrentTheme().accentPrimary : Color.parseColor("#94A3B8"));

        updatePocketShakeButtonUI();
        refreshBottomSwappableButtons();

        if (cur != null) {
            miniTitle.setText(cur.title);
            miniArtist.setText(cur.artist);
            fpSongTitle.setText(cur.title);
            fpSongArtist.setText(cur.artist);
            if (mainAdapter != null) mainAdapter.setActiveSongId(cur.id);

            Bitmap bmp = loadSongBitmap(cur);
            fpAlbumArtDiscView.setSongBitmap(bmp);
            if (bmp != null) miniThumbnail.setImageBitmap(bmp);
            else miniThumbnail.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private Bitmap loadSongBitmap(SongItem song) {
        try {
            String custom = themeManager.getSongCustomPhoto(song.id);
            Uri target = (custom != null && !custom.isEmpty()) ? Uri.parse(custom) : song.getAlbumArtUri();
            InputStream is = getContentResolver().openInputStream(target);
            if (is != null) {
                Bitmap b = BitmapFactory.decodeStream(is);
                is.close();
                return b;
            }
        } catch (Exception ignored) {
        }
        return null;
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
                Toast.makeText(this, "Song Photo Permanently Saved in App!", Toast.LENGTH_SHORT).show();
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
