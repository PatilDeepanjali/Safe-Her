package com.example.women_safety_ml;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST("predict")
    Call<PredictionResponse> predictSafety(
            @Body PredictionRequest request
    );
}
