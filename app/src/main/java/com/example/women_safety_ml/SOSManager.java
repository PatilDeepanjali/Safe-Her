package com.example.women_safety_ml;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class SOSManager {

    private static final String TAG         = "SOSManager";
    private static final String PREFS       = "SafeHerPrefs";
    private static final String KEY_MSG     = "sos_message";
    private static final String DEFAULT_MSG =
            "HELP! I am in danger. Please contact police immediately. My live location: ";

    private final Context context;
    private final FirebaseFirestore db;
    private String userId;
    private LiveLocationTracker liveTracker;
    // Live tracking web page URL — hosted on Firebase
    private static final String LIVE_TRACKING_PAGE =
            "https://safeher-2f27d.web.app/live_tracking.html?uid=";

    // Raw database URL (fallback)
    private static final String TRACKING_BASE_URL =
            "https://safeher-2f27d-default-rtdb.asia-southeast1.firebasedatabase.app/liveLocations/";

    public interface SOSCallback {
        void onSMSSent(int count);
        void onSMSFailed(String error);
    }

    public SOSManager(Context context) {
        this.context = context;
        this.db      = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            this.userId = auth.getCurrentUser().getUid();
        }
        this.liveTracker = new LiveLocationTracker(context);
    }

    public void sendSOSToAllContacts(double lat, double lng, SOSCallback callback) {

        // Check 1: User logged in
        if (userId == null) {
            Log.e(TAG, "User not logged in");
            if (callback != null) callback.onSMSFailed("User not logged in");
            return;
        }

        // Check 2: SMS permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission not granted");
            if (callback != null) callback.onSMSFailed("SMS permission not granted. Go to Settings > Permissions and grant SMS permission.");
            return;
        }

        // Start live location tracking
        liveTracker.startTracking(null);

        // Build URLs
        String staticUrl  = "https://maps.google.com/?q=" + lat + "," + lng;
        String livePageUrl = LIVE_TRACKING_PAGE + userId; // ← web page with live map

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String customMsg = prefs.getString(KEY_MSG, DEFAULT_MSG);
        String fullMessage = customMsg
                + "\n\nCurrent location: " + staticUrl
                + "\n\nLive tracking map: " + livePageUrl
                + "\n(Map updates every 10 seconds)";

        Log.d(TAG, "Fetching contacts from Firestore for user: " + userId);

        // Fetch contacts from Firestore
        db.collection("users").document(userId)
                .collection("contacts")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> phones = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String phone = doc.getString("phone");
                        if (phone != null && !phone.isEmpty()) {
                            phones.add(phone);
                            Log.d(TAG, "Found contact: " + doc.getString("name") + " → " + phone);
                        }
                    }

                    if (phones.isEmpty()) {
                        Log.e(TAG, "No contacts found in Firestore");
                        if (callback != null)
                            callback.onSMSFailed("No emergency contacts found! Please add contacts first.");
                        return;
                    }

                    // Send SMS to each contact
                    int sent = 0;
                    for (String phone : phones) {
                        if (sendSMS(phone, fullMessage)) sent++;
                    }

                    Log.d(TAG, "SOS sent to " + sent + "/" + phones.size() + " contacts");
                    if (callback != null) callback.onSMSSent(sent);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore fetch failed: " + e.getMessage());
                    if (callback != null)
                        callback.onSMSFailed("Could not fetch contacts: " + e.getMessage());
                });
    }

    private boolean sendSMS(String phoneNumber, String message) {
        try {
            // Step 1: Remove all non-numeric chars except +
            String clean = phoneNumber.replaceAll("[^+0-9]", "");

            if (clean.isEmpty()) {
                Log.e(TAG, "Invalid phone number: " + phoneNumber);
                return false;
            }

            // Step 2: Auto-add Indian country code if missing
            if (clean.startsWith("+")) {
                // Already has country code e.g. +919876543210 → use as is
            } else if (clean.startsWith("91") && clean.length() == 12) {
                // Has 91 but missing + e.g. 919876543210 → add +
                clean = "+" + clean;
            } else if (clean.length() == 10) {
                // Just 10 digit number e.g. 9876543210 → add +91
                clean = "+91" + clean;
            } else {
                // Unknown format → try adding + prefix
                clean = "+" + clean;
            }

            Log.d(TAG, "Sending SMS to cleaned number: " + clean);

            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(message);
            sms.sendMultipartTextMessage(clean, null, parts, null, null);

            Log.d(TAG, "✅ SMS sent to: " + clean);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ SMS failed to " + phoneNumber + " : " + e.getMessage());
            return false;
        }
    }
}