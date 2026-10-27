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
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
    }

    // =========================================================================
    // 1. ULTRA-MODERN GLASS FLOATING SEARCH SCREEN
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

        // Top Glass Status Header
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
        statusTv.setText("✨ AI Instant Studio Radar  •  " + allSongs.size() + " Songs");
        statusTv.setTextColor(Color.WHITE);
        statusTv.setTextSize(13f);
        statusTv.setTypeface(null, Typeface.BOLD);
        topPod.addView(statusTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(topPod);

        // Center Results List
        final RecyclerView searchRecycler = new RecyclerView(context);
        searchRecycler.setLayoutManager(new LinearLayoutManager(context));
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rvLp.topMargin = (int) (6 * d);
        root.addView(searchRecycler, rvLp);

        final int[] filterMode = {0};
        final List<SongItem> filteredList = new ArrayList<>(allSongs);
        final TextView[] pillBtns = new TextView[5];

        // Sleek Floating Filter Chips
        HorizontalScrollView pillScroll = new HorizontalScrollView(context);
        pillScroll.setHorizontalScrollBarEnabled(false);
        pillScroll.setPadding(0, (int) (6 * d), 0, (int) (6 * d));

        LinearLayout pillRow = new LinearLayout(context);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] titles = {"All (" + allSongs.size() + ")", "🎵 Audio", "🎬 Video-Audio", "❤️ Liked", "🔥 DJ / Bass"};

        // Ultra-Modern Glowing Glass Search Capsule at Bottom
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
    // 2. ULTRA-MODERN BENTO-GRID "ME" SECTION (Settings)
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
        root.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (26 * d));

        // 1. TOP LUXURY VIP MEMBERSHIP & MONETIZATION CARD (₹50, ₹60, ₹99)
        LinearLayout vipCard = createGlassBentoCard(context, d, "#161E32");
        TextView vipTitle = new TextView(context);
        vipTitle.setText("👑 HYBRID STUDIO VIP  •  " + themeManager.getActiveVipPlan());
        vipTitle.setTextColor(Color.WHITE);
        vipTitle.setTextSize(15f);
        vipTitle.setTypeface(null, Typeface.BOLD);
        vipCard.addView(vipTitle);

        TextView vipSub = new TextView(context);
        vipSub.setText("All Pro Audio Features Free • Upgrade for Ad-Free + 300% Extreme Bass:");
        vipSub.setTextColor(Color.parseColor("#CBD5E1"));
        vipSub.setTextSize(11.5f);
        vipSub.setPadding(0, (int) (3 * d), 0, (int) (10 * d));
        vipCard.addView(vipSub);

        LinearLayout plansRow = new LinearLayout(context);
        plansRow.setOrientation(LinearLayout.HORIZONTAL);
        plansRow.setWeightSum(3f);

        addPlanBentoTile(context, plansRow, d, "₹50 Plan", "Silver VIP", 50, themeManager, callbacks);
        addPlanBentoTile(context, plansRow, d, "₹60 Plan", "Gold VIP", 60, themeManager, callbacks);
        addPlanBentoTile(context, plansRow, d, "₹99 Plan", "Legend VIP", 99, themeManager, callbacks);

        vipCard.addView(plansRow);
        root.addView(vipCard);

        // 2. BENTO ROW 1: [🦁 15 CUTE ANIMALS STUDIO FOLDER] + [🌐 LANGUAGE STUDIO FOLDER]
        LinearLayout bentoRow1 = new LinearLayout(context);
        bentoRow1.setOrientation(LinearLayout.HORIZONTAL);
        bentoRow1.setWeightSum(2f);

        LinearLayout animalCard = createGlassBentoCard(context, d, "#131F30");
        LinearLayout.LayoutParams c1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        c1Lp.rightMargin = (int) (6 * d);
        c1Lp.bottomMargin = (int) (10 * d);
        animalCard.setLayoutParams(c1Lp);

        TextView acTitle = new TextView(context);
        acTitle.setText("🦁 Cute Mascot Folder");
        acTitle.setTextColor(Color.WHITE);
        acTitle.setTextSize(14f);
        acTitle.setTypeface(null, Typeface.BOLD);
        animalCard.addView(acTitle);

        TextView acSub = new TextView(context);
        acSub.setText("15 Cute Animals • 15 Colors\nLion Cub, Penguin, Polar...");
        acSub.setTextColor(Color.parseColor("#CBD5E1"));
        acSub.setTextSize(11f);
        acSub.setPadding(0, (int) (4 * d), 0, 0);
        animalCard.addView(acSub);
        AudioThreeDotStudioHelper.attachBouncySpring(animalCard, () ->
                showCutePuppyStudioFolderDialog(context, themeManager, callbacks));

        LinearLayout langCard = createGlassBentoCard(context, d, "#131F30");
        LinearLayout.LayoutParams c2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        c2Lp.leftMargin = (int) (6 * d);
        c2Lp.bottomMargin = (int) (10 * d);
        langCard.setLayoutParams(c2Lp);

        TextView lcTitle = new TextView(context);
        lcTitle.setText("🌐 Language Folder");
        lcTitle.setTextColor(Color.WHITE);
        lcTitle.setTextSize(14f);
        lcTitle.setTypeface(null, Typeface.BOLD);
        langCard.addView(lcTitle);

        TextView lcSub = new TextView(context);
        lcSub.setText("Active: " + themeManager.getAppLanguage() + "\nHindi, English, Punjabi...");
        lcSub.setTextColor(Color.parseColor("#CBD5E1"));
        lcSub.setTextSize(11f);
        lcSub.setPadding(0, (int) (4 * d), 0, 0);
        langCard.addView(lcSub);
        AudioThreeDotStudioHelper.attachBouncySpring(langCard, () ->
                showLanguageFolderDialog(context, themeManager, callbacks));

        bentoRow1.addView(animalCard);
        bentoRow1.addView(langCard);
        root.addView(bentoRow1);

        // 3. BENTO ROW 2: [📁 SONG SETTINGS FOLDER] + [✂️ CONVERTER & CUTTER FOLDER]
        LinearLayout bentoRow2 = new LinearLayout(context);
        bentoRow2.setOrientation(LinearLayout.HORIZONTAL);
        bentoRow2.setWeightSum(2f);

        LinearLayout songSetCard = createGlassBentoCard(context, d, "#131F30");
        songSetCard.setLayoutParams(c1Lp);
        TextView ssTitle = new TextView(context);
        ssTitle.setText("📁 Song Settings Folder");
        ssTitle.setTextColor(Color.WHITE);
        ssTitle.setTextSize(14f);
        ssTitle.setTypeface(null, Typeface.BOLD);
        songSetCard.addView(ssTitle);

        TextView ssSub = new TextView(context);
        ssSub.setText("20+ Themes • Wallpaper\nDisc/Box • Pocket Shake");
        ssSub.setTextColor(Color.parseColor("#CBD5E1"));
        ssSub.setTextSize(11f);
        ssSub.setPadding(0, (int) (4 * d), 0, 0);
        songSetCard.addView(ssSub);
        AudioThreeDotStudioHelper.attachBouncySpring(songSetCard, () ->
                showSongSettingsFolderDialog(context, themeManager, pocketShakeManager, currentVolumePercent, callbacks));

        LinearLayout convCard = createGlassBentoCard(context, d, "#131F30");
        convCard.setLayoutParams(c2Lp);
        TextView cvTitle = new TextView(context);
        cvTitle.setText("✂️ Converter & Cutter");
        cvTitle.setTextColor(Color.WHITE);
        cvTitle.setTextSize(14f);
        cvTitle.setTypeface(null, Typeface.BOLD);
        convCard.addView(cvTitle);

        TextView cvSub = new TextView(context);
        cvSub.setText("Aadha Gaana Chalayein\nVideo to MP3 • Ringtone");
        cvSub.setTextColor(Color.parseColor("#CBD5E1"));
        cvSub.setTextSize(11f);
        cvSub.setPadding(0, (int) (4 * d), 0, 0);
        convCard.addView(cvSub);
        AudioThreeDotStudioHelper.attachBouncySpring(convCard, () ->
                showConverterAndSongCutterFolderDialog(context, themeManager, callbacks));

        bentoRow2.addView(songSetCard);
        bentoRow2.addView(convCard);
        root.addView(bentoRow2);

        // 4. FULL-WIDTH ACOUSTIC STUDIO & 200% VOLUME BOOSTER CARD
        LinearLayout eqMasterCard = createGlassBentoCard(context, d, "#152236");
        TextView eqTitle = new TextView(context);
        eqTitle.setText("🎚️ 5-Band Dolby Equalizer • Speed Dial • Timer Studio");
        eqTitle.setTextColor(Color.WHITE);
        eqTitle.setTextSize(14f);
        eqTitle.setTypeface(null, Typeface.BOLD);
        eqMasterCard.addView(eqTitle);

        TextView eqSub = new TextView(context);
        eqSub.setText("Tap to open pure buttonless studio sliders, Lofi Pitch Lab & Sleep Timer Dial");
        eqSub.setTextColor(Color.parseColor("#CBD5E1"));
        eqSub.setTextSize(11.5f);
        eqSub.setPadding(0, (int) (3 * d), 0, 0);
        eqMasterCard.addView(eqSub);
        AudioThreeDotStudioHelper.attachBouncySpring(eqMasterCard, () -> {
            if (callbacks != null) callbacks.onOpenEqualizerLab();
        });
        root.addView(eqMasterCard);

        // 5. BOTTOM 3-COLUMN COMPACT BENTO ROW: [⭐ RATE US] + [ℹ️ ABOUT US] + [🔒 PRIVACY]
        LinearLayout bottomTriRow = new LinearLayout(context);
        bottomTriRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomTriRow.setWeightSum(3f);

        addCompactFooterBento(context, bottomTriRow, d, "⭐ Rate 5★", "Review App", () -> showRateUsDialog(context));
        addCompactFooterBento(context, bottomTriRow, d, "ℹ️ About Us", "v2.5 Studio", () -> showAboutUsDialog(context, themeManager));
        addCompactFooterBento(context, bottomTriRow, d, "🔒 Privacy", "Safe & Link", () -> showPrivacyPolicyDialog(context, themeManager));

        root.addView(bottomTriRow);
        scroll.addView(root);
        return scroll;
    }

    private static void addCompactFooterBento(
            Context context, LinearLayout parent, float d,
            String title, String sub, Runnable action) {

        LinearLayout box = createGlassBentoCard(context, d, "#121C2B");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (4 * d), 0, (int) (4 * d), 0);
        box.setLayoutParams(lp);
        box.setGravity(Gravity.CENTER);

        TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(13f);
        t.setTypeface(null, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        box.addView(t);

        TextView s = new TextView(context);
        s.setText(sub);
        s.setTextColor(Color.parseColor("#94A3B8"));
        s.setTextSize(10.5f);
        s.setGravity(Gravity.CENTER);
        box.addView(s);

        AudioThreeDotStudioHelper.attachBouncySpring(box, action);
        parent.addView(box);
    }

    private static void addPlanBentoTile(
            final Context context, LinearLayout parent, float d,
            final String priceLabel, final String badgeName, final int amountInr,
            final ThemeManager themeManager, final SettingsCallbacks callbacks) {

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding((int) (8 * d), (int) (10 * d), (int) (8 * d), (int) (10 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (4 * d), 0, (int) (4 * d), 0);
        box.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#24FFFFFF"));
        bg.setCornerRadius(14f * d);
        bg.setStroke(Math.max(1, (int) (1.4f * d)), Color.WHITE);
        box.setBackground(bg);

        TextView tvPrice = new TextView(context);
        tvPrice.setText(priceLabel);
        tvPrice.setTextColor(Color.WHITE);
        tvPrice.setTextSize(14f);
        tvPrice.setTypeface(null, Typeface.BOLD);
        box.addView(tvPrice);

        TextView tvSub = new TextView(context);
        tvSub.setText(badgeName);
        tvSub.setTextColor(Color.parseColor("#E2E8F0"));
        tvSub.setTextSize(10.5f);
        tvSub.setTypeface(null, Typeface.BOLD);
        box.addView(tvSub);

        AudioThreeDotStudioHelper.attachBouncySpring(box, () -> {
            String[] opts = {
                    "💳 Pay " + priceLabel + " via UPI App (GPay / PhonePe / Paytm)",
                    "✨ Activate " + priceLabel + " (" + badgeName + ") Supporter Mode"
            };
            SongAdapter.showPureWhiteAudioMenuDialog(context, "Unlock " + priceLabel + " (" + badgeName + ")", opts, which -> {
                if (which == 0) {
                    try {
                        Uri upiUri = Uri.parse("upi://pay?pa=hybridmusic@upi&pn=Hybrid%20Music%20Player&am="
                                + amountInr + ".00&cu=INR&tn=" + Uri.encode(badgeName));
                        Intent upiIntent = new Intent(Intent.ACTION_VIEW, upiUri);
                        context.startActivity(Intent.createChooser(upiIntent, "Pay " + priceLabel + " with UPI"));
                    } catch (Exception e) {
                        Toast.makeText(context, "Activating Supporter Badge!", Toast.LENGTH_SHORT).show();
                    }
                }
                themeManager.setActiveVipPlan(priceLabel + " " + badgeName);
                if (callbacks != null) callbacks.onPuppyOrLanguageUpdated();
                Toast.makeText(context, "👑 Activated: " + priceLabel + " (" + badgeName + ")!", Toast.LENGTH_LONG).show();
            });
        });

        parent.addView(box);
    }

    // =========================================================================
    // CUTE ANIMAL MASCOT STUDIO FOLDER (15 WORLDWIDE BABY ANIMALS + 15 COLOURS)
    // =========================================================================
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

        // Live Large Standing Cute Animal Preview at Top
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
        tvH.setText("🦁 15 Cute Baby Animals Studio");
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

        TextView sec1 = new TextView(context);
        sec1.setText("Choose Cute Baby Animal (15 Worldwide Animals):");
        sec1.setTextColor(Color.WHITE);
        sec1.setTextSize(13.5f);
        sec1.setTypeface(null, Typeface.BOLD);
        sec1.setPadding(0, (int) (4 * d), 0, (int) (8 * d));
        col.addView(sec1);

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

        TextView sec2 = new TextView(context);
        sec2.setText("Choose Coat Colour (15 Colours):");
        sec2.setTextColor(Color.WHITE);
        sec2.setTextSize(13.5f);
        sec2.setTypeface(null, Typeface.BOLD);
        sec2.setPadding(0, (int) (10 * d), 0, (int) (8 * d));
        col.addView(sec2);

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
        doneBtn.setText("Apply Cute Mascot");
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
                "🎨 Choose 20+ Studio Themes (" + themeManager.getCurrentTheme().name + ")",
                "🖼️ Set Custom Gallery Photo as App Background",
                "🔄 Restore Clean Default Theme Background",
                "🔊 Master Volume 200% Booster (Current: " + currentVolumePercent + "%)",
                "📳 Pocket Shake Sensor: " + (shakeOn ? "ON (2x Hand Shake)" : "OFF (Default Safe)"),
                "💿 Center Art Style: " + (themeManager.isVinylDiscMode() ? "Rotating Disc + Top Danda" : "Square Album Box (. .)"),
                "📊 Seekbar Style: " + (themeManager.isWaveformMode() ? "Lambe Dande (Waveform)" : "Simple Straight Line"),
                "🎧 10-Second DJ Slow Fade Transition: " + (themeManager.isDjSlowFadeEnabled() ? "ON" : "OFF"),
                "⭐ Customize Top Header Name & Star Badge"
        };

        SongAdapter.showPureWhiteAudioMenuDialog(context, "📁 Song & Player Settings Folder", items, which -> {
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

    private static void showConverterAndSongCutterFolderDialog(
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

        SongAdapter.showPureWhiteAudioMenuDialog(context, "✂️ Audio Converter & Song Editor", tools, which -> {
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
                "💬 Share Feedback"
        };
        SongAdapter.showPureWhiteAudioMenuDialog(context, "⭐ Rate Hybrid Music Player", stars, which ->
                Toast.makeText(context, "❤️ Thank you for rating Hybrid Music Player 5 Stars!", Toast.LENGTH_LONG).show());
    }

    private static void showAboutUsDialog(Context context, ThemeManager tm) {
        new AlertDialog.Builder(context)
                .setTitle("ℹ️ About Hybrid Music Player")
                .setMessage("Hybrid Music Player ★ (v2.5 Ultra Edition)\n\n"
                        + "• Built for Android 5 to Android 16\n"
                        + "• 15 Cute Worldwide Baby Animals with Gravity Physics\n"
                        + "• Buttonless 5-Band Dolby EQ, 8D Surround & 432Hz/528Hz\n"
                        + "• Built-in Song Cutter/Trimmer & Video-to-Audio Engine\n"
                        + "• Status: " + tm.getActiveVipPlan())
                .setPositiveButton("Awesome!", null)
                .show();
    }

    private static void showPrivacyPolicyDialog(final Context context, final ThemeManager tm) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 24, 40, 12);

        TextView info = new TextView(context);
        info.setText("100% Safe & Offline Privacy Policy:\nHybrid Music Player plays your local audio and video songs securely on your phone.\n\nPaste your custom Privacy Policy link below whenever ready:");
        info.setTextColor(Color.BLACK);
        info.setTextSize(13.5f);
        box.addView(info);

        final EditText urlInput = new EditText(context);
        urlInput.setHint("https://your-privacy-policy-link.com");
        urlInput.setText(tm.getPrivacyPolicyUrl());
        box.addView(urlInput);

        new AlertDialog.Builder(context)
                .setTitle("🔒 Privacy Policy & Custom Link")
                .setView(box)
                .setPositiveButton("Save Link", (d, w) -> {
                    tm.setPrivacyPolicyUrl(urlInput.getText().toString());
                    Toast.makeText(context, "Privacy Policy Link Saved!", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Open Link", (d, w) -> {
                    String link = urlInput.getText().toString().trim();
                    if (!link.isEmpty() && link.startsWith("http")) {
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
                    } else {
                        Toast.makeText(context, "Abhi link add nahi kiya gaya hai", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private static LinearLayout createGlassBentoCard(Context context, float d, String bgHex) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (14 * d);
        card.setPadding(pad, (int) (12 * d), pad, (int) (12 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(20f * d);
        bg.setStroke(Math.max(1, (int) (1.3f * d)), Color.WHITE);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (10 * d);
        card.setLayoutParams(lp);
        return card;
    }
}
