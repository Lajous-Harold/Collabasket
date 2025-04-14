package com.example.collabasket.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.ProduitGroupes;
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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_produit_groupes, parent, false);
        return new ProduitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProduitViewHolder holder, int position) {
        ProduitAvecId produitAvecId = produits.get(position);
        ProduitGroupes produit = produitAvecId.produit;

        holder.nomProduit.setText(produit.getNom());
        holder.detailsProduit.setText(produit.getQuantite() + " " + produit.getUnite() + " • " + produit.getCategorie());
        holder.ajoutePar.setText("Ajouté par : " + produit.getAjoutePar());

        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(produit.isCoche());
        holder.nomProduit.setPaintFlags(produit.isCoche() ?
                holder.nomProduit.getPaintFlags() | 16 :
                holder.nomProduit.getPaintFlags() & ~16);
        holder.btnSupprimer.setVisibility(produit.isCoche() ? View.VISIBLE : View.GONE);

        holder.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            produit.setCoche(isChecked);
            notifyItemChanged(position);
            firestore.collection("groups")
                    .document(groupId)
                    .collection("produits")
                    .document(produitAvecId.id)
                    .update("coche", isChecked)
                    .addOnFailureListener(e -> Toast.makeText(holder.itemView.getContext(), "Erreur de mise à jour", Toast.LENGTH_SHORT).show());
        });

        holder.btnSupprimer.setOnClickListener(v -> {
            firestore.collection("groups")
                    .document(groupId)
                    .collection("produits")
                    .document(produitAvecId.id)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        produits.remove(position);
                        notifyItemRemoved(position);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(holder.itemView.getContext(), "Erreur lors de la suppression", Toast.LENGTH_SHORT).show();
                    });
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

        public ProduitViewHolder(@NonNull View itemView) {
            super(itemView);
            nomProduit = itemView.findViewById(R.id.text_nom_produit);
            detailsProduit = itemView.findViewById(R.id.text_details_produit);
            ajoutePar = itemView.findViewById(R.id.text_ajoute_par);
            checkbox = itemView.findViewById(R.id.checkbox_produit);
            btnSupprimer = itemView.findViewById(R.id.btn_supprimer);
        }
    }
}
