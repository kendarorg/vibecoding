package org.kendar.sync.ui.browser.local;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.kendar.sync.R;

import java.util.List;

public class StorageBrowserAdapter extends RecyclerView.Adapter<StorageBrowserAdapter.StorageViewHolder> {

    private List<StorageItem> items;
    private final StorageItemClickListener listener;

    public StorageBrowserAdapter(List<StorageItem> items, StorageItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void updateItems(List<StorageItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StorageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_storage, parent, false);
        return new StorageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StorageViewHolder holder, int position) {
        StorageItem item = items.get(position);
        holder.nameTextView.setText(item.getName());

        // Set the correct icon based on directory or file
        holder.iconImageView.setImageResource(
                item.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);

        // Show select button only for directories
        if (item.isDirectory()) {
            holder.selectButton.setVisibility(View.VISIBLE);
        } else {
            holder.selectButton.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });

        holder.selectButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelectDirectoryClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class StorageViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImageView;
        TextView nameTextView;
        Button selectButton;

        StorageViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.image_item_icon);
            nameTextView = itemView.findViewById(R.id.text_item_name);
            selectButton = itemView.findViewById(R.id.button_select);
        }
    }

    interface StorageItemClickListener {
        void onItemClick(StorageItem item);
        void onSelectDirectoryClick(StorageItem item);
    }
}