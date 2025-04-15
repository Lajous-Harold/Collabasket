package com.example.collabasket.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.collabasket.R;

public class CGUFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cgu, container, false);
        TextView cguText = view.findViewById(R.id.text_cgu);

        // Insère ici le texte brut de tes CGU
        String texteCGU = "Conditions Générales d’Utilisation (CGU)\n\n" +
                "1. Objet\nCette application permet le partage de listes de courses entre plusieurs utilisateurs...\n\n" +
                "2. Utilisation\nL’utilisateur s’engage à...\n\n" +
                "3. Données\nLes données personnelles sont utilisées uniquement pour...\n\n" +
                "4. Responsabilité\nL’éditeur ne peut être tenu responsable en cas de...\n\n" +
                "5. Modifications\nLes CGU peuvent être modifiées à tout moment.\n\n" +
                "Dernière mise à jour : Avril 2025.";

        cguText.setText(texteCGU);
        return view;
    }
}
