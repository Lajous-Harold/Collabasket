package com.example.collabasket.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.ProduitGroupes;
import com.example.collabasket.model.ProduitGroupesHistorique;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.List;

public class ProduitGroupesHistoriqueAdapter extends RecyclerView.Adapter<ProduitGroupesHistoriqueAdapter.ViewHolder> {

    private List<ProduitGroupesHistorique> produits;
    private String groupId;

    public ProduitGroupesHistoriqueAdapter(List<ProduitGroupesHistorique> produits) {
        this.produits = produits;
    }

    public void setProduits(List<ProduitGroupesHistorique> produits) {
        this.produits = produits;
        notifyDataSetChanged();
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_produit_historique_groupe, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProduitGroupesHistorique produit = produits.get(position);

        String quantite = (produit.quantite % 1 == 0)
                ? String.valueOf((int) produit.quantite)
                : String.valueOf(produit.quantite);

        holder.nom.setText(quantite + " " + produit.unite + " de " + produit.nom);
        holder.categorie.setText(produit.categorie);
        holder.ajoutePar.setText("Ajouté par : " + produit.ajoutePar);

        holder.btnAjouter.setOnClickListener(v -> {
            if (groupId == null || groupId.isEmpty()) {
                Toast.makeText(holder.itemView.getContext(), "Erreur : ID du groupe manquant", Toast.LENGTH_SHORT).show();
                return;
            }

            String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            FirebaseFirestore.getInstance().collection("users")
                    .document(currentUserId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String currentUsername = documentSnapshot.getString("username");

                        FirebaseFirestore.getInstance()
                                .collection("groups")
                                .document(groupId)
                                .collection("produits")
                                .whereEqualTo("nom", produit.nom)
                                .whereEqualTo("categorie", produit.categorie)
                                .whereEqualTo("unite", produit.unite)
                                .whereEqualTo("quantite", produit.quantite)
                                .get()
                                .addOnSuccessListener(query -> {
                                    if (query.isEmpty()) {
                                        ProduitGroupes nouveauProduit = new ProduitGroupes(
                                                produit.nom,
                                                produit.categorie,
                                                produit.quantite,
                                                produit.unite,
                                                groupId,
                                                currentUsername != null ? currentUsername : "Inconnu"
                                        );
                                        FirebaseFirestore.getInstance()
                                                .collection("groups")
                                                .document(groupId)
                                                .collection("produits")
                                                .add(nouveauProduit);
                                    } else {
                                        Toast.makeText(holder.itemView.getContext(), "Ce produit existe déjà dans la liste", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    });
        });
    }

    @Override
    public int getItemCount() {
        return produits.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nom, categorie, ajoutePar;
        Button btnAjouter;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nom = itemView.findViewById(R.id.nom_historique);
            categorie = itemView.findViewById(R.id.categorie_historique);
            ajoutePar = itemView.findViewById(R.id.text_ajoute_par);
            btnAjouter = itemView.findViewById(R.id.btn_ajouter_depuis_historique);
        }
    }
}