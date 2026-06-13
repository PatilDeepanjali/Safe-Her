package com.example.women_safety_ml;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;


import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 101;

    private ImageView ivProfilePhoto;
    private EditText  etName, etPhone, etEmail, etPassword, etEmergencyContact;
    private Button    btnCreateAccount;
    private TextView  tvLogin;


    private Uri imageUri;
    private StorageReference storageReference;
    private FirebaseFirestore db;

    private FirebaseAuth mAuth;
    // Modern ActivityResult API for image picker
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null) {

                            imageUri = result.getData().getData();

                            ivProfilePhoto.setImageURI(imageUri);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_register);

        storageReference = FirebaseStorage.getInstance().getReference();

        mAuth = FirebaseAuth.getInstance();

        db = FirebaseFirestore.getInstance();

        initViews();
        setListeners();
    }

    private void initViews() {
        ivProfilePhoto       = findViewById(R.id.ivProfilePhoto);
        etName               = findViewById(R.id.etName);
        etPhone              = findViewById(R.id.etPhone);
        etEmail              = findViewById(R.id.etEmail);
        etPassword           = findViewById(R.id.etPassword);
        etEmergencyContact   = findViewById(R.id.etEmergencyContact);
        btnCreateAccount     = findViewById(R.id.btnCreateAccount);
        tvLogin              = findViewById(R.id.tvLogin);
    }

    private void setListeners() {

        // ── Profile photo picker ──
        ImageView ivUpload = findViewById(R.id.ivUploadPhoto);
        ivUpload.setOnClickListener(v -> openImagePicker());
        ivProfilePhoto.setOnClickListener(v -> openImagePicker());

        // ── Create Account button ──
        btnCreateAccount.setOnClickListener(v -> {
            if (validateInputs()) {
                registerUser();
            }
        });

        // ── Navigate back to Login ──
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    /** Open gallery to pick a profile photo */
    private void openImagePicker() {

        Intent intent = new Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        );

        intent.setType("image/*");

        imagePickerLauncher.launch(intent);
    }

    /** Validate all fields before submission */
    private boolean validateInputs() {
        String name      = etName.getText().toString().trim();
        String phone     = etPhone.getText().toString().trim();
        String email     = etEmail.getText().toString().trim();
        String password  = etPassword.getText().toString().trim();
        String emergency = etEmergencyContact.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            etName.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(phone) || phone.length() < 10) {
            etPhone.setError("Enter a valid phone number");
            etPhone.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            etEmail.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(emergency) || emergency.length() < 10) {
            etEmergencyContact.setError("Enter a valid emergency contact");
            etEmergencyContact.requestFocus();
            return false;
        }
        return true;
    }

    /** Register user — hook up Firebase Auth / API here */
    private void registerUser() {

        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String emergency = etEmergencyContact.getText().toString().trim();

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        String userId =
                                mAuth.getCurrentUser().getUid();

                        Map<String, Object> userMap =
                                new HashMap<>();

                        userMap.put("name", name);
                        userMap.put("phone", phone);
                        userMap.put("email", email);
                        userMap.put("emergencyContact", emergency);

                        db.collection("users")
                                .document(userId)
                                .set(userMap)
                                .addOnSuccessListener(unused -> {

                                    Toast.makeText(
                                            RegisterActivity.this,
                                            "Registration Successful",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    startActivity(new Intent(
                                            RegisterActivity.this,
                                            LoginActivity.class
                                    ));

                                    finish();
                                });

                    } else {

                        Toast.makeText(
                                RegisterActivity.this,
                                task.getException().getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}