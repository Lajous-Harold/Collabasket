package com.example.collabasket;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.*;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CompleteRegistrationActivity extends AppCompatActivity {

    private EditText editUsername, editEmail, editPassword, editConfirmPassword;
    private Button btnComplete, btnBack;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!._*]).{8,}$"
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "CompleteRegistrationActivity onCreate lancé");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_registration);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        editUsername = findViewById(R.id.edit_username);
        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        editConfirmPassword = findViewById(R.id.edit_confirm_password);
        btnComplete = findViewById(R.id.btn_complete_registration);
        btnBack = findViewById(R.id.btn_back);

        btnComplete.setOnClickListener(v -> finishRegistration());

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, PhoneRegistrationActivity.class));
            finish();
        });
    }

    private void finishRegistration() {
        String username = editUsername.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString();
        String confirmPassword = editConfirmPassword.getText().toString();
        String verifiedPhone = getIntent().getStringExtra("verifiedPhone");

        if (TextUtils.isEmpty(username)) {
            editUsername.setError("Nom requis");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Email invalide");
            return;
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            editPassword.setError("Mot de passe peu sécurisé");
            return;
        }

        if (!password.equals(confirmPassword)) {
            editConfirmPassword.setError("Les mots de passe ne correspondent pas");
            return;
        }

        // ✅ Vérifie si l’email est déjà utilisé avant de linker
        mAuth.fetchSignInMethodsForEmail(email)
                .addOnSuccessListener(result -> {
                    List<String> providers = result.getSignInMethods();
                    if (providers != null && !providers.isEmpty()) {
                        Toast.makeText(this, "Cet email est déjà lié à un autre compte.", Toast.LENGTH_LONG).show();
                    } else {
                        // 🔐 L’email n’est pas encore utilisé
                        AuthCredential emailCredential = EmailAuthProvider.getCredential(email, password);
                        mAuth.getCurrentUser().linkWithCredential(emailCredential)
                                .addOnSuccessListener(linked -> {
                                    FirebaseUser user = linked.getUser();
                                    if (user != null) {
                                        handleAccountCreation(user, username, email, verifiedPhone);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erreur lors de la vérification de l'email : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void handleAccountCreation(FirebaseUser user, String username, String email, String phone) {
        user.sendEmailVerification().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("uid", user.getUid());
                userData.put("username", username);
                userData.put("email", email);
                userData.put("phone", phone);
                userData.put("createdAt", FieldValue.serverTimestamp());

                Map<String, Object> defaultNotifications = new HashMap<>();
                defaultNotifications.put("global", true);
                defaultNotifications.put("produitAjoute", true);
                defaultNotifications.put("produitSupprime", true);
                defaultNotifications.put("membreAjoute", true);
                defaultNotifications.put("groupeCree", true);

                userData.put("notificationsSettings", defaultNotifications);

                firestore.collection("users").document(user.getUid())
                        .set(userData)
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(this, "Email de vérification envoyé à " + email, Toast.LENGTH_LONG).show();
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        });
            } else {
                Toast.makeText(this, "Erreur lors de l'envoi de l'email", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
