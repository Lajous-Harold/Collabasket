package com.example.collabasket;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.collabasket.ui.fragments.CompteFragment;
import com.example.collabasket.ui.fragments.GroupesFragment;
import com.example.collabasket.ui.fragments.ListeFragment;
import com.example.collabasket.ui.fragments.ListeGroupesFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "MainActivity onCreate lancé");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Redirection depuis une invitation si présente
        Intent intent = getIntent();
        if (intent.hasExtra("redirectToGroupId")) {
            String groupId = intent.getStringExtra("redirectToGroupId");

            FirebaseFirestore.getInstance()
                    .collection("groups")
                    .document(groupId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            String groupName = snapshot.contains("groupName") ? snapshot.getString("groupName") : "Groupe";

                            ListeGroupesFragment fragment = new ListeGroupesFragment();
                            Bundle bundle = new Bundle();
                            bundle.putString("groupId", groupId);
                            bundle.putString("groupName", groupName);
                            fragment.setArguments(bundle);

                            getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.fragment_container, fragment)
                                    .commit();

                            intent.removeExtra("redirectToGroupId");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Erreur lors de l'accès au groupe", Toast.LENGTH_SHORT).show();
                    });
        }



        BottomNavigationView navView = findViewById(R.id.bottom_navigation);

        navView.setOnItemSelectedListener(item -> {
            Fragment selected = null;
            int id = item.getItemId();

            if (id == R.id.nav_liste) {
                selected = new ListeFragment();
            } else if (id == R.id.nav_groupes) {
                selected = new GroupesFragment();
            } else if (id == R.id.nav_compte) {
                selected = new CompteFragment();
            }

            if (selected != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, selected)
                        .commit();
            }

            return true;
        });

        // Charger le fragment par défaut
        if (savedInstanceState == null) {
            navView.setSelectedItemId(R.id.nav_liste);
        }

        // Gérer le lien dynamique s’il est présent
        handleDynamicLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // Important pour que getIntent() soit mis à jour
        handleDynamicLink(intent);
    }

    private void handleDynamicLink(Intent intent) {
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(intent)
                .addOnSuccessListener(this, pendingDynamicLinkData -> {
                    Uri deepLink = null;
                    if (pendingDynamicLinkData != null) {
                        deepLink = pendingDynamicLinkData.getLink();
                    }

                    if (deepLink != null && deepLink.getQueryParameter("groupId") != null) {
                        String groupId = deepLink.getQueryParameter("groupId");

                        ListeGroupesFragment fragment = new ListeGroupesFragment();
                        Bundle args = new Bundle();
                        if (groupId != null && !groupId.isEmpty()) {
                            args.putString("groupId", groupId);
                        }
                        args.putString("groupName", "Groupe");


                        fragment.setArguments(args);

                        getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, fragment)
                                .commit();
                    }
                })
                .addOnFailureListener(this, e -> Log.e(TAG, "Erreur Dynamic Link : " + e.getMessage()));
    }
}
