package com.example.women_safety_ml;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.Executor;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnGoogle, btnBiometric;
    private TextView tvRegister;

    private FirebaseAuth mAuth;



    @Override
    protected void onStart() {
        super.onStart();

        if (mAuth.getCurrentUser() != null) {
            goToMain();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null)
            getSupportActionBar().hide();

        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        initViews();
        setListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnBiometric = findViewById(R.id.btnBiometric);
        tvRegister = findViewById(R.id.tvRegister);
    }

    private void setListeners() {

        btnLogin.setOnClickListener(v -> {

            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Enter email");
                return;
            }

            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Enter password");
                return;
            }

            loginUser(email, password);
        });

        btnGoogle.setOnClickListener(v -> {
            Toast.makeText(this,
                    "Google Sign-In coming soon",
                    Toast.LENGTH_SHORT).show();
        });

        btnBiometric.setOnClickListener(v -> showBiometricPrompt());

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this,
                    RegisterActivity.class));
        });
    }

    private void loginUser(String email, String password) {

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        Toast.makeText(LoginActivity.this,
                                "Login Successful",
                                Toast.LENGTH_SHORT).show();

                        goToMain();

                    } else {

                        Toast.makeText(LoginActivity.this,
                                task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showBiometricPrompt() {

        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt biometricPrompt =
                new BiometricPrompt(this, executor,
                        new BiometricPrompt.AuthenticationCallback() {

                            @Override
                            public void onAuthenticationSucceeded(
                                    BiometricPrompt.AuthenticationResult result) {

                                super.onAuthenticationSucceeded(result);

                                Toast.makeText(LoginActivity.this,
                                        "Authentication successful!",
                                        Toast.LENGTH_SHORT).show();

                                goToMain();
                            }

                            @Override
                            public void onAuthenticationFailed() {

                                super.onAuthenticationFailed();

                                Toast.makeText(LoginActivity.this,
                                        "Authentication Failed",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });

        BiometricPrompt.PromptInfo promptInfo =
                new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Biometric Login")
                        .setSubtitle("Use fingerprint or face unlock")
                        .setNegativeButtonText("Cancel")
                        .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void goToMain() {

        startActivity(new Intent(LoginActivity.this,
                MainActivity.class));

        finish();
    }
}