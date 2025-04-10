package com.example.collabasket;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private EditText editEmail, editPassword, editConfirmPassword, editUsername, editPhone;
    private CountryCodePicker ccp;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    // Mot de passe sécurisé : min 8 caractères, maj, min, chiffre, symbole
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!._*]).{8,}$"
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        editConfirmPassword = findViewById(R.id.edit_confirm_password);
        editUsername = findViewById(R.id.edit_username);
        editPhone = findViewById(R.id.edit_phone);
        ccp = findViewById(R.id.ccp);

        findViewById(R.id.btn_register).setOnClickListener(v -> registerUser());

        TextView textLogin = findViewById(R.id.text_login);
        textLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String email = editEmail.getText().toString().trim();
        String pass1 = editPassword.getText().toString();
        String pass2 = editConfirmPassword.getText().toString();
        String username = editUsername.getText().toString().trim();
        String rawPhone = editPhone.getText().toString().replaceFirst("^0+", "").trim();
        String phone = ccp.getSelectedCountryCodeWithPlus() + rawPhone;

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError("Email invalide");
            return;
        }

        if (!PASSWORD_PATTERN.matcher(pass1).matches()) {
            editPassword.setError("8 caractères min, 1 maj, 1 min, 1 chiffre, 1 spécial");
            return;
        }

        if (!pass1.equals(pass2)) {
            editConfirmPassword.setError("Les mots de passe ne correspondent pas");
            return;
        }

        if (TextUtils.isEmpty(username)) {
            editUsername.setError("Nom requis");
            return;
        }

        if (!phone.matches("^[+]?\\d{6,15}$")) {
            editPhone.setError("Numéro invalide");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, pass1)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        user.sendEmailVerification()
                                .addOnSuccessListener(unused -> {
                                    Map<String, Object> userData = new HashMap<>();
                                    userData.put("uid", user.getUid());
                                    userData.put("email", email);
                                    userData.put("username", username);
                                    userData.put("phone", phone);

                                    firestore.collection("users").document(user.getUid())
                                            .set(userData)
                                            .addOnSuccessListener(unused2 -> {
                                                Toast.makeText(this, "Inscription réussie. Vérifiez votre email.", Toast.LENGTH_LONG).show();
                                                startActivity(new Intent(this, LoginActivity.class));
                                                finish();
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Erreur d'envoi de l'email", Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erreur inscription : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
