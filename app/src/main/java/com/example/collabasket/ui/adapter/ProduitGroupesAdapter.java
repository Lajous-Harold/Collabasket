package com.example.collabasket.ui.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.ProduitGroupes;
import com.example.collabasket.model.ProduitGroupesHistorique;
import com.example.collabasket.utils.ProduitGroupesDiffCallback;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ProduitGroupesAdapter extends RecyclerView.Adapter<ProduitGroupesAdapter.ProduitViewHolder> {

    public static class ProduitAvecId {
        public final String id;
        public final ProduitGroupes produit;

        public ProduitAvecId(String id, ProduitGroupes produit) {
            this.id = id;
            this.produit = produit;
        }
    }

    private List<ProduitAvecId> produits = new ArrayList<>();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final String groupId;

    public ProduitGroupesAdapter(String groupId) {
        this.groupId = groupId;
    }

    public void setProduits(List<ProduitAvecId> nouveauxProduits) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new ProduitGroupesDiffCallback(this.produits, nouveauxProduits));
        this.produits = nouveauxProduits;
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ProduitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_produit_groupes, parent, false);
        return new ProduitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProduitViewHolder holder, int position) {
        ProduitAvecId produitAvecId = produits.get(position);
        ProduitGroupes produit = produitAvecId.produit;

        // Affichage du nom + quantité + catégorie
        String quantite = (produit.getQuantite() % 1 == 0)
                ? String.valueOf((int) produit.getQuantite())
                : String.valueOf(produit.getQuantite());

        holder.nomProduit.setText(quantite + " " + produit.getUnite() + " de " + produit.getNom());
        holder.detailsProduit.setText(produit.getCategorie());
        holder.ajoutePar.setText("Ajouté par : " + produit.getAjoutePar());

        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(produit.isCoche());

        if (produit.isCoche()) {
            holder.nomProduit.setPaintFlags(holder.nomProduit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.btnAchete.setVisibility(View.VISIBLE);
        } else {
            holder.nomProduit.setPaintFlags(holder.nomProduit.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.btnAchete.setVisibility(View.GONE);
        }

        holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            produit.setCoche(isChecked);
            notifyItemChanged(position);

            firestore.collection("groups")
                    .document(groupId)
                    .collection("produits")
                    .document(produitAvecId.id)
                    .update("coche", isChecked);
        });

        holder.btnAchete.setOnClickListener(v -> {
            ajouterDansHistoriqueEtSupprimer(produitAvecId.id, produit, holder);
        });

        holder.btnSupprimer.setOnClickListener(v -> {
            ajouterDansHistoriqueEtSupprimer(produitAvecId.id, produit, holder);
        });
    }

    private void ajouterDansHistoriqueEtSupprimer(String docId, ProduitGroupes produit, ProduitViewHolder holder) {
        ProduitGroupesHistorique historique = new ProduitGroupesHistorique(produit);

        firestore.collection("groups")
                .document(groupId)
                .collection("historique")
                .add(historique)
                .addOnSuccessListener(aVoid -> {
                    firestore.collection("groups")
                            .document(groupId)
                            .collection("produits")
                            .document(docId)
                            .delete()
                            .addOnSuccessListener(v -> Toast.makeText(holder.itemView.getContext(), "Produit archivé", Toast.LENGTH_SHORT).show());
                });
    }

    @Override
    public int getItemCount() {
        return produits.size();
    }

    static class ProduitViewHolder extends RecyclerView.ViewHolder {

        TextView nomProduit, detailsProduit, ajoutePar;
        CheckBox checkbox;
        ImageButton btnSupprimer;
        Button btnAchete;

        public ProduitViewHolder(@NonNull View itemView) {
            super(itemView);
            nomProduit = itemView.findViewById(R.id.text_nom_produit);
            detailsProduit = itemView.findViewById(R.id.text_details_produit);
            ajoutePar = itemView.findViewById(R.id.text_ajoute_par);
            checkbox = itemView.findViewById(R.id.checkbox_produit);
            btnSupprimer = itemView.findViewById(R.id.btn_supprimer);
            btnAchete = itemView.findViewById(R.id.btn_achete);
        }
    }
}
