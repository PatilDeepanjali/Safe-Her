package com.example.women_safety_ml;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // UPDATE HERE:
    // For local testing on Wi-Fi hotspot: "http://<YOUR_PC_IP>:5000/"
    // For live cloud hosting on Render:   "https://safeher-ml-api.onrender.com/"
    private static final String BASE_URL =
            "https://safe-her-ml.onrender.com/";

    private static Retrofit retrofit;

    public static Retrofit getInstance() {

        if (retrofit == null) {
            // Set 8-second timeout so the on-device fallback triggers quickly if the server is sleeping or unreachable
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .writeTimeout(8, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(
                            GsonConverterFactory.create()
                    )
                    .build();
        }

        return retrofit;
    }
}