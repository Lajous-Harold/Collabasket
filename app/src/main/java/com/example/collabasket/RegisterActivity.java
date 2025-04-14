package com.example.collabasket;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private EditText editEmail, editPassword, editConfirmPassword, editUsername, editPhone, editCode;
    private Button btnRegister, btnVerifyCode;
    private CountryCodePicker ccp;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private String verificationId;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!._*]).{8,}$"
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "RegisterActivity onCreate lancé");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        editConfirmPassword = findViewById(R.id.edit_confirm_password);
        editUsername = findViewById(R.id.edit_username);
        editPhone = findViewById(R.id.edit_phone);
        editCode = findViewById(R.id.edit_code);
        btnRegister = findViewById(R.id.btn_register);
        btnVerifyCode = findViewById(R.id.btn_verify_code);
        ccp = findViewById(R.id.ccp);

        btnVerifyCode.setEnabled(false);
        editCode.setEnabled(false);

        btnRegister.setOnClickListener(v -> registerUser());

        btnVerifyCode.setOnClickListener(v -> verifyPhoneCode());

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
            editPassword.setError("Mot de passe peu sécurisé");
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
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.sendEmailVerification();

                        // Enregistrement Firestore en attendant
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("uid", user.getUid());
                        userData.put("email", email);
                        userData.put("username", username);
                        userData.put("phone", phone);

                        firestore.collection("users").document(user.getUid()).set(userData);

                        // Envoi du code par SMS
                        PhoneAuthOptions options =
                                PhoneAuthOptions.newBuilder(mAuth)
                                        .setPhoneNumber(phone)
                                        .setTimeout(60L, TimeUnit.SECONDS)
                                        .setActivity(this)
                                        .setCallbacks(callbacks)
                                        .build();
                        PhoneAuthProvider.verifyPhoneNumber(options);

                        Toast.makeText(this, "Vérifiez votre email. Code SMS envoyé.", Toast.LENGTH_LONG).show();
                        btnVerifyCode.setEnabled(true);
                        editCode.setEnabled(true);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erreur inscription : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void verifyPhoneCode() {
        String code = editCode.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            editCode.setError("Code requis");
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        mAuth.getCurrentUser().linkWithCredential(credential)
                .addOnSuccessListener(linked -> {
                    Toast.makeText(this, "Téléphone lié avec succès", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Erreur de liaison : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    // Auto remplissage si possible
                    verifyPhoneCode(); // Ou lien auto
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    Toast.makeText(RegisterActivity.this, "Échec SMS : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCodeSent(@NonNull String verificationId,
                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    RegisterActivity.this.verificationId = verificationId;
                    Toast.makeText(RegisterActivity.this, "Code SMS envoyé", Toast.LENGTH_SHORT).show();
                }
            };
}
