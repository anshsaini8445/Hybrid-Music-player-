package com.hybridmusic.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.ColorUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ThemeManager {

    private static final String PREF_NAME = "hybrid_music_prefs";
    private static final String KEY_THEME_INDEX = "theme_index";
    private static final String KEY_CUSTOM_BG_URI = "custom_bg_uri";
    private static final String KEY_DISC_MODE = "disc_mode";
    private static final String KEY_WAVEFORM_MODE = "waveform_mode";
    private static final String KEY_HEADER_TITLE = "header_title";
    private static final String KEY_HEADER_STAR = "header_star";
    private static final String KEY_FAVORITES = "favorite_song_ids";
    private static final String KEY_BOTTOM_ORDER = "bottom_icon_order";
    private static final String KEY_DJ_SLOW_FADE = "dj_slow_fade";

    private static final String KEY_PUPPY_BREED = "puppy_breed_index";
    private static final String KEY_PUPPY_COLOR = "puppy_color_index";
    private static final String KEY_APP_LANGUAGE = "app_language_code";
    private static final String KEY_VIP_PLAN = "vip_supporter_plan";
    private static final String KEY_VIP_ALL_THEMES = "vip_all_60_themes_unlocked";
    private static final String KEY_PRIVACY_URL = "privacy_policy_url";

    public static class ThemePreset {
        public final String name;
        public final int bgPrimary;
        public final int bgSurface;
        public final int bgCard;
        public final int accentPrimary;
        public final int accentSecondary;

        public ThemePreset(String name, String bg1, String bg2, String bg3, String acc1, String acc2) {
            this.name = name;
            this.bgPrimary = Color.parseColor(bg1);
            this.bgSurface = Color.parseColor(bg2);
            this.bgCard = Color.parseColor(bg3);
            this.accentPrimary = Color.parseColor(acc1);
            this.accentSecondary = Color.parseColor(acc2);
        }
    }

    // =========================================================================
    // 60 STUDIO THEMES (1-10 FREE UNLOCKED, 11-60 LOCKED: 3 ADS OR VIP PAY)
    // =========================================================================
    private static final ThemePreset[] THEMES = new ThemePreset[]{
            // --- 1 to 10: FREE STARTER STUDIO THEMES ---
            new ThemePreset("1. Muso Cyber Neon", "#070B12", "#101724", "#182234", "#00E5FF", "#FF2E93"),
            new ThemePreset("2. PLAYit Royal Gold", "#0F0C08", "#1C160E", "#292014", "#FFD700", "#FF8C00"),
            new ThemePreset("3. AMOLED Pitch Black", "#000000", "#0A0A0A", "#141414", "#00FF87", "#00B8FF"),
            new ThemePreset("4. Crimson Velvet", "#1A070D", "#2B0C16", "#3D1220", "#FF3366", "#FF99AC"),
            new ThemePreset("5. Emerald Matrix", "#06150E", "#0C261A", "#143827", "#10B981", "#34D399"),
            new ThemePreset("6. Deep Ocean Blue", "#071324", "#0E223D", "#173357", "#38BDF8", "#818CF8"),
            new ThemePreset("7. Ultra Violet Galaxy", "#120824", "#1E1038", "#2B184D", "#C084FC", "#F472B6"),
            new ThemePreset("8. Sunset Mandarin", "#1C0D06", "#2E160B", "#422112", "#FB923C", "#FACC15"),
            new ThemePreset("9. Tokyo Midnight", "#090D16", "#131B2E", "#1F2A44", "#F43F5E", "#38BDF8"),
            new ThemePreset("10. Dracula Studio", "#1E1F29", "#282A36", "#343746", "#BD93F9", "#FF79C6"),

            // --- 11 to 20: CYBERPUNK & NEON PRO SERIES (LOCKED: 3 ADS OR VIP) ---
            new ThemePreset("11. Cyberpunk 2077 Yellow", "#141204", "#242008", "#332D0C", "#FCEE09", "#00F0FF"),
            new ThemePreset("12. Neon Laser Magenta", "#170514", "#290924", "#3B0E34", "#FF00FF", "#00FFFF"),
            new ThemePreset("13. Electric Cobalt Pro", "#040B1C", "#091636", "#102252", "#2563EB", "#60A5FA"),
            new ThemePreset("14. Synthwave Retro 80s", "#120422", "#21093B", "#311054", "#F43F5E", "#FBBF24"),
            new ThemePreset("15. Rose Quartz Luxury", "#1C1018", "#2B1925", "#3B2333", "#FB7185", "#F472B6"),
            new ThemePreset("16. Hyperion Cyan Pulse", "#03161A", "#07282E", "#0C3B44", "#06B6D4", "#67E8F9"),
            new ThemePreset("17. Toxic Bio-Hazard", "#0B1705", "#152B0A", "#204010", "#84CC16", "#BEF264"),
            new ThemePreset("18. Plasma Violet Storm", "#0E051F", "#1A0A38", "#271052", "#8B5CF6", "#C4B5FD"),
            new ThemePreset("19. Inferno Red Nitros", "#1F0505", "#360A0A", "#4D1010", "#EF4444", "#FCA5A5"),
            new ThemePreset("20. Arctic Ice Neon", "#06141F", "#0D2438", "#153652", "#7DD3FC", "#E0F2FE"),

            // --- 21 to 30: TRUE AMOLED PITCH BLACK SERIES (LOCKED: 3 ADS OR VIP) ---
            new ThemePreset("21. AMOLED Pure White", "#000000", "#080808", "#121212", "#FFFFFF", "#94A3B8"),
            new ThemePreset("22. AMOLED Ruby Blood", "#000000", "#0D0406", "#1A080C", "#E11D48", "#FDA4AF"),
            new ThemePreset("23. AMOLED Cyber Gold", "#000000", "#0D0B04", "#1A1608", "#EAB308", "#FEF08A"),
            new ThemePreset("24. AMOLED Neon Purple", "#000000", "#09040F", "#130821", "#A855F7", "#E9D5FF"),
            new ThemePreset("25. AMOLED Electric Blue", "#000000", "#040812", "#081226", "#3B82F6", "#93C5FD"),
            new ThemePreset("26. AMOLED Emerald Glow", "#000000", "#030D08", "#071C12", "#10B981", "#A7F3D0"),
            new ThemePreset("27. AMOLED Hot Pink", "#000000", "#0F040A", "#210816", "#EC4899", "#FBCFE8"),
            new ThemePreset("28. AMOLED Tangerine", "#000000", "#0F0703", "#211006", "#F97316", "#FED7AA"),
            new ThemePreset("29. AMOLED Teal Mint", "#000000", "#030D0D", "#071C1C", "#14B8A6", "#99F6E4"),
            new ThemePreset("30. AMOLED Silver Chrome", "#000000", "#0B0C0E", "#16181D", "#CBD5E1", "#F8FAFC"),

            // --- 31 to 40: LUXURY GOLD, DIAMOND & ROYAL SERIES (LOCKED: 3 ADS OR VIP) ---
            new ThemePreset("31. 24K Dubai Royal Gold", "#120E04", "#211A08", "#33280D", "#FFD700", "#FEF08A"),
            new ThemePreset("32. Black Diamond VIP", "#0A0C10", "#141820", "#1F2532", "#E2E8F0", "#38BDF8"),
            new ThemePreset("33. Imperial Maharaja Red", "#1F060A", "#330B12", "#4A101A", "#F59E0B", "#EF4444"),
            new ThemePreset("34. Platinum Champagne", "#141310", "#24221C", "#36332A", "#FDE68A", "#D6D3D1"),
            new ThemePreset("35. Sapphire Crown Jewel", "#060D21", "#0C1940", "#132661", "#60A5FA", "#FACC15"),
            new ThemePreset("36. Rose Gold Luxury", "#1A0F10", "#2E1A1C", "#422629", "#FDA4AF", "#FBBF24"),
            new ThemePreset("37. Obsidian Bronze", "#120D0A", "#211813", "#33251D", "#D97706", "#FDE68A"),
            new ThemePreset("38. Emerald Crown Palace", "#051710", "#0A2B1E", "#10402D", "#34D399", "#FACC15"),
            new ThemePreset("39. Amethyst Royal Velvet", "#14081F", "#240F38", "#351752", "#C084FC", "#FDE047"),
            new ThemePreset("40. Titanium Carbon Pro", "#0E1014", "#181C23", "#242A35", "#94A3B8", "#38BDF8"),

            // --- 41 to 50: GALAXY, SPACE & VAPORWAVE SERIES (LOCKED: 3 ADS OR VIP) ---
            new ThemePreset("41. Andromeda Nebula", "#08061A", "#110D33", "#1B154D", "#818CF8", "#F472B6"),
            new ThemePreset("42. Supernova Starburst", "#1A0912", "#301122", "#471932", "#FB7185", "#FBBF24"),
            new ThemePreset("43. Deep Space Odyssey", "#05070F", "#0A0F21", "#111836", "#38BDF8", "#A78BFA"),
            new ThemePreset("44. Vaporwave Sunset", "#170924", "#28103D", "#3B1859", "#FF71CE", "#01CDFE"),
            new ThemePreset("45. Aurora Borealis", "#041417", "#09252B", "#0F3840", "#2DD4BF", "#A855F7"),
            new ThemePreset("46. Mars Colony Red", "#1C0A06", "#30120B", "#471B11", "#F97316", "#FCA5A5"),
            new ThemePreset("47. Saturn Rings Cosmic", "#12101A", "#201C2E", "#2F2A45", "#FDE047", "#C084FC"),
            new ThemePreset("48. Milky Way Starlight", "#080B14", "#101629", "#19223D", "#E2E8F0", "#60A5FA"),
            new ThemePreset("49. Alien Pulsar Green", "#04170C", "#082B17", "#0E4223", "#4ADE80", "#22D3EE"),
            new ThemePreset("50. Eclipse Dark Moon", "#09090D", "#13131C", "#1E1E2C", "#F43F5E", "#E2E8F0"),

            // --- 51 to 60: NATURE, COFFEE & CLEAN DAYLIGHT SERIES (LOCKED: 3 ADS OR VIP) ---
            new ThemePreset("51. Espresso Dark Roast", "#140E0B", "#241914", "#36261E", "#D97706", "#FDE68A"),
            new ThemePreset("52. Midnight Pine Forest", "#07120C", "#0E2116", "#163322", "#22C55E", "#86EFAC"),
            new ThemePreset("53. Monsoon Thunder Cloud", "#0C1117", "#161F2B", "#212E40", "#64748B", "#38BDF8"),
            new ThemePreset("54. Cherry Blossom Night", "#1A0C14", "#2E1523", "#421F33", "#F472B6", "#FBCFE8"),
            new ThemePreset("55. Himalayan Glacier", "#08141C", "#102533", "#18374C", "#38BDF8", "#F0F9FF"),
            new ThemePreset("56. Clean Snow Daylight", "#F8FAFC", "#FFFFFF", "#E2E8F0", "#0284C7", "#DB2777"),
            new ThemePreset("57. Sakura Pink Daylight", "#FFF1F2", "#FFFFFF", "#FFE4E6", "#E11D48", "#9333EA"),
            new ThemePreset("58. Mint Breeze Daylight", "#F0FDF4", "#FFFFFF", "#DCFCE7", "#059669", "#0284C7"),
            new ThemePreset("59. Royal Lavender Day", "#FAF5FF", "#FFFFFF", "#F3E8FF", "#7C3AED", "#DB2777"),
            new ThemePreset("60. Golden Ivory Daylight", "#FFFBEB", "#FFFFFF", "#FEF3C7", "#D97706", "#DC2626")
    };

    // 15 Cute Worldwide Baby Animals
    public static final String[] PUPPY_BREED_NAMES = new String[]{
            "1. 🦁 Cute Lion Cub (Sher Ka Baccha)",
            "2. 🐧 Baby Emperor Penguin (Cute Tuxedo)",
            "3. 🐻‍❄️ Snowy White Polar Cub (Arctic Cutie)",
            "4. 🦊 American Red Panda Cub (Fluffy Tail)",
            "5. 🐯 Royal Baby Tiger Cub (Standing)",
            "6. 🐨 Cute Koala Joey (Fluffy Ears)",
            "7. 🐼 Chibi Giant Panda Cub (Snow & Black)",
            "8. 🦦 Baby Sea Otter (Holding Star)",
            "9. 🐕 White Samoyed Cloud Puppy",
            "10. 🐰 Fluffy White Snow Bunny",
            "11. 🦉 Baby Snowy Owl Chick (Big Eyes)",
            "12. 🐆 Baby Snow Leopard Cub",
            "13. 🐿️ Cute American Chipmunk",
            "14. 🦊 Desert Fennec Fox (Big Ears)",
            "15. 🐶 Golden Retriever Baby Pup"
    };

    public static final String[] PUPPY_COLOR_NAMES = new String[]{
            "1. Pure Snow White", "2. Lion Golden Honey", "3. Penguin Ice Blue", "4. Sakura Pink",
            "5. Sunset Tiger Orange", "6. Cyber Neon Cyan", "7. Lavender Magic", "8. Mint Cream",
            "9. Caramel Brown", "10. Strawberry Red", "11. Lemon Glow", "12. Peach Rose",
            "13. Neon Lime", "14. Silver Frost", "15. Royal Purple"
    };

    public static final int[] PUPPY_COLOR_HEX = new int[]{
            Color.parseColor("#FFFFFF"), Color.parseColor("#FBBF24"), Color.parseColor("#38BDF8"),
            Color.parseColor("#F472B6"), Color.parseColor("#FB923C"), Color.parseColor("#00E5FF"),
            Color.parseColor("#C084FC"), Color.parseColor("#34D399"), Color.parseColor("#D97706"),
            Color.parseColor("#F87171"), Color.parseColor("#FACC15"), Color.parseColor("#FDA4AF"),
            Color.parseColor("#A3E635"), Color.parseColor("#E2E8F0"), Color.parseColor("#A855F7")
    };

    private final SharedPreferences prefs;

    public ThemeManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public ThemePreset[] getAllThemes() {
        return THEMES;
    }

    public int getCurrentThemeIndex() {
        int idx = prefs.getInt(KEY_THEME_INDEX, 0);
        if (idx < 0 || idx >= THEMES.length) idx = 0;
        return idx;
    }

    public void setThemeIndex(int index) {
        prefs.edit().putInt(KEY_THEME_INDEX, index).apply();
    }

    public ThemePreset getCurrentTheme() {
        return THEMES[getCurrentThemeIndex()];
    }

    // =========================================================================
    // THEME LOCK & 3-AD / VIP UNLOCK ENGINE
    // =========================================================================
    public boolean isVipAllThemesUnlocked() {
        return prefs.getBoolean(KEY_VIP_ALL_THEMES, false)
                || !getActiveVipPlan().equals("Free Studio User");
    }

    public void unlockAllThemesVip(String planName) {
        prefs.edit()
                .putBoolean(KEY_VIP_ALL_THEMES, true)
                .putString(KEY_VIP_PLAN, planName)
                .apply();
    }

    public boolean isThemeUnlocked(int themeIndex) {
        // First 10 themes (index 0 to 9) are FREE by default
        if (themeIndex < 10) return true;
        if (isVipAllThemesUnlocked()) return true;
        return getThemeAdProgress(themeIndex) >= 3;
    }

    public int getThemeAdProgress(int themeIndex) {
        return prefs.getInt("theme_ads_watched_" + themeIndex, 0);
    }

    public int incrementThemeAdProgress(int themeIndex) {
        int next = Math.min(3, getThemeAdProgress(themeIndex) + 1);
        prefs.edit().putInt("theme_ads_watched_" + themeIndex, next).apply();
        return next;
    }

    public int getCustomGalleryAdProgress() {
        if (isVipAllThemesUnlocked()) return 3;
        return prefs.getInt("custom_gallery_theme_ads", 0);
    }

    public int incrementCustomGalleryAdProgress() {
        int next = Math.min(3, getCustomGalleryAdProgress() + 1);
        prefs.edit().putInt("custom_gallery_theme_ads", next).apply();
        return next;
    }

    /**
     * Opens the Complete 60-Theme Store + User Custom Gallery Theme with 3-Ad Lock & Paid VIP Unlock!
     */
    public void show60ThemeStoreDialog(
            final Activity activity,
            final int galleryRequestCode,
            final Runnable onThemeApplied) {

        final float d = activity.getResources().getDisplayMetrics().density;
        LinearLayout rootCard = new LinearLayout(activity);
        rootCard.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * d);
        rootCard.setPadding(pad, pad, pad, (int) (12 * d));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#F7080C14"));
        cardBg.setCornerRadius(26f * d);
        cardBg.setStroke(Math.max(2, (int) (1.8f * d)), Color.WHITE);
        rootCard.setBackground(cardBg);

        final AlertDialog storeDialog = new AlertDialog.Builder(activity).setView(rootCard).create();
        if (storeDialog.getWindow() != null) {
            storeDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Header Row
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding((int) (4 * d), 0, (int) (4 * d), (int) (10 * d));

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("🎨 60 Studio Themes & VIP Store");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(16.5f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(isVipAllThemesUnlocked()
                ? "👑 VIP Active: All 60 Themes + Gallery Theme Unlocked!"
                : "1-10 Free • 11-60 Locked (Watch 3 Ads Free or Pay ₹50/₹99 VIP)");
        tvSub.setTextColor(Color.parseColor("#CBD5E1"));
        tvSub.setTextSize(11.5f);
        titleCol.addView(tvSub);

        TextView btnClose = new TextView(activity);
        btnClose.setText("⋮");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(18f);
        btnClose.setTypeface(null, Typeface.BOLD);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setLayoutParams(new LinearLayout.LayoutParams((int) (38 * d), (int) (38 * d)));
        btnClose.setBackground(AudioThreeDotStudioHelper.makeFrostedWhiteCircle(d));
        btnClose.setOnClickListener(v -> storeDialog.dismiss());

        headerRow.addView(titleCol);
        headerRow.addView(btnClose);
        rootCard.addView(headerRow);

        // Top Action Bar: [🖼️ User Custom Gallery Photo Theme] + [👑 Unlock All 60 VIP]
        LinearLayout topBtnsRow = new LinearLayout(activity);
        topBtnsRow.setOrientation(LinearLayout.HORIZONTAL);
        topBtnsRow.setWeightSum(2f);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.bottomMargin = (int) (10 * d);
        topBtnsRow.setLayoutParams(topLp);

        TextView btnUserPhoto = new TextView(activity);
        int galAds = getCustomGalleryAdProgress();
        btnUserPhoto.setText(galAds >= 3
                ? "🖼️ User Photo Theme (Ready)"
                : "🔒 User Photo Theme (" + galAds + "/3 Ads)");
        btnUserPhoto.setTextColor(Color.parseColor("#0A0A10"));
        btnUserPhoto.setTextSize(11.5f);
        btnUserPhoto.setTypeface(null, Typeface.BOLD);
        btnUserPhoto.setGravity(Gravity.CENTER);
        btnUserPhoto.setPadding((int) (8 * d), (int) (10 * d), (int) (8 * d), (int) (10 * d));
        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b1Lp.rightMargin = (int) (5 * d);
        btnUserPhoto.setLayoutParams(b1Lp);
        GradientDrawable g1 = new GradientDrawable();
        g1.setColor(Color.WHITE);
        g1.setCornerRadius(14f * d);
        btnUserPhoto.setBackground(g1);

        btnUserPhoto.setOnClickListener(v -> {
            storeDialog.dismiss();
            if (getCustomGalleryAdProgress() >= 3) {
                Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                activity.startActivityForResult(pick, galleryRequestCode);
            } else {
                showCustomGalleryUnlockDialog(activity, galleryRequestCode, onThemeApplied);
            }
        });

        TextView btnVipUnlockAll = new TextView(activity);
        btnVipUnlockAll.setText("👑 Unlock All 60 (₹50/₹99)");
        btnVipUnlockAll.setTextColor(Color.WHITE);
        btnVipUnlockAll.setTextSize(11.5f);
        btnVipUnlockAll.setTypeface(null, Typeface.BOLD);
        btnVipUnlockAll.setGravity(Gravity.CENTER);
        btnVipUnlockAll.setPadding((int) (8 * d), (int) (10 * d), (int) (8 * d), (int) (10 * d));
        LinearLayout.LayoutParams b2Lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b2Lp.leftMargin = (int) (5 * d);
        btnVipUnlockAll.setLayoutParams(b2Lp);
        btnVipUnlockAll.setBackground(AudioThreeDotStudioHelper.makeFrostedWhitePill(d));

        btnVipUnlockAll.setOnClickListener(v -> {
            storeDialog.dismiss();
            showPayVipPlansDialog(activity, onThemeApplied);
        });

        topBtnsRow.addView(btnUserPhoto);
        topBtnsRow.addView(btnVipUnlockAll);
        rootCard.addView(topBtnsRow);

        // Scrollable List of all 60 Themes with Color Preview Swatches & Lock Status
        ScrollView scroll = new ScrollView(activity);
        LinearLayout listCol = new LinearLayout(activity);
        listCol.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < THEMES.length; i++) {
            final int themeIdx = i;
            final ThemePreset preset = THEMES[i];
            final boolean unlocked = isThemeUnlocked(i);
            final boolean isCurrent = (getCurrentThemeIndex() == i && !hasCustomBgPhoto());
            final int watchedAds = getThemeAdProgress(i);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding((int) (12 * d), (int) (11 * d), (int) (12 * d), (int) (11 * d));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int) (6 * d);
            row.setLayoutParams(rlp);

            GradientDrawable rBg = new GradientDrawable();
            rBg.setColor(isCurrent ? Color.parseColor("#34FFFFFF") : Color.parseColor("#16FFFFFF"));
            rBg.setCornerRadius(15f * d);
            rBg.setStroke(Math.max(1, (int) (isCurrent ? 2f * d : 1f * d)), Color.WHITE);
            row.setBackground(rBg);

            // Live Theme Color Swatch Circle
            View swatch = new View(activity);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams((int) (28 * d), (int) (28 * d));
            slp.rightMargin = (int) (12 * d);
            swatch.setLayoutParams(slp);
            GradientDrawable sBg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{preset.bgPrimary, preset.accentPrimary, preset.accentSecondary});
            sBg.setShape(GradientDrawable.OVAL);
            sBg.setStroke(Math.max(1, (int) (1.5f * d)), Color.WHITE);
            swatch.setBackground(sBg);

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(activity);
            tvName.setText(preset.name);
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(14f);
            tvName.setTypeface(null, Typeface.BOLD);
            textCol.addView(tvName);

            TextView tvStatus = new TextView(activity);
            if (isCurrent) {
                tvStatus.setText("✨ ACTIVE STUDIO THEME");
                tvStatus.setTextColor(Color.WHITE);
            } else if (unlocked) {
                tvStatus.setText(i < 10 ? "✅ Free Unlocked • Tap to Apply" : "🔓 Unlocked • Tap to Apply");
                tvStatus.setTextColor(Color.parseColor("#A7F3D0"));
            } else {
                tvStatus.setText("🔒 Locked • Watch 3 Ads (" + watchedAds + "/3) or Pay VIP");
                tvStatus.setTextColor(Color.parseColor("#FDE047"));
            }
            tvStatus.setTextSize(11f);
            tvStatus.setTypeface(null, Typeface.BOLD);
            textCol.addView(tvStatus);

            TextView badge = new TextView(activity);
            badge.setText(unlocked ? (isCurrent ? "ACTIVE" : "APPLY") : "🔒 " + watchedAds + "/3");
            badge.setTextColor(unlocked ? Color.parseColor("#0A0A10") : Color.WHITE);
            badge.setTextSize(11f);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
            GradientDrawable bBg = new GradientDrawable();
            bBg.setColor(unlocked ? Color.WHITE : Color.parseColor("#28FFFFFF"));
            bBg.setCornerRadius(12f * d);
            bBg.setStroke(Math.max(1, (int) d), Color.WHITE);
            badge.setBackground(bBg);

            row.addView(swatch);
            row.addView(textCol);
            row.addView(badge);

            row.setOnClickListener(v -> {
                storeDialog.dismiss();
                if (unlocked) {
                    setCustomBgUri("");
                    setThemeIndex(themeIdx);
                    if (onThemeApplied != null) onThemeApplied.run();
                    Toast.makeText(activity, "🎨 Theme Applied: " + preset.name, Toast.LENGTH_SHORT).show();
                } else {
                    showLockedTheme3AdOrPayDialog(activity, themeIdx, galleryRequestCode, onThemeApplied);
                }
            });

            listCol.addView(row);
        }

        scroll.addView(listCol);
        rootCard.addView(scroll);
        storeDialog.show();
    }

    /**
     * Dialog shown when user clicks any Locked Theme (11 to 60):
     * Lets user either Watch 3 Ads (0/3 -> 1/3 -> 2/3 -> 3/3) OR Pay ₹50 / ₹60 / ₹99 VIP!
     */
    private void showLockedTheme3AdOrPayDialog(
            final Activity activity,
            final int themeIdx,
            final int galleryRequestCode,
            final Runnable onThemeApplied) {

        final ThemePreset preset = THEMES[themeIdx];
        final int watched = getThemeAdProgress(themeIdx);
        final int remaining = Math.max(0, 3 - watched);

        String[] options = new String[]{
                "🎬 Watch Sponsor Ad (+1)  [Progress: " + watched + "/3 Ads • " + remaining + " Left]",
                "💳 Pay ₹50 Silver VIP Plan (Unlock All 60 Themes Forever)",
                "💳 Pay ₹60 Gold VIP Plan (Unlock All 60 Themes + Pro Bass)",
                "👑 Pay ₹99 Legend VIP Plan (Unlock Everything Lifetime)",
                "🔙 Back to 60 Themes Store"
        };

        SongAdapter.showPureWhiteAudioMenuDialog(
                activity,
                "🔒 Unlock: " + preset.name + " (" + watched + "/3 Ads)",
                options,
                which -> {
                    if (which == 0) {
                        play5SecondRewardedAdSimulation(activity, "Ad " + (watched + 1) + " of 3 for " + preset.name, () -> {
                            int newCount = incrementThemeAdProgress(themeIdx);
                            if (newCount >= 3) {
                                setCustomBgUri("");
                                setThemeIndex(themeIdx);
                                if (onThemeApplied != null) onThemeApplied.run();
                                Toast.makeText(activity, "🎉 3/3 Ads Complete! " + preset.name + " Permanently Unlocked & Applied!", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(activity, "✅ Ad Watched (" + newCount + "/3)! Watch " + (3 - newCount) + " more to unlock!", Toast.LENGTH_SHORT).show();
                                showLockedTheme3AdOrPayDialog(activity, themeIdx, galleryRequestCode, onThemeApplied);
                            }
                        });
                    } else if (which == 1) {
                        triggerUpiOrUnlockAllVip(activity, 50, "₹50 Silver VIP", themeIdx, onThemeApplied);
                    } else if (which == 2) {
                        triggerUpiOrUnlockAllVip(activity, 60, "₹60 Gold VIP", themeIdx, onThemeApplied);
                    } else if (which == 3) {
                        triggerUpiOrUnlockAllVip(activity, 99, "₹99 Legend VIP", themeIdx, onThemeApplied);
                    } else {
                        show60ThemeStoreDialog(activity, galleryRequestCode, onThemeApplied);
                    }
                });
    }

    private void showCustomGalleryUnlockDialog(
            final Activity activity,
            final int galleryRequestCode,
            final Runnable onThemeApplied) {

        final int watched = getCustomGalleryAdProgress();
        String[] opts = new String[]{
                "🎬 Watch Sponsor Ad (+1) to Unlock Custom Photo Theme [" + watched + "/3 Ads]",
                "👑 Pay ₹50 / ₹60 / ₹99 VIP (Unlock Custom Photo + All 60 Themes)"
        };
        SongAdapter.showPureWhiteAudioMenuDialog(
                activity,
                "🔒 Unlock Custom User Photo Theme (" + watched + "/3 Ads)",
                opts,
                which -> {
                    if (which == 0) {
                        play5SecondRewardedAdSimulation(activity, "Ad " + (watched + 1) + " of 3 for Custom Photo Theme", () -> {
                            int newCount = incrementCustomGalleryAdProgress();
                            if (newCount >= 3) {
                                Toast.makeText(activity, "🎉 3/3 Ads Complete! Pick your photo from Gallery!", Toast.LENGTH_LONG).show();
                                Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                                activity.startActivityForResult(pick, galleryRequestCode);
                            } else {
                                showCustomGalleryUnlockDialog(activity, galleryRequestCode, onThemeApplied);
                            }
                        });
                    } else {
                        showPayVipPlansDialog(activity, onThemeApplied);
                    }
                });
    }

    private void showPayVipPlansDialog(final Activity activity, final Runnable onThemeApplied) {
        String[] plans = new String[]{
                "🥈 Pay ₹50 Silver Plan -> Unlock All 60 Themes Forever",
                "🥇 Pay ₹60 Gold Plan -> Unlock All 60 Themes + 200% Booster",
                "👑 Pay ₹99 Legend Plan -> Lifetime All 60 Themes + VIP Crown"
        };
        SongAdapter.showPureWhiteAudioMenuDialog(activity, "💳 Choose VIP Plan (Instant Unlock)", plans, which -> {
            if (which == 0) triggerUpiOrUnlockAllVip(activity, 50, "₹50 Silver VIP", getCurrentThemeIndex(), onThemeApplied);
            else if (which == 1) triggerUpiOrUnlockAllVip(activity, 60, "₹60 Gold VIP", getCurrentThemeIndex(), onThemeApplied);
            else if (which == 2) triggerUpiOrUnlockAllVip(activity, 99, "₹99 Legend VIP", getCurrentThemeIndex(), onThemeApplied);
        });
    }

    private void triggerUpiOrUnlockAllVip(
            final Activity activity,
            final int amountInr,
            final String planTitle,
            final int targetThemeIdx,
            final Runnable onThemeApplied) {

        try {
            Uri upiUri = Uri.parse("upi://pay?pa=hybridmusic@upi&pn=Hybrid%20Music%20Player&am="
                    + amountInr + ".00&cu=INR&tn=" + Uri.encode(planTitle));
            Intent upiIntent = new Intent(Intent.ACTION_VIEW, upiUri);
            activity.startActivity(Intent.createChooser(upiIntent, "Pay " + planTitle + " via UPI"));
        } catch (Exception ignored) {
        }
        unlockAllThemesVip(planTitle);
        setThemeIndex(targetThemeIdx);
        if (onThemeApplied != null) onThemeApplied.run();
        Toast.makeText(activity, "👑 " + planTitle + " Activated! All 60 Themes Unlocked Forever!", Toast.LENGTH_LONG).show();
    }

    /**
     * Simulates a 5-Second Rewarded Video Ad with live countdown so user watches 3 Ads to unlock!
     */
    private void play5SecondRewardedAdSimulation(
            final Activity activity,
            String adHeader,
            final Runnable onAdFinished) {

        float d = activity.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int pad = (int) (24 * d);
        box.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0A0F1D"));
        bg.setCornerRadius(24f * d);
        bg.setStroke(Math.max(2, (int) (2 * d)), Color.WHITE);
        box.setBackground(bg);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("🎬 SPONSOR VIDEO AD");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(17f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        box.addView(tvTitle);

        TextView tvSub = new TextView(activity);
        tvSub.setText(adHeader + "\n\nHybrid Music Player Pro Audio Sponsor");
        tvSub.setTextColor(Color.parseColor("#CBD5E1"));
        tvSub.setTextSize(13f);
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, (int) (10 * d), 0, (int) (16 * d));
        box.addView(tvSub);

        final TextView tvTimer = new TextView(activity);
        tvTimer.setText("⏳ Reward in 5 seconds...");
        tvTimer.setTextColor(Color.parseColor("#0A0A10"));
        tvTimer.setTextSize(14f);
        tvTimer.setTypeface(null, Typeface.BOLD);
        tvTimer.setGravity(Gravity.CENTER);
        tvTimer.setPadding((int) (18 * d), (int) (10 * d), (int) (18 * d), (int) (10 * d));
        GradientDrawable tBg = new GradientDrawable();
        tBg.setColor(Color.WHITE);
        tBg.setCornerRadius(16f * d);
        tvTimer.setBackground(tBg);
        box.addView(tvTimer);

        final AlertDialog adDialog = new AlertDialog.Builder(activity)
                .setView(box)
                .setCancelable(false)
                .create();
        if (adDialog.getWindow() != null) {
            adDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        adDialog.show();

        new CountDownTimer(4500, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long sec = (millisUntilFinished / 1000) + 1;
                tvTimer.setText("⏳ Ad Playing... " + sec + "s left");
            }

            @Override
            public void onFinish() {
                if (adDialog.isShowing()) {
                    adDialog.dismiss();
                }
                if (onAdFinished != null) {
                    onAdFinished.run();
                }
            }
        }.start();
    }

    public boolean isLightBackground() {
        double luminance = ColorUtils.calculateLuminance(getCurrentTheme().bgPrimary);
        return luminance > 0.5;
    }

    public int getPrimaryTextColor() {
        if (hasCustomBgPhoto()) return Color.WHITE;
        return isLightBackground() ? Color.parseColor("#0F172A") : Color.WHITE;
    }

    public int getSecondaryTextColor() {
        if (hasCustomBgPhoto()) return Color.parseColor("#E2E8F0");
        return isLightBackground() ? Color.parseColor("#334155") : Color.parseColor("#CBD5E1");
    }

    public int getIconTintColor() {
        return getPrimaryTextColor();
    }

    public int getCardColorWithAlpha() {
        int card = getCurrentTheme().bgCard;
        if (hasCustomBgPhoto()) {
            return Color.argb(205, 15, 23, 42);
        }
        return card;
    }

    public void setCustomBgUri(String uriString) {
        prefs.edit().putString(KEY_CUSTOM_BG_URI, uriString != null ? uriString : "").apply();
    }

    public String getCustomBgUri() {
        return prefs.getString(KEY_CUSTOM_BG_URI, "");
    }

    public boolean hasCustomBgPhoto() {
        String uri = getCustomBgUri();
        return uri != null && !uri.trim().isEmpty();
    }

    public boolean isVinylDiscMode() {
        return prefs.getBoolean(KEY_DISC_MODE, false);
    }

    public void setVinylDiscMode(boolean vinylMode) {
        prefs.edit().putBoolean(KEY_DISC_MODE, vinylMode).apply();
    }

    public boolean isWaveformMode() {
        return prefs.getBoolean(KEY_WAVEFORM_MODE, true);
    }

    public void setWaveformMode(boolean waveform) {
        prefs.edit().putBoolean(KEY_WAVEFORM_MODE, waveform).apply();
    }

    public boolean isDjSlowFadeEnabled() {
        return prefs.getBoolean(KEY_DJ_SLOW_FADE, true);
    }

    public void setDjSlowFadeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DJ_SLOW_FADE, enabled).apply();
    }

    public String getHeaderTitle() {
        return prefs.getString(KEY_HEADER_TITLE, "Hybrid Music Player");
    }

    public void setHeaderTitle(String title) {
        if (title != null && !title.trim().isEmpty()) {
            prefs.edit().putString(KEY_HEADER_TITLE, title.trim()).apply();
        }
    }

    public String getHeaderStar() {
        return prefs.getString(KEY_HEADER_STAR, "★");
    }

    public void setHeaderStar(String starSymbol) {
        prefs.edit().putString(KEY_HEADER_STAR, starSymbol != null ? starSymbol : "★").apply();
    }

    public int getPuppyBreedIndex() {
        int idx = prefs.getInt(KEY_PUPPY_BREED, 0);
        if (idx < 0 || idx >= PUPPY_BREED_NAMES.length) idx = 0;
        return idx;
    }

    public void setPuppyBreedIndex(int idx) {
        prefs.edit().putInt(KEY_PUPPY_BREED, idx).apply();
    }

    public int getPuppyColorIndex() {
        int idx = prefs.getInt(KEY_PUPPY_COLOR, 1);
        if (idx < 0 || idx >= PUPPY_COLOR_HEX.length) idx = 0;
        return idx;
    }

    public void setPuppyColorIndex(int idx) {
        prefs.edit().putInt(KEY_PUPPY_COLOR, idx).apply();
    }

    public int getSelectedPuppyColor() {
        return PUPPY_COLOR_HEX[getPuppyColorIndex()];
    }

    public String getAppLanguage() {
        return prefs.getString(KEY_APP_LANGUAGE, "Hinglish");
    }

    public void setAppLanguage(String language) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language).apply();
    }

    public String getActiveVipPlan() {
        return prefs.getString(KEY_VIP_PLAN, "Free Studio User");
    }

    public void setActiveVipPlan(String planName) {
        prefs.edit().putString(KEY_VIP_PLAN, planName).apply();
        if (!"Free Studio User".equals(planName)) {
            prefs.edit().putBoolean(KEY_VIP_ALL_THEMES, true).apply();
        }
    }

    public void setSongTrimSeconds(long songId, int startSec, int endSec) {
        prefs.edit()
                .putInt("trim_start_" + songId, Math.max(0, startSec))
                .putInt("trim_end_" + songId, Math.max(0, endSec))
                .apply();
    }

    public int getSongTrimStartSec(long songId) {
        return prefs.getInt("trim_start_" + songId, 0);
    }

    public int getSongTrimEndSec(long songId) {
        return prefs.getInt("trim_end_" + songId, 0);
    }

    public String getPrivacyPolicyUrl() {
        return prefs.getString(KEY_PRIVACY_URL, "");
    }

    public void setPrivacyPolicyUrl(String url) {
        prefs.edit().putString(KEY_PRIVACY_URL, url != null ? url.trim() : "").apply();
    }

    public void setSongCustomPhoto(long songId, String uriString) {
        prefs.edit().putString("song_photo_" + songId, uriString).apply();
    }

    public String getSongCustomPhoto(long songId) {
        return prefs.getString("song_photo_" + songId, "");
    }

    public boolean isFavorite(long songId) {
        Set<String> set = prefs.getStringSet(KEY_FAVORITES, new HashSet<String>());
        return set.contains(String.valueOf(songId));
    }

    public boolean toggleFavorite(long songId) {
        Set<String> existing = prefs.getStringSet(KEY_FAVORITES, new HashSet<String>());
        Set<String> updated = new HashSet<>(existing);
        String key = String.valueOf(songId);
        boolean nowFav;
        if (updated.contains(key)) {
            updated.remove(key);
            nowFav = false;
        } else {
            updated.add(key);
            nowFav = true;
        }
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply();
        return nowFav;
    }

    public int[] getBottomControlOrder() {
        String raw = prefs.getString(KEY_BOTTOM_ORDER, "2,1,3,0");
        String[] parts = raw.split(",");
        int[] order = new int[]{2, 1, 3, 0};
        if (parts.length == 4) {
            try {
                for (int i = 0; i < 4; i++) {
                    order[i] = Integer.parseInt(parts[i].trim());
                }
            } catch (Exception ignored) {
            }
        }
        return order;
    }

    public void rotateBottomControlOrder() {
        int[] current = getBottomControlOrder();
        int first = current[0];
        current[0] = current[1];
        current[1] = current[2];
        current[2] = current[3];
        current[3] = first;
        String saved = current[0] + "," + current[1] + "," + current[2] + "," + current[3];
        prefs.edit().putString(KEY_BOTTOM_ORDER, saved).apply();
    }

    public void saveCustomLyrics(long songId, String lyrics) {
        prefs.edit().putString("lyrics_" + songId, lyrics).apply();
    }

    public String getSavedLyrics(long songId) {
        return prefs.getString("lyrics_" + songId, "");
    }

    public List<String> getPlaylistNames() {
        Set<String> names = prefs.getStringSet("playlist_names",
                new HashSet<>(Arrays.asList("My Top Hits", "Night Drive", "Gym Bass")));
        return new ArrayList<>(names);
    }

    public void addSongToPlaylist(String playlistName, long songId) {
        Set<String> names = new HashSet<>(getPlaylistNames());
        names.add(playlistName);
        prefs.edit().putStringSet("playlist_names", names).apply();

        String key = "playlist_songs_" + playlistName;
        Set<String> songs = new HashSet<>(prefs.getStringSet(key, new HashSet<String>()));
        songs.add(String.valueOf(songId));
        prefs.edit().putStringSet(key, songs).apply();
    }

    public Set<String> getPlaylistSongs(String playlistName) {
        return prefs.getStringSet("playlist_songs_" + playlistName, new HashSet<String>());
    }
}
