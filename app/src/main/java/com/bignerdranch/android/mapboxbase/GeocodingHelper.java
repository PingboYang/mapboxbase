package com.bignerdranch.android.mapboxbase;

import android.util.Log;
import com.mapbox.api.geocoding.v5.MapboxGeocoding;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.api.geocoding.v5.models.GeocodingResponse;
import com.mapbox.geojson.Point;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GeocodingHelper {

    public interface GeocodingCallback {
        void onResult(Point point);
        void onError(String message);
    }

    public static void geocodeLocation(String locationName, String accessToken, GeocodingCallback callback) {
        Point nycPoint = Point.fromLngLat(-74.0060, 40.7128); // NYC coordinates
        MapboxGeocoding geocoding = MapboxGeocoding.builder()
                .accessToken(accessToken)
                .query(locationName)
                .limit(1)
                .proximity(nycPoint)  // This biases results toward NYC
                .build();

        geocoding.enqueueCall(new Callback<GeocodingResponse>() {
            @Override
            public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {
                if (response.isSuccessful() && !response.body().features().isEmpty()) {
                    CarmenFeature feature = response.body().features().get(0);
                    callback.onResult(feature.center());
                } else {
                    callback.onError("No result for: " + locationName);
                }
            }

            @Override
            public void onFailure(Call<GeocodingResponse> call, Throwable t) {
                callback.onError("Geocoding error: " + t.getMessage());
            }
        });
    }
}