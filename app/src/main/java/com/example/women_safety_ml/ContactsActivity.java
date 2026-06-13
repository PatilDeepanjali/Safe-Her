package com.example.women_safety_ml;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactsActivity extends BaseActivity
        implements ContactAdapter.OnContactActionListener {

    private RecyclerView rvContacts;
    private ContactAdapter adapter;
    private List<Contact> contactList = new ArrayList<>();
    private FloatingActionButton fabAdd;
    private ProgressBar progressBar;
    private LinearLayout tvEmpty;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;

    // Firestore collection path: users/{uid}/contacts/{contactId}
    private static final String COL_USERS    = "users";
    private static final String COL_CONTACTS = "contacts";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_contacts);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        userId = user.getUid();

        initViews();
        setupRecyclerView();
        setListeners();
        loadContactsFromFirestore();   // ← real Firebase load
    }

    private void initViews() {
        rvContacts  = findViewById(R.id.rvContacts);
        fabAdd      = findViewById(R.id.fabAddContact);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty     = findViewById(R.id.tvEmpty);  // LinearLayout in XML
    }

    private void setupRecyclerView() {
        adapter = new ContactAdapter(contactList, this);
        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        rvContacts.setAdapter(adapter);
    }

    private void setListeners() {
        fabAdd.setOnClickListener(v -> showAddContactDialog());
        setupBottomNav(R.id.tabContacts);
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
    }

    // ── LOAD CONTACTS FROM FIRESTORE ──
    private void loadContactsFromFirestore() {
        showLoading(true);

        db.collection(COL_USERS).document(userId)
                .collection(COL_CONTACTS)
                .orderBy("priority", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    showLoading(false);
                    contactList.clear();

                    for (QueryDocumentSnapshot doc : snapshot) {
                        Contact c = new Contact(
                                doc.getString("name"),
                                doc.getString("phone"),
                                Boolean.TRUE.equals(doc.getBoolean("priority"))
                        );
                        c.setDocumentId(doc.getId()); // store Firestore doc ID
                        contactList.add(c);
                    }

                    adapter.notifyDataSetChanged();
                    showEmptyState(contactList.isEmpty());
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to load contacts: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // ── ADD CONTACT ──
    private void showAddContactDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_contact, null);
        EditText etName  = dialogView.findViewById(R.id.etContactName);
        EditText etPhone = dialogView.findViewById(R.id.etContactPhone);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnAddContact).setOnClickListener(v -> {
            String name  = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (phone.length() < 10) {
                etPhone.setError("Enter valid phone number");
                return;
            }

            dialog.dismiss();
            saveContactToFirestore(name, phone, false);
        });

        dialog.show();
    }

    // ── SAVE TO FIRESTORE ──
    private void saveContactToFirestore(String name, String phone, boolean priority) {
        showLoading(true);

        Map<String, Object> data = new HashMap<>();
        data.put("name",     name);
        data.put("phone",    phone);
        data.put("priority", priority);

        db.collection(COL_USERS).document(userId)
                .collection(COL_CONTACTS)
                .add(data)
                .addOnSuccessListener(ref -> {
                    showLoading(false);
                    Contact c = new Contact(name, phone, priority);
                    c.setDocumentId(ref.getId());
                    contactList.add(c);
                    adapter.notifyItemInserted(contactList.size() - 1);
                    showEmptyState(false);
                    Toast.makeText(this, "✅ " + name + " added!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to add: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // ── EDIT CONTACT ──
    @Override
    public void onEdit(int position) {
        Contact contact = contactList.get(position);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);

        EditText etName = new EditText(this);
        etName.setText(contact.getName());
        etName.setHint("Contact Name");
        layout.addView(etName);

        EditText etPhone = new EditText(this);
        etPhone.setText(contact.getPhone());
        etPhone.setHint("Phone Number");
        etPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        layout.addView(etPhone);

        new AlertDialog.Builder(this)
                .setTitle("✏️ Edit Contact")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName  = etName.getText().toString().trim();
                    String newPhone = etPhone.getText().toString().trim();
                    if (newName.isEmpty() || newPhone.isEmpty()) return;

                    // Update Firestore
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("name",  newName);
                    updates.put("phone", newPhone);

                    db.collection(COL_USERS).document(userId)
                            .collection(COL_CONTACTS)
                            .document(contact.getDocumentId())
                            .update(updates)
                            .addOnSuccessListener(a -> {
                                contact.setName(newName);
                                contact.setPhone(newPhone);
                                adapter.notifyItemChanged(position);
                                Toast.makeText(this, "✅ Contact updated!",
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── DELETE CONTACT ──
    @Override
    public void onDelete(int position) {
        Contact contact = contactList.get(position);

        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Remove " + contact.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection(COL_USERS).document(userId)
                            .collection(COL_CONTACTS)
                            .document(contact.getDocumentId())
                            .delete()
                            .addOnSuccessListener(a -> {
                                contactList.remove(position);
                                adapter.notifyItemRemoved(position);
                                showEmptyState(contactList.isEmpty());
                                Toast.makeText(this, "Contact removed",
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── STAR / PRIORITY TOGGLE ──
    @Override
    public void onStarToggle(int position) {
        Contact contact = contactList.get(position);
        boolean newPriority = !contact.isPriority();

        db.collection(COL_USERS).document(userId)
                .collection(COL_CONTACTS)
                .document(contact.getDocumentId())
                .update("priority", newPriority)
                .addOnSuccessListener(a -> {
                    contact.setPriority(newPriority);
                    adapter.notifyItemChanged(position);
                });
    }

    private void showLoading(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState(boolean empty) {
        if (tvEmpty != null)
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rvContacts != null)
            rvContacts.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}