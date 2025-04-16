package com.example.collabasket;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.*;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;

import com.hbb20.CountryCodePicker;

import java.util.concurrent.TimeUnit;

public class PhoneRegistrationActivity extends AppCompatActivity {

    private EditText editPhone, editCode;
    private Button btnSendCode, btnVerifyCode;
    private ImageButton btnBack;
    private CountryCodePicker ccp;
    private FirebaseAuth mAuth;
    private String verificationId;
    private String verifiedPhone; // Pour conserver le numéro complet

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "PhoneRegistrationActivity onCreate lancé");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_registration);

        mAuth = FirebaseAuth.getInstance();

        ccp = findViewById(R.id.ccp_phone);
        editPhone = findViewById(R.id.edit_phone);
        editCode = findViewById(R.id.edit_code);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnVerifyCode = findViewById(R.id.btn_verify_code);
        btnBack = findViewById(R.id.btn_back);

        editCode.setVisibility(View.GONE);
        btnVerifyCode.setVisibility(View.GONE);

        btnSendCode.setOnClickListener(v -> {
            String rawPhone = editPhone.getText().toString().replaceFirst("^0+", "").trim();
            if (TextUtils.isEmpty(rawPhone)) {
                editPhone.setError("Numéro requis");
                return;
            }

            verifiedPhone = ccp.getSelectedCountryCodeWithPlus() + rawPhone;

            // Désactiver la modification du champ
            editPhone.setEnabled(false);
            editPhone.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            ccp.setCcpClickable(false);

            PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                    .setPhoneNumber(verifiedPhone)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(callbacks)
                    .build();

            PhoneAuthProvider.verifyPhoneNumber(options);
        });

        btnVerifyCode.setOnClickListener(v -> {
            String code = editCode.getText().toString().trim();
            if (TextUtils.isEmpty(code)) {
                editCode.setError("Code requis");
                return;
            }
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
            mAuth.signInWithCredential(credential)
                    .addOnSuccessListener(result -> {
                        Intent intent = new Intent(this, CompleteRegistrationActivity.class);
                        intent.putExtra("verifiedPhone", verifiedPhone); // ➕ passage du téléphone
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    signInWithPhoneAuthCredential(credential);
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    Toast.makeText(PhoneRegistrationActivity.this, "Échec : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCodeSent(@NonNull String verificationId,
                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    PhoneRegistrationActivity.this.verificationId = verificationId;
                    Toast.makeText(PhoneRegistrationActivity.this, "Code envoyé", Toast.LENGTH_SHORT).show();
                    editCode.setVisibility(View.VISIBLE);
                    btnVerifyCode.setVisibility(View.VISIBLE);
                }
            };

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    Intent intent = new Intent(this, CompleteRegistrationActivity.class);
                    intent.putExtra("verifiedPhone", verifiedPhone); // ➕ passage du téléphone
                    startActivity(intent);
                    finish();
                });
    }
}