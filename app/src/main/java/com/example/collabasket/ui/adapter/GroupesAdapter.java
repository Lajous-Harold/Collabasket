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

import java.util.HashMap;
import java.util.Map;

public class GroupesAdapter extends ListAdapter<Groupes, GroupesAdapter.GroupViewHolder> {

    public interface OnGroupClickListener {
        void onGroupClick(Groupes group);
    }

    private final OnGroupClickListener listener;
    private final Map<String, String> rolesParGroupe = new HashMap<>();

    public GroupesAdapter(OnGroupClickListener listener) {
        super(new GroupesDiffCallback());
        this.listener = listener;
    }

    public void setRolePourGroupe(String groupId, String role) {
        rolesParGroupe.put(groupId, role);
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
        String nomGroupe = group.getGroupName();
        String role = rolesParGroupe.getOrDefault(group.getId(), "");
        if (!role.isEmpty()) {
            nomGroupe += " (" + role + ")";
        }
        holder.textGroupName.setText(nomGroupe);

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
