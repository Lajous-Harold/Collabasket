package com.example.collabasket.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Groupes;
import com.example.collabasket.utils.GroupesDiffCallback;

public class GroupesAdapter extends ListAdapter<Groupes, GroupesAdapter.GroupViewHolder> {

    public interface OnGroupClickListener {
        void onGroupClick(Groupes group);
    }

    private final OnGroupClickListener listener;

    public GroupesAdapter(OnGroupClickListener listener) {
        super(new GroupesDiffCallback());
        this.listener = listener;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_groupes, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Groupes group = getItem(position);
        holder.textGroupName.setText(group.getGroupName());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGroupClick(group);
            }
        });
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView textGroupName;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            textGroupName = itemView.findViewById(R.id.text_group_name);
        }
    }
}
