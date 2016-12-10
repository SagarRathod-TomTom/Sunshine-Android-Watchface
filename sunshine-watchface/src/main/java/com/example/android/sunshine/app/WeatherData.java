package com.example.android.sunshine.app;

import android.graphics.Bitmap;

/**
 * Created by samsung on 10-Dec-2016.
 */

public class WeatherData {

    private static WeatherData mWeatherData = new WeatherData();

    private String mMaxTemperature;
    private String mMinTemperature;
    private String mWeatherCondition;
    private Bitmap mWeatherImage;

    private WeatherData(){}

    public static WeatherData getInstance(){
        return  mWeatherData;
    }

    public String getMaxTemperature() {
        return mMaxTemperature;
    }

    public void setMaxTemperature(String mMaxTemperature) {
        this.mMaxTemperature = mMaxTemperature;
    }

    public String getMinTemperature() {
        return mMinTemperature;
    }

    public void setMinTemperature(String mMinTemperature) {
        this.mMinTemperature = mMinTemperature;
    }

    public String getWeatherCondition() {
        return mWeatherCondition;
    }

    public void setWeatherCondition(String mWeatherCondition) {
        this.mWeatherCondition = mWeatherCondition;
    }

    public static void setmWeatherData(WeatherData mWeatherData) {
        WeatherData.mWeatherData = mWeatherData;
    }

    public Bitmap getWeatherImage() {
        return mWeatherImage;
    }

    public void setWeatherImage(Bitmap mWeatherImage) {
        this.mWeatherImage = mWeatherImage;
    }
}
