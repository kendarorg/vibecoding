package com.kendar.sync.ui.target;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kendar.sync.R;

import java.util.List;

/**
 * Adapter for displaying remote targets in a RecyclerView
 */
public class TargetAdapter extends RecyclerView.Adapter<TargetAdapter.TargetViewHolder> {
    private final Context context;
    private final List<String> targets;
    private final TargetClickListener listener;
    private int selectedPosition = -1;

    public interface TargetClickListener {
        void onTargetSelected(String target, int position);
    }

    public TargetAdapter(Context context, List<String> targets, TargetClickListener listener) {
        this.context = context;
        this.targets = targets;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TargetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_target, parent, false);
        return new TargetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TargetViewHolder holder, int position) {
        String target = targets.get(position);
        holder.bind(target, position);
    }

    @Override
    public int getItemCount() {
        return targets.size();
    }

    public void setSelectedPosition(int position) {
        int previousPosition = selectedPosition;
        selectedPosition = position;

        if (previousPosition >= 0) {
            notifyItemChanged(previousPosition);
        }
        notifyItemChanged(selectedPosition);
    }

    class TargetViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final RadioButton selectRadioButton;

        public TargetViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.targetNameTextView);
            selectRadioButton = itemView.findViewById(R.id.selectRadioButton);
        }

        public void bind(String target, int position) {
            nameTextView.setText(target);
            selectRadioButton.setChecked(position == selectedPosition);

            // Set click listeners
            View.OnClickListener clickListener = v -> {
                listener.onTargetSelected(target, position);
            };

            itemView.setOnClickListener(clickListener);
            selectRadioButton.setOnClickListener(clickListener);
        }
    }
}
