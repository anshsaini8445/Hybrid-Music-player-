
import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private MusicPlaybackService musicService;
    private boolean isBound = false;
    private ThemeManager themeManager;
    private PocketShakeManager pocketShakeManager;

    private List<SongItem> allSongs = new ArrayList<>();
    private RecyclerView recyclerView;
    private SongAdapter songAdapter;

    private DancingPuppyView headerPuppyView;
    private TextView miniTitle, miniArtist;
    private Button miniPlayPauseBtn;
    private View miniPlayerBar;

    private static final int PERMISSION_REQ_CODE = 2026;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlaybackService.LocalBinder binder = (MusicPlaybackService.LocalBinder) service;
            musicService = binder.getService();
            isBound = true;
            updateMiniPlayerUI();
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

        headerPuppyView = findViewById(R.id.headerPuppyView);
        miniTitle = findViewById(R.id.miniTitle);
        miniArtist = findViewById(R.id.miniArtist);
        miniPlayPauseBtn = findViewById(R.id.miniPlayPauseBtn);
        miniPlayerBar = findViewById(R.id.miniPlayerBar);

        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        FrameLayoutContainerAdd(recyclerView);

        pocketShakeManager = new PocketShakeManager(this, () -> {
            if (musicService != null) {
                musicService.playNext();
                Toast.makeText(this, "Pocket Shake: Next Song Played!", Toast.LENGTH_SHORT).show();
            }
        });

        // Start & Bind MusicPlaybackService
        Intent intent = new Intent(this, MusicPlaybackService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        setupBottomNavigation();
        checkPermissionsAndLoadSongs();

        findViewById(R.id.profileThemeButton).setOnClickListener(v -> showThemeChooserDialog());
        miniPlayPauseBtn.setOnClickListener(v -> {
            if (musicService != null) {
                musicService.playToggle();
                updateMiniPlayerUI();
            }
        });
    }

    private void FrameLayoutContainerAdd(View view) {
        android.widget.FrameLayout container = findViewById(R.id.mainContentContainer);
        container.removeAllViews();
        container.addView(view);
    }

    private void checkPermissionsAndLoadSongs() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{perm, Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQ_CODE);
        } else {
            loadDeviceAudioSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadDeviceAudioSongs();
        } else {
            Toast.makeText(this, "Storage permission is required to play audio songs.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadDeviceAudioSongs() {
        allSongs.clear();
        ContentResolver resolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED
        };

        try (Cursor cursor = resolver.query(uri, projection, MediaStore.Audio.Media.IS_MUSIC + "!=0", null, MediaStore.Audio.Media.DATE_ADDED + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
                int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

                do {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    String album = cursor.getString(albumCol);
                    String path = cursor.getString(dataCol);
                    long albumId = cursor.getLong(albumIdCol);
                    long duration = cursor.getLong(durCol);
                    long size = cursor.getLong(sizeCol);
                    long dateAdded = cursor.getLong(dateCol);

                    String mediaUriStr = Uri.withAppendedPath(uri, String.valueOf(id)).toString();
                    boolean isVideoAudio = path != null && (path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".8k"));

                    allSongs.add(new SongItem(id, title, artist, album, path, mediaUriStr, albumId, duration, size, dateAdded, isVideoAudio, "Music"));
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {
        }

        songAdapter = new SongAdapter(this, allSongs, themeManager, new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(int position) {
                if (musicService != null) {
                    musicService.setQueue(allSongs, position);
                    updateMiniPlayerUI();
                }
            }

            @Override
            public void onThreeDotClick(SongItem song, View view) {
                showSongOptionsDialog(song);
            }
        });
        recyclerView.setAdapter(songAdapter);
    }

    private void setupBottomNavigation() {
        findViewById(R.id.navSongsBtn).setOnClickListener(v -> {
            if (songAdapter != null) {
                recyclerView.setAdapter(songAdapter);
                FrameLayoutContainerAdd(recyclerView);
            }
        });

        findViewById(R.id.navSearchBtn).setOnClickListener(v -> showSearchScreen());
        findViewById(R.id.navSettingsBtn).setOnClickListener(v -> showSettingsScreen());
    }

    private void showSearchScreen() {
        LinearLayout searchLayout = new LinearLayout(this);
        searchLayout.setOrientation(LinearLayout.VERTICAL);
        searchLayout.setPadding(16, 16, 16, 16);

        EditText searchInput = new EditText(this);
        searchInput.setHint("Search songs, artists, folders...");
        searchInput.setTextColor(0xFFFFFFFF);
        searchInput.setHintTextColor(0xFF64748B);
        searchLayout.addView(searchInput);

        RecyclerView searchResultRecycler = new RecyclerView(this);
        searchResultRecycler.setLayoutManager(new LinearLayoutManager(this));
        searchLayout.addView(searchResultRecycler);

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase().trim();
                List<SongItem> filtered = new ArrayList<>();
                for (SongItem song : allSongs) {
                    if (song.title.toLowerCase().contains(query) || song.artist.toLowerCase().contains(query)) {
                        filtered.add(song);
                    }
                }
                SongAdapter adapter = new SongAdapter(MainActivity.this, filtered, themeManager, new SongAdapter.OnSongClickListener() {
                    @Override
                    public void onSongClick(int position) {
                        if (musicService != null) {
                            musicService.setQueue(filtered, position);
                            updateMiniPlayerUI();
                        }
                    }

                    @Override
                    public void onThreeDotClick(SongItem song, View view) {
                        showSongOptionsDialog(song);
                    }
                });
                searchResultRecycler.setAdapter(adapter);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        FrameLayoutContainerAdd(searchLayout);
    }

    private void showSettingsScreen() {
        LinearLayout settingsLayout = new LinearLayout(this);
        settingsLayout.setOrientation(LinearLayout.VERTICAL);
        settingsLayout.setPadding(20, 20, 20, 20);

        Button themeBtn = new Button(this);
        themeBtn.setText("Change 20+ Themes & Gallery Photo");
        themeBtn.setOnClickListener(v -> showThemeChooserDialog());
        settingsLayout.addView(themeBtn);

        Button eqBtn = new Button(this);
        eqBtn.setText("PLAYit Equalizer, Speed & Sleep Timer");
        eqBtn.setOnClickListener(v -> {
            if (musicService != null) {
                new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
            }
        });
        settingsLayout.addView(eqBtn);

        Button pocketBtn = new Button(this);
        pocketBtn.setText(pocketShakeManager.isEnabled() ? "Pocket Shake: ON (Tap to Disable)" : "Pocket Shake: OFF (Tap to Enable)");
        pocketBtn.setOnClickListener(v -> {
            boolean newState = !pocketShakeManager.isEnabled();
            pocketShakeManager.setEnabled(newState);
            pocketBtn.setText(newState ? "Pocket Shake: ON (Tap to Disable)" : "Pocket Shake: OFF (Tap to Enable)");
            Toast.makeText(this, newState ? "Pocket Shake Enabled (Double-Shake)" : "Pocket Shake Disabled", Toast.LENGTH_SHORT).show();
        });
        settingsLayout.addView(pocketBtn);

        FrameLayoutContainerAdd(settingsLayout);
    }

    private void showSongOptionsDialog(SongItem song) {
        String[] options = {"Play Now", "Add to Favorites", "Audio Sound & Equalizer", "Permanent Phone Delete"};
        new AlertDialog.Builder(this)
                .setTitle(song.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (musicService != null) {
                            int idx = allSongs.indexOf(song);
                            if (idx >= 0) {
                                musicService.setQueue(allSongs, idx);
                                updateMiniPlayerUI();
                            }
                        }
                    } else if (which == 1) {
                        boolean fav = themeManager.toggleFavorite(song.id);
                        Toast.makeText(this, fav ? "Added to Favorites" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        if (musicService != null) {
                            new EqualizerSpeedTimerDialog(this, musicService, themeManager).show();
                        }
                    } else if (which == 3) {
                        SongAdapter.showPermanentDeleteDialog(this, song, () -> loadDeviceAudioSongs());
                    }
                })
                .show();
    }

    private void showThemeChooserDialog() {
        ThemeManager.ThemePreset[] themes = themeManager.getAllThemes();
        String[] names = new String[themes.length];
        for (int i = 0; i < themes.length; i++) {
            names[i] = themes[i].name;
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Hybrid Music Theme (1 to 20)")
                .setItems(names, (dialog, which) -> {
                    themeManager.setThemeIndex(which);
                    Toast.makeText(this, "Applied: " + names[which], Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Pick Gallery Photo", (d, w) -> {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    startActivityForResult(photoPickerIntent, 999);
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 999 && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                themeManager.setCustomBgUri(imageUri.toString());
                Toast.makeText(this, "Custom Gallery Theme Photo Applied Successfully!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateMiniPlayerUI() {
        if (musicService != null) {
            SongItem current = musicService.getCurrentSong();
            if (current != null) {
                miniTitle.setText(current.title);
                miniArtist.setText(current.artist);
                boolean playing = musicService.isPlaying();
                miniPlayPauseBtn.setBackgroundResource(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                if (headerPuppyView != null) {
                    headerPuppyView.setPlayingState(playing);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        if (pocketShakeManager != null) {
            pocketShakeManager.setEnabled(false);
        }
    }
}
