package com.hybridmusic.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
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
    }

    // 1. MODERN BOTTOM SEARCH SCREEN (Instant Filtering + Thumb-Friendly Bottom Bar)
    public static View buildModernSearchScreen(
            final Context context,
            final List<SongItem> allSongs,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 12);

        final TextView statusTv = new TextView(context);
        statusTv.setText("🔍 Instant Search • " + allSongs.size() + " Songs Ready");
        statusTv.setTextColor(Color.WHITE);
        statusTv.setTextSize(14f);
        statusTv.setTypeface(null, Typeface.BOLD);
        statusTv.setPadding(8, 4, 8, 10);
        root.addView(statusTv);

        final RecyclerView searchRecycler = new RecyclerView(context);
        searchRecycler.setLayoutManager(new LinearLayoutManager(context));
        root.addView(searchRecycler, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final int[] filterMode = {0}; // 0=All, 1=Audio, 2=VideoAudio, 3=Favorites, 4=LongSongs
        final List<SongItem> filteredList = new ArrayList<>(allSongs);
        final TextView[] pillBtns = new TextView[5];

        // Bottom Filter Pills Row (Right above bottom search input)
        HorizontalScrollView pillScroll = new HorizontalScrollView(context);
        pillScroll.setHorizontalScrollBarEnabled(false);
        pillScroll.setPadding(0, 10, 0, 10);

        LinearLayout pillRow = new LinearLayout(context);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] titles = {"All (" + allSongs.size() + ")", "🎵 Audio", "🎬 Video-Audio", "❤️ Favorites", "📏 Long Tracks"};

        // Bottom Search Bar Container
        LinearLayout searchBox = new LinearLayout(context);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(24, 12, 18, 12);

        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(Color.parseColor("#141E2B"));
        boxBg.setCornerRadius(44f);
        boxBg.setStroke(3, Color.WHITE);
        searchBox.setBackground(boxBg);

        final EditText input = new EditText(context);
        input.setHint("Type song, singer or folder name...");
        input.setSingleLine(true);
        input.setBackground(null);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setTextSize(15f);
        searchBox.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView clearMenuBtn = new TextView(context);
        clearMenuBtn.setText("⋮");
        clearMenuBtn.setTextColor(Color.WHITE);
        clearMenuBtn.setTextSize(20f);
        clearMenuBtn.setTypeface(null, Typeface.BOLD);
        clearMenuBtn.setPadding(18, 4, 12, 4);
        searchBox.addView(clearMenuBtn);

        final Runnable runFilter = () -> {
            String q = input.getText().toString().toLowerCase(Locale.US).trim();
            filteredList.clear();
            for (SongItem s : allSongs) {
                if (filterMode[0] == 1 && s.isVideoAudio) continue;
                if (filterMode[0] == 2 && !s.isVideoAudio) continue;
                if (filterMode[0] == 3 && !themeManager.isFavorite(s.id)) continue;

                if (q.isEmpty()
                        || s.title.toLowerCase(Locale.US).contains(q)
                        || s.artist.toLowerCase(Locale.US).contains(q)
                        || s.album.toLowerCase(Locale.US).contains(q)
                        || s.folderName.toLowerCase(Locale.US).contains(q)) {
                    filteredList.add(s);
                }
            }
            if (filterMode[0] == 4) {
                Collections.sort(filteredList, (a, b) -> Long.compare(b.duration, a.duration));
            }
            statusTv.setText("🔍 Matching Results: " + filteredList.size() + " Songs");

            for (int i = 0; i < pillBtns.length; i++) {
                GradientDrawable pBg = new GradientDrawable();
                pBg.setCornerRadius(44f);
                if (i == filterMode[0]) {
                    pBg.setColor(Color.WHITE);
                    pillBtns[i].setTextColor(Color.BLACK);
                } else {
                    pBg.setColor(Color.parseColor("#16202C"));
                    pBg.setStroke(2, Color.WHITE);
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
            pill.setTextSize(12.5f);
            pill.setTypeface(null, Typeface.BOLD);
            pill.setPadding(30, 16, 30, 16);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = 12;
            pill.setOnClickListener(v -> {
                filterMode[0] = idx;
                runFilter.run();
            });
            pillBtns[i] = pill;
            pillRow.addView(pill, lp);
        }

        pillScroll.addView(pillRow);
        root.addView(pillScroll);
        root.addView(searchBox);

        clearMenuBtn.setOnClickListener(v -> input.setText(""));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { runFilter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        runFilter.run();
        return root;
    }

    // 2. MODERN MUSO SETTINGS (MI) DASHBOARD (100% Real Working, Zero Dummy)
    public static View buildModernSettingsScreen(
            final Context context,
            final ThemeManager themeManager,
            final PocketShakeManager pocketShakeManager,
            final int currentVolumePercent,
            final SettingsCallbacks callbacks) {

        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 16, 20, 32);

        // Title Banner
        TextView header = new TextView(context);
        header.setText("⚙️ Hybrid Muso Studio Settings (MI)");
        header.setTextColor(Color.WHITE);
        header.setTextSize(18f);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(6, 4, 6, 18);
        root.addView(header);

        // Live 200% Volume Booster Slider Card (Triggers Screenshot Volume Patti!)
        LinearLayout volCard = createModernCard(context);
        final TextView volLabel = new TextView(context);
        volLabel.setText("🔊 Master Volume & 4-Dot Booster: " + currentVolumePercent + "%");
        volLabel.setTextColor(Color.WHITE);
        volLabel.setTextSize(14.5f);
        volLabel.setTypeface(null, Typeface.BOLD);
        volCard.addView(volLabel);

        SeekBar volSeek = new SeekBar(context);
        volSeek.setMax(200);
        volSeek.setProgress(currentVolumePercent);
        volSeek.setPadding(10, 16, 10, 8);
        volSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volLabel.setText("🔊 Master Volume & 4-Dot Booster: " + progress + "%");
                if (fromUser && callbacks != null) {
                    callbacks.onVolumeChangedFromSettings(progress);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        volCard.addView(volSeek);
        root.addView(volCard);

        // Real Functional Settings Cards
        addActionCard(context, root,
                "🎨 20+ Studio Themes (Global Theme Engine)",
                "Active: " + themeManager.getCurrentTheme().name + " • Auto White Contrast",
                v -> callbacks.onOpenThemePicker());

        addActionCard(context, root,
                "🖼️ Set Custom Gallery Photo as App Background",
                themeManager.hasCustomBgPhoto() ? "Custom Photo Active (Tap to Change)" : "Pick any photo from phone gallery",
                v -> callbacks.onPickGalleryThemePhoto());

        addActionCard(context, root,
                "🔄 Restore Clean Default Theme Background",
                "Remove gallery background photo and keep solid dark/light theme",
                v -> callbacks.onResetThemePhoto());

        addActionCard(context, root,
                "🎛️ Sound & Pitch Lab (Lofi / Slowed + Reverb + 5-Band EQ)",
                "Real hardware 5-Band Dolby Equalizer, Sub-Bass, Speed & Pitch Shift",
                v -> callbacks.onOpenEqualizerLab());

        boolean shakeOn = pocketShakeManager != null && pocketShakeManager.isEnabled();
        addActionCard(context, root,
                "📳 Pocket Shake Sensor: " + (shakeOn ? "ON (2x Hand Shake)" : "OFF (Default Safe)"),
                "Proximity pocket protection + double hand shake to skip track",
                v -> callbacks.onTogglePocketShake());

        addActionCard(context, root,
                "💿 Center Art Style: " + (themeManager.isVinylDiscMode() ? "Rotating Vinyl Disc + Top Danda" : "Square Album Box (. .)"),
                "Tap to switch between Square Box and Spinning Vinyl Record with Tonearm",
                v -> callbacks.onToggleDiscMode());

        addActionCard(context, root,
                "📊 Seekbar Style: " + (themeManager.isWaveformMode() ? "Lambe Dande (Waveform Bars)" : "Simple Straight Line"),
                "Tap to switch between Tall Waveform Bars and Clean Straight Line",
                v -> callbacks.onToggleWaveformMode());

        addActionCard(context, root,
                "🎧 10-Second DJ Slow Fade Transition: " + (themeManager.isDjSlowFadeEnabled() ? "ON" : "OFF"),
                "Smoothly slows down and fades out during the last 10 seconds of every song",
                v -> callbacks.onToggleDjSlowFade());

        addActionCard(context, root,
                "⭐ Customize App Title & Star Icon (" + themeManager.getHeaderTitle() + " " + themeManager.getHeaderStar() + ")",
                "Change header app name and star badge anytime",
                v -> callbacks.onCustomizeHeader());

        scroll.addView(root);
        return scroll;
    }

    private static LinearLayout createModernCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 24, 28, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#141E2B"));
        bg.setCornerRadius(32f);
        bg.setStroke(2, Color.WHITE);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 16;
        card.setLayoutParams(lp);
        return card;
    }

    private static void addActionCard(Context context, LinearLayout parent,
                                      String title, String subtitle, View.OnClickListener click) {
        LinearLayout card = createModernCard(context);
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15f);
        t.setTypeface(null, Typeface.BOLD);
        card.addView(t);

        TextView s = new TextView(context);
        s.setText(subtitle);
        s.setTextColor(Color.parseColor("#CBD5E1"));
        s.setTextSize(12.5f);
        s.setPadding(0, 6, 0, 0);
        card.addView(s);

        card.setOnClickListener(click);
        parent.addView(card);
    }
}
