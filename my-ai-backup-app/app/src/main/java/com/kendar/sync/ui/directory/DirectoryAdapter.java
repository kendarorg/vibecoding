package com.kendar.sync.ui.directory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.R;
import com.kendar.sync.model.DirectoryInfo;

import java.util.List;

/**
 * Adapter for displaying directories in a RecyclerView
 */
public class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.DirectoryViewHolder> {
    private final Context context;
    private final List<DirectoryInfo> directories;
    private final DirectoryClickListener listener;

    public interface DirectoryClickListener {
        void onDirectoryClick(DirectoryInfo directory, int position);
        void onDirectorySelect(DirectoryInfo directory, int position);
    }

    public DirectoryAdapter(Context context, List<DirectoryInfo> directories, DirectoryClickListener listener) {
        this.context = context;
        this.directories = directories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DirectoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_directory, parent, false);
        return new DirectoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DirectoryViewHolder holder, int position) {
        DirectoryInfo directory = directories.get(position);
        holder.bind(directory, position);
    }

    @Override
    public int getItemCount() {
        return directories.size();
    }

    class DirectoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView fileCountTextView;
        private final RadioButton selectRadioButton;

        public DirectoryViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.directoryNameTextView);
            fileCountTextView = itemView.findViewById(R.id.fileCountTextView);
            selectRadioButton = itemView.findViewById(R.id.selectRadioButton);
        }

        public void bind(DirectoryInfo directory, int position) {
            nameTextView.setText(directory.getName());
            fileCountTextView.setText(directory.getFileCount() + " files");
            selectRadioButton.setChecked(directory.isSelected());

            // Set click listeners
            itemView.setOnClickListener(v -> listener.onDirectoryClick(directory, position));
            selectRadioButton.setOnClickListener(v -> listener.onDirectorySelect(directory, position));
        }
    }
}
