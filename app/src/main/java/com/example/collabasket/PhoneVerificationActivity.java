package com.example.collabasket;

import android.os.Bundle;
import android.text.TextUtils;
import android.content.Intent;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.*;

import java.util.concurrent.TimeUnit;

public class PhoneVerificationActivity extends AppCompatActivity {

    private EditText editPhone, editCode;
    private Button btnSendCode, btnVerifyCode;
    private String verificationId;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_verification);

        mAuth = FirebaseAuth.getInstance();

        editPhone = findViewById(R.id.edit_phone);
        editCode = findViewById(R.id.edit_code);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnVerifyCode = findViewById(R.id.btn_verify_code);

        editCode.setVisibility(View.GONE);
        btnVerifyCode.setVisibility(View.GONE);

        // 🔧 Ligne 34 : bouton pour envoyer le code
        btnSendCode.setOnClickListener(v -> {
            String phone = editPhone.getText().toString().trim();
            if (TextUtils.isEmpty(phone) || phone.length() < 6) {
                editPhone.setError("Numéro invalide");
                return;
            }

            PhoneAuthOptions options =
                    PhoneAuthOptions.newBuilder(mAuth)
                            .setPhoneNumber(phone)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(this)
                            .setCallbacks(callbacks)
                            .build();
            PhoneAuthProvider.verifyPhoneNumber(options);
        });

        // 🔧 Ligne 52 : bouton pour vérifier le code
        btnVerifyCode.setOnClickListener(v -> {
            String code = editCode.getText().toString().trim();
            if (TextUtils.isEmpty(code)) {
                editCode.setError("Code requis");
                return;
            }
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
            signInWithPhoneAuthCredential(credential);
        });
    }

    // 🔧 Ligne 63 : gestion Firebase
    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    signInWithPhoneAuthCredential(credential);
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    Toast.makeText(PhoneVerificationActivity.this, "Erreur : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    PhoneVerificationActivity.this.verificationId = verificationId;
                    Toast.makeText(PhoneVerificationActivity.this, "Code envoyé", Toast.LENGTH_SHORT).show();

                    editCode.setVisibility(View.VISIBLE);
                    btnVerifyCode.setVisibility(View.VISIBLE);
                }
            };

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(user.getUid())
                                .get()
                                .addOnSuccessListener(document -> {
                                    if (document.exists()) {
                                        // ✅ Utilisateur connu
                                        startActivity(new Intent(this, MainActivity.class));
                                        finish();
                                    } else {
                                        // ❌ Utilisateur inconnu → demander création de compte
                                        Toast.makeText(this, "Aucun compte lié à ce numéro. Veuillez créer un compte.", Toast.LENGTH_LONG).show();
                                        FirebaseAuth.getInstance().signOut();
                                        startActivity(new Intent(this, RegisterActivity.class));
                                        finish();
                                    }
                                });

                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Échec : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

}
