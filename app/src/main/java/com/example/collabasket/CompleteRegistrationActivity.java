package com.example.collabasket;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
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

        AuthCredential emailCredential = EmailAuthProvider.getCredential(email, password);
        mAuth.getCurrentUser().linkWithCredential(emailCredential)
                .addOnSuccessListener(linked -> {
                    FirebaseUser user = linked.getUser();
                    if (user != null) {
                        user.sendEmailVerification();

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("uid", user.getUid());
                        userData.put("username", username);
                        userData.put("email", email);
                        userData.put("phone", user.getPhoneNumber());

                        firestore.collection("users").document(user.getUid())
                                .set(userData)
                                .addOnSuccessListener(unused -> {
                                    new AlertDialog.Builder(this)
                                            .setTitle("Inscription réussie")
                                            .setMessage("Votre compte a bien été créé.\n\nUn email de vérification a été envoyé à " + email + ".")
                                            .setPositiveButton("Se connecter", (dialog, which) -> {
                                                startActivity(new Intent(this, LoginActivity.class));
                                                finish();
                                            })
                                            .setCancelable(false)
                                            .show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
