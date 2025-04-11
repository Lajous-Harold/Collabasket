package com.example.collabasket.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Groupes;

import java.util.List;

public class GroupesAdapter extends RecyclerView.Adapter<GroupesAdapter.GroupViewHolder> {

    private List<Groupes> groupes;

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_groupes, parent, false);
        return new GroupViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Groupes currentGroup = groupes.get(position);
        holder.groupName.setText(currentGroup.getGroupName());
    }

    @Override
    public int getItemCount() {
        return groupes != null ? groupes.size() : 0;
    }

    public void setGroups(List<Groupes> groupes) {
        this.groupes = groupes;
        notifyDataSetChanged();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        private TextView groupName;

        public GroupViewHolder(View itemView) {
            super(itemView);
            groupName = itemView.findViewById(R.id.text_group_name);
        }
    }
}
