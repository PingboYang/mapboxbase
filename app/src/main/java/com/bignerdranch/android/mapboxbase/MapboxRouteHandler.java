package com.bignerdranch.android.mapboxbase;

import android.content.Context;
import android.util.Log;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.models.RouteOptions;
import com.mapbox.api.geocoding.v6.MapboxV6Geocoding;
import com.mapbox.api.geocoding.v6.V6ForwardGeocodingRequestOptions;
import com.mapbox.api.geocoding.v6.models.V6Feature;
import com.mapbox.api.geocoding.v6.models.V6Response;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.Style;
import com.mapbox.navigation.base.extensions.RouteOptionsExtensions;
import com.mapbox.navigation.base.route.NavigationRoute;
import com.mapbox.navigation.base.route.NavigationRouterCallback;
import com.mapbox.navigation.base.route.RouterFailure;
import com.mapbox.navigation.base.route.RouterOrigin;
import com.mapbox.navigation.core.MapboxNavigation;
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi;
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView;

import java.util.Arrays;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapboxRouteHandler {



    public static void handleRoute(Context context, EditText startEdit, EditText endEdit,
                                   MapboxNavigation mapboxNavigation, MapView mapView,
                                   MapboxRouteLineApi routeLineApi, MapboxRouteLineView routeLineView,
                                   String accessToken) {

        String startAddress = startEdit.getText().toString().trim();
        String endAddress = endEdit.getText().toString().trim();
        Point center = Point.fromLngLat(-74.0060, 40.7128);  // Manhattan

        MapboxV6Geocoding originGeocoder = MapboxV6Geocoding.builder(
                accessToken,
                V6ForwardGeocodingRequestOptions.builder(startAddress).proximity(center).build()
        ).build();

        MapboxV6Geocoding destGeocoder = MapboxV6Geocoding.builder(
                accessToken,
                V6ForwardGeocodingRequestOptions.builder(endAddress).proximity(center).build()
        ).build();

        originGeocoder.enqueueCall(new Callback<V6Response>() {
            @Override
            public void onResponse(Call<V6Response> call, Response<V6Response> response) {
                List<V6Feature> originFeatures = response.body().features();
                if (originFeatures != null && !originFeatures.isEmpty()) {
                    Point origin = (Point) originFeatures.get(0).geometry();

                    destGeocoder.enqueueCall(new Callback<V6Response>() {
                        @Override
                        public void onResponse(Call<V6Response> call, Response<V6Response> response) {
                            List<V6Feature> destFeatures = response.body().features();
                            if (destFeatures != null && !destFeatures.isEmpty()) {
                                Point destination = (Point) destFeatures.get(0).geometry();

                                RouteOptions.Builder builder = RouteOptions.builder()
                                        .coordinatesList(Arrays.asList(origin, destination))
                                        .profile(DirectionsCriteria.PROFILE_DRIVING)
                                        .overview(DirectionsCriteria.OVERVIEW_FULL)
                                        .steps(true);

                                RouteOptionsExtensions.applyDefaultNavigationOptions(builder);

                                mapboxNavigation.requestRoutes(builder.build(), new NavigationRouterCallback() {
                                    @Override
                                    public void onRoutesReady(@NonNull List<NavigationRoute> routes, @NonNull String routerOrigin) {
                                        if (!routes.isEmpty()) {
                                            mapboxNavigation.setNavigationRoutes(routes);
                                            routeLineApi.setNavigationRoutes(routes, result -> {
                                                Style style = mapView.getMapboxMap().getStyle();
                                                if (style != null) {
                                                    routeLineView.renderRouteDrawData(style, result);
                                                }
                                            });

                                            mapView.getMapboxMap().setCamera(new CameraOptions.Builder()
                                                    .center(origin)
                                                    .zoom(12.0)
                                                    .build());
                                        }
                                    }

                                    @Override
                                    public void onFailure(@NonNull List<RouterFailure> reasons, @NonNull RouteOptions routeOptions) {
                                        Log.e("RouteRequest", "Failed: " + reasons);
                                    }

                                    @Override
                                    public void onCanceled(@NonNull RouteOptions routeOptions, @NonNull String routerOrigin) {
                                        Log.d("RouteRequest", "Canceled");
                                    }
                                });

                            } else {
                                Log.e("Geocode", "Destination not found");
                            }
                        }

                        @Override
                        public void onFailure(Call<V6Response> call, Throwable t) {
                            Log.e("Geocode", "Destination geocode failed: " + t.getMessage());
                        }
                    });
                } else {
                    Log.e("Geocode", "Origin not found");
                }
            }

            @Override
            public void onFailure(Call<V6Response> call, Throwable t) {
                Log.e("Geocode", "Origin geocode failed: " + t.getMessage());
            }
        });


    }
}

