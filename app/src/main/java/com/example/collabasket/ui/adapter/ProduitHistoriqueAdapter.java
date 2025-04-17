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
import com.example.collabasket.model.ProduitHistorique;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.List;

public class ProduitHistoriqueAdapter extends RecyclerView.Adapter<ProduitHistoriqueAdapter.ViewHolder> {

    private List<ProduitHistorique> produits;

    public ProduitHistoriqueAdapter(List<ProduitHistorique> produits) {
        this.produits = produits;
    }

    public void setProduits(List<ProduitHistorique> produits) {
        this.produits = produits;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_produit_historique, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProduitHistorique produit = produits.get(position);
        String quantite = (produit.quantite % 1 == 0)
                ? String.valueOf((int) produit.quantite)
                : String.valueOf(produit.quantite);

        holder.nom.setText(quantite + " " + produit.unite + " de " + produit.nom);
        holder.categorie.setText(produit.categorie);

        holder.btnAjouter.setOnClickListener(v -> {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .collection("produits")
                    .whereEqualTo("nom", produit.nom)
                    .whereEqualTo("categorie", produit.categorie)
                    .whereEqualTo("quantite", produit.quantite)
                    .whereEqualTo("unite", produit.unite)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.isEmpty()) {
                            FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(uid)
                                    .collection("produits")
                                    .add(produit)
                                    .addOnSuccessListener(docRef -> {
                                        Toast.makeText(holder.itemView.getContext(), "Produit réajouté à la liste", Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Toast.makeText(holder.itemView.getContext(), "Déjà dans votre liste", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    @Override
    public int getItemCount() {
        return produits.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nom, categorie;
        Button btnAjouter;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            nom = itemView.findViewById(R.id.nom_historique);
            categorie = itemView.findViewById(R.id.categorie_historique);
            btnAjouter = itemView.findViewById(R.id.btn_ajouter_depuis_historique);
        }
    }
}