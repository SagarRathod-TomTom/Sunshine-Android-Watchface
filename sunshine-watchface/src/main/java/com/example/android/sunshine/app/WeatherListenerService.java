package com.example.android.sunshine.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * @author Sagar Rathod
 *
 * Listens sunshine weather updates from handheld device.
 */
public class WeatherListenerService extends WearableListenerService implements
GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks{

    private static final String TAG = WeatherListenerService.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;

    public WeatherListenerService(){
        Log.d(TAG, "Instantiating WearableListenerService object");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addOnConnectionFailedListener(this)
                .addConnectionCallbacks(this)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo("/sunshine") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                    String maxTemperature = dataMap.getString("max_temperature");
                    String minTemperature = dataMap.getString("min_temperature");
                    String weatherCondition = dataMap.getString("weather_condition");

                    Log.e("Max Temp:" , maxTemperature);
                    Log.e("Min Temp:", minTemperature);
                    Log.e("Weather Condition", weatherCondition);

                    Asset asset = dataMap.getAsset("weather_image");
                    Bitmap bitmap = loadBitmapFromAsset(asset);

                    WeatherData weatherData = WeatherData.getInstance();
                    weatherData.setMaxTemperature(maxTemperature);
                    weatherData.setMinTemperature(minTemperature);
                    weatherData.setWeatherCondition(weatherCondition);
                    weatherData.setWeatherImage(bitmap);
                }
            }
        }
    }

    public Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result =
                mGoogleApiClient.blockingConnect(10000, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG,"Google API Connected.");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG,"Google API Suspended.");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG,"Google API Failed." + " with Error code:" + connectionResult.getErrorCode());
    }
}
