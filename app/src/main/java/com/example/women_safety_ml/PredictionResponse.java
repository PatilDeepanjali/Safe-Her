package com.example.women_safety_ml;

import com.google.gson.annotations.SerializedName;

public class PredictionResponse {

    @SerializedName("prediction")
    private int prediction;

    @SerializedName("status")
    private String status;

    @SerializedName("unsafe_probability")
    private double unsafe_probability;

    @SerializedName("error")
    private String error;

    public int getPrediction()           { return prediction; }
    public String getStatus()            { return status; }
    public double getUnsafe_probability(){ return unsafe_probability; }
    public String getError()             { return error; }
}