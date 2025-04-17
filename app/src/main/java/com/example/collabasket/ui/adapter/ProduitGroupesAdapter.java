package com.example.collabasket.ui.adapter;

import android.app.AlertDialog;
import android.content.Context;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        String quantite = (produit.getQuantite() % 1 == 0)
                ? String.valueOf((int) produit.getQuantite())
                : String.valueOf(produit.getQuantite());

        holder.nomProduit.setText(quantite + " " + produit.getUnite() + " de " + produit.getNom());
        holder.detailsProduit.setText(produit.getCategorie());
        holder.ajoutePar.setText("Ajouté par : " + produit.getAjoutePar());

        holder.checkbox_produit.setOnCheckedChangeListener(null);
        holder.checkbox_produit.setChecked(produit.isCoche());
        holder.checkbox_produit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            firestore.collection("groups")
                    .document(groupId)
                    .collection("produits")
                    .document(produitAvecId.id)
                    .update("coche", isChecked)
                    .addOnSuccessListener(unused -> notifyItemChanged(holder.getAdapterPosition()));
        });

        if (produit.isCoche()) {
            holder.nomProduit.setPaintFlags(holder.nomProduit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.btnAchete.setVisibility(View.VISIBLE);
        } else {
            holder.nomProduit.setPaintFlags(holder.nomProduit.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.btnAchete.setVisibility(View.GONE);
        }

        holder.btnSupprimer.setOnClickListener(v -> {
            Context context = holder.itemView.getContext();
            new AlertDialog.Builder(context)
                    .setTitle("Confirmation")
                    .setMessage("Supprimer ce produit ?")
                    .setPositiveButton("Oui", (dialog, which) -> verifierEtSupprimer(holder.getAdapterPosition()))
                    .setNegativeButton("Annuler", null)
                    .show();
        });

        holder.btnAchete.setOnClickListener(v -> {
            Context context = holder.itemView.getContext();
            new AlertDialog.Builder(context)
                    .setTitle("Confirmation")
                    .setMessage("Marquer ce produit comme acheté ?")
                    .setPositiveButton("Oui", (dialog, which) -> verifierEtMarquerAchete(holder.getAdapterPosition()))
                    .setNegativeButton("Annuler", null)
                    .show();
        });
    }

    private void verifierEtSupprimer(int position) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firestore.collection("groups").document(groupId).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                DocumentSnapshot member = document;
                String role = document.getString("members." + currentUid + ".role");
                if ("Propriétaire".equals(role) || "Administrateur".equals(role)) {
                    supprimer(position);
                } else {
                    Toast.makeText(null, "Permission refusée", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void verifierEtMarquerAchete(int position) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firestore.collection("groups").document(groupId).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                String role = document.getString("members." + currentUid + ".role");
                if ("Propriétaire".equals(role) || "Administrateur".equals(role)) {
                    marquerAchete(position);
                } else {
                    Toast.makeText(null, "Permission refusée", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void supprimer(int position) {
        ProduitAvecId produitAvecId = produits.get(position);

        firestore.collection("groups")
                .document(groupId)
                .collection("produits")
                .document(produitAvecId.id)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    firestore.collection("groups")
                            .document(groupId)
                            .collection("historique")
                            .add(new ProduitGroupesHistorique(produitAvecId.produit));
                });
    }

    private void marquerAchete(int position) {
        supprimer(position);
    }

    @Override
    public int getItemCount() {
        return produits.size();
    }

    static class ProduitViewHolder extends RecyclerView.ViewHolder {
        TextView nomProduit, detailsProduit, ajoutePar;
        CheckBox checkbox_produit;
        Button btnAchete;
        ImageButton btnSupprimer;

        public ProduitViewHolder(@NonNull View itemView) {
            super(itemView);
            nomProduit = itemView.findViewById(R.id.text_nom_produit);
            detailsProduit = itemView.findViewById(R.id.text_details_produit);
            ajoutePar = itemView.findViewById(R.id.text_ajoute_par);
            checkbox_produit = itemView.findViewById(R.id.checkbox_produit);
            btnAchete = itemView.findViewById(R.id.btn_achete);
            btnSupprimer = itemView.findViewById(R.id.btn_supprimer);
        }
    }
}