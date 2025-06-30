package com.bignerdranch.android.mapboxbase;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.extension.style.layers.generated.LineLayer;
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap;
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;
//import com.mapbox.maps.plugin.gestures.gestures;
//import com.mapbox.navigation.base.route.NavigationRoute;
//import com.mapbox.navigation.base.route.RouteOptions;
//import com.mapbox.navigation.core.MapboxNavigation;
//import com.mapbox.navigation.core.trip.session.RoutesObserver;
//import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi;
//import com.mapbox.navigation.ui.maps.route.line.model.RouteLine;
//import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources;
//import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources;
//import com.mapbox.navigation.ui.maps.route.line.model.RouteLineUpdateValue;
//import com.mapbox.navigation.ui.maps.route.line.model.RouteLines;
//import com.mapbox.navigation.ui.maps.route.line.view.MapboxRouteLineView;

import java.util.Collections;
import java.util.List;

public class MapboxRouteHelper {

    /*private final Context context;
    private final String accessToken;
    private final MapboxMap mapboxMap;
    private final Point origin;
    private final Point destination;

    public MapboxRouteHelper(Context context, String accessToken, MapboxMap mapboxMap, Point origin, Point destination) {
        this.context = context;
        this.accessToken = accessToken;
        this.mapboxMap = mapboxMap;
        this.origin = origin;
        this.destination = destination;
    }

    public void drawRoute() {
        MapboxNavigation mapboxNavigation = new MapboxNavigation.Builder(context)
                .accessToken(accessToken)
                .build();

        RouteOptions routeOptions = RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .coordinatesList(Collections.singletonList(origin, destination))
                .build();

        mapboxNavigation.requestRoutes(routeOptions, new NavigationRouterCallback() {
            @Override
            public void onRoutesReady(@NonNull List<NavigationRoute> routes, @NonNull RouterOrigin routerOrigin) {
                if (!routes.isEmpty()) {
                    NavigationRoute route = routes.get(0);
                    List<Point> routePoints = LineString.fromPolyline(route.directionsRoute().geometry(), 6).coordinates();

                    mapboxMap.getStyle(style -> {
                        // Create source
                        GeoJsonSource routeSource = new GeoJsonSource.Builder("route-source")
                                .geometry(LineString.fromLngLats(routePoints))
                                .build();
                        style.addSource(routeSource);

                        // Create line layer
                        LineLayer routeLayer = new LineLayer("route-layer", "route-source")
                                .lineColor(Color.BLUE)
                                .lineWidth(5.0)
                                .lineCap(LineCap.ROUND)
                                .lineJoin(LineJoin.ROUND);
                        style.addLayer(routeLayer);
                    });
                }
            }

            @Override
            public void onFailure(@NonNull List<RouterFailure> routerFailures, @NonNull RouteOptions routeOptions) {
                Log.e("MapboxRoute", "Route request failed");
            }

            @Override
            public void onCanceled(@NonNull RouteOptions routeOptions, @NonNull RouterOrigin routerOrigin) {
                Log.d("MapboxRoute", "Route request canceled");
            }
        });
    }*/
}
