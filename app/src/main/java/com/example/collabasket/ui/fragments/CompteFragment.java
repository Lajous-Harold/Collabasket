package com.example.collabasket.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.collabasket.LoginActivity;
import com.example.collabasket.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.FirebaseException;
import com.hbb20.CountryCodePicker;


public class CompteFragment extends Fragment {

    private TextView textEmail, textPhone, textUid;
    private EditText editNom;
    private Button btnUpdateNom, btnUpdatePassword, btnUpdatePhone;

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private FirebaseUser user;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "CompteFragment onCreateView");
        View view = inflater.inflate(R.layout.fragment_compte, container, false);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();

        editNom = view.findViewById(R.id.edit_nom);
        textEmail = view.findViewById(R.id.text_email);
        textPhone = view.findViewById(R.id.text_phone);
        textUid = view.findViewById(R.id.text_uid);

        btnUpdateNom = view.findViewById(R.id.btn_update_nom);
        btnUpdatePassword = view.findViewById(R.id.btn_update_password);
        btnUpdatePhone = view.findViewById(R.id.btn_update_phone);

        if (user != null) {
            textEmail.setText(user.getEmail());
            textPhone.setText(user.getPhoneNumber());
            textUid.setText("UID : " + user.getUid());

            // Charger le nom depuis Firestore
            firestore.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            editNom.setText(doc.getString("username"));
                        }
                    });
        }

        btnUpdateNom.setOnClickListener(v -> {
            String nouveauNom = editNom.getText().toString().trim();
            if (!nouveauNom.isEmpty()) {
                firestore.collection("users").document(user.getUid())
                        .update("username", nouveauNom)
                        .addOnSuccessListener(unused -> Toast.makeText(getContext(), "Nom mis à jour", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Erreur : " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } else {
                editNom.setError("Nom requis");
            }
        });

        btnUpdatePassword.setOnClickListener(v -> {
            if (user != null && user.getEmail() != null) {
                mAuth.sendPasswordResetEmail(user.getEmail())
                        .addOnSuccessListener(unused -> Toast.makeText(getContext(), "Email de réinitialisation envoyé", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Erreur : " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        btnUpdatePhone.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_phone_update, null);
            EditText inputPhone = dialogView.findViewById(R.id.edit_new_phone);
            CountryCodePicker ccp = dialogView.findViewById(R.id.ccp_new_phone);

            new AlertDialog.Builder(getContext())
                    .setTitle("Modifier le numéro de téléphone")
                    .setView(dialogView)
                    .setPositiveButton("Envoyer le code", (dialog, which) -> {
                        String rawPhone = inputPhone.getText().toString().replaceFirst("^0+", "").trim();
                        String fullPhone = ccp.getSelectedCountryCodeWithPlus() + rawPhone;

                        if (rawPhone.isEmpty() || rawPhone.length() < 6) {
                            Toast.makeText(getContext(), "Numéro invalide", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Vérification via SMS
                        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                                .setPhoneNumber(fullPhone)
                                .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                                .setActivity(requireActivity())
                                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                    @Override
                                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                                        updateUserPhone(credential);
                                    }

                                    @Override
                                    public void onVerificationFailed(@NonNull FirebaseException e) {
                                        Toast.makeText(getContext(), "Échec : " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }

                                    @Override
                                    public void onCodeSent(@NonNull String verificationId,
                                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {
                                        askCodeForVerification(verificationId);
                                    }
                                })
                                .build();

                        PhoneAuthProvider.verifyPhoneNumber(options);
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        });

        Button btnLogout = view.findViewById(R.id.btn_logout);
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            // Redirige vers la page de connexion
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        return view;
    }


    private void askCodeForVerification(String verificationId) {
        EditText inputCode = new EditText(getContext());
        inputCode.setHint("Code reçu par SMS");

        new AlertDialog.Builder(getContext())
                .setTitle("Code de vérification")
                .setView(inputCode)
                .setPositiveButton("Valider", (dialog, which) -> {
                    String code = inputCode.getText().toString().trim();
                    if (!code.isEmpty()) {
                        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
                        updateUserPhone(credential);
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void updateUserPhone(PhoneAuthCredential credential) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            user.updatePhoneNumber(credential)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(getContext(), "Numéro mis à jour", Toast.LENGTH_SHORT).show();
                        textPhone.setText(user.getPhoneNumber());
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }
}
