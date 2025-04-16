package com.example.collabasket;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;

public class SplashActivity extends AppCompatActivity {

    private static final int ANIMATION_DURATION = 1000;
    private static final int SPLASH_DELAY = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "SplashActivity onCreate lancé");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Animation logo + titre
        ImageView logo = findViewById(R.id.logo_app);

        logo.setAlpha(0f);

        logo.animate().alpha(1f).setDuration(ANIMATION_DURATION).start();

        // Traitement des liens d'invitation
        FirebaseDynamicLinks.getInstance()
                .getDynamicLink(getIntent())
                .addOnSuccessListener(this, pendingDynamicLinkData -> {
                    Uri deepLink = null;
                    if (pendingDynamicLinkData != null) {
                        deepLink = pendingDynamicLinkData.getLink();
                        Log.d("DYNAMIC_LINK", "Lien détecté : " + deepLink);

                        if (deepLink != null && deepLink.getQueryParameter("groupId") != null) {
                            String groupId = deepLink.getQueryParameter("groupId");

                            new Handler().postDelayed(() -> {
                                Intent intent = new Intent(this, InvitationHandlerActivity.class);
                                intent.putExtra("groupId", groupId);
                                startActivity(intent);
                                finish();
                            }, SPLASH_DELAY);
                            return;
                        }
                    }

                    // Aucun lien spécial : comportement classique
                    delayThenRedirect();
                })
                .addOnFailureListener(this, e -> {
                    Log.w("DYNAMIC_LINK", "Échec récupération lien dynamique", e);
                    delayThenRedirect();
                });
    }

    private void delayThenRedirect() {
        new Handler().postDelayed(this::handleDefaultRedirection, SPLASH_DELAY);
    }

    private void handleDefaultRedirection() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null && currentUser.isEmailVerified()) {
            startActivity(new Intent(this, MainActivity.class));
        } else {
            startActivity(new Intent(this, LoginActivity.class));
        }

        finish();
    }
}
