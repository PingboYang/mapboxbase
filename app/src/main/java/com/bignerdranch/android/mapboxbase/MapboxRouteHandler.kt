package com.bignerdranch.android.mapboxbase

import android.content.Context
import android.util.Log
import android.widget.EditText
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.geocoding.v6.MapboxV6Geocoding
import com.mapbox.api.geocoding.v6.V6ForwardGeocodingRequestOptions
import com.mapbox.api.geocoding.v6.models.V6Response
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineError
import com.mapbox.navigation.ui.maps.route.line.model.RouteSetValue
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

object MapboxRouteHandler {

    private fun resolveAlias(query: String): String {
        val aliasMap = mapOf(
            "bmcc" to "199 Chambers St, New York, NY",
            "the met" to "The Metropolitan Museum of Art, New York, NY",
            "nyu" to "New York University, New York, NY"
        )
        val normalized = query.trim().lowercase(Locale.getDefault())
        return aliasMap[normalized] ?: query
    }

    fun handleRoute(
        context: Context,
        startEdit: EditText,
        endEdit: EditText,
        mapboxNavigation: MapboxNavigation,
        mapView: MapView,
        routeLineApi: MapboxRouteLineApi,
        routeLineView: MapboxRouteLineView,
        accessToken: String,
        onComplete: (() -> Unit)? = null
    ) {
        val rawStart = startEdit.text.toString().trim()
        val rawEnd = endEdit.text.toString().trim()
        val startAddress = resolveAlias(rawStart)
        val endAddress = resolveAlias(rawEnd)
        val center = Point.fromLngLat(-74.0060, 40.7128) // Manhattan center

        val originGeocoder = MapboxV6Geocoding.builder(
            accessToken,
            V6ForwardGeocodingRequestOptions.builder(startAddress).proximity(center).build()
        ).build()

        val destGeocoder = MapboxV6Geocoding.builder(
            accessToken,
            V6ForwardGeocodingRequestOptions.builder(endAddress).proximity(center).build()
        ).build()

        originGeocoder.enqueueCall(object : Callback<V6Response> {
            override fun onResponse(call: Call<V6Response>, response: Response<V6Response>) {
                val originFeatures = response.body()?.features()
                if (!originFeatures.isNullOrEmpty()) {
                    val origin = originFeatures[0].geometry() as Point

                    destGeocoder.enqueueCall(object : Callback<V6Response> {
                        override fun onResponse(call: Call<V6Response>, response: Response<V6Response>) {
                            val destFeatures = response.body()?.features()
                            if (!destFeatures.isNullOrEmpty()) {
                                val destination = destFeatures[0].geometry() as Point

                                val builder = RouteOptions.builder()
                                    .coordinatesList(listOf(origin, destination))
                                    .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC) // Enable traffic-aware routing
                                    .overview(DirectionsCriteria.OVERVIEW_FULL)
                                    .steps(true)
                                    .voiceInstructions(true) // Enable voice instructions
                                    .bannerInstructions(true) // Enable banner instructions
                                    .applyDefaultNavigationOptions()

                                mapboxNavigation.requestRoutes(
                                    builder.build(),
                                    object : NavigationRouterCallback {
                                        override fun onRoutesReady(
                                            routes: List<NavigationRoute>,
                                            routerOrigin: String
                                        ) {
                                            if (routes.isNotEmpty()) {
                                                // Set routes for navigation
                                                mapboxNavigation.setNavigationRoutes(routes)
                                                routeLineApi.setNavigationRoutes(
                                                    routes,
                                                    MapboxNavigationConsumer<Expected<RouteLineError, RouteSetValue>> { result ->
                                                        mapView.mapboxMap.style?.let { style ->
                                                            routeLineView.renderRouteDrawData(style, result)
                                                        }
                                                    }
                                                )

                                                mapView.mapboxMap.setCamera(
                                                    CameraOptions.Builder()
                                                        .center(origin)
                                                        .zoom(12.0)
                                                        .build()
                                                )
                                                
                                                // Show navigation button when route is calculated
                                                if (context is MainActivity) {
                                                    context.runOnUiThread {
                                                        context.findViewById<android.widget.Button>(R.id.startNavigationButton)?.visibility = android.view.View.VISIBLE
                                                        android.widget.Toast.makeText(context, "Route calculated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                                        // Update route info
                                                        context.updateRouteInfo(routes.first())
                                                    }
                                                }
                                                onComplete?.invoke()
                                            }
                                        }

                                        override fun onFailure(
                                            reasons: List<RouterFailure>,
                                            routeOptions: RouteOptions
                                        ) {
                                            Log.e("RouteRequest", "Route failure: $reasons")
                                            if (context is MainActivity) {
                                                context.runOnUiThread {
                                                    android.widget.Toast.makeText(context, "Failed to calculate route: ${reasons.firstOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                            onComplete?.invoke()
                                        }

                                        override fun onCanceled(
                                            routeOptions: RouteOptions,
                                            routerOrigin: String
                                        ) {
                                            Log.d("RouteRequest", "Route request canceled")
                                            onComplete?.invoke()
                                        }
                                    }
                                )
                            } else {
                                Log.e("Geocode", "Destination not found.")
                                if (context is MainActivity) {
                                    context.runOnUiThread {
                                        android.widget.Toast.makeText(context, "Destination not found. Please try a different address.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                                onComplete?.invoke()
                            }
                        }

                        override fun onFailure(call: Call<V6Response>, t: Throwable) {
                            Log.e("Geocode", "Destination geocode failed: ${t.message}")
                            if (context is MainActivity) {
                                context.runOnUiThread {
                                    android.widget.Toast.makeText(context, "Failed to find destination: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                            onComplete?.invoke()
                        }
                    })
                } else {
                    Log.e("Geocode", "Origin not found.")
                    if (context is MainActivity) {
                        context.runOnUiThread {
                            android.widget.Toast.makeText(context, "Start location not found. Please try a different address.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    onComplete?.invoke()
                }
            }

            override fun onFailure(call: Call<V6Response>, t: Throwable) {
                Log.e("Geocode", "Origin geocode failed: ${t.message}")
                if (context is MainActivity) {
                    context.runOnUiThread {
                        android.widget.Toast.makeText(context, "Failed to find start location: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                onComplete?.invoke()
            }
        })
    }
}
