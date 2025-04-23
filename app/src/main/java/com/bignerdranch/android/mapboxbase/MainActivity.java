package com.bignerdranch.android.mapboxbase;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;
import com.mapbox.maps.MapboxMap;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;

public class MainActivity extends AppCompatActivity {
    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Make sure this matches your XML filename

        mapView = findViewById(R.id.mapView);
        MapboxMap mapboxMap = mapView.getMapboxMap();

        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS, style -> {
            // Set camera position to New York City as an example
            mapboxMap.setCamera(
                    new CameraOptions.Builder()
                            .center(Point.fromLngLat(-74.0060, 40.7128)) // NYC
                            .zoom(12.0)
                            .build()
            );
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

}
