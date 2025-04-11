package com.example.collabasket;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.collabasket.ui.fragments.CompteFragment;
import com.example.collabasket.ui.fragments.GroupesFragment;
import com.example.collabasket.ui.fragments.ListeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView navView = findViewById(R.id.bottom_navigation);

        // Gestion de la navigation par le BottomNavigationView
        navView.setOnItemSelectedListener(item -> {
            Fragment selected = null;

            // Vérifier quel item est sélectionné
            int id = item.getItemId();
            if (id == R.id.nav_liste) {
                selected = new ListeFragment();
            } else if (id == R.id.nav_groupes) {
                selected = new GroupesFragment();
            } else if (id == R.id.nav_compte) {
                selected = new CompteFragment();
            }

            // Remplacer le fragment si nécessaire
            if (selected != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, selected)
                        .commit();
            }

            return true;
        });

        // Démarrage avec le fragment par défaut (ListeFragment)
        if (savedInstanceState == null) {
            navView.setSelectedItemId(R.id.nav_liste);
        }
    }
}
