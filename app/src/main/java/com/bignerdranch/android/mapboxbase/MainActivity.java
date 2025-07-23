package com.bignerdranch.android.mapboxbase;

import static com.mapbox.navigation.base.extensions.RouteOptionsExtensions.applyDefaultNavigationOptions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.MapboxDirections;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;

import com.mapbox.api.directions.v5.models.RouteOptions;
import com.mapbox.api.geocoding.v6.MapboxV6Geocoding;
import com.mapbox.api.geocoding.v6.V6ForwardGeocodingRequestOptions;
import com.mapbox.api.geocoding.v6.models.V6Feature;
import com.mapbox.api.geocoding.v6.models.V6Response;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;
import com.mapbox.maps.MapboxMap;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.extension.style.layers.generated.LineLayer;
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap;
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;
import com.mapbox.navigation.base.options.NavigationOptions;
import com.mapbox.navigation.base.route.NavigationRoute;
import com.mapbox.navigation.base.route.NavigationRouterCallback;
import com.mapbox.navigation.base.route.RouterFailure;
import com.mapbox.navigation.base.route.RouterOrigin;
import com.mapbox.navigation.core.MapboxNavigation;
import com.mapbox.navigation.core.MapboxNavigationProvider;
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp;
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi;
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView;
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions;
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions;
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions;

import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions;

import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private Button zoomIn;
    private Button zoomOut;
    private EditText startLocationEditText;
    private EditText destinationEditText;
    private Button mapboxRouteButton;
    private Button safeRouteButton;
    private MapboxNavigation mapboxNavigation;


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

        // Initialize MapboxNavigation (only once in your activity)
        NavigationOptions navigationOptions = new NavigationOptions.Builder(this)
                .build();
        MapboxNavigation mapboxNavigation = MapboxNavigationProvider.create(navigationOptions);

// Optional: Setup route line API for drawing route on map
        // API options for route logic
        // Route line API setup
        MapboxRouteLineApiOptions apiOptions = new MapboxRouteLineApiOptions.Builder().build();
        MapboxRouteLineApi routeLineApi = new MapboxRouteLineApi(apiOptions);

// Route line view setup
        MapboxRouteLineViewOptions viewOptions = new MapboxRouteLineViewOptions.Builder(this).build();
        MapboxRouteLineView routeLineView = new MapboxRouteLineView(viewOptions);



        mapboxRouteButton.setOnClickListener(v -> {
            String startAddress = startLocationEditText.getText().toString().trim();
            String destinationAddress = destinationEditText.getText().toString().trim();
            String token = getString(R.string.mapbox_access_token);


            Point nycCenter = Point.fromLngLat(-74.0060, 40.7128);  // Manhattan
            V6ForwardGeocodingRequestOptions originOptions = V6ForwardGeocodingRequestOptions
                    .builder(startAddress)
                    .proximity(nycCenter)
                    .build();
            V6ForwardGeocodingRequestOptions destOptions = V6ForwardGeocodingRequestOptions
                    .builder(destinationAddress)
                    .proximity(nycCenter)
                    .build();

            MapboxV6Geocoding originGeocoder = MapboxV6Geocoding.builder(token, originOptions).build();
            MapboxV6Geocoding destGeocoder = MapboxV6Geocoding.builder(token, destOptions).build();

            originGeocoder.enqueueCall(new Callback<V6Response>() {
                @Override
                public void onResponse(Call<V6Response> call, Response<V6Response> response) {
                    List<V6Feature> originResults = response.body().features();
                    if (originResults != null && !originResults.isEmpty()) {
                        Point originPoint = (Point) originResults.get(0).geometry();

                        destGeocoder.enqueueCall(new Callback<V6Response>() {
                            @Override
                            public void onResponse(Call<V6Response> call, Response<V6Response> response) {
                                List<V6Feature> destResults = response.body().features();
                                if (destResults != null && !destResults.isEmpty()) {
                                    Point destination = (Point) destResults.get(0).geometry();

                                    // Build route options using best practices
                                    RouteOptions.Builder routeBuilder = RouteOptions.builder()
                                            .coordinatesList(Arrays.asList(originPoint, destination))
                                            .profile(DirectionsCriteria.PROFILE_DRIVING)
                                            .overview(DirectionsCriteria.OVERVIEW_FULL)
                                            .steps(true);
                                    applyDefaultNavigationOptions(routeBuilder); // Adds language, geometry format, etc.
                                    Point finalOrigin = originPoint;

                                    mapboxNavigation.requestRoutes(routeBuilder.build(), new NavigationRouterCallback() {
                                        @Override
                                        public void onRoutesReady(@NonNull List<NavigationRoute> routes, @NonNull String routerOrigin) {
                                            if (!routes.isEmpty()) {
                                                NavigationRoute route = routes.get(0);
                                                mapboxNavigation.setNavigationRoutes(routes);

                                                // Draw route using RouteLineApi
                                                routeLineApi.setNavigationRoutes(routes, expected -> {
                                                    Style style = mapView.getMapboxMap().getStyle();
                                                    if (style != null) {
                                                        routeLineView.renderRouteDrawData(style, expected);
                                                    }
                                                });

                                                // Move camera
                                                mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                                                        .center(finalOrigin)
                                                        .zoom(12.0)
                                                        .build()
                                                );
                                            }
                                        }

                                        @Override
                                        public void onFailure(@NonNull List<RouterFailure> reasons, @NonNull RouteOptions routeOptions) {
                                            Log.e("RouteRequest", "Route request failed: " + reasons.toString());
                                        }

                                        @Override
                                        public void onCanceled(@NonNull RouteOptions routeOptions, @NonNull String routerOrigin) {
                                            Log.d("RouteRequest", "Route request canceled.");
                                        }
                                    });

                                } else {
                                    Log.e("Geocode", "Destination not found");
                                }
                            }

                            @Override
                            public void onFailure(Call<V6Response> call, Throwable t) {
                                Log.e("Geocode", "Destination geocoding failed: " + t.getMessage());
                            }
                        });

                    } else {
                        Log.e("Geocode", "Origin not found");
                    }
                }

                @Override
                public void onFailure(Call<V6Response> call, Throwable t) {
                    Log.e("Geocode", "Origin geocoding failed: " + t.getMessage());
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


