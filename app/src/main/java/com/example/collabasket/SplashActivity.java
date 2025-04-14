package com.example.collabasket;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "SplashActivity onCreate lancé");

        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null && currentUser.isEmailVerified()) {
            // Utilisateur déjà connecté et validé
            startActivity(new Intent(this, MainActivity.class));
        } else {
            // Pas connecté ou email non vérifié
            startActivity(new Intent(this, LoginActivity.class));
        }

        finish(); // on ne revient jamais sur le splash
    }
}
