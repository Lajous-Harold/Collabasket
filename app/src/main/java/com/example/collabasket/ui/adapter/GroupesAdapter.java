package com.example.collabasket.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Groupes;

import java.util.ArrayList;
import java.util.List;

public class GroupesAdapter extends RecyclerView.Adapter<GroupesAdapter.GroupViewHolder> {

    public interface OnGroupClickListener {
        void onGroupClick(Groupes group, String groupId);
    }

    private List<Groupes> groups = new ArrayList<>();
    private List<String> groupIds = new ArrayList<>();
    private final OnGroupClickListener listener;

    public GroupesAdapter(OnGroupClickListener listener) {
        this.listener = listener;
    }

    public void setGroups(List<Groupes> newGroups, List<String> ids) {
        this.groups = newGroups;
        this.groupIds = ids;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_groupes, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Groupes group = groups.get(position);
        String groupId = groupIds.get(position);
        holder.textGroupName.setText(group.getGroupName());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGroupClick(group, groupId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView textGroupName;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            textGroupName = itemView.findViewById(R.id.text_group_name);
        }
    }
}
