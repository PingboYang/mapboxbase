package com.bignerdranch.android.mapboxbase

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import android.speech.tts.TextToSpeech
import java.util.Locale
// Removed unused Mapbox Search imports for simpler autocomplete

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var zoomIn: Button
    private lateinit var zoomOut: Button
    private lateinit var startLocationEditText: AutoCompleteTextView
    private lateinit var destinationEditText: AutoCompleteTextView
    private lateinit var mapboxRouteButton: Button
    private lateinit var safeRouteButton: Button
    private lateinit var startNavigationButton: Button
    private lateinit var mapboxNavigation: MapboxNavigation
    lateinit var routeInfoTextView: android.widget.TextView
    private lateinit var stopNavigationButton: Button
    private lateinit var recenterButton: Button
    private lateinit var navigationControls: android.widget.LinearLayout
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var soundButton: Button
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView

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
        startNavigationButton = findViewById(R.id.startNavigationButton)
        routeInfoTextView = findViewById(R.id.routeInfoTextView)
        stopNavigationButton = findViewById(R.id.stopNavigationButton)
        recenterButton = findViewById(R.id.recenterButton)
        navigationControls = findViewById(R.id.navigationControls)
        soundButton = findViewById(R.id.soundButton)

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

        // Add click listener to map to dismiss keyboard
        mapView.setOnClickListener {
            // Hide keyboard when map is clicked
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
            currentFocus?.clearFocus()
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

        // Step 4: Setup Autocomplete
        setupAutocomplete()

        // Step 5: Setup Mapbox Navigation
        val navigationOptions = NavigationOptions.Builder(this).build()
        mapboxNavigation = MapboxNavigationProvider.create(navigationOptions)

        val routeLineApi = MapboxRouteLineApi(
            MapboxRouteLineApiOptions.Builder().build()
        )
        val routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(this).build()
        )
        
        // Store routeLineApi and routeLineView as class properties for safe route
        this.routeLineApi = routeLineApi
        this.routeLineView = routeLineView

        // Setup Text-to-Speech for voice navigation
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    android.util.Log.e("TTS", "Language not supported")
                }
            } else {
                android.util.Log.e("TTS", "Initialization failed")
            }
        }
        
        // Setup sound button
        soundButton.setOnClickListener {
            if (textToSpeech.isSpeaking) {
                textToSpeech.stop()
                soundButton.text = "🔊"
            } else {
                textToSpeech.speak("Voice navigation enabled", TextToSpeech.QUEUE_FLUSH, null, null)
                soundButton.text = "🔇"
            }
        }
        
        // Setup voice instruction callbacks
        mapboxNavigation.registerRouteProgressObserver { routeProgress ->
            val bannerInstructions = routeProgress.bannerInstructions
            bannerInstructions?.primary()?.let { primary ->
                val instruction = primary.text()
                if (instruction.isNotEmpty() && !textToSpeech.isSpeaking) {
                    textToSpeech.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }

        // Step 6: Route button logic
        mapboxRouteButton.setOnClickListener {
            val startText = startLocationEditText.text.toString().trim()
            val destText = destinationEditText.text.toString().trim()
            
            if (startText.isEmpty() || destText.isEmpty()) {
                android.widget.Toast.makeText(this, "Please enter both start and destination", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Hide keyboard
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
            currentFocus?.clearFocus()
            
            // Show loading state
            mapboxRouteButton.text = "Calculating..."
            mapboxRouteButton.isEnabled = false
            
            MapboxRouteHandler.handleRoute(
                this,
                startLocationEditText,
                destinationEditText,
                mapboxNavigation,
                mapView,
                routeLineApi,
                routeLineView,
                getString(R.string.mapbox_access_token)
            ) {
                // Reset button state
                mapboxRouteButton.text = "Mapbox Route"
                mapboxRouteButton.isEnabled = true
            }
        }

        // Step 7: Safe route
        safeRouteButton.setOnClickListener {
            val startText = startLocationEditText.text.toString().trim()
            val destText = destinationEditText.text.toString().trim()

            if (startText.isEmpty() || destText.isEmpty()) {
                android.widget.Toast.makeText(this, "Please enter both start and destination", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Hide keyboard
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
            currentFocus?.clearFocus()

            SafeRouteHandler.handleSafeRoute(
                context = this,
                startEditText = startLocationEditText,
                destinationEditText = destinationEditText,
                mapView = mapView,
                routeLineApi = routeLineApi,
                routeLineView = routeLineView,
                accessToken = getString(R.string.mapbox_access_token)
            )
        }

        // Step 7: Start Navigation
        startNavigationButton.setOnClickListener {
            if (mapboxNavigation.getNavigationRoutes().isNotEmpty()) {
                startNavigation()
            } else {
                android.widget.Toast.makeText(this, "Please calculate a route first", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Step 8: Navigation Controls
        stopNavigationButton.setOnClickListener {
            stopNavigation()
        }

        recenterButton.setOnClickListener {
            recenterMap()
        }

        // Optional: Autofill setup if needed
        // setupAddressAutofill()
    }

    private fun setupAutocomplete() {
        // Predefined suggestions for common locations with correct addresses
        val commonLocations = listOf(
            "BMCC - Borough of Manhattan Community College, 199 Chambers St, New York, NY 10007",
            "The Metropolitan Museum of Art, 1000 5th Ave, New York, NY 10028",
            "NYU - New York University, 70 Washington Square S, New York, NY 10012",
            "Times Square, New York, NY 10036",
            "Central Park, New York, NY 10024",
            "Brooklyn Bridge, New York, NY 10038",
            "Statue of Liberty, New York, NY 10004",
            "Empire State Building, 350 5th Ave, New York, NY 10118",
            "Grand Central Terminal, 89 E 42nd St, New York, NY 10017",
            "Wall Street, New York, NY 10005",
            "Broadway, New York, NY",
            "Fifth Avenue, New York, NY",
            "Madison Square Garden, 4 Pennsylvania Plaza, New York, NY 10001",
            "High Line Park, New York, NY 10011",
            "9/11 Memorial, 180 Greenwich St, New York, NY 10007"
        )

        // Setup autocomplete for start location
        val startAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, commonLocations)
        startLocationEditText.setAdapter(startAdapter)
        startLocationEditText.threshold = 1 // Show suggestions after 1 character
        
        // Add action listener to move to destination field on Enter
        startLocationEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                destinationEditText.requestFocus()
                true
            } else {
                false
            }
        }

        // Setup autocomplete for destination
        val destAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, commonLocations)
        destinationEditText.setAdapter(destAdapter)
        destinationEditText.threshold = 1 // Show suggestions after 1 character
        
        // Add action listener to dismiss keyboard on Enter
        destinationEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                destinationEditText.clearFocus()
                // Hide keyboard
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(destinationEditText.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun startNavigation() {
        // Start turn-by-turn navigation
        mapboxNavigation.startTripSession()
        
        // Show navigation controls
        navigationControls.visibility = View.VISIBLE
        startNavigationButton.visibility = View.GONE
        
        android.widget.Toast.makeText(this, "Navigation started!", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun stopNavigation() {
        // Stop navigation
        mapboxNavigation.stopTripSession()
        
        // Hide navigation controls
        navigationControls.visibility = View.GONE
        startNavigationButton.visibility = View.VISIBLE
        
        android.widget.Toast.makeText(this, "Navigation stopped", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun recenterMap() {
        // Recenter map on current route
        val routes = mapboxNavigation.getNavigationRoutes()
        if (routes.isNotEmpty()) {
            val route = routes.first()
            val coordinates = route.directionsRoute.routeOptions()?.coordinatesList()
            if (!coordinates.isNullOrEmpty()) {
                val centerPoint = coordinates.first()
                mapView.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(centerPoint)
                        .zoom(14.0)
                        .build()
                )
            }
        }
    }

    fun updateRouteInfo(route: com.mapbox.navigation.base.route.NavigationRoute) {
        val directionsRoute = route.directionsRoute
        val duration = directionsRoute.duration()
        val distance = directionsRoute.distance()
        
        val durationText = if (duration != null) {
            val minutes = (duration / 60).toInt()
            "${minutes} min"
        } else "Unknown"
        
        val distanceText = if (distance != null) {
            val km = distance / 1000.0
            String.format("%.1f km", km)
        } else "Unknown"
        
        val routeInfo = "Route: $distanceText • $durationText"
        routeInfoTextView.text = routeInfo
        routeInfoTextView.visibility = View.VISIBLE
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
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        // mapboxNavigation.onDestroy() // Removed as it's internal
    }
}
