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
import android.graphics.BitmapFactory;
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

    private int currentTopTab = 0; // 0=All, 1=VideoAudio, 2=Fav, 3=Playlists, 4=Folders, 5=Artists
    private int currentBottomTab = 0; // 0=Songs, 1=Search, 2=Settings(MI)
    private long songIdForPhotoPick = -1;

    // UI References
    private View rootLayout, topHeaderBar, categoryTabsScroll, miniPlayerBar, bottomNavBar, fullPlayerOverlay;
    private ImageView customThemeBgImage, miniThumbnail;
    private TextView appTitleText, miniTitle, miniArtist;
    private Button miniPlayPauseBtn;
    private DancingPuppyView headerPuppyView, fpPuppyView;

    // Full Player UI References
    private AlbumArtDiscView fpAlbumArtDiscView;
    private View fpLyricsContainer, queueBackdropOverlay, queueSlidePanel;
    private TextView fpLyricsText, fpSongTitle, fpSongArtist, fpCurrentTime, fpTotalTime, fpHeaderStatus, queueHeaderTitle;
    private WaveformSeekBarView fpWaveformSeekBar;
    private Button fpPlayPauseBtn, fpShuffleBtn, fpRepeatBtn, fpPocketShakeBtn, fpQueueBtn, fpLyricsFavBtn;
    private final Button[] bottomSlotBtns = new Button[4];
    private RecyclerView queueRecyclerView;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private GestureDetector tabSwipeDetector;

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (musicService != null && isBound) {
                int pos = musicService.getCurrentPosition();
                int dur = musicService.getDuration();
                fpWaveformSeekBar.setProgressFraction((float) pos / Math.max(1, dur));
                fpWaveformSeekBar.setABMarkers(musicService.getFractionA(), musicService.getFractionB());
                fpWaveformSeekBar.setPlaying(musicService.isPlaying());
                fpCurrentTime.setText(formatMs(pos));
                fpTotalTime.setText(formatMs(dur));
            }
            uiHandler.postDelayed(this, 500);
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
        // Pocket Shake is STRICTLY OFF by default when app opens
        pocketShakeManager = new PocketShakeManager(this, () -> {
            if (musicService != null) {
                musicService.playNext();
                Toast.makeText(this, "📳 Double-Shake: Playing Next Song!", Toast.LENGTH_SHORT).show();
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
        categoryTabsScroll = findViewById(R.id.categoryTabsScroll);
        miniPlayerBar = findViewById(R.id.miniPlayerBar);
        bottomNavBar = findViewById(R.id.bottomNavBar);
        fullPlayerOverlay = findViewById(R.id.fullPlayerOverlay);

        appTitleText = findViewById(R.id.appTitleText);
        headerPuppyView = findViewById(R.id.headerPuppyView);
        miniThumbnail = findViewById(R.id.miniThumbnail);
        miniTitle = findViewById(R.id.miniTitle);
        miniTitle.setSelected(true);
        miniArtist = findViewById(R.id.miniArtist);
        miniPlayPauseBtn = findViewById(R.id.miniPlayPauseBtn);

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
        fpHeaderStatus = findViewById(R.id.fpHeaderStatus);
        fpWaveformSeekBar = findViewById(R.id.fpWaveformSeekBar);
        fpPlayPauseBtn = findViewById(R.id.fpPlayPauseBtn);
        fpShuffleBtn = findViewById(R.id.fpShuffleBtn);
        fpRepeatBtn = findViewById(R.id.fpRepeatBtn);
        fpPocketShakeBtn = findViewById(R.id.fpPocketShakeBtn);
        fpQueueBtn = findViewById(R.id.fpQueueBtn);

        bottomSlotBtns[0] = findViewById(R.id.fpSlot0Btn);
        bottomSlotBtns[1] = findViewById(R.id.fpSlot1Btn);
        bottomSlotBtns[2] = findViewById(R.id.fpSlot2Btn);
        bottomSlotBtns[3] = findViewById(R.id.fpSlot3Btn);

        queueBackdropOverlay = findViewById(R.id.queueBackdropOverlay);
        queueSlidePanel = findViewById(R.id.queueSlidePanel);
        queueHeaderTitle = findViewById(R.id.queueHeaderTitle);
        queueRecyclerView = findViewById(R.id.queueRecyclerView);
        queueRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mainRecyclerView = new RecyclerView(this);
        mainRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupInteractions() {
        // Customizable App Name & Star Badge on tap
        appTitleText.setOnClickListener(v -> showCustomizeHeaderDialog());
        findViewById(R.id.topSortMenuBtn).setOnClickListener(v -> showSortMenuDialog());
        findViewById(R.id.profileThemeButton).setOnClickListener(v -> showThemePickerDialog());

        // Top Category Tabs
        findViewById(R.id.tabAllSongs).setOnClickListener(v -> selectTopCategoryTab(0));
        findViewById(R.id.tabVideoAudio).setOnClickListener(v -> selectTopCategoryTab(1));
        findViewById(R.id.tabFavorites).setOnClickListener(v -> selectTopCategoryTab(2));
        findViewById(R.id.tabPlaylists).setOnClickListener(v -> selectTopCategoryTab(3));
        findViewById(R.id.tabFolders).setOnClickListener(v -> selectTopCategoryTab(4));
        findViewById(R.id.tabArtists).setOnClickListener(v -> selectTopCategoryTab(5));

        // Horizontal Swipe Gesture on Song List (Songs -> VideoAudio -> Fav -> Playlists -> Folders -> Artists -> Search -> Settings)
        tabSwipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy) * 1.6f && Math.abs(dx) > 130) {
                    if (dx < 0) {
                        // Swipe Right-to-Left
                        if (currentBottomTab == 0) {
                            if (currentTopTab < 5) selectTopCategoryTab(currentTopTab + 1);
                            else showBottomSection(1); // After last folder/artist tab -> jump to Search!
                        } else if (currentBottomTab == 1) {
                            showBottomSection(2); // From Search -> jump to Settings (MI)!
                        }
                    } else {
                        // Swipe Left-to-Right
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

        // Bottom Navigation
        findViewById(R.id.navSongsBtn).setOnClickListener(v -> showBottomSection(0));
        findViewById(R.id.navSearchBtn).setOnClickListener(v -> showBottomSection(1));
        findViewById(R.id.navSettingsBtn).setOnClickListener(v -> showBottomSection(2));

        // Mini Player Bar
        miniPlayerBar.setOnClickListener(v -> openFullPlayer());
        miniPlayPauseBtn.setOnClickListener(v -> {
            if (musicService != null) musicService.playToggle();
        });
        findViewById(R.id.miniNextBtn).setOnClickListener(v -> {
            if (musicService != null) musicService.playNext();
        });

        // Full Player Controls
        findViewById(R.id.fpCloseBtn).setOnClickListener(v -> fullPlayerOverlay.setVisibility(View.GONE));
        findViewById(R.id.fpThreeDotBtn).setOnClickListener(v -> showFullPlayerThreeDotMenu());
        fpQueueBtn.setOnClickListener(v -> openRightSlideQueue());

        // Tap anywhere outside the Queue Panel to close Playing Queue immediately
        queueBackdropOverlay.setOnClickListener(v -> closeRightSlideQueue());
        findViewById(R.id.queueCloseCutBtn).setOnClickListener(v -> closeRightSlideQueue());

        // Square Art / Vinyl Disc Gesture: Swipe Right-to-Left opens Lyrics automatically
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
                fpLyricsFavBtn.setText(fav ? "❤️ Favorited" : "♡ Add Favorite");
                Toast.makeText(this, fav ? "Added to Favorites ❤️" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
            }
        });

        // Pocket Shake toggle button (located where Like button used to be)
        fpPocketShakeBtn.setOnClickListener(v -> {
            boolean nextState = !pocketShakeManager.isEnabled();
            pocketShakeManager.setEnabled(nextState);
            updatePocketShakeButtonUI();
            Toast.makeText(this, nextState
                    ? "Pocket Shake ON (Do baar haath se hilane par Next Song chalega)"
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
        findViewById(R.id.fpPrevBtn).setOnClickListener(v -> {
            if (musicService != null) musicService.playPrevious();
        });
        findViewById(R.id.fpNextBtn).setOnClickListener(v -> {
            if (musicService != null) musicService.playNext();
        });

        fpShuffleBtn.setOnClickListener(v -> {
            if (musicService != null) {
                boolean next = !musicService.isShuffle();
                musicService.setShuffle(next);
                syncPlayerUI();
                Toast.makeText(this, next ? "Shuffle: ON 🔀" : "Shuffle: OFF", Toast.LENGTH_SHORT).show();
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

        // 1. Load All Audio Songs (MP3, AAC, WAV, FLAC, M4A, OGG)
        queryMediaUri(resolver, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, false);

        // 2. Load All Video Songs (MP4, MKV, 4K, 8K) to play seamlessly as Audio!
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

                    if (duration < 3000) continue; // Skip tiny notification beeps
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
            for (SongItem s : allSongs) if (!s.isVideoAudio) displayedSongs.add(s);
        } else if (tabIndex == 1) {
            for (SongItem s : allSongs) if (s.isVideoAudio) displayedSongs.add(s);
        } else if (tabIndex == 2) {
            for (SongItem s : allSongs) if (themeManager.isFavorite(s.id)) displayedSongs.add(s);
        } else if (tabIndex == 3) {
            showPlaylistFilterPicker();
            return;
        } else if (tabIndex == 4) {
            List<SongItem> copy = new ArrayList<>(allSongs);
            Collections.sort(copy, (a, b) -> a.folderName.compareToIgnoreCase(b.folderName));
            displayedSongs.addAll(copy);
        } else if (tabIndex == 5) {
            List<SongItem> copy = new ArrayList<>(allSongs);
            Collections.sort(copy, (a, b) -> a.artist.compareToIgnoreCase(b.artist));
            displayedSongs.addAll(copy);
        }

        refreshMainListAdapter();
    }

    private void showPlaylistFilterPicker() {
        List<String> names = themeManager.getPlaylistNames();
        String[] arr = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Select Playlist Folder")
                .setItems(arr, (d, which) -> {
                    Set<String> ids = themeManager.getPlaylistSongs(arr[which]);
                    displayedSongs.clear();
                    for (SongItem s : allSongs) {
                        if (ids.contains(String.valueOf(s.id))) displayedSongs.add(s);
                    }
                    if (displayedSongs.isEmpty()) displayedSongs.addAll(allSongs);
                    refreshMainListAdapter();
                })
                .show();
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
                showSongThreeDotSheet(song);
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

        categoryTabsScroll.setVisibility(sectionIndex == 0 ? View.VISIBLE : View.GONE);

        if (sectionIndex == 0) {
            container.addView(mainRecyclerView);
        } else if (sectionIndex == 1) {
            container.addView(buildBottomSearchScreen());
        } else if (sectionIndex == 2) {
            container.addView(buildMISettingsScreen());
        }
        applyActiveThemeEverywhere();
    }

    // Search Screen: Instant filtering + Search Bar & Audio/Video Pills at the BOTTOM
    private View buildBottomSearchScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 8, 12, 8);

        RecyclerView searchRecycler = new RecyclerView(this);
        searchRecycler.setLayoutManager(new LinearLayoutManager(this));
        LinearLayout.LayoutParams rvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(searchRecycler, rvParams);

        final int[] filterMode = {0}; // 0=All, 1=Audio Only, 2=Video-Audio Only
        final List<SongItem> filteredList = new ArrayList<>(allSongs);

        // Bottom Filter Pills (Right above the bottom search bar so thumb never reaches top!)
        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setGravity(Gravity.CENTER);

        Button pillAll = new Button(this);
        pillAll.setText("All Media (" + allSongs.size() + ")");
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

        // Bottom Search Bar
        EditText searchBar = new EditText(this);
        searchBar.setHint("🔍 Type here for instant song search...");
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
                    showSongThreeDotSheet(song);
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

    // Real Functional Settings (MI) Screen - Zero Dummy Items!
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
            Toast.makeText(this, "Clean Theme Restored", Toast.LENGTH_SHORT).show();
        });
        addSettingButton(box, "📳 Pocket Shake (" + (pocketShakeManager.isEnabled() ? "ON" : "OFF - Default") + ")", v -> {
            boolean next = !pocketShakeManager.isEnabled();
            pocketShakeManager.setEnabled(next);
            showBottomSection(2);
            Toast.makeText(this, next ? "Pocket Shake ON (2x Hand Shake)" : "Pocket Shake OFF", Toast.LENGTH_SHORT).show();
        });
        addSettingButton(box, "💿 Toggle Player Style: Square Box vs Rotating Disc + Danda", v -> {
            boolean next = !themeManager.isVinylDiscMode();
            themeManager.setVinylDiscMode(next);
            fpAlbumArtDiscView.setVinylDiscMode(next);
            Toast.makeText(this, next ? "Vinyl Disc + Top Danda Mode ON" : "Square Album Box Mode ON", Toast.LENGTH_SHORT).show();
        });
        addSettingButton(box, "📊 Toggle Seekbar: Lambe Dande (Waveform) vs Simple Line", v -> {
            boolean next = !themeManager.isWaveformMode();
            themeManager.setWaveformMode(next);
            fpWaveformSeekBar.setWaveformStyle(next);
            Toast.makeText(this, next ? "Lambe Dande (Waveform) ON" : "Simple Player Line ON", Toast.LENGTH_SHORT).show();
        });
        addSettingButton(box, "🎧 10-Second DJ Slow Fade Transition (" + (themeManager.isDjSlowFadeEnabled() ? "ON" : "OFF") + ")", v -> {
            boolean next = !themeManager.isDjSlowFadeEnabled();
            themeManager.setDjSlowFadeEnabled(next);
            if (musicService != null) musicService.setDjSlowFadeEnabled(next);
            showBottomSection(2);
        });
        addSettingButton(box, "🎚️ Open PLAYit 5-Band Equalizer, Speed & Sleep Timer", v -> {
            if (musicService != null) new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
        });
        addSettingButton(box, "⭐ Customize Header Title & Star Symbol", v -> showCustomizeHeaderDialog());

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
            fpLyricsFavBtn.setText(themeManager.isFavorite(s.id) ? "❤️ Favorited" : "♡ Add Favorite");
            String saved = themeManager.getSavedLyrics(s.id);
            if (saved != null && !saved.isEmpty()) {
                fpLyricsText.setText(saved);
            } else if (autoSearchIfEmpty) {
                fetchOnlineLyricsForCurrentSong();
            } else {
                fpLyricsText.setText("🎵 " + s.title + " - " + s.artist + "\n\nTap 'Find Online Lyrics' below to search via Internet.");
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
                String cleanTitle = song.title.replaceAll("\\(.*?\\)|\\[.*?\\]", "").trim();
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
                    : "🎶 " + song.title + "\nSinger: " + song.artist + "\n\n(Online lyrics not found for this regional track. You can paste/save custom lyrics from the 3-dot menu!)";

            if (resultLyrics != null && !resultLyrics.trim().isEmpty()) {
                themeManager.saveCustomLyrics(song.id, resultLyrics);
            }
            runOnUiThread(() -> fpLyricsText.setText(finalLyrics));
        }).start();
    }

    private void showFullPlayerThreeDotMenu() {
        String[] items = {
                "💿 Switch Square Box / Rotating Disc + Danda",
                "📊 Switch Lambe Dande (Waveform) / Simple Line",
                "🖼️ Set Custom Photo for This Song from Library",
                "🔄 Reorder Bottom 4 Icons (Timer, A-B, EQ, Speed)",
                "🎚️ Open 5-Band Equalizer, Speed & Sleep Timer",
                "📝 Edit / Save Custom Lyrics for This Song"
        };
        new AlertDialog.Builder(this)
                .setTitle("Player Customization & Options")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        boolean next = !themeManager.isVinylDiscMode();
                        themeManager.setVinylDiscMode(next);
                        fpAlbumArtDiscView.setVinylDiscMode(next);
                    } else if (which == 1) {
                        boolean next = !themeManager.isWaveformMode();
                        themeManager.setWaveformMode(next);
                        fpWaveformSeekBar.setWaveformStyle(next);
                    } else if (which == 2) {
                        if (musicService != null && musicService.getCurrentSong() != null) {
                            songIdForPhotoPick = musicService.getCurrentSong().id;
                            Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(pick, 902);
                        }
                    } else if (which == 3) {
                        themeManager.rotateBottomControlOrder();
                        refreshBottomSwappableButtons();
                        Toast.makeText(this, "Bottom 4 Controls Order Swapped!", Toast.LENGTH_SHORT).show();
                    } else if (which == 4) {
                        if (musicService != null) new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
                    } else if (which == 5) {
                        showEditLyricsDialog();
                    }
                })
                .show();
    }

    private void refreshBottomSwappableButtons() {
        int[] order = themeManager.getBottomControlOrder();
        String[] labels = {"⏲ Timer", "🔁 A-B", "🎚 Equalizer", "⚡ Speed"};
        for (int slot = 0; slot < 4; slot++) {
            final int actionType = order[slot];
            bottomSlotBtns[slot].setText(labels[actionType]);
            bottomSlotBtns[slot].setOnClickListener(v -> {
                if (musicService == null) return;
                if (actionType == 0 || actionType == 2 || actionType == 3) {
                    new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
                } else if (actionType == 1) {
                    String status = musicService.cycleABRepeat();
                    Toast.makeText(this, "A-B Repeat: " + status, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void showSongThreeDotSheet(SongItem song) {
        String[] opts = {
                "▶ Play Song Now",
                "❤️ Toggle Favorite",
                "🖼️ Set Song Photo from Gallery (Permanent)",
                "➕ Add to Playlist",
                "🎚️ 5-Band Equalizer & Speed Slider",
                "ℹ️ Song Details & Path",
                "🗑️ Delete Song Permanently from Phone"
        };
        new AlertDialog.Builder(this)
                .setTitle(song.title)
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        int idx = displayedSongs.indexOf(song);
                        if (musicService != null && idx >= 0) {
                            musicService.setQueue(displayedSongs, idx);
                            openFullPlayer();
                        }
                    } else if (which == 1) {
                        boolean fav = themeManager.toggleFavorite(song.id);
                        Toast.makeText(this, fav ? "Added to Favorites ❤️" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        songIdForPhotoPick = song.id;
                        Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        startActivityForResult(pick, 902);
                    } else if (which == 3) {
                        themeManager.addSongToPlaylist("My Top Hits", song.id);
                        Toast.makeText(this, "Added to 'My Top Hits'", Toast.LENGTH_SHORT).show();
                    } else if (which == 4) {
                        if (musicService != null) new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
                    } else if (which == 5) {
                        new AlertDialog.Builder(this)
                                .setTitle("Song Details")
                                .setMessage("Title: " + song.title + "\nSinger: " + song.artist
                                        + "\nDuration: " + song.getFormattedDuration()
                                        + "\nSize: " + song.getFormattedSize()
                                        + "\nPath: " + song.path)
                                .setPositiveButton("OK", null).show();
                    } else if (which == 6) {
                        SongAdapter.showPermanentDeleteDialog(this, song, this::loadAllAudioAndVideoSongs);
                    }
                })
                .show();
    }

    private void showSortMenuDialog() {
        String[] sorts = {"🔤 A to Z Songs", "🆕 New Downloaded Songs", "📏 Long Size / Duration Songs"};
        new AlertDialog.Builder(this)
                .setTitle("Sort Songs (High Contrast)")
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
        nameIn.setHint("App Name (e.g. Hybrid Music Player)");
        box.addView(nameIn);

        final EditText starIn = new EditText(this);
        starIn.setText(themeManager.getHeaderStar());
        starIn.setHint("Star Badge (e.g. ★, ⭐, ⚡, 👑)");
        box.addView(starIn);

        new AlertDialog.Builder(this)
                .setTitle("Customize App Name & Star Icon")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    themeManager.setHeaderTitle(nameIn.getText().toString().trim());
                    themeManager.setHeaderStar(starIn.getText().toString().trim());
                    applyActiveThemeEverywhere();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditLyricsDialog() {
        if (musicService == null || musicService.getCurrentSong() == null) return;
        final SongItem song = musicService.getCurrentSong();
        final EditText input = new EditText(this);
        input.setText(themeManager.getSavedLyrics(song.id));
        input.setHint("Paste or type song lyrics here...");
        new AlertDialog.Builder(this)
                .setTitle("Save Lyrics: " + song.title)
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    themeManager.saveCustomLyrics(song.id, input.getText().toString());
                    fpLyricsText.setText(input.getText().toString());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyActiveThemeEverywhere() {
        ThemeManager.ThemePreset t = themeManager.getCurrentTheme();
        int priText = themeManager.getPrimaryTextColor();
        int secText = themeManager.getSecondaryTextColor();

        rootLayout.setBackgroundColor(t.bgPrimary);
        fullPlayerOverlay.setBackgroundColor(t.bgPrimary);
        topHeaderBar.setBackgroundColor(t.bgSurface);
        bottomNavBar.setBackgroundColor(t.bgSurface);
        miniPlayerBar.setBackgroundColor(t.bgCard);

        appTitleText.setText(themeManager.getHeaderTitle() + " " + themeManager.getHeaderStar());
        appTitleText.setTextColor(priText);
        miniTitle.setTextColor(priText);
        miniArtist.setTextColor(secText);
        fpSongTitle.setTextColor(priText);
        fpSongArtist.setTextColor(secText);
        fpHeaderStatus.setTextColor(t.accentPrimary);

        headerPuppyView.setAccentColor(t.accentPrimary);
        fpPuppyView.setAccentColor(t.accentPrimary);
        fpAlbumArtDiscView.setAccentColor(t.accentPrimary);
        fpWaveformSeekBar.setAccentColor(t.accentPrimary);

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
        fpPocketShakeBtn.setTextColor(on ? themeManager.getCurrentTheme().accentPrimary : 0xFFCBD5E1);
    }

    private void syncPlayerUI() {
        if (musicService == null) return;
        SongItem cur = musicService.getCurrentSong();
        boolean playing = musicService.isPlaying();

        headerPuppyView.setPlayingState(playing);
        fpPuppyView.setPlayingState(playing);
        fpAlbumArtDiscView.setPlaying(playing);
        fpWaveformSeekBar.setPlaying(playing);

        // Play ("▶") when paused, Two Straight Bars ("❚❚" / Do Dande) when playing
        miniPlayPauseBtn.setText(playing ? "❚❚" : "▶");
        fpPlayPauseBtn.setText(playing ? "❚❚" : "▶");

        List<SongItem> q = musicService.getQueueList();
        fpQueueBtn.setText("Queue (" + (q.isEmpty() ? 0 : musicService.getCurrentIndex() + 1) + "/" + q.size() + ")");

        fpShuffleBtn.setTextColor(musicService.isShuffle()
                ? themeManager.getCurrentTheme().accentPrimary : 0xFFCBD5E1);

        int rep = musicService.getRepeatMode();
        fpRepeatBtn.setText(rep == 1 ? "🔂" : (rep == 0 ? "🔁" : "➡️"));
        fpRepeatBtn.setTextColor(rep != 2
                ? themeManager.getCurrentTheme().accentPrimary : 0xFFCBD5E1);

        updatePocketShakeButtonUI();

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
