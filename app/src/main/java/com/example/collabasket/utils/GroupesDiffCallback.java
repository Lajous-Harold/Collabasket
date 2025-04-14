package com.example.collabasket.utils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.example.collabasket.model.Groupes;

public class GroupesDiffCallback extends DiffUtil.ItemCallback<Groupes> {
    @Override
    public boolean areItemsTheSame(@NonNull Groupes oldItem, @NonNull Groupes newItem) {
        // On suppose que chaque groupe a un ID unique (par exemple : id du document Firestore)
        return oldItem.getId().equals(newItem.getId());
    }

    @Override
    public boolean areContentsTheSame(@NonNull Groupes oldItem, @NonNull Groupes newItem) {
        return oldItem.equals(newItem);
    }
}
