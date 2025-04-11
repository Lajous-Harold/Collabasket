package com.example.collabasket.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.collabasket.R;
import com.example.collabasket.model.ProduitGroupes;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ProduitGroupesAdapter extends RecyclerView.Adapter<ProduitGroupesAdapter.ProduitViewHolder> {

    private List<ProduitGroupes> produits = new ArrayList<>();

    public void setProduits(List<ProduitGroupes> produits) {
        this.produits = produits;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProduitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_produit_groupes, parent, false);
        return new ProduitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProduitViewHolder holder, int position) {
        ProduitGroupes produit = produits.get(position);

        holder.nomProduit.setText(produit.getNom());
        holder.detailsProduit.setText(
                produit.getQuantite() + " " + produit.getUnite() + " • " + produit.getCategorie()
        );
        holder.ajoutePar.setText("Ajouté par : " + produit.getAjoutePar());
    }

    @Override
    public int getItemCount() {
        return produits.size();
    }

    static class ProduitViewHolder extends RecyclerView.ViewHolder {
        TextView nomProduit;
        TextView detailsProduit;
        TextView ajoutePar;

        public ProduitViewHolder(@NonNull View itemView) {
            super(itemView);
            nomProduit = itemView.findViewById(R.id.text_nom_produit);
            detailsProduit = itemView.findViewById(R.id.text_details_produit);
            ajoutePar = itemView.findViewById(R.id.text_ajoute_par);
        }
    }
}
