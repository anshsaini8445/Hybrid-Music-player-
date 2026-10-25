package com.hybridmusic.player;

import android.content.ContentUris;
import android.net.Uri;
import java.util.Locale;

public class SongItem {
    public long id;
    public String title;
    public String artist;
    public String album;
    public String path;
    public String uriString;
    public long albumId;
    public long duration;
    public long size;
    public long dateAdded;
    public boolean isVideoAudio;
    public String folderName;
    public String customArtUri;

    public SongItem(long id, String title, String artist, String album, String path,
                    String uriString, long albumId, long duration, long size,
                    long dateAdded, boolean isVideoAudio, String folderName) {
        this.id = id;
        this.title = (title == null || title.trim().isEmpty()) ? "Unknown Track" : title.trim();
        this.artist = (artist == null || artist.trim().isEmpty() || artist.contains("<unknown>"))
                ? "Unknown Singer" : artist.trim();
        this.album = (album == null || album.trim().isEmpty()) ? "Single Track" : album.trim();
        this.path = path != null ? path : "";
        this.uriString = uriString;
        this.albumId = albumId;
        this.duration = duration;
        this.size = size;
        this.dateAdded = dateAdded;
        this.isVideoAudio = isVideoAudio;
        this.folderName = (folderName == null || folderName.isEmpty()) ? "Music" : folderName;
    }

    public Uri getMediaUri() {
        return Uri.parse(uriString);
    }

    public Uri getAlbumArtUri() {
        if (customArtUri != null && !customArtUri.isEmpty()) {
            return Uri.parse(customArtUri);
        }
        return ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), albumId);
    }

    public String getFormattedDuration() {
        long totalSeconds = Math.max(0, duration / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    public String getFormattedSize() {
        double mb = size / (1024.0 * 1024.0);
        return String.format(Locale.US, "%.1f MB", mb);
    }
}
