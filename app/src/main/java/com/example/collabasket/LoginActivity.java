package com.example.collabasket;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        Button btnLogin = findViewById(R.id.btn_login);
        TextView linkRegister = findViewById(R.id.text_register);
        Button btnPhoneLogin = findViewById(R.id.btn_phone_login);

        btnLogin.setOnClickListener(v -> loginUser());

        btnPhoneLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, PhoneVerificationActivity.class));
        });

        linkRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
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
