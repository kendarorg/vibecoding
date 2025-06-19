package org.kendar.sync.ui.browser.local;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.kendar.sync.R;

import java.util.List;

public class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.DirectoryViewHolder> {

    private final List<DirectoryItem> directories;
    private final DirectorySelectListener listener;

    public DirectoryAdapter(List<DirectoryItem> directories, DirectorySelectListener listener) {
        this.directories = directories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DirectoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_directory, parent, false);
        return new DirectoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DirectoryViewHolder holder, int position) {
        DirectoryItem item = directories.get(position);
        holder.directoryName.setText(item.getDisplayName());
        holder.directoryPath.setText(item.getPath());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDirectorySelected(item.getPath());
            }
        });
    }

    @Override
    public int getItemCount() {
        return directories.size();
    }

    static class DirectoryViewHolder extends RecyclerView.ViewHolder {
        TextView directoryName;
        TextView directoryPath;

        DirectoryViewHolder(@NonNull View itemView) {
            super(itemView);
            directoryName = itemView.findViewById(R.id.text_directory_name);
            directoryPath = itemView.findViewById(R.id.text_directory_path);
        }
    }

    interface DirectorySelectListener {
        void onDirectorySelected(String path);
    }
}