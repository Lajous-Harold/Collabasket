package com.example.collabasket;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.collabasket.ui.fragments.CompteFragment;
import com.example.collabasket.ui.fragments.GroupesFragment;
import com.example.collabasket.ui.fragments.ListeFragment;
import com.example.collabasket.ui.fragments.ListeGroupesFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import android.Manifest;
import android.content.pm.PackageManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "MainActivity onCreate lancé");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            checkPermissionAndUpdateSettings();
        } else {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }

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

        if (savedInstanceState == null) {
            navView.setSelectedItemId(R.id.nav_liste);
        }

        handleDynamicLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
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

    private void checkPermissionAndUpdateSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101);
            } else {
                updateNotificationSettingsAndToken();
            }
        } else {
            updateNotificationSettingsAndToken();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateNotificationSettingsAndToken();
            } else {
                Toast.makeText(this, "Les notifications n'ont pas été activées", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateNotificationSettingsAndToken() {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUid)
                .update("notificationsSettings.global", true);

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("FCM_TOKEN", "Échec de la récupération du token", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.d("FCM_TOKEN", "Token obtenu : " + token);

                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(currentUid)
                            .update("fcmToken", token)
                            .addOnSuccessListener(aVoid -> Log.d("FCM_TOKEN", "Token enregistré dans Firestore"))
                            .addOnFailureListener(e -> Log.w("FCM_TOKEN", "Erreur d’enregistrement", e));
                });
    }
}
