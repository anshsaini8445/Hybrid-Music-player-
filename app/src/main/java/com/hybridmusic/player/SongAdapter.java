
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    public interface OnSongClickListener {
        void onSongClick(int position);
        void onThreeDotClick(SongItem song, View view);
    }

    private final Context context;
    private final List<SongItem> songList;
    private final ThemeManager themeManager;
    private final OnSongClickListener listener;

    public SongAdapter(Context context, List<SongItem> songList, ThemeManager themeManager, OnSongClickListener listener) {
        this.context = context;
        this.songList = songList;
        this.themeManager = themeManager;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        SongItem song = songList.get(position);
        holder.titleText.setText(song.title);
        holder.artistText.setText(song.artist + " • " + song.getFormattedDuration());
        holder.titleText.setTextColor(themeManager.getPrimaryTextColor());
        holder.artistText.setTextColor(themeManager.getSecondaryTextColor());

        // Load thumbnail or fallback
        try {
            holder.artImage.setImageURI(song.getAlbumArtUri());
        } catch (Exception e) {
            holder.artImage.setImageResource(android.R.drawable.ic_media_play);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSongClick(position);
        });

        // Three Dot Menu with Double-Confirmation Permanent Phone Delete
        holder.threeDotBtn.setOnClickListener(v -> {
            if (listener != null) listener.onThreeDotClick(song, v);
        });
    }

    public static void showPermanentDeleteDialog(Context context, SongItem song, Runnable onDeleteSuccess) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Permanently?")
                .setMessage("Are you sure you want to delete '" + song.title + "' from your phone storage permanently?")
                .setPositiveButton("First Confirm", (dialog, which) -> {
                    // Second Confirmation to prevent accidental taps
                    new AlertDialog.Builder(context)
                            .setTitle("Final Warning!")
                            .setMessage("This action cannot be undone. The audio file will be wiped from your device.")
                            .setPositiveButton("Delete Forever", (d2, w2) -> {
                                try {
                                    File f = new File(song.path);
                                    if (f.exists()) {
                                        f.delete();
                                    }
                                } catch (Exception ignored) {
                                }
                                if (onDeleteSuccess != null) onDeleteSuccess.run();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    public static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView artImage;
        TextView titleText, artistText;
        View threeDotBtn;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            artImage = itemView.findViewById(R.id.songThumbnail);
            titleText = itemView.findViewById(R.id.songTitle);
            artistText = itemView.findViewById(R.id.songArtist);
            threeDotBtn = itemView.findViewById(R.id.threeDotButton);
        }
    }
}
