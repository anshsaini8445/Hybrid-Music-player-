package com.hybridmusic.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
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
    private static final String KEY_DISC_MODE = "disc_mode"; // true = Vinyl Disc + Danda, false = Square Box
    private static final String KEY_WAVEFORM_MODE = "waveform_mode"; // true = Lambe Dande, false = Simple Line
    private static final String KEY_HEADER_TITLE = "header_title";
    private static final String KEY_HEADER_STAR = "header_star";
    private static final String KEY_FAVORITES = "favorite_song_ids";
    private static final String KEY_BOTTOM_ORDER = "bottom_icon_order";
    private static final String KEY_DJ_SLOW_FADE = "dj_slow_fade";

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

    // 20 High-Contrast Modern Themes (Dark, Neon, AMOLED & Clean Daylight Themes)
    private static final ThemePreset[] THEMES = new ThemePreset[]{
            new ThemePreset("1. Muso Cyber Neon", "#0B0F19", "#151D2E", "#1E293B", "#00E5FF", "#FF2E93"),
            new ThemePreset("2. PLAYit Royal Gold", "#0F0C08", "#1C160E", "#292014", "#FFD700", "#FF8C00"),
            new ThemePreset("3. AMOLED Pitch Black", "#000000", "#0D0D0D", "#1A1A1A", "#00FF87", "#00B8FF"),
            new ThemePreset("4. Crimson Velvet", "#1A070D", "#2B0C16", "#3D1220", "#FF3366", "#FF99AC"),
            new ThemePreset("5. Emerald Matrix", "#06150E", "#0C261A", "#143827", "#10B981", "#34D399"),
            new ThemePreset("6. Deep Ocean Blue", "#071324", "#0E223D", "#173357", "#38BDF8", "#818CF8"),
            new ThemePreset("7. Ultra Violet Galaxy", "#120824", "#1E1038", "#2B184D", "#C084FC", "#F472B6"),
            new ThemePreset("8. Sunset Mandarin", "#1C0D06", "#2E160B", "#422112", "#FB923C", "#FACC15"),
            new ThemePreset("9. Tokyo Midnight", "#090D16", "#131B2E", "#1F2A44", "#F43F5E", "#38BDF8"),
            new ThemePreset("10. Dracula Studio", "#1E1F29", "#282A36", "#343746", "#BD93F9", "#FF79C6"),
            new ThemePreset("11. Nordic Frost Dark", "#141A21", "#1F2933", "#2B3845", "#67E8F9", "#A7F3D0"),
            new ThemePreset("12. Lava Burst", "#1A0805", "#2B100A", "#3D1810", "#EF4444", "#F59E0B"),
            new ThemePreset("13. Copper Bass", "#17110E", "#261C17", "#362821", "#D97706", "#FBBF24"),
            new ThemePreset("14. Mint Electric", "#071717", "#0F2929", "#173B3B", "#2DD4BF", "#A3E635"),
            new ThemePreset("15. Rose Quartz Dark", "#1C1018", "#2B1925", "#3B2333", "#FB7185", "#F472B6"),
            new ThemePreset("16. Indigo Starlight", "#0B0E24", "#14193B", "#1F2654", "#818CF8", "#38BDF8"),
            new ThemePreset("17. Forest Amber", "#0D140A", "#172412", "#22361B", "#84CC16", "#EAB308"),
            new ThemePreset("18. Clean Snow Daylight", "#F8FAFC", "#FFFFFF", "#E2E8F0", "#0284C7", "#DB2777"),
            new ThemePreset("19. Sakura Daylight", "#FFF1F2", "#FFFFFF", "#FFE4E6", "#E11D48", "#9333EA"),
            new ThemePreset("20. Mint Daylight Breeze", "#F0FDF4", "#FFFFFF", "#DCFCE7", "#059669", "#0284C7")
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

    // Automatic Contrast Engine: Never allows black text on dark background or white text on light background
    public boolean isLightBackground() {
        double luminance = ColorUtils.calculateLuminance(getCurrentTheme().bgPrimary);
        return luminance > 0.5;
    }

    public int getPrimaryTextColor() {
        // If user set a custom background photo, always use crisp white with shadow/card contrast
        if (hasCustomBgPhoto()) {
            return Color.WHITE;
        }
        return isLightBackground() ? Color.parseColor("#0F172A") : Color.WHITE;
    }

    public int getSecondaryTextColor() {
        if (hasCustomBgPhoto()) {
            return Color.parseColor("#E2E8F0");
        }
        return isLightBackground() ? Color.parseColor("#334155") : Color.parseColor("#CBD5E1");
    }

    public int getIconTintColor() {
        return getPrimaryTextColor();
    }

    public int getCardColorWithAlpha() {
        int card = getCurrentTheme().bgCard;
        if (hasCustomBgPhoto()) {
            return Color.argb(200, 15, 23, 42);
        }
        return card;
    }

    // Custom Gallery Theme Photo Support
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

    // Disc + Danda Mode vs Square Box Mode
    public boolean isVinylDiscMode() {
        return prefs.getBoolean(KEY_DISC_MODE, false); // Default Square Box or toggleable to Disc + Danda
    }

    public void setVinylDiscMode(boolean vinylMode) {
        prefs.edit().putBoolean(KEY_DISC_MODE, vinylMode).apply();
    }

    // Waveform (Lambe Dande) vs Simple Line Seekbar
    public boolean isWaveformMode() {
        return prefs.getBoolean(KEY_WAVEFORM_MODE, true);
    }

    public void setWaveformMode(boolean waveform) {
        prefs.edit().putBoolean(KEY_WAVEFORM_MODE, waveform).apply();
    }

    // 10-Second DJ Slow Transition Toggle
    public boolean isDjSlowFadeEnabled() {
        return prefs.getBoolean(KEY_DJ_SLOW_FADE, true);
    }

    public void setDjSlowFadeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DJ_SLOW_FADE, enabled).apply();
    }

    // Customizable App Title & Star Icon in Top Bar
    public String getHeaderTitle() {
        return prefs.getString(KEY_HEADER_TITLE, "Hybrid Music Player");
    }

    public void setHeaderTitle(String title) {
        prefs.edit().putString(KEY_HEADER_TITLE, title).apply();
    }

    public String getHeaderStar() {
        return prefs.getString(KEY_HEADER_STAR, "★");
    }

    public void setHeaderStar(String starSymbol) {
        prefs.edit().putString(KEY_HEADER_STAR, starSymbol).apply();
    }

    // Permanent Custom Photo Per Song from Library
    public void setSongCustomPhoto(long songId, String uriString) {
        prefs.edit().putString("song_photo_" + songId, uriString).apply();
    }

    public String getSongCustomPhoto(long songId) {
        return prefs.getString("song_photo_" + songId, "");
    }

    // Favorites Management
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

    // Swappable Bottom Controls Order (0=Timer, 1=A-B Repeat, 2=Equalizer, 3=Speed)
    public int[] getBottomControlOrder() {
        String raw = prefs.getString(KEY_BOTTOM_ORDER, "0,1,2,3");
        String[] parts = raw.split(",");
        int[] order = new int[]{0, 1, 2, 3};
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

    public void swapBottomControls(int posA, int posB) {
        int[] current = getBottomControlOrder();
        if (posA >= 0 && posA < 4 && posB >= 0 && posB < 4) {
            int temp = current[posA];
            current[posA] = current[posB];
            current[posB] = temp;
            String saved = current[0] + "," + current[1] + "," + current[2] + "," + current[3];
            prefs.edit().putString(KEY_BOTTOM_ORDER, saved).apply();
        }
    }

    // Custom Saved Lyrics Per Song
    public void saveCustomLyrics(long songId, String lyrics) {
        prefs.edit().putString("lyrics_" + songId, lyrics).apply();
    }

    public String getSavedLyrics(long songId) {
        return prefs.getString("lyrics_" + songId, "");
    }

    // User Playlists Support
    public List<String> getPlaylistNames() {
        Set<String> names = prefs.getStringSet("playlist_names", new HashSet<>(Arrays.asList("My Top Hits", "Night Drive", "Gym Bass")));
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
