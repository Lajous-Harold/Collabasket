package com.example.collabasket;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "LoginActivity onCreate lancé");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        Button btnLogin = findViewById(R.id.btn_login);
        Button btnPhoneLogin = findViewById(R.id.btn_phone_login);
        TextView textRegister = findViewById(R.id.text_register);

        // Vérification si la permission est déjà donnée
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Demander la permission si elle n'est pas encore accordée
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101); // Code de requête pour la permission
            }
        }

        btnLogin.setOnClickListener(v -> loginUser());

        btnPhoneLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, PhoneVerificationActivity.class));
        });

        textRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, PhoneRegistrationActivity.class));
        });
    }
    // Gérer la réponse à la demande de permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission accordée, on active les notifications dans Firestore
                String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUid)
                        .update("notificationsSettings.global", true); // Activer les notifications
            } else {
                // Permission refusée, afficher un message et ne pas activer les notifications
                Toast.makeText(this, "Les notifications n'ont pas été activées", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void loginUser() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Email invalide");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            editPassword.setError("Mot de passe requis");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null && user.isEmailVerified()) {
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Veuillez vérifier votre adresse email", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Connexion échouée : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
