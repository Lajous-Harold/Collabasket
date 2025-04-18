package com.example.collabasket.ui.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
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
import com.google.firebase.messaging.FirebaseMessaging;
import com.hbb20.CountryCodePicker;

import com.google.android.material.materialswitch.MaterialSwitch;

public class CompteFragment extends Fragment {

    private TextView textEmail, textPhone, textUid;
    private EditText editNom;
    private Button btnUpdateNom, btnUpdatePassword, btnUpdatePhone;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private FirebaseUser user;
    private MaterialSwitch switchTheme;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "CompteFragment onCreateView");
        View view = inflater.inflate(R.layout.fragment_compte, container, false);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();
        prefs = requireContext().getSharedPreferences("settings", getContext().MODE_PRIVATE);

        editNom = view.findViewById(R.id.edit_nom);
        textEmail = view.findViewById(R.id.text_email);
        textPhone = view.findViewById(R.id.text_phone);
        textUid = view.findViewById(R.id.text_uid);

        btnUpdateNom = view.findViewById(R.id.btn_update_nom);
        btnUpdatePassword = view.findViewById(R.id.btn_update_password);
        btnUpdatePhone = view.findViewById(R.id.btn_update_phone);
        switchTheme = view.findViewById(R.id.switch_theme);

        if (user != null) {
            textEmail.setText(user.getEmail());
            textPhone.setText(user.getPhoneNumber());
            textUid.setText("UID : " + user.getUid());

            firestore.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            editNom.setText(doc.contains("username") && doc.get("username") != null ? doc.getString("username") : "");
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

        Button btnNotifications = view.findViewById(R.id.btn_notifications);
        btnNotifications.setOnClickListener(v -> {
            NotificationsFragment notificationsFragment = new NotificationsFragment();
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, notificationsFragment)
                    .addToBackStack(null)
                    .commit();
        });

        Button btnCGU = view.findViewById(R.id.btn_cgu);
        btnCGU.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new CGUFragment())
                    .addToBackStack(null)
                    .commit();
        });

        Button btnLogout = view.findViewById(R.id.btn_logout);
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        // Initialisation du switch depuis les préférences (ou fallback sur le thème système)
        boolean darkMode = prefs.getBoolean("dark_mode",
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        switchTheme.setChecked(darkMode);

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
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
                        updateFcmToken();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        }
    }

    private void updateFcmToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("FCM_TOKEN", "Erreur récupération token", task.getException());
                        return;
                    }

                    String token = task.getResult();
                    Log.d("FCM_TOKEN", "Token après maj numéro : " + token);

                    String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .update("fcmToken", token)
                            .addOnSuccessListener(aVoid -> Log.d("FCM_TOKEN", "Token mis à jour après modif numéro"))
                            .addOnFailureListener(e -> Log.w("FCM_TOKEN", "Échec maj token", e));
                });
    }
}
