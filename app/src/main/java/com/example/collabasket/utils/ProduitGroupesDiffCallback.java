package com.example.collabasket.utils;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.example.collabasket.model.ProduitGroupes;
import com.example.collabasket.ui.adapter.ProduitGroupesAdapter.ProduitAvecId;

import java.util.List;

public class ProduitGroupesDiffCallback extends DiffUtil.Callback {

    private final List<ProduitAvecId> oldList;
    private final List<ProduitAvecId> newList;

    public ProduitGroupesDiffCallback(List<ProduitAvecId> oldList, List<ProduitAvecId> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).id.equals(newList.get(newItemPosition).id);
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        ProduitGroupes oldProduit = oldList.get(oldItemPosition).produit;
        ProduitGroupes newProduit = newList.get(newItemPosition).produit;
        return oldProduit.getNom().equals(newProduit.getNom())
                && oldProduit.getQuantite() == newProduit.getQuantite()
                && oldProduit.getUnite().equals(newProduit.getUnite())
                && oldProduit.getCategorie().equals(newProduit.getCategorie())
                && oldProduit.getAjoutePar().equals(newProduit.getAjoutePar())
                && oldProduit.isCoche() == newProduit.isCoche();
    }
}
