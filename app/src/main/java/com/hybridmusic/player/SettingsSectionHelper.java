package com.hybridmusic.player;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsSectionHelper {

    public interface SettingsCallbacks {
        void onOpenThemePicker();
        void onPickGalleryThemePhoto();
        void onResetThemePhoto();
        void onOpenEqualizerLab();
        void onTogglePocketShake();
        void onToggleDiscMode();
        void onToggleWaveformMode();
        void onToggleDjSlowFade();
        void onCustomizeHeader();
        void onVolumeChangedFromSettings(int newPercent);
        void onPlaySearchedSong(List<SongItem> list, int position);
        void onOpenSongOptions(SongItem song);
        default void onPuppyOrLanguageUpdated() {}
        default List<SongItem> getAllSongsForConverter() { return new ArrayList<>(); }
        default void onPlayTrimmedSong(SongItem song, int startSec, int endSec) {}
        default void onToggleMusicPetVisibility(boolean visible) {}
        default boolean isMusicPetVisible() { return true; }
        default String getPlayingTimeText() { return "0h 4min ▶"; }
        default void onRefreshAndFixSongs() {}
    }

    // =========================================================================
    // 1. SCREENSHOT (1000069379.jpg) "FOR YOU" TAB VIEW (Recently Played + Last Added)
    // =========================================================================
    public static View buildMusoForYouView(
            final Context context,
            final List<SongItem> allSongs,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

        final float d = context.getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (24 * d));

        // Section 1: Recently Played + "▶ Play" Pill
        LinearLayout recHeader = new LinearLayout(context);
        recHeader.setOrientation(LinearLayout.HORIZONTAL);
        recHeader.setGravity(Gravity.CENTER_VERTICAL);
        recHeader.setPadding(0, (int) (4 * d), 0, (int) (10 * d));

        TextView tvRec = new TextView(context);
        tvRec.setText("Recently Played");
        tvRec.setTextColor(Color.WHITE);
        tvRec.setTextSize(19f);
        tvRec.setTypeface(null, Typeface.BOLD);
        recHeader.addView(tvRec);

        TextView btnPlayRec = new TextView(context);
        btnPlayRec.setText("▶ Play");
        btnPlayRec.setTextColor(Color.WHITE);
        btnPlayRec.setTextSize(12f);
        btnPlayRec.setTypeface(null, Typeface.BOLD);
        btnPlayRec.setPadding((int) (14 * d), (int) (5 * d), (int) (14 * d), (int) (5 * d));
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playLp.leftMargin = (int) (12 * d);
        btnPlayRec.setLayoutParams(playLp);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Color.parseColor("#38FFFFFF"));
        pillBg.setCornerRadius(16f * d);
        btnPlayRec.setBackground(pillBg);
        btnPlayRec.setOnClickListener(v -> {
            if (!allSongs.isEmpty() && callbacks != null) callbacks.onPlaySearchedSong(allSongs, 0);
        });
        recHeader.addView(btnPlayRec);
        root.addView(recHeader);

        // Top 3 Recently Played Rows
        int topCount = Math.min(3, allSongs.size());
        for (int i = 0; i < topCount; i++) {
            final int pos = i;
            final SongItem s = allSongs.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (6 * d), 0, (int) (6 * d));

            ImageView img = new ImageView(context);
            img.setLayoutParams(new LinearLayout.LayoutParams((int) (58 * d), (int) (58 * d)));
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            MediaArtLoader.loadSongArtInto(context, s, themeManager, img);
            row.addView(img);

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding((int) (12 * d), 0, (int) (8 * d), 0);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView t = new TextView(context);
            t.setText(s.title);
            t.setTextColor(Color.WHITE);
            t.setTextSize(14.5f);
            t.setTypeface(null, Typeface.BOLD);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t);

            TextView a = new TextView(context);
            a.setText(s.artist);
            a.setTextColor(Color.parseColor("#94A3B8"));
            a.setTextSize(12f);
            col.addView(a);
            row.addView(col);

            TextView playCircle = new TextView(context);
            playCircle.setText("▶");
            playCircle.setTextColor(Color.WHITE);
            playCircle.setTextSize(11f);
            playCircle.setGravity(Gravity.CENTER);
            playCircle.setLayoutParams(new LinearLayout.LayoutParams((int) (28 * d), (int) (28 * d)));
            playCircle.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
            row.addView(playCircle);

            row.setOnClickListener(v -> {
                if (callbacks != null) callbacks.onPlaySearchedSong(allSongs, pos);
            });
            root.addView(row);
        }

        // Section 2: Last Added + Horizontal Square Cover Cards (Matches Screenshot 1000069379.jpg!)
        LinearLayout lastHeader = new LinearLayout(context);
        lastHeader.setOrientation(LinearLayout.HORIZONTAL);
        lastHeader.setGravity(Gravity.CENTER_VERTICAL);
        lastHeader.setPadding(0, (int) (16 * d), 0, (int) (10 * d));

        TextView tvLast = new TextView(context);
        tvLast.setText("Last Added");
        tvLast.setTextColor(Color.WHITE);
        tvLast.setTextSize(19f);
        tvLast.setTypeface(null, Typeface.BOLD);
        lastHeader.addView(tvLast);

        TextView btnPlayLast = new TextView(context);
        btnPlayLast.setText("▶ Play");
        btnPlayLast.setTextColor(Color.WHITE);
        btnPlayLast.setTextSize(12f);
        btnPlayLast.setTypeface(null, Typeface.BOLD);
        btnPlayLast.setPadding((int) (14 * d), (int) (5 * d), (int) (14 * d), (int) (5 * d));
        btnPlayLast.setLayoutParams(playLp);
        btnPlayLast.setBackground(pillBg);
        btnPlayLast.setOnClickListener(v -> {
            if (!allSongs.isEmpty() && callbacks != null) callbacks.onPlaySearchedSong(allSongs, 0);
        });
        lastHeader.addView(btnPlayLast);
        root.addView(lastHeader);

        HorizontalScrollView hScroll = new HorizontalScrollView(context);
        hScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout hRow = new LinearLayout(context);
        hRow.setOrientation(LinearLayout.HORIZONTAL);

        int cardLimit = Math.min(10, allSongs.size());
        for (int i = 0; i < cardLimit; i++) {
            final int pos = i;
            final SongItem s = allSongs.get(i);
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams((int) (112 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = (int) (12 * d);
            card.setLayoutParams(clp);

            ImageView cov = new ImageView(context);
            cov.setLayoutParams(new LinearLayout.LayoutParams((int) (112 * d), (int) (112 * d)));
            cov.setScaleType(ImageView.ScaleType.CENTER_CROP);
            MediaArtLoader.loadSongArtInto(context, s, themeManager, cov);
            card.addView(cov);

            TextView ct = new TextView(context);
            ct.setText(s.title);
            ct.setTextColor(Color.WHITE);
            ct.setTextSize(13f);
            ct.setTypeface(null, Typeface.BOLD);
            ct.setMaxLines(2);
            ct.setEllipsize(TextUtils.TruncateAt.END);
            ct.setPadding(0, (int) (6 * d), 0, (int) (2 * d));
            card.addView(ct);

            TextView ca = new TextView(context);
            ca.setText(s.artist);
            ca.setTextColor(Color.parseColor("#94A3B8"));
            ca.setTextSize(11.5f);
            ca.setSingleLine(true);
            card.addView(ca);

            card.setOnClickListener(v -> {
                if (callbacks != null) callbacks.onPlaySearchedSong(allSongs, pos);
            });
            hRow.addView(card);
        }
        hScroll.addView(hRow);
        root.addView(hScroll);

        // Section 3: Create Playlists Banner Card (Matches Bottom of Screenshot 1000069379.jpg!)
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setPadding((int) (18 * d), (int) (18 * d), (int) (18 * d), (int) (18 * d));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = (int) (18 * d);
        banner.setLayoutParams(blp);
        GradientDrawable banBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#EC4899"), Color.parseColor("#6366F1"), Color.parseColor("#0F172A")});
        banBg.setCornerRadius(20f * d);
        banner.setBackground(banBg);

        TextView bt1 = new TextView(context);
        bt1.setText("Create playlists");
        bt1.setTextColor(Color.WHITE);
        bt1.setTextSize(18f);
        bt1.setTypeface(null, Typeface.BOLD);
        banner.addView(bt1);

        TextView bt2 = new TextView(context);
        bt2.setText("Enjoy exclusive music during sports, work & drive");
        bt2.setTextColor(Color.parseColor("#F1F5F9"));
        bt2.setTextSize(12.5f);
        bt2.setPadding(0, (int) (4 * d), 0, 0);
        banner.addView(bt2);
        root.addView(banner);

        scroll.addView(root);
        return scroll;
    }

    // =========================================================================
    // 2. SCREENSHOT (1000069372.jpg) "PLAYLIST" TAB VIEW
    // =========================================================================
    public static View buildMusoPlaylistView(
            final Context context,
            final List<SongItem> allSongs,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

        final float d = context.getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (24 * d));

        int favCount = 0;
        for (SongItem s : allSongs) if (themeManager.isFavorite(s.id)) favCount++;

        addMusoPlaylistRow(context, root, d, "＋", "#27272A", "#94A3B8", "New Playlist", "", () ->
                Toast.makeText(context, "Tap any song's 3-dot menu to add to playlist!", Toast.LENGTH_SHORT).show());

        addMusoPlaylistRow(context, root, d, "♥", "#3B1224", "#FB7185", "Favorite",
                favCount > 0 ? favCount + " Songs" : "No Song", () -> {
                    List<SongItem> favs = new ArrayList<>();
                    for (SongItem s : allSongs) if (themeManager.isFavorite(s.id)) favs.add(s);
                    if (!favs.isEmpty() && callbacks != null) callbacks.onPlaySearchedSong(favs, 0);
                    else Toast.makeText(context, "No favorite songs yet", Toast.LENGTH_SHORT).show();
                });

        addMusoPlaylistRow(context, root, d, "🕒", "#2E1038", "#E879F9", "Recently",
                allSongs.isEmpty() ? "No Song" : Math.min(25, allSongs.size()) + " Songs", () -> {
                    if (!allSongs.isEmpty() && callbacks != null) callbacks.onPlaySearchedSong(allSongs, 0);
                });

        addMusoPlaylistRow(context, root, d, "☰♪", "#1E1B4B", "#818CF8", "Song With Lyrics",
                allSongs.size() + " Songs", () -> {
                    if (!allSongs.isEmpty() && callbacks != null) callbacks.onPlaySearchedSong(allSongs, 0);
                });

        addMusoPlaylistRow(context, root, d, "⇄", "#27272A", "#94A3B8", "Playlist Transfer",
                "Import & Sync Local Folders", () -> {
                    if (callbacks != null) callbacks.onRefreshAndFixSongs();
                });

        scroll.addView(root);
        return scroll;
    }

    private static void addMusoPlaylistRow(
            Context context, LinearLayout parent, float d,
            String symbol, String boxHex, String iconHex,
            String title, String subtitle, Runnable onClick) {

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (8 * d), 0, (int) (8 * d));

        TextView iconBox = new TextView(context);
        iconBox.setText(symbol);
        iconBox.setTextColor(Color.parseColor(iconHex));
        iconBox.setTextSize(24f);
        iconBox.setTypeface(null, Typeface.BOLD);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setLayoutParams(new LinearLayout.LayoutParams((int) (68 * d), (int) (68 * d)));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(boxHex));
        bg.setCornerRadius(14f * d);
        iconBox.setBackground(bg);
        row.addView(iconBox);

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (16 * d), 0, 0, 0);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16.5f);
        t.setTypeface(null, Typeface.BOLD);
        col.addView(t);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = new TextView(context);
            s.setText(subtitle);
            s.setTextColor(Color.parseColor("#94A3B8"));
            s.setTextSize(13f);
            s.setPadding(0, (int) (2 * d), 0, 0);
            col.addView(s);
        }
        row.addView(col);

        AudioThreeDotStudioHelper.attachBouncySpring(row, onClick);
        parent.addView(row);
    }

    // =========================================================================
    // 3. ULTRA-MODERN SEARCH SCREEN
    // =========================================================================
    public static View buildModernSearchScreen(
            final Context context,
            final List<SongItem> allSongs,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

        final float d = context.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (12 * d), (int) (6 * d), (int) (12 * d), (int) (6 * d));

        LinearLayout topPod = new LinearLayout(context);
        topPod.setOrientation(LinearLayout.HORIZONTAL);
        topPod.setGravity(Gravity.CENTER_VERTICAL);
        topPod.setPadding((int) (14 * d), (int) (9 * d), (int) (14 * d), (int) (9 * d));
        GradientDrawable podBg = new GradientDrawable();
        podBg.setColor(Color.parseColor("#18FFFFFF"));
        podBg.setCornerRadius(18f * d);
        podBg.setStroke(Math.max(1, (int) d), Color.WHITE);
        topPod.setBackground(podBg);

        final TextView statusTv = new TextView(context);
        statusTv.setText("✨ Instant Studio Search  •  " + allSongs.size() + " Songs");
        statusTv.setTextColor(Color.WHITE);
        statusTv.setTextSize(13f);
        statusTv.setTypeface(null, Typeface.BOLD);
        topPod.addView(statusTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(topPod);

        final RecyclerView searchRecycler = new RecyclerView(context);
        searchRecycler.setLayoutManager(new LinearLayoutManager(context));
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rvLp.topMargin = (int) (6 * d);
        root.addView(searchRecycler, rvLp);

        final int[] filterMode = {0};
        final List<SongItem> filteredList = new ArrayList<>(allSongs);
        final TextView[] pillBtns = new TextView[5];

        HorizontalScrollView pillScroll = new HorizontalScrollView(context);
        pillScroll.setHorizontalScrollBarEnabled(false);
        pillScroll.setPadding(0, (int) (6 * d), 0, (int) (6 * d));

        LinearLayout pillRow = new LinearLayout(context);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] titles = {"All (" + allSongs.size() + ")", "🎵 Audio", "🎬 Video-Audio", "❤️ Liked", "🔥 DJ / Bass"};

        LinearLayout searchCapsule = new LinearLayout(context);
        searchCapsule.setOrientation(LinearLayout.HORIZONTAL);
        searchCapsule.setGravity(Gravity.CENTER_VERTICAL);
        searchCapsule.setPadding((int) (14 * d), (int) (8 * d), (int) (12 * d), (int) (8 * d));

        GradientDrawable capBg = new GradientDrawable();
        capBg.setColor(Color.parseColor("#141E30"));
        capBg.setCornerRadius(26f * d);
        capBg.setStroke(Math.max(2, (int) (1.6f * d)), Color.WHITE);
        searchCapsule.setBackground(capBg);

        TextView searchBadge = new TextView(context);
        searchBadge.setText("🔍");
        searchBadge.setTextSize(15f);
        searchBadge.setPadding(0, 0, (int) (10 * d), 0);
        searchCapsule.addView(searchBadge);

        final EditText input = new EditText(context);
        input.setHint("Search songs, singers, albums, remix...");
        input.setSingleLine(true);
        input.setBackground(null);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setTextSize(14f);
        searchCapsule.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView clearBtn = new TextView(context);
        clearBtn.setText("Reset");
        clearBtn.setTextColor(Color.parseColor("#0A0A10"));
        clearBtn.setTextSize(11f);
        clearBtn.setTypeface(null, Typeface.BOLD);
        clearBtn.setPadding((int) (10 * d), (int) (4 * d), (int) (10 * d), (int) (4 * d));
        GradientDrawable clrBg = new GradientDrawable();
        clrBg.setColor(Color.WHITE);
        clrBg.setCornerRadius(12f * d);
        clearBtn.setBackground(clrBg);
        searchCapsule.addView(clearBtn);

        final Runnable runFilter = () -> {
            String q = input.getText().toString().toLowerCase(Locale.US).trim();
            filteredList.clear();
            for (SongItem s : allSongs) {
                if (filterMode[0] == 1 && s.isVideoAudio) continue;
                if (filterMode[0] == 2 && !s.isVideoAudio) continue;
                if (filterMode[0] == 3 && !themeManager.isFavorite(s.id)) continue;
                if (filterMode[0] == 4) {
                    String lower = (s.title + " " + s.artist).toLowerCase(Locale.US);
                    if (!lower.contains("dj") && !lower.contains("remix") && !lower.contains("bass")) continue;
                }

                if (q.isEmpty()
                        || s.title.toLowerCase(Locale.US).contains(q)
                        || s.artist.toLowerCase(Locale.US).contains(q)
                        || s.album.toLowerCase(Locale.US).contains(q)
                        || s.folderName.toLowerCase(Locale.US).contains(q)) {
                    filteredList.add(s);
                }
            }
            statusTv.setText("✨ Instant Results: " + filteredList.size() + " Tracks Found");

            for (int i = 0; i < pillBtns.length; i++) {
                GradientDrawable pBg = new GradientDrawable();
                pBg.setCornerRadius(16f * d);
                if (i == filterMode[0]) {
                    pBg.setColor(Color.WHITE);
                    pillBtns[i].setTextColor(Color.parseColor("#0A0A10"));
                } else {
                    pBg.setColor(Color.parseColor("#1EFFFFFF"));
                    pBg.setStroke(Math.max(1, (int) d), Color.WHITE);
                    pillBtns[i].setTextColor(Color.WHITE);
                }
                pillBtns[i].setBackground(pBg);
            }

            SongAdapter ad = new SongAdapter(context, filteredList, themeManager, false, new SongAdapter.OnSongClickListener() {
                @Override
                public void onSongClick(int position) {
                    if (callbacks != null) callbacks.onPlaySearchedSong(filteredList, position);
                }
                @Override
                public void onThreeDotClick(SongItem song, int position) {
                    if (callbacks != null) callbacks.onOpenSongOptions(song);
                }
            });
            searchRecycler.setAdapter(ad);
        };

        for (int i = 0; i < titles.length; i++) {
            final int idx = i;
            TextView pill = new TextView(context);
            pill.setText(titles[i]);
            pill.setTextSize(11.5f);
            pill.setTypeface(null, Typeface.BOLD);
            pill.setPadding((int) (13 * d), (int) (6 * d), (int) (13 * d), (int) (6 * d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) (6 * d);
            AudioThreeDotStudioHelper.attachBouncySpring(pill, () -> {
                filterMode[0] = idx;
                runFilter.run();
            });
            pillBtns[i] = pill;
            pillRow.addView(pill, lp);
        }

        pillScroll.addView(pillRow);
        root.addView(pillScroll);
        root.addView(searchCapsule);

        AudioThreeDotStudioHelper.attachBouncySpring(clearBtn, () -> input.setText(""));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { runFilter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        runFilter.run();
        return root;
    }

    // =========================================================================
    // 4. EXACT SCREENSHOT (1000069380.jpg) "ME" SECTION
    // =========================================================================
    public static View buildModernSettingsScreen(
            final Context context,
            final ThemeManager themeManager,
            final PocketShakeManager pocketShakeManager,
            final int currentVolumePercent,
            final SettingsCallbacks callbacks) {

        final float d = context.getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (28 * d));

        // 1. TOP PROFILE & "👑 Remove Ads" HEADER (Matches Screenshot 1000069380.jpg!)
        LinearLayout profileRow = new LinearLayout(context);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        profileRow.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (14 * d));

        TextView avatarCircle = new TextView(context);
        avatarCircle.setText("♫");
        avatarCircle.setTextColor(Color.WHITE);
        avatarCircle.setTextSize(28f);
        avatarCircle.setGravity(Gravity.CENTER);
        avatarCircle.setLayoutParams(new LinearLayout.LayoutParams((int) (68 * d), (int) (68 * d)));
        GradientDrawable avBg = new GradientDrawable();
        avBg.setShape(GradientDrawable.OVAL);
        avBg.setColor(Color.parseColor("#44FFFFFF"));
        avBg.setStroke(Math.max(2, (int) (2 * d)), Color.WHITE);
        avatarCircle.setBackground(avBg);
        profileRow.addView(avatarCircle);

        LinearLayout loginCol = new LinearLayout(context);
        loginCol.setOrientation(LinearLayout.VERTICAL);
        loginCol.setPadding((int) (16 * d), 0, 0, 0);
        loginCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvLogin = new TextView(context);
        tvLogin.setText(themeManager.isVipAllThemesUnlocked() ? "VIP Studio Member" : "Login");
        tvLogin.setTextColor(Color.WHITE);
        tvLogin.setTextSize(20f);
        tvLogin.setTypeface(null, Typeface.BOLD);
        loginCol.addView(tvLogin);

        TextView btnRemoveAds = new TextView(context);
        btnRemoveAds.setText("👑 Remove Ads  •  " + themeManager.getActiveVipPlan());
        btnRemoveAds.setTextColor(Color.parseColor("#1E293B"));
        btnRemoveAds.setTextSize(11.5f);
        btnRemoveAds.setTypeface(null, Typeface.BOLD);
        btnRemoveAds.setPadding((int) (12 * d), (int) (4 * d), (int) (12 * d), (int) (4 * d));
        LinearLayout.LayoutParams raLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        raLp.topMargin = (int) (6 * d);
        btnRemoveAds.setLayoutParams(raLp);
        GradientDrawable raBg = new GradientDrawable();
        raBg.setColor(Color.parseColor("#D8E2F0"));
        raBg.setCornerRadius(14f * d);
        btnRemoveAds.setBackground(raBg);
        loginCol.addView(btnRemoveAds);

        profileRow.addView(loginCol);
        AudioThreeDotStudioHelper.attachBouncySpring(profileRow, () -> {
            if (callbacks != null) callbacks.onOpenThemePicker();
        });
        root.addView(profileRow);

        // 2. "Playing Time: 0h 4min ▶" GLASS CARD (Matches Screenshot 1000069380.jpg!)
        LinearLayout timeCard = new LinearLayout(context);
        timeCard.setOrientation(LinearLayout.VERTICAL);
        timeCard.setPadding((int) (18 * d), (int) (16 * d), (int) (18 * d), (int) (16 * d));
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tcLp.bottomMargin = (int) (14 * d);
        timeCard.setLayoutParams(tcLp);
        GradientDrawable tcBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#2A1228"), Color.parseColor("#18122B")});
        tcBg.setCornerRadius(18f * d);
        tcBg.setStroke(Math.max(1, (int) (1.2f * d)), Color.parseColor("#55FFFFFF"));
        timeCard.setBackground(tcBg);

        TextView tvPtLabel = new TextView(context);
        tvPtLabel.setText("Playing Time");
        tvPtLabel.setTextColor(Color.parseColor("#CBD5E1"));
        tvPtLabel.setTextSize(12.5f);
        timeCard.addView(tvPtLabel);

        TextView tvPtVal = new TextView(context);
        tvPtVal.setText(callbacks != null ? callbacks.getPlayingTimeText() : "0h 4min ▶");
        tvPtVal.setTextColor(Color.parseColor("#F472B6"));
        tvPtVal.setTextSize(20f);
        tvPtVal.setTypeface(null, Typeface.BOLD);
        tvPtVal.setPadding(0, (int) (4 * d), 0, 0);
        timeCard.addView(tvPtVal);
        root.addView(timeCard);

        // 3. THREE SQUARE CARDS ROW: [👕 Style Center] + [👁️ Hide & Scan] + [🎵 MP3 Convert]
        LinearLayout triRow = new LinearLayout(context);
        triRow.setOrientation(LinearLayout.HORIZONTAL);
        triRow.setWeightSum(3f);
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trLp.bottomMargin = (int) (14 * d);
        triRow.setLayoutParams(trLp);

        addMusoTopSquareCard(context, triRow, d, "👕", "#2D1E3E", "Style Center", () -> {
            if (callbacks != null) callbacks.onOpenThemePicker();
        });

        addMusoTopSquareCard(context, triRow, d, "👁️", "#1B332B", "Hide & Scan", () -> {
            if (callbacks != null) callbacks.onRefreshAndFixSongs();
            Toast.makeText(context, "🔍 Scanned & filtered short audio clips!", Toast.LENGTH_SHORT).show();
        });

        addMusoTopSquareCard(context, triRow, d, "🎵⇄", "#1E2D3D", "MP3 Convert", () ->
                showConverterAndSongCutterFolderDialog(context, themeManager, callbacks));

        root.addView(triRow);

        // 4. DARK ROUNDED LIST CONTAINER BELOW (Settings, Help & Feedback, Share, Fix Songs, Group, About, Music Pet)
        LinearLayout listCard = new LinearLayout(context);
        listCard.setOrientation(LinearLayout.VERTICAL);
        listCard.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (10 * d));
        GradientDrawable lcBg = new GradientDrawable();
        lcBg.setColor(Color.parseColor("#12151E"));
        lcBg.setCornerRadius(22f * d);
        lcBg.setStroke(Math.max(1, (int) d), Color.parseColor("#28FFFFFF"));
        listCard.setBackground(lcBg);

        addMusoMenuItem(context, listCard, d, "⚙️", "Settings (Themes, Language, Shake, EQ)", false, () ->
                showSongSettingsFolderDialog(context, themeManager, pocketShakeManager, currentVolumePercent, callbacks));

        addMusoMenuItem(context, listCard, d, "💬", "Help & Feedback", false, () ->
                showRateUsDialog(context));

        addMusoMenuItem(context, listCard, d, "📤", "Share Hybrid Music Player", false, () -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, "Listen to music with 60 Studio Themes & Cute Dancing Pet on Hybrid Music Player!");
            context.startActivity(Intent.createChooser(share, "Share App"));
        });

        addMusoMenuItem(context, listCard, d, "🔧", "Fix Songs (Repair Covers & Scan)", true, () -> {
            if (callbacks != null) callbacks.onRefreshAndFixSongs();
            Toast.makeText(context, "🔧 All songs & album covers repaired!", Toast.LENGTH_SHORT).show();
        });

        addMusoMenuItem(context, listCard, d, "🌐", "Language Folder (" + themeManager.getAppLanguage() + ")", false, () ->
                showLanguageFolderDialog(context, themeManager, callbacks));

        addMusoMenuItem(context, listCard, d, "ℹ️", "About us & Privacy Policy", false, () ->
                showAboutUsDialog(context, themeManager));

        // Bottom Row: "🐶 Music Pet" with Pink Toggle Switch + Tap to Open 15 Cute Animals Studio!
        LinearLayout petRow = new LinearLayout(context);
        petRow.setOrientation(LinearLayout.HORIZONTAL);
        petRow.setGravity(Gravity.CENTER_VERTICAL);
        petRow.setPadding((int) (6 * d), (int) (14 * d), (int) (6 * d), (int) (14 * d));

        TextView petIcon = new TextView(context);
        petIcon.setText("🐶");
        petIcon.setTextSize(18f);
        petIcon.setPadding(0, 0, (int) (14 * d), 0);
        petRow.addView(petIcon);

        TextView petTitle = new TextView(context);
        petTitle.setText("Music Pet (Tap for 15 Cute Animals)");
        petTitle.setTextColor(Color.WHITE);
        petTitle.setTextSize(15.5f);
        petTitle.setTypeface(null, Typeface.BOLD);
        petTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        petTitle.setOnClickListener(v -> showCutePuppyStudioFolderDialog(context, themeManager, callbacks));
        petRow.addView(petTitle);

        Switch petSwitch = new Switch(context);
        petSwitch.setChecked(callbacks == null || callbacks.isMusicPetVisible());
        petSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (callbacks != null) callbacks.onToggleMusicPetVisibility(isChecked);
        });
        petRow.addView(petSwitch);
        listCard.addView(petRow);

        root.addView(listCard);
        scroll.addView(root);
        return scroll;
    }

    private static void addMusoTopSquareCard(
            Context context, LinearLayout parent, float d,
            String emoji, String bgHex, String label, Runnable onClick) {

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding((int) (8 * d), (int) (16 * d), (int) (8 * d), (int) (16 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (4 * d), 0, (int) (4 * d), 0);
        card.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(16f * d);
        bg.setStroke(Math.max(1, (int) d), Color.parseColor("#33FFFFFF"));
        card.setBackground(bg);

        TextView ic = new TextView(context);
        ic.setText(emoji);
        ic.setTextColor(Color.WHITE);
        ic.setTextSize(24f);
        ic.setGravity(Gravity.CENTER);
        card.addView(ic);

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, (int) (8 * d), 0, 0);
        card.addView(tv);

        AudioThreeDotStudioHelper.attachBouncySpring(card, onClick);
        parent.addView(card);
    }

    private static void addMusoMenuItem(
            Context context, LinearLayout parent, float d,
            String icon, String title, boolean showPinkDot, Runnable onClick) {

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (6 * d), (int) (14 * d), (int) (6 * d), (int) (14 * d));

        TextView ic = new TextView(context);
        ic.setText(icon);
        ic.setTextColor(Color.parseColor("#CBD5E1"));
        ic.setTextSize(17f);
        ic.setPadding(0, 0, (int) (14 * d), 0);
        row.addView(ic);

        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);

        if (showPinkDot) {
            View dot = new View(context);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int) (8 * d), (int) (8 * d));
            dlp.rightMargin = (int) (10 * d);
            dot.setLayoutParams(dlp);
            GradientDrawable dBg = new GradientDrawable();
            dBg.setShape(GradientDrawable.OVAL);
            dBg.setColor(Color.parseColor("#FF2E93"));
            dot.setBackground(dBg);
            row.addView(dot);
        }

        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextColor(Color.parseColor("#94A3B8"));
        arrow.setTextSize(20f);
        row.addView(arrow);

        AudioThreeDotStudioHelper.attachBouncySpring(row, onClick);
        parent.addView(row);
    }

    public static void showCutePuppyStudioFolderDialog(
            final Context context,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

        final float d = context.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * d);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F7080C14"));
        bg.setCornerRadius(26f * d);
        bg.setStroke(Math.max(2, (int) (1.8f * d)), Color.WHITE);
        root.setBackground(bg);

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(root).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout previewBox = new LinearLayout(context);
        previewBox.setOrientation(LinearLayout.HORIZONTAL);
        previewBox.setGravity(Gravity.CENTER_VERTICAL);
        previewBox.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (12 * d));

        final DancingPuppyView liveMascot = new DancingPuppyView(context);
        liveMascot.setPlayingState(true);
        liveMascot.setLayoutParams(new LinearLayout.LayoutParams((int) (82 * d), (int) (82 * d)));
        previewBox.addView(liveMascot);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding((int) (12 * d), 0, 0, 0);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvH = new TextView(context);
        tvH.setText("🐶 Muso Pet & 15 Cute Animals");
        tvH.setTextColor(Color.WHITE);
        tvH.setTextSize(16f);
        tvH.setTypeface(null, Typeface.BOLD);
        titleCol.addView(tvH);

        final TextView tvActiveInfo = new TextView(context);
        tvActiveInfo.setText(ThemeManager.PUPPY_BREED_NAMES[themeManager.getPuppyBreedIndex()]
                + "\nColor: " + ThemeManager.PUPPY_COLOR_NAMES[themeManager.getPuppyColorIndex()]);
        tvActiveInfo.setTextColor(Color.parseColor("#CBD5E1"));
        tvActiveInfo.setTextSize(12f);
        titleCol.addView(tvActiveInfo);
        previewBox.addView(titleCol);
        root.addView(previewBox);

        ScrollView scroll = new ScrollView(context);
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < ThemeManager.PUPPY_BREED_NAMES.length; i++) {
            final int breedIdx = i;
            TextView row = new TextView(context);
            row.setText(ThemeManager.PUPPY_BREED_NAMES[i]);
            row.setTextColor(Color.WHITE);
            row.setTextSize(13f);
            row.setTypeface(null, Typeface.BOLD);
            row.setPadding((int) (14 * d), (int) (11 * d), (int) (14 * d), (int) (11 * d));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int) (6 * d);
            row.setLayoutParams(rlp);

            GradientDrawable rBg = new GradientDrawable();
            rBg.setColor(themeManager.getPuppyBreedIndex() == i ? Color.parseColor("#38FFFFFF") : Color.parseColor("#18FFFFFF"));
            rBg.setCornerRadius(14f * d);
            rBg.setStroke(Math.max(1, (int) d), Color.WHITE);
            row.setBackground(rBg);

            row.setOnClickListener(v -> {
                themeManager.setPuppyBreedIndex(breedIdx);
                liveMascot.setPreviewBreedAndColor(breedIdx, themeManager.getPuppyColorIndex());
                liveMascot.triggerGravityJump();
                tvActiveInfo.setText(ThemeManager.PUPPY_BREED_NAMES[breedIdx]
                        + "\nColor: " + ThemeManager.PUPPY_COLOR_NAMES[themeManager.getPuppyColorIndex()]);
                if (callbacks != null) callbacks.onPuppyOrLanguageUpdated();
            });
            col.addView(row);
        }

        GridLayout colorGrid = new GridLayout(context);
        colorGrid.setColumnCount(3);
        for (int c = 0; c < ThemeManager.PUPPY_COLOR_NAMES.length; c++) {
            final int colorIdx = c;
            TextView chip = new TextView(context);
            chip.setText(ThemeManager.PUPPY_COLOR_NAMES[c]);
            chip.setTextSize(10.5f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding((int) (6 * d), (int) (10 * d), (int) (6 * d), (int) (10 * d));

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins((int) (4 * d), (int) (4 * d), (int) (4 * d), (int) (4 * d));
            chip.setLayoutParams(glp);

            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(ThemeManager.PUPPY_COLOR_HEX[c]);
            cBg.setCornerRadius(12f * d);
            cBg.setStroke(Math.max(2, (int) (1.5f * d)), Color.WHITE);
            chip.setBackground(cBg);
            chip.setTextColor(Color.parseColor("#0A0A10"));

            chip.setOnClickListener(v -> {
                themeManager.setPuppyColorIndex(colorIdx);
                liveMascot.setPreviewBreedAndColor(themeManager.getPuppyBreedIndex(), colorIdx);
                liveMascot.triggerGravityJump();
                tvActiveInfo.setText(ThemeManager.PUPPY_BREED_NAMES[themeManager.getPuppyBreedIndex()]
                        + "\nColor: " + ThemeManager.PUPPY_COLOR_NAMES[colorIdx]);
                if (callbacks != null) callbacks.onPuppyOrLanguageUpdated();
            });
            colorGrid.addView(chip);
        }
        col.addView(colorGrid);

        TextView doneBtn = new TextView(context);
        doneBtn.setText("Apply Music Pet");
        doneBtn.setTextColor(Color.parseColor("#0A0A10"));
        doneBtn.setTextSize(14f);
        doneBtn.setTypeface(null, Typeface.BOLD);
        doneBtn.setGravity(Gravity.CENTER);
        doneBtn.setPadding(0, (int) (12 * d), 0, (int) (12 * d));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = (int) (12 * d);
        doneBtn.setLayoutParams(dlp);
        GradientDrawable dBg = new GradientDrawable();
        dBg.setColor(Color.WHITE);
        dBg.setCornerRadius(16f * d);
        doneBtn.setBackground(dBg);
        doneBtn.setOnClickListener(v -> dialog.dismiss());
        col.addView(doneBtn);

        scroll.addView(col);
        root.addView(scroll);
        dialog.show();
    }

    private static void showLanguageFolderDialog(
            final Context context, final ThemeManager themeManager, final SettingsCallbacks callbacks) {

        final String[] langs = new String[]{
                "Hinglish (Default India Studio)",
                "Hindi (हिंदी - भारत)",
                "English (International Studio)",
                "Punjabi (ਪੰਜਾਬੀ म्यूजिक)",
                "Haryanvi (हरियाणवी देसी स्टूडियो)"
        };
        final String[] codes = new String[]{"Hinglish", "Hindi", "English", "Punjabi", "Haryanvi"};

        SongAdapter.showPureWhiteAudioMenuDialog(context, "🌐 Select App Language Folder", langs, which -> {
            themeManager.setAppLanguage(codes[which]);
            if (callbacks != null) callbacks.onPuppyOrLanguageUpdated();
            Toast.makeText(context, "Language Applied: " + langs[which], Toast.LENGTH_SHORT).show();
        });
    }

    private static void showSongSettingsFolderDialog(
            final Context context, final ThemeManager themeManager,
            final PocketShakeManager pocketShakeManager, final int currentVolumePercent,
            final SettingsCallbacks callbacks) {

        boolean shakeOn = pocketShakeManager != null && pocketShakeManager.isEnabled();
        String[] items = new String[]{
                "🎨 Open 60 Studio Themes & VIP Store (" + themeManager.getCurrentTheme().name + ")",
                "🖼️ Set Custom Gallery Photo as App Background",
                "🔄 Restore Clean Default Theme Background",
                "🔊 Master Volume 200% Booster (Current: " + currentVolumePercent + "%)",
                "📳 Pocket Shake Sensor: " + (shakeOn ? "ON (2x Hand Shake)" : "OFF (Default Safe)"),
                "💿 Center Art Style: " + (themeManager.isVinylDiscMode() ? "Rotating Disc + Top Danda" : "Square Album Box (. .)"),
                "📊 Seekbar Style: " + (themeManager.isWaveformMode() ? "Lambe Dande (Waveform)" : "Simple Straight Line"),
                "🎧 10-Second DJ Slow Fade Transition: " + (themeManager.isDjSlowFadeEnabled() ? "ON" : "OFF"),
                "⭐ Customize Top Header Name & Star Badge"
        };

        SongAdapter.showPureWhiteAudioMenuDialog(context, "⚙️ Song & Player Settings", items, which -> {
            if (callbacks == null) return;
            switch (which) {
                case 0: callbacks.onOpenThemePicker(); break;
                case 1: callbacks.onPickGalleryThemePhoto(); break;
                case 2: callbacks.onResetThemePhoto(); break;
                case 3:
                    int nextVol = currentVolumePercent >= 200 ? 100 : 200;
                    callbacks.onVolumeChangedFromSettings(nextVol);
                    break;
                case 4: callbacks.onTogglePocketShake(); break;
                case 5: callbacks.onToggleDiscMode(); break;
                case 6: callbacks.onToggleWaveformMode(); break;
                case 7: callbacks.onToggleDjSlowFade(); break;
                case 8: callbacks.onCustomizeHeader(); break;
            }
        });
    }

    public static void showConverterAndSongCutterFolderDialog(
            final Context context, final ThemeManager themeManager, final SettingsCallbacks callbacks) {

        final List<SongItem> allSongs = callbacks != null ? callbacks.getAllSongsForConverter() : new ArrayList<>();
        String[] tools = new String[]{
                "✂️ Song Cutter / Aadha Gaana Chalayein (Set Start & End Time)",
                "⚡ Quick Play First Half (Shuru ka 50% Aadha Gaana Chalayein)",
                "🔥 Quick Play Second Half (Beech se 50%-100% Gaana Chalayein)",
                "🎬 Convert Video Song to Pure Audio Stream (MP3/M4A Mode)",
                "🔔 Cut & Set Song as Phone Ringtone",
                "🎛️ Open Lofi / Slowed + Reverb & Pitch Converter"
        };

        SongAdapter.showPureWhiteAudioMenuDialog(context, "🎵 MP3 Convert & Song Cutter", tools, which -> {
            if (allSongs.isEmpty()) {
                Toast.makeText(context, "Pehle phone mein koi song load hone dein", Toast.LENGTH_SHORT).show();
                return;
            }
            if (which == 5) {
                if (callbacks != null) callbacks.onOpenEqualizerLab();
                return;
            }
            showPickSongForConverterDialog(context, allSongs, themeManager, callbacks, which);
        });
    }

    private static void showPickSongForConverterDialog(
            final Context context, final List<SongItem> songs,
            final ThemeManager themeManager, final SettingsCallbacks callbacks, final int toolMode) {

        int limit = Math.min(songs.size(), 40);
        String[] titles = new String[limit];
        for (int i = 0; i < limit; i++) {
            titles[i] = (i + 1) + ". " + songs.get(i).title + " (" + songs.get(i).getFormattedDuration() + ")";
        }

        SongAdapter.showPureWhiteAudioMenuDialog(context, "Select Song to Edit / Convert", titles, idx -> {
            final SongItem chosen = songs.get(idx);
            int totalSec = Math.max(10, (int) (chosen.duration / 1000L));

            if (toolMode == 0) {
                showCustomStartEndTrimmerDialog(context, chosen, totalSec, themeManager, callbacks);
            } else if (toolMode == 1) {
                int halfSec = totalSec / 2;
                themeManager.setSongTrimSeconds(chosen.id, 0, halfSec);
                if (callbacks != null) callbacks.onPlayTrimmedSong(chosen, 0, halfSec);
                Toast.makeText(context, "✂️ Playing First Half (0s to " + halfSec + "s): " + chosen.title, Toast.LENGTH_LONG).show();
            } else if (toolMode == 2) {
                int halfSec = totalSec / 2;
                themeManager.setSongTrimSeconds(chosen.id, halfSec, totalSec);
                if (callbacks != null) callbacks.onPlayTrimmedSong(chosen, halfSec, totalSec);
                Toast.makeText(context, "✂️ Playing Second Half (" + halfSec + "s to " + totalSec + "s): " + chosen.title, Toast.LENGTH_LONG).show();
            } else if (toolMode == 3) {
                chosen.isVideoAudio = false;
                if (callbacks != null) callbacks.onPlayTrimmedSong(chosen, 0, totalSec);
                Toast.makeText(context, "🎵 Converted to Pure Audio Stream: " + chosen.title, Toast.LENGTH_LONG).show();
            } else if (toolMode == 4) {
                AudioThreeDotStudioHelper.setSongAsPhoneRingtone(context, chosen);
            }
        });
    }

    private static void showCustomStartEndTrimmerDialog(
            final Context context, final SongItem song, final int totalSec,
            final ThemeManager themeManager, final SettingsCallbacks callbacks) {

        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * d);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundColor(Color.parseColor("#101826"));

        final TextView tvRange = new TextView(context);
        tvRange.setText("✂️ Trim Range: 0 sec to " + (totalSec / 2) + " sec (Total: " + totalSec + "s)");
        tvRange.setTextColor(Color.WHITE);
        tvRange.setTextSize(14f);
        tvRange.setTypeface(null, Typeface.BOLD);
        box.addView(tvRange);

        final SeekBar sbStart = new SeekBar(context);
        sbStart.setMax(totalSec - 5);
        sbStart.setProgress(0);
        box.addView(sbStart);

        final SeekBar sbEnd = new SeekBar(context);
        sbEnd.setMax(totalSec);
        sbEnd.setProgress(totalSec / 2);
        box.addView(sbEnd);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int s = sbStart.getProgress();
                int e = Math.max(s + 5, sbEnd.getProgress());
                tvRange.setText("✂️ Trim Range: " + s + " sec to " + e + " sec (Total: " + totalSec + "s)");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        sbStart.setOnSeekBarChangeListener(listener);
        sbEnd.setOnSeekBarChangeListener(listener);

        new AlertDialog.Builder(context)
                .setTitle("Song Editor: " + song.title)
                .setView(box)
                .setPositiveButton("Play Cut Song Now", (d1, w1) -> {
                    int s = sbStart.getProgress();
                    int e = Math.max(s + 5, sbEnd.getProgress());
                    themeManager.setSongTrimSeconds(song.id, s, e);
                    if (callbacks != null) callbacks.onPlayTrimmedSong(song, s, e);
                })
                .setNeutralButton("Reset Full Song", (d2, w2) -> {
                    themeManager.setSongTrimSeconds(song.id, 0, 0);
                    if (callbacks != null) callbacks.onPlayTrimmedSong(song, 0, totalSec);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void showRateUsDialog(final Context context) {
        String[] stars = {
                "⭐⭐⭐⭐⭐  5 Stars - Mind-Blowing Music Player!",
                "⭐⭐⭐⭐  4 Stars - Very Smooth & Cute Mascot!",
                "💬 Send Feedback"
        };
        SongAdapter.showPureWhiteAudioMenuDialog(context, "💬 Help & Feedback", stars, which ->
                Toast.makeText(context, "❤️ Thank you for supporting Hybrid Music Player!", Toast.LENGTH_LONG).show());
    }

    private static void showAboutUsDialog(Context context, ThemeManager tm) {
        new AlertDialog.Builder(context)
                .setTitle("ℹ️ About Hybrid Music Player")
                .setMessage("Hybrid Music Player ★ (v3.0 Muso Studio Edition)\n\n"
                        + "• Built for Android 5 to Android 16\n"
                        + "• 60 Studio Themes (3-Ad Unlock & VIP Store)\n"
                        + "• Muso 3D Pet + 15 Cute Animals with Gravity Physics\n"
                        + "• Buttonless 5-Band Dolby EQ, 8D Surround & 432Hz/528Hz\n"
                        + "• Status: " + tm.getActiveVipPlan())
                .setPositiveButton("Awesome!", null)
                .show();
    }
}
