package com.bignerdranch.android.mapboxbase

import android.content.Context
import android.location.Geocoder
import android.util.Log
import android.widget.EditText
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.bindgen.Value
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object SafeRouteHandler {

    fun handleSafeRoute(
        context: Context,
        startEditText: EditText,
        destinationEditText: EditText,
        mapView: MapView,
        routeLineApi: MapboxRouteLineApi,
        routeLineView: MapboxRouteLineView,
        accessToken: String
    ) {
        val originText = startEditText.text.toString().trim()
        val destinationText = destinationEditText.text.toString().trim()

        if (originText.isBlank() || destinationText.isBlank()) {
            if (context is MainActivity) {
                android.widget.Toast.makeText(context, "Please enter both start and destination", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        geocodeAddress(context, originText) { originPoint ->
            geocodeAddress(context, destinationText) { destinationPoint ->
                    fetchSafeRouteFromBackend(originPoint, destinationPoint) { polylinePoints ->
                        if (polylinePoints.isNotEmpty()) {
                            // Draw the safe route first
                            drawPolylineOnMap(mapView, polylinePoints, routeLineApi, routeLineView)
                            
                            // Then fetch traffic data for the same route
                            fetchTrafficDataForRoute(originPoint, destinationPoint, accessToken) { trafficRoute ->
                                if (trafficRoute != null) {
                                    // Overlay traffic information
                                    overlayTrafficData(mapView, trafficRoute)
                                    
                                    // Calculate and display time estimates
                                    displayTrafficInfo(context, trafficRoute, polylinePoints)
                                }
                            }
                            
                            if (context is MainActivity) {
                                android.widget.Toast.makeText(context, "Safe route with traffic data calculated!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (context is MainActivity) {
                                android.widget.Toast.makeText(context, "No safe route found for this destination", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
            }
        }
    }


    private fun geocodeAddress(context: Context, address: String, callback: (Point) -> Unit) {
        Thread {
            try {
                val geocoder = Geocoder(context)
                val results = geocoder.getFromLocationName(address, 1)
                if (!results.isNullOrEmpty()) {
                    val location = results[0]
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    (context as? MainActivity)?.runOnUiThread { callback(point) }
                }
            } catch (e: Exception) {
                Log.e("SafeRouteHandler", "Geocoding failed: ${e.message}")
                if (context is MainActivity) {
                    context.runOnUiThread {
                        android.widget.Toast.makeText(context, "Failed to find location: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    private fun fetchSafeRouteFromBackend(
        origin: Point,
        destination: Point,
        callback: (List<Point>) -> Unit
    ) {
        Thread {
            try {
                val startLat = origin.latitude()
                val startLng = origin.longitude()
                val destLat = destination.latitude()
                val destLng = destination.longitude()
                val gridCode = 7

                val urlString =
                    "https://backend-collision.onrender.com/api/NYCSafeRouteWithPoints" +
                            "?latitude1=$startLat&longitude1=$startLng" +
                            "&latitude2=$destLat&longitude2=$destLng&gridcode=$gridCode"

                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val response = connection.inputStream.bufferedReader().use { it.readText() }

                val json = JSONObject(response)
                val dataset = json.getJSONArray("dataset")

                val pathPoints = mutableListOf<Point>()

                for (i in 0 until dataset.length()) {
                    val line = dataset.getJSONObject(i)
                    val geom = line.getJSONObject("geom")
                    val coordinates = geom.getJSONArray("coordinates")

                    for (j in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(j)
                        val lng = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        val point = Point.fromLngLat(lng, lat)

                        // Avoid duplicate points (optional)
                        if (pathPoints.isEmpty() || pathPoints.last() != point) {
                            pathPoints.add(point)
                        }
                    }
                }

                callbackOnMainThread(callback, pathPoints)

            } catch (e: Exception) {
                Log.e("SafeRouteHandler", "Failed to fetch safe route: ${e.message}")
                callbackOnMainThread(callback, emptyList())
            }
        }.start()
    }


    private fun callbackOnMainThread(callback: (List<Point>) -> Unit, result: List<Point>) {
        // Run callback on UI thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            callback(result)
        }
    }

    private fun decodePolyline(encoded: String): List<Point> {
        val poly = mutableListOf<Point>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(Point.fromLngLat(lng / 1E5, lat / 1E5))
        }

        return poly
    }

    private fun drawPolylineOnMap(
        mapView: MapView, 
        points: List<Point>,
        routeLineApi: MapboxRouteLineApi,
        routeLineView: MapboxRouteLineView
    ) {
        Log.d("SafeRouteHandler", "Drawing ${points.size} points on map")
        
        if (points.isNotEmpty()) {
            // Center the map on the route
            val firstPoint = points.first()
            mapView.mapboxMap.setCamera(
                com.mapbox.maps.CameraOptions.Builder()
                    .center(firstPoint)
                    .zoom(12.0)
                    .build()
            )
            
            // Log detailed route information for debugging
            Log.d("SafeRouteHandler", "Safe route with ${points.size} points:")
            points.take(10).forEachIndexed { index, point ->
                Log.d("SafeRouteHandler", "Point $index: ${point.latitude()}, ${point.longitude()}")
            }
            
            // Log route statistics
            val totalDistance = calculateTotalDistance(points)
            Log.d("SafeRouteHandler", "Total route distance: ${String.format("%.2f", totalDistance)} meters")
            
            // Draw the safe route on the map using direct JSON API
            mapView.mapboxMap.getStyle { style ->
                try {
                    // Create LineString from points
                    val lineString = com.mapbox.geojson.LineString.fromLngLats(points)
                    val featureCollection = com.mapbox.geojson.FeatureCollection.fromFeatures(
                        listOf(com.mapbox.geojson.Feature.fromGeometry(lineString))
                    )
                    
                    // Remove existing safe route layers and sources if any
                    if (style.styleLayerExists("safe-route-layer")) {
                        style.removeStyleLayer("safe-route-layer")
                    }
                    if (style.styleSourceExists("safe-route-source")) {
                        style.removeStyleSource("safe-route-source")
                    }
                    
                    // Add GeoJSON source using Value API
                    val sourceJson = """
                        {
                            "type": "geojson",
                            "data": ${featureCollection.toJson()}
                        }
                    """.trimIndent()
                    val sourceValue = Value.fromJson(sourceJson)
                    if (sourceValue.isValue) {
                        style.addStyleSource("safe-route-source", sourceValue.value!!)
                    }
                    
                    // Add line layer using Value API
                    val layerJson = """
                        {
                            "id": "safe-route-layer",
                            "type": "line",
                            "source": "safe-route-source",
                            "paint": {
                                "line-color": "#FF6B35",
                                "line-width": 6,
                                "line-opacity": 0.9
                            }
                        }
                    """.trimIndent()
                    val layerValue = Value.fromJson(layerJson)
                    if (layerValue.isValue) {
                        style.addStyleLayer(layerValue.value!!, null)
                    }
                    
                    Log.d("SafeRouteHandler", "Safe route drawn successfully on map with ${points.size} points")
                    
                } catch (e: Exception) {
                    Log.e("SafeRouteHandler", "Failed to draw safe route: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun calculateTotalDistance(points: List<Point>): Double {
        if (points.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            totalDistance += calculateDistance(prev, curr)
        }
        return totalDistance
    }
    
    private fun calculateDistance(point1: Point, point2: Point): Double {
        val lat1 = Math.toRadians(point1.latitude())
        val lon1 = Math.toRadians(point1.longitude())
        val lat2 = Math.toRadians(point2.latitude())
        val lon2 = Math.toRadians(point2.longitude())
        
        val dlat = lat2 - lat1
        val dlon = lon2 - lon1
        
        val a = Math.sin(dlat / 2) * Math.sin(dlat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dlon / 2) * Math.sin(dlon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return 6371000 * c // Earth's radius in meters
    }
    
    private fun fetchTrafficDataForRoute(
        origin: Point,
        destination: Point,
        accessToken: String,
        callback: (DirectionsRoute?) -> Unit
    ) {
        val routeOptions = RouteOptions.builder()
            .coordinatesList(listOf(origin, destination))
            .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC) // Enable traffic data
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .steps(true)
            .build()

        val client = MapboxDirections.builder()
            .routeOptions(routeOptions)
            .accessToken(accessToken)
            .build()

        client.enqueueCall(object : Callback<DirectionsResponse> {
            override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                val routes = response.body()?.routes()
                if (!routes.isNullOrEmpty()) {
                    callback(routes[0])
                } else {
                    callback(null)
                }
            }

            override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                Log.e("SafeRouteHandler", "Failed to fetch traffic data: ${t.message}")
                callback(null)
            }
        })
    }
    
    private fun overlayTrafficData(mapView: MapView, trafficRoute: DirectionsRoute) {
        mapView.mapboxMap.getStyle { style ->
            try {
                // Remove existing traffic overlay if any
                if (style.styleLayerExists("traffic-overlay-layer")) {
                    style.removeStyleLayer("traffic-overlay-layer")
                }
                if (style.styleSourceExists("traffic-overlay-source")) {
                    style.removeStyleSource("traffic-overlay-source")
                }
                
                // Create traffic overlay using the traffic route geometry
                val routeGeometryString = trafficRoute.geometry()
                if (routeGeometryString != null) {
                    val routeGeometry = com.mapbox.geojson.LineString.fromJson(routeGeometryString)
                    if (routeGeometry != null) {
                        val featureCollection = com.mapbox.geojson.FeatureCollection.fromFeatures(
                            listOf(com.mapbox.geojson.Feature.fromGeometry(routeGeometry))
                        )
                    
                    // Add traffic overlay source
                    val sourceJson = """
                        {
                            "type": "geojson",
                            "data": ${featureCollection.toJson()}
                        }
                    """.trimIndent()
                    val sourceValue = Value.fromJson(sourceJson)
                    if (sourceValue.isValue) {
                        style.addStyleSource("traffic-overlay-source", sourceValue.value!!)
                    }
                    
                    // Add traffic overlay layer (semi-transparent blue to show traffic route)
                    val layerJson = """
                        {
                            "id": "traffic-overlay-layer",
                            "type": "line",
                            "source": "traffic-overlay-source",
                            "paint": {
                                "line-color": "#0066CC",
                                "line-width": 3,
                                "line-opacity": 0.3
                            }
                        }
                    """.trimIndent()
                    val layerValue = Value.fromJson(layerJson)
                    if (layerValue.isValue) {
                        style.addStyleLayer(layerValue.value!!, null)
                    }
                    
                    Log.d("SafeRouteHandler", "Traffic overlay added successfully")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("SafeRouteHandler", "Failed to overlay traffic data: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun displayTrafficInfo(context: Context, trafficRoute: DirectionsRoute, safeRoutePoints: List<Point>) {
        try {
            val duration = trafficRoute.duration()
            val distance = trafficRoute.distance()
            val safeRouteDistance = calculateTotalDistance(safeRoutePoints)
            
            val durationText = if (duration != null) {
                val minutes = (duration / 60).toInt()
                "${minutes} min"
            } else "Unknown"
            
            val distanceText = if (distance != null) {
                val km = distance / 1000.0
                String.format("%.1f km", km)
            } else "Unknown"
            
            val safeDistanceText = String.format("%.1f km", safeRouteDistance / 1000.0)
            
            val trafficInfo = "Safe Route: $safeDistanceText • Traffic Route: $distanceText • Time: $durationText"
            
            if (context is MainActivity) {
                context.runOnUiThread {
                    // Update route info display with traffic data
                    val trafficInfo = "Safe Route: $safeDistanceText • Traffic Route: $distanceText • Time: $durationText"
                    context.routeInfoTextView.text = trafficInfo
                    context.routeInfoTextView.visibility = android.view.View.VISIBLE
                    
                    // Show detailed toast
                    android.widget.Toast.makeText(
                        context, 
                        "Safe Route: $safeDistanceText\nTraffic Route: $distanceText\nEstimated Time: $durationText", 
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            
            Log.d("SafeRouteHandler", "Traffic info displayed: $trafficInfo")
            
        } catch (e: Exception) {
            Log.e("SafeRouteHandler", "Failed to display traffic info: ${e.message}")
            e.printStackTrace()
        }
    }
}
