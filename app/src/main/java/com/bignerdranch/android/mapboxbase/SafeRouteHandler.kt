package com.bignerdranch.android.mapboxbase

import android.content.Context
import android.location.Geocoder
import android.util.Log
import android.widget.EditText
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SafeRouteHandler {

    fun handleSafeRoute(
        context: Context,
        startEditText: EditText,
        destinationEditText: EditText,
        mapView: MapView,
        apiKey: String
    ) {
        val originText = startEditText.text.toString()
        val destinationText = destinationEditText.text.toString()

        if (originText.isBlank() || destinationText.isBlank()) return

        geocodeAddress(context, originText) { originPoint ->
            geocodeAddress(context, destinationText) { destinationPoint ->
                fetchSafeRoute(originPoint, destinationPoint, apiKey) { polylinePoints ->
                    drawPolylineOnMap(mapView, polylinePoints)
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
            }
        }.start()
    }

    private fun fetchSafeRoute(
        origin: Point,
        destination: Point,
        apiKey: String,
        callback: (List<Point>) -> Unit
    ) {
        Thread {
            try {
                val urlString =
                    "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude()},${origin.longitude()}&destination=${destination.latitude()},${destination.longitude()}&key=$apiKey"
                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val response = connection.inputStream.bufferedReader().use { it.readText() }

                val json = JSONObject(response)
                val polyline = json
                    .getJSONArray("routes")
                    .getJSONObject(0)
                    .getJSONObject("overview_polyline")
                    .getString("points")

                val decodedPoints = decodePolyline(polyline)

                // Run UI update
                callbackOnMainThread(callback, decodedPoints)
            } catch (e: Exception) {
                Log.e("SafeRouteHandler", "API call failed: ${e.message}")
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

    private fun drawPolylineOnMap(mapView: MapView, points: List<Point>) {
        val annotationApi = mapView.annotations
        val polylineManager = annotationApi.createPolylineAnnotationManager()

        polylineManager.create(
            PolylineAnnotationOptions()
                .withPoints(points)
                .withLineColor("#FF0000")
                .withLineWidth(5.0)
        )
    }
}
