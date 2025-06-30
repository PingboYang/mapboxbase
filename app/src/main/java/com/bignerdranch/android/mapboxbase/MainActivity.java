package com.bignerdranch.android.mapboxbase;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;
import com.mapbox.maps.MapboxMap;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.plugin.Plugin;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;


public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private Button zoomIn;
    private Button zoomOut;
    private EditText startLocationEditText;
    private EditText destinationEditText;
    private Button mapboxRouteButton;
    private Button safeRouteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Connects to your activity_main.xml

        // Find views
        mapView = findViewById(R.id.mapView);
        zoomIn = findViewById(R.id.zoom_in_button);
        zoomOut = findViewById(R.id.zoom_out_button);
        startLocationEditText = findViewById(R.id.startLocationEditText);
        destinationEditText = findViewById(R.id.destinationEditText);
        mapboxRouteButton = findViewById(R.id.mapboxRouteButton);
        safeRouteButton = findViewById(R.id.safeRouteButton);

        MapboxMap mapboxMap = mapView.getMapboxMap();

        // Load the Mapbox Streets style
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS, style -> {
            // Set initial camera view to New York City
            mapboxMap.setCamera(
                    new CameraOptions.Builder()
                            .center(Point.fromLngLat(-74.0060, 40.7128)) // New York City
                            .zoom(12.0)
                            .build()
            );
        });

        // Set up Zoom In Button
        zoomIn.setOnClickListener(v -> {
            mapView.getMapboxMap().setCamera(
                    new CameraOptions.Builder()
                            .zoom(mapView.getMapboxMap().getCameraState().getZoom() + 1)
                            .build()
            );
        });

        // Set up Zoom Out Button
        zoomOut.setOnClickListener(v -> {
            mapView.getMapboxMap().setCamera(
                    new CameraOptions.Builder()
                            .zoom(mapView.getMapboxMap().getCameraState().getZoom() - 1)
                            .build()
            );
        });



        mapboxRouteButton.setOnClickListener(v -> {
            String startAddress = startLocationEditText.getText().toString().trim();
            String token = getString(R.string.mapbox_access_token);

            GeocodingHelper.geocodeLocation(startAddress, token, new GeocodingHelper.GeocodingCallback() {
                @Override
                public void onResult(Point point) {
                    Log.d("Geocoding", "Result: " + point.toString());

                    // Optional: Move camera to geocoded point
                    mapView.getMapboxMap().setCamera(
                            new CameraOptions.Builder()
                                    .center(point)
                                    .zoom(14.0)
                                    .build()
                    );
                }

                @Override
                public void onError(String message) {
                    Log.e("Geocoding", "Error: " + message);
                }
            });
        });





        safeRouteButton.setOnClickListener(v -> {
            // TODO: Add logic to call your Safe Route backend
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
