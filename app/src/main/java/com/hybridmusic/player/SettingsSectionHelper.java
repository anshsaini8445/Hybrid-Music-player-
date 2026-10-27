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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
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
        default void onPuppyOrLanguageUpdated() {}
        default List<SongItem> getAllSongsForConverter() { return new ArrayList<>(); }
        default void onPlayTrimmedSong(SongItem song, int startSec, int endSec) {}
    }

    // =========================================================================
    // 1. ULTRA-ATTRACTIVE FUTURISTIC SEARCH SECTION (Compact Bottom Bar & Chips)
    // =========================================================================
    public static View buildModernSearchScreen(
            final Context context,
            final List<SongItem> allSongs,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

        final float d = context.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (12 * d), (int) (8 * d), (int) (12 * d), (int) (6 * d));

        // Top Futuristic Radar Header Card
        LinearLayout topBanner = new LinearLayout(context);
        topBanner.setOrientation(LinearLayout.HORIZONTAL);
        topBanner.setGravity(Gravity.CENTER_VERTICAL);
        topBanner.setPadding((int) (14 * d), (int) (8 * d), (int) (14 * d), (int) (8 * d));
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(Color.parseColor("#141E2E"));
        bannerBg.setCornerRadius(16f * d);
        bannerBg.setStroke(Math.max(1, (int) d), Color.parseColor("#44FFFFFF"));
        topBanner.setBackground(bannerBg);

        final TextView statusTv = new TextView(context);
        statusTv.setText("⚡ AI Instant Radar • " + allSongs.size() + " Tracks Indexed");
        statusTv.setTextColor(Color.WHITE);
        statusTv.setTextSize(12.5f);
        statusTv.setTypeface(null, Typeface.BOLD);
        topBanner.addView(statusTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(topBanner);

        // Center Results List
        final RecyclerView searchRecycler = new RecyclerView(context);
        searchRecycler.setLayoutManager(new LinearLayoutManager(context));
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rvLp.topMargin = (int) (6 * d);
        root.addView(searchRecycler, rvLp);

        final int[] filterMode = {0}; // 0=All, 1=Audio, 2=VideoAudio, 3=Favorites, 4=DJ/Remix
        final List<SongItem> filteredList = new ArrayList<>(allSongs);
        final TextView[] pillBtns = new TextView[5];

        // Compact Bottom Filter Pills Row (Smaller & Sleeker as Requested)
        HorizontalScrollView pillScroll = new HorizontalScrollView(context);
        pillScroll.setHorizontalScrollBarEnabled(false);
        pillScroll.setPadding(0, (int) (6 * d), 0, (int) (6 * d));

        LinearLayout pillRow = new LinearLayout(context);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] titles = {"All (" + allSongs.size() + ")", "🎵 Audio", "🎬 Video-Audio", "❤️ Favs", "🔥 DJ / Bass"};

        // Compact Sleek Bottom Search Box
        LinearLayout searchBox = new LinearLayout(context);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding((int) (16 * d), (int) (7 * d), (int) (12 * d), (int) (7 * d));

        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(Color.parseColor("#101824"));
        boxBg.setCornerRadius(22f * d);
        boxBg.setStroke(Math.max(1, (int) (1.4f * d)), Color.WHITE);
        searchBox.setBackground(boxBg);

        final EditText input = new EditText(context);
        input.setHint("🔍 Search song, singer, remix or folder...");
        input.setSingleLine(true);
        input.setBackground(null);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#94A3B8"));
        input.setTextSize(13.5f);
        searchBox.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView clearBtn = new TextView(context);
        clearBtn.setText("⋮");
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setTextSize(16f);
        clearBtn.setTypeface(null, Typeface.BOLD);
        clearBtn.setPadding((int) (10 * d), (int) (2 * d), (int) (8 * d), (int) (2 * d));
        searchBox.addView(clearBtn);

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
            statusTv.setText("⚡ Matching Results: " + filteredList.size() + " Songs");

            for (int i = 0; i < pillBtns.length; i++) {
                GradientDrawable pBg = new GradientDrawable();
                pBg.setCornerRadius(16f * d);
                if (i == filterMode[0]) {
                    pBg.setColor(Color.WHITE);
                    pillBtns[i].setTextColor(Color.parseColor("#0A0A10"));
                } else {
                    pBg.setColor(Color.parseColor("#16202C"));
                    pBg.setStroke(Math.max(1, (int) d), Color.parseColor("#88FFFFFF"));
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
            pill.setPadding((int) (12 * d), (int) (6 * d), (int) (12 * d), (int) (6 * d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) (6 * d);
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

        clearBtn.setOnClickListener(v -> input.setText(""));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { runFilter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        runFilter.run();
        return root;
    }

    // =========================================================================
    // 2. COMPLETE FOLDER-BASED MI SECTION (Settings)
    // =========================================================================
    public static View buildModernSettingsScreen(
            final Context context,
            final ThemeManager themeManager,
            final PocketShakeManager pocketShakeManager,
            final int currentVolumePercent,
            final SettingsCallbacks callbacks) {

        final float d = context.getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (28 * d));

        // ---------------------------------------------------------------------
        // A. SABSE UPAR: VIP MEMBERSHIP & SUPPORT PLANS (₹50, ₹60, ₹99 Plans)
        // ---------------------------------------------------------------------
        LinearLayout vipCard = createFolderCard(context, d, "#1A142E", "#FFD700");
        TextView vipTitle = new TextView(context);
        vipTitle.setText("👑 Hybrid VIP Support Plans (" + themeManager.getActiveVipPlan() + ")");
        vipTitle.setTextColor(Color.WHITE);
        vipTitle.setTextSize(15.5f);
        vipTitle.setTypeface(null, Typeface.BOLD);
        vipCard.addView(vipTitle);

        TextView vipSub = new TextView(context);
        vipSub.setText("Support Hybrid Music Player & unlock Golden VIP Badge + Priority Studio Engine:");
        vipSub.setTextColor(Color.parseColor("#E2E8F0"));
        vipSub.setTextSize(12f);
        vipSub.setPadding(0, (int) (4 * d), 0, (int) (10 * d));
        vipCard.addView(vipSub);

        LinearLayout plansRow = new LinearLayout(context);
        plansRow.setOrientation(LinearLayout.HORIZONTAL);
        plansRow.setWeightSum(3f);

        addPlanButton(context, plansRow, d, "₹50 Plan", "Silver Pass", 50, themeManager, callbacks);
        addPlanButton(context, plansRow, d, "₹60 Plan", "Gold Pass", 60, themeManager, callbacks);
        addPlanButton(context, plansRow, d, "₹99 Plan", "VIP Legend", 99, themeManager, callbacks);

        vipCard.addView(plansRow);
        root.addView(vipCard);

        // ---------------------------------------------------------------------
        // B. SIDE-BY-SIDE FOLDERS: [1. CUTE PUPPY FOLDER] + [2. LANGUAGE FOLDER]
        // ---------------------------------------------------------------------
        LinearLayout dualRow = new LinearLayout(context);
        dualRow.setOrientation(LinearLayout.HORIZONTAL);
        dualRow.setWeightSum(2f);
        LinearLayout.LayoutParams dualLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dualLp.bottomMargin = (int) (12 * d);
        dualRow.setLayoutParams(dualLp);

        // Folder 1: Cute Puppy Folder (7 Puppies + 15 Colors)
        LinearLayout puppyFolder = createFolderCard(context, d, "#132032", "#FFFFFF");
        LinearLayout.LayoutParams f1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        f1Lp.rightMargin = (int) (6 * d);
        puppyFolder.setLayoutParams(f1Lp);

        TextView pfTitle = new TextView(context);
        pfTitle.setText("🐶 Cute Puppy Folder");
        pfTitle.setTextColor(Color.WHITE);
        pfTitle.setTextSize(14.5f);
        pfTitle.setTypeface(null, Typeface.BOLD);
        puppyFolder.addView(pfTitle);

        String activeBreed = ThemeManager.PUPPY_BREED_NAMES[themeManager.getPuppyBreedIndex()];
        String activeColor = ThemeManager.PUPPY_COLOR_NAMES[themeManager.getPuppyColorIndex()];
        TextView pfSub = new TextView(context);
        pfSub.setText("7 Cute Puppies • 15 Colors\nActive: " + activeColor);
        pfSub.setTextColor(Color.parseColor("#CBD5E1"));
        pfSub.setTextSize(11.5f);
        pfSub.setPadding(0, (int) (4 * d), 0, 0);
        puppyFolder.addView(pfSub);

        puppyFolder.setOnClickListener(v -> showCutePuppyStudioFolderDialog(context, themeManager, callbacks));

        // Folder 2: Language Folder (Hindi, Hinglish, English, Punjabi, Haryanvi)
        LinearLayout langFolder = createFolderCard(context, d, "#132032", "#FFFFFF");
        LinearLayout.LayoutParams f2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        f2Lp.leftMargin = (int) (6 * d);
        langFolder.setLayoutParams(f2Lp);

        TextView lfTitle = new TextView(context);
        lfTitle.setText("🌐 Language Folder");
        lfTitle.setTextColor(Color.WHITE);
        lfTitle.setTextSize(14.5f);
        lfTitle.setTypeface(null, Typeface.BOLD);
        langFolder.addView(lfTitle);

        TextView lfSub = new TextView(context);
        lfSub.setText("App Bhasha / Language\nActive: " + themeManager.getAppLanguage());
        lfSub.setTextColor(Color.parseColor("#CBD5E1"));
        lfSub.setTextSize(11.5f);
        lfSub.setPadding(0, (int) (4 * d), 0, 0);
        langFolder.addView(lfSub);

        langFolder.setOnClickListener(v -> showLanguageFolderDialog(context, themeManager, callbacks));

        dualRow.addView(puppyFolder);
        dualRow.addView(langFolder);
        root.addView(dualRow);

        // ---------------------------------------------------------------------
        // C. FOLDER 3: SONG & AUDIO PLAYER SETTINGS FOLDER
        // ---------------------------------------------------------------------
        addMainFolderRow(context, root, d,
                "📁 Song & Audio Settings Folder",
                "20+ Studio Themes • Gallery Photo • 200% Volume Booster • Pocket Shake • Disc/Box • Lambe Dande • 10s DJ Fade",
                v -> showSongSettingsFolderDialog(context, themeManager, pocketShakeManager, currentVolumePercent, callbacks));

        // ---------------------------------------------------------------------
        // D. FOLDER 4 (NEW): CONVERTER & SONG CUTTER / EDITOR ("Aadha Gana Chalana")
        // ---------------------------------------------------------------------
        addMainFolderRow(context, root, d,
                "✂️ NEW: Audio Converter & Song Cutter Folder",
                "Song Edit Karein • Aadha Gaana Chalayein (Start-End Trim) • Video to MP3 Audio • Ringtone Cutter",
                v -> showConverterAndSongCutterFolderDialog(context, themeManager, callbacks));

        // ---------------------------------------------------------------------
        // E. DIRECT EQUALIZER & SOUND LAB LAUNCHER
        // ---------------------------------------------------------------------
        addMainFolderRow(context, root, d,
                "🎛️ 5-Band Dolby Equalizer & Sound Pitch Lab",
                "Lofi Chill • Slowed + Hall Reverb • Nightcore • Sub-Woofer Bass Boost • Custom Sleep Timer",
                v -> {
                    if (callbacks != null) callbacks.onOpenEqualizerLab();
                });

        // ---------------------------------------------------------------------
        // F. BOTTOM MI OPTIONS: RATE US, ABOUT US, PRIVACY POLICY
        // ---------------------------------------------------------------------
        addMainFolderRow(context, root, d,
                "⭐ Rate Hybrid Music Player (5 Stars)",
                "App kaisa laga? Apni 5-Star rating aur review dein",
                v -> showRateUsDialog(context));

        addMainFolderRow(context, root, d,
                "ℹ️ About Us (Hybrid Music Player Studio)",
                "Version 2.0 Ultra • Built for Android 5 to Android 16 • Hi-Res Audio & Video-Audio Engine",
                v -> showAboutUsDialog(context, themeManager));

        addMainFolderRow(context, root, d,
                "🔒 Privacy Policy & Terms",
                themeManager.getPrivacyPolicyUrl().isEmpty()
                        ? "100% Offline Safe • Tap to view or add your custom Privacy Policy link"
                        : "Link: " + themeManager.getPrivacyPolicyUrl(),
                v -> showPrivacyPolicyDialog(context, themeManager));

        scroll.addView(root);
        return scroll;
    }

    private static void addPlanButton(
            final Context context,
            LinearLayout parent,
            float d,
            final String priceLabel,
            final String badgeName,
            final int amountInr,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

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
        tvSub.setTextColor(Color.parseColor("#FFD700"));
        tvSub.setTextSize(11f);
        tvSub.setTypeface(null, Typeface.BOLD);
        box.addView(tvSub);

        box.setOnClickListener(v -> {
            String[] opts = {
                    "💳 Pay " + priceLabel + " via UPI App (GPay / PhonePe / Paytm)",
                    "✨ Activate " + priceLabel + " (" + badgeName + ") Supporter Mode"
            };
            SongAdapter.showPureWhiteAudioMenuDialog(context, "Select " + priceLabel + " (" + badgeName + ")", opts, which -> {
                if (which == 0) {
                    try {
                        Uri upiUri = Uri.parse("upi://pay?pa=hybridmusic@upi&pn=Hybrid%20Music%20Player&am="
                                + amountInr + ".00&cu=INR&tn=" + Uri.encode(badgeName));
                        Intent upiIntent = new Intent(Intent.ACTION_VIEW, upiUri);
                        context.startActivity(Intent.createChooser(upiIntent, "Pay " + priceLabel + " with UPI"));
                    } catch (Exception e) {
                        Toast.makeText(context, "UPI app not found, activating Supporter Badge!", Toast.LENGTH_SHORT).show();
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
    // FOLDER 1 DIALOG: CUTE PUPPY STUDIO (7 CUTE PUPPIES + 15 COLOURS)
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
        bg.setColor(Color.parseColor("#F70A0E17"));
        bg.setCornerRadius(26f * d);
        bg.setStroke(Math.max(2, (int) (1.8f * d)), Color.WHITE);
        root.setBackground(bg);

        final AlertDialog dialog = new AlertDialog.Builder(context).setView(root).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Live Cute Puppy Preview at Top
        LinearLayout previewBox = new LinearLayout(context);
        previewBox.setOrientation(LinearLayout.HORIZONTAL);
        previewBox.setGravity(Gravity.CENTER_VERTICAL);
        previewBox.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (12 * d));

        final DancingPuppyView livePuppy = new DancingPuppyView(context);
        livePuppy.setPlayingState(true);
        livePuppy.setLayoutParams(new LinearLayout.LayoutParams((int) (72 * d), (int) (72 * d)));
        previewBox.addView(livePuppy);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding((int) (12 * d), 0, 0, 0);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvH = new TextView(context);
        tvH.setText("🐶 Cute Puppy Studio Folder");
        tvH.setTextColor(Color.WHITE);
        tvH.setTextSize(16.5f);
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
        sec1.setText("Choose Cute Puppy Icon (7 Types):");
        sec1.setTextColor(Color.WHITE);
        sec1.setTextSize(14f);
        sec1.setTypeface(null, Typeface.BOLD);
        sec1.setPadding(0, (int) (4 * d), 0, (int) (8 * d));
        col.addView(sec1);

        for (int i = 0; i < ThemeManager.PUPPY_BREED_NAMES.length; i++) {
            final int breedIdx = i;
            TextView row = new TextView(context);
            row.setText(ThemeManager.PUPPY_BREED_NAMES[i]);
            row.setTextColor(Color.WHITE);
            row.setTextSize(13.5f);
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
                livePuppy.setPreviewBreedAndColor(breedIdx, themeManager.getPuppyColorIndex());
                tvActiveInfo.setText(ThemeManager.PUPPY_BREED_NAMES[breedIdx]
                        + "\nColor: " + ThemeManager.PUPPY_COLOR_NAMES[themeManager.getPuppyColorIndex()]);
                if (callbacks != null) callbacks.onPuppyOrLanguageUpdated();
            });
            col.addView(row);
        }

        TextView sec2 = new TextView(context);
        sec2.setText("Choose Puppy Coat Colour (15 Colours):");
        sec2.setTextColor(Color.WHITE);
        sec2.setTextSize(14f);
        sec2.setTypeface(null, Typeface.BOLD);
        sec2.setPadding(0, (int) (10 * d), 0, (int) (8 * d));
        col.addView(sec2);

        GridLayout colorGrid = new GridLayout(context);
        colorGrid.setColumnCount(3);
        for (int c = 0; c < ThemeManager.PUPPY_COLOR_NAMES.length; c++) {
            final int colorIdx = c;
            TextView chip = new TextView(context);
            chip.setText(ThemeManager.PUPPY_COLOR_NAMES[c]);
            chip.setTextSize(11f);
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
                livePuppy.setPreviewBreedAndColor(themeManager.getPuppyBreedIndex(), colorIdx);
                tvActiveInfo.setText(ThemeManager.PUPPY_BREED_NAMES[themeManager.getPuppyBreedIndex()]
                        + "\nColor: " + ThemeManager.PUPPY_COLOR_NAMES[colorIdx]);
                if (callbacks != null) callbacks.onPuppyOrLanguageUpdated();
            });
            colorGrid.addView(chip);
        }
        col.addView(colorGrid);

        TextView doneBtn = new TextView(context);
        doneBtn.setText("Save & Apply Cute Puppy");
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

    // =========================================================================
    // FOLDER 2 DIALOG: LANGUAGE STUDIO FOLDER
    // =========================================================================
    private static void showLanguageFolderDialog(
            final Context context,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

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

    // =========================================================================
    // FOLDER 3 DIALOG: SONG & AUDIO PLAYER SETTINGS FOLDER
    // =========================================================================
    private static void showSongSettingsFolderDialog(
            final Context context,
            final ThemeManager themeManager,
            final PocketShakeManager pocketShakeManager,
            final int currentVolumePercent,
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

    // =========================================================================
    // FOLDER 4 DIALOG: CONVERTER & SONG CUTTER / EDITOR ("Aadha Gaana Chalana")
    // =========================================================================
    private static void showConverterAndSongCutterFolderDialog(
            final Context context,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

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
            final Context context,
            final List<SongItem> songs,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks,
            final int toolMode) {

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
            final Context context,
            final SongItem song,
            final int totalSec,
            final ThemeManager themeManager,
            final SettingsCallbacks callbacks) {

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

    // =========================================================================
    // RATE US, ABOUT US & PRIVACY POLICY DIALOGS
    // =========================================================================
    private static void showRateUsDialog(final Context context) {
        String[] stars = {
                "⭐⭐⭐⭐⭐  5 Stars - Mind-Blowing Music Player!",
                "⭐⭐⭐⭐  4 Stars - Very Smooth & Cute Puppy!",
                "💬 Share Feedback / Open Play Store Rating"
        };
        SongAdapter.showPureWhiteAudioMenuDialog(context, "⭐ Rate Hybrid Music Player", stars, which -> {
            Toast.makeText(context, "❤️ Thank you for rating Hybrid Music Player 5 Stars!", Toast.LENGTH_LONG).show();
        });
    }

    private static void showAboutUsDialog(Context context, ThemeManager tm) {
        new AlertDialog.Builder(context)
                .setTitle("ℹ️ About Hybrid Music Player")
                .setMessage("Hybrid Music Player ★ (v2.0 Studio Edition)\n\n"
                        + "• Built for Android 5 (Lollipop) to Android 16\n"
                        + "• Interactive Cute Puppy Mascot (7 Breeds & 15 Colors)\n"
                        + "• 5-Band Dolby Equalizer, 200% Booster & Lofi Pitch Lab\n"
                        + "• Built-in Song Cutter/Trimmer & Video-to-Audio Engine\n"
                        + "• Active Status: " + tm.getActiveVipPlan())
                .setPositiveButton("Awesome!", null)
                .show();
    }

    private static void showPrivacyPolicyDialog(final Context context, final ThemeManager tm) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 24, 40, 12);

        TextView info = new TextView(context);
        info.setText("100% Safe & Offline Privacy Policy:\nHybrid Music Player plays your local audio and video songs securely on your phone without uploading your personal files anywhere.\n\nAap jab apna Privacy Policy URL bana lo, to niche paste karke save kar sakte ho:");
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

    private static LinearLayout createFolderCard(Context context, float d, String bgHex, String strokeHex) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (15 * d);
        card.setPadding(pad, (int) (13 * d), pad, (int) (13 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(20f * d);
        bg.setStroke(Math.max(1, (int) (1.4f * d)), Color.parseColor(strokeHex));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * d);
        card.setLayoutParams(lp);
        return card;
    }

    private static void addMainFolderRow(
            Context context,
            LinearLayout parent,
            float d,
            String title,
            String subtitle,
            View.OnClickListener click) {

        LinearLayout card = createFolderCard(context, d, "#131D2B", "#FFFFFF");
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14.5f);
        t.setTypeface(null, Typeface.BOLD);
        card.addView(t);

        TextView s = new TextView(context);
        s.setText(subtitle);
        s.setTextColor(Color.parseColor("#CBD5E1"));
        s.setTextSize(12f);
        s.setPadding(0, (int) (4 * d), 0, 0);
        card.addView(s);

        card.setOnClickListener(click);
        parent.addView(card);
    }
}
