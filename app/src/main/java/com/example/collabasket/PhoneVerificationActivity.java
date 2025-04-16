package com.example.collabasket;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hbb20.CountryCodePicker;

import java.util.concurrent.TimeUnit;

public class PhoneVerificationActivity extends AppCompatActivity {

    private EditText editPhone, editCode;
    private Button btnSendCode, btnVerifyCode;
    private String verificationId;
    private FirebaseAuth mAuth;
    private CountryCodePicker ccp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "PhoneVerificationActivity onCreate lancé");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_verification);

        mAuth = FirebaseAuth.getInstance();

        ccp = findViewById(R.id.ccp_phone);
        editPhone = findViewById(R.id.edit_phone);
        editCode = findViewById(R.id.edit_code);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnVerifyCode = findViewById(R.id.btn_verify_code);

        editCode.setVisibility(View.GONE);
        btnVerifyCode.setVisibility(View.GONE);

        // 🔹 Envoi du code
        btnSendCode.setOnClickListener(v -> {
            editPhone.setError(null); // Nettoyage
            String rawPhone = editPhone.getText().toString().replaceFirst("^0+", "").trim();
            if (TextUtils.isEmpty(rawPhone) || rawPhone.length() < 6) {
                editPhone.setError("Numéro invalide");
                return;
            }

            String phone = ccp.getSelectedCountryCodeWithPlus() + rawPhone;

            PhoneAuthOptions options =
                    PhoneAuthOptions.newBuilder(mAuth)
                            .setPhoneNumber(phone)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(this)
                            .setCallbacks(callbacks)
                            .build();
            PhoneAuthProvider.verifyPhoneNumber(options);
        });

        // 🔹 Vérification du code reçu
        btnVerifyCode.setOnClickListener(v -> {
            editCode.setError(null); // Nettoyage
            String code = editCode.getText().toString().trim();
            if (TextUtils.isEmpty(code)) {
                editCode.setError("Code requis");
                return;
            }
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
            signInWithPhoneAuthCredential(credential);
        });

        ImageButton btnRetour = findViewById(R.id.btn_back);
        btnRetour.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }


    // 🔸 Callback Firebase
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
                public void onCodeSent(@NonNull String verificationId,
                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    PhoneVerificationActivity.this.verificationId = verificationId;
                    Toast.makeText(PhoneVerificationActivity.this, "Code envoyé", Toast.LENGTH_SHORT).show();

                    // ✅ Afficher champ de code et bouton
                    editCode.setVisibility(View.VISIBLE);
                    btnVerifyCode.setVisibility(View.VISIBLE);

                    // ✅ Désactiver le champ de téléphone
                    editPhone.setEnabled(false);
                    editPhone.setTextColor(ContextCompat.getColor(PhoneVerificationActivity.this, android.R.color.darker_gray));
                    ccp.setCcpClickable(false);

                    // ✅ Bloquer les envois multiples
                    btnSendCode.setEnabled(false);
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
                                        // ✅ Connexion directe
                                        startActivity(new Intent(this, MainActivity.class));
                                        finish();
                                    } else {
                                        // ❌ Pas de compte associé
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
