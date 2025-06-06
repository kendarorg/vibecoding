package com.kendar.sync.ui.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.R;

import java.util.List;

public class RemotePathAdapter extends RecyclerView.Adapter<RemotePathAdapter.PathViewHolder> {

    private List<String> paths;
    private final OnPathSelectedListener listener;
    private int selectedPosition = -1;

    public interface OnPathSelectedListener {
        void onPathSelected(String path);
    }

    public RemotePathAdapter(List<String> paths, OnPathSelectedListener listener) {
        this.paths = paths;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PathViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_remote_path, parent, false);
        return new PathViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PathViewHolder holder, int position) {
        String path = paths.get(position);
        holder.bind(path, position);
    }

    @Override
    public int getItemCount() {
        return paths.size();
    }

    public void updatePaths(List<String> paths) {
        this.paths = paths;
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    class PathViewHolder extends RecyclerView.ViewHolder {
        private final TextView pathTextView;
        private final RadioButton radioButton;

        public PathViewHolder(@NonNull View itemView) {
            super(itemView);
            pathTextView = itemView.findViewById(R.id.text_path);
            radioButton = itemView.findViewById(R.id.radio_select);
        }

        public void bind(String path, int position) {
            pathTextView.setText(path);
            radioButton.setChecked(position == selectedPosition);

            itemView.setOnClickListener(v -> {
                selectedPosition = getAdapterPosition();
                listener.onPathSelected(path);
                notifyDataSetChanged();
            });

            radioButton.setOnClickListener(v -> {
                selectedPosition = getAdapterPosition();
                listener.onPathSelected(path);
                notifyDataSetChanged();
            });
        }
    }
}