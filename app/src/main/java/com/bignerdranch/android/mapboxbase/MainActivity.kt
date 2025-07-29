package com.bignerdranch.android.mapboxbase

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var zoomIn: Button
    private lateinit var zoomOut: Button
    private lateinit var startLocationEditText: EditText
    private lateinit var destinationEditText: EditText
    private lateinit var mapboxRouteButton: Button
    private lateinit var safeRouteButton: Button
    private lateinit var mapboxNavigation: MapboxNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Step 1: Initialize views
        mapView = findViewById(R.id.mapView)
        zoomIn = findViewById(R.id.zoom_in_button)
        zoomOut = findViewById(R.id.zoom_out_button)
        startLocationEditText = findViewById(R.id.startLocationEditText)
        destinationEditText = findViewById(R.id.destinationEditText)
        mapboxRouteButton = findViewById(R.id.mapboxRouteButton)
        safeRouteButton = findViewById(R.id.safeRouteButton)

        // Step 2: Map style and camera
        val mapboxMap = mapView.mapboxMap
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS) {
            mapboxMap.setCamera(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(-74.0060, 40.7128)) // NYC
                    .zoom(12.0)
                    .build()
            )
        }

        // Step 3: Zoom buttons
        zoomIn.setOnClickListener {
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .zoom(mapView.mapboxMap.cameraState.zoom + 1)
                    .build()
            )
        }

        zoomOut.setOnClickListener {
            mapView.mapboxMap.setCamera(
                CameraOptions.Builder()
                    .zoom(mapView.mapboxMap.cameraState.zoom - 1)
                    .build()
            )
        }

        // Step 4: Setup Mapbox Navigation
        val navigationOptions = NavigationOptions.Builder(this).build()
        mapboxNavigation = MapboxNavigationProvider.create(navigationOptions)

        val routeLineApi = MapboxRouteLineApi(
            MapboxRouteLineApiOptions.Builder().build()
        )
        val routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(this).build()
        )

        // Step 5: Route button logic
        mapboxRouteButton.setOnClickListener {
            MapboxRouteHandler.handleRoute(
                this,
                startLocationEditText,
                destinationEditText,
                mapboxNavigation,
                mapView,
                routeLineApi,
                routeLineView,
                getString(R.string.mapbox_access_token)
            )
        }

        // Step 6: Safe route (if needed)
        safeRouteButton.setOnClickListener {
            SafeRouteHandler.handleSafeRoute(
                context = this,
                startEditText = startLocationEditText,
                destinationEditText = destinationEditText,
                mapView = mapView
            )
        }


        // Optional: Autofill setup if needed
        // setupAddressAutofill()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
}
