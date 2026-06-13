package com.example.women_safety_ml;

import com.google.gson.annotations.SerializedName;

public class PredictionRequest {

    @SerializedName("Speed")
    private int Speed;

    @SerializedName("HeartRate")
    private int HeartRate;

    @SerializedName("Time")
    private int Time;

    @SerializedName("Acceleration")
    private int Acceleration;

    @SerializedName("LocationType")
    private int LocationType;

    @SerializedName("SoundLevel")
    private int SoundLevel;

    public PredictionRequest(int speed, int heartRate, int time,
                             int acceleration, int locationType, int soundLevel) {
        this.Speed        = speed;
        this.HeartRate    = heartRate;
        this.Time         = time;
        this.Acceleration = acceleration;
        this.LocationType = locationType;
        this.SoundLevel   = soundLevel;
    }

    // Getters
    public int getSpeed()        { return Speed; }
    public int getHeartRate()    { return HeartRate; }
    public int getTime()         { return Time; }
    public int getAcceleration() { return Acceleration; }
    public int getLocationType() { return LocationType; }
    public int getSoundLevel()   { return SoundLevel; }
}