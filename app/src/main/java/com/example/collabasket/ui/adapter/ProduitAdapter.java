package com.example.collabasket.ui.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Produit;
import com.example.collabasket.utils.HistoriqueUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ProduitAdapter extends RecyclerView.Adapter<ProduitAdapter.ProduitViewHolder> {

    private List<Produit> produits = new ArrayList<>();
    private OnSuppressionListener suppressionListener;

    public interface OnSuppressionListener {
        void onSupprimer(Produit produit);
    }

    public void setOnSuppressionListener(OnSuppressionListener listener) {
        this.suppressionListener = listener;
    }

    @NonNull
    @Override
    public ProduitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_produit, parent, false);
        return new ProduitViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ProduitViewHolder holder, int position) {
        Produit current = produits.get(position);

        String quantiteFormatee = (current.quantite % 1 == 0)
                ? String.valueOf((int) current.quantite)
                : String.valueOf(current.quantite);
        holder.nomTextView.setText(quantiteFormatee + " " + current.unite + " de " + current.nom);

        holder.categorieTextView.setText(current.categorie);
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(current.coche);

        if (current.coche) {
            holder.nomTextView.setPaintFlags(holder.nomTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.btnAchete.setVisibility(View.VISIBLE);
        } else {
            holder.nomTextView.setPaintFlags(holder.nomTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.btnAchete.setVisibility(View.GONE);
        }

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            current.coche = isChecked;
            notifyItemChanged(position);

            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .collection("produits")
                    .whereEqualTo("nom", current.nom)
                    .whereEqualTo("categorie", current.categorie)
                    .whereEqualTo("quantite", current.quantite)
                    .whereEqualTo("unite", current.unite)
                    .get()
                    .addOnSuccessListener(snapshots -> {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            doc.getReference().update("coche", isChecked);
                        }
                    });
        });

        holder.btnAchete.setOnClickListener(v -> {
            HistoriqueUtils.ajouterProduitSiAbsent(current);

            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .collection("produits")
                    .whereEqualTo("nom", current.nom)
                    .whereEqualTo("categorie", current.categorie)
                    .whereEqualTo("quantite", current.quantite)
                    .whereEqualTo("unite", current.unite)
                    .get()
                    .addOnSuccessListener(query -> {
                        for (QueryDocumentSnapshot doc : query) {
                            doc.getReference().delete();
                        }
                    });
        });

        holder.btnSupprimer.setOnClickListener(v -> {
            if (suppressionListener != null) {
                suppressionListener.onSupprimer(current);
            }
        });
    }

    @Override
    public int getItemCount() {
        return produits.size();
    }

    public void setProduits(List<Produit> produits) {
        this.produits = produits;
        notifyDataSetChanged();
    }

    static class ProduitViewHolder extends RecyclerView.ViewHolder {
        private final TextView nomTextView;
        private final TextView categorieTextView;
        private final CheckBox checkBox;
        private final ImageButton btnSupprimer;
        private final Button btnAchete;

        public ProduitViewHolder(View itemView) {
            super(itemView);
            nomTextView = itemView.findViewById(R.id.nom_produit);
            categorieTextView = itemView.findViewById(R.id.categorie_produit);
            checkBox = itemView.findViewById(R.id.checkbox_produit);
            btnSupprimer = itemView.findViewById(R.id.btn_supprimer);
            btnAchete = itemView.findViewById(R.id.btn_achete);
        }
    }
}