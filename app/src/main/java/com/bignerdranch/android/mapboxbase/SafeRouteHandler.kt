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
        mapView: MapView
    ) {
        val originText = startEditText.text.toString()
        val destinationText = destinationEditText.text.toString()

        if (originText.isBlank() || destinationText.isBlank()) return

        geocodeAddress(context, originText) { originPoint ->
            geocodeAddress(context, destinationText) { destinationPoint ->
                fetchSafeRouteFromBackend(originPoint, destinationPoint) { polylinePoints ->
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
                val gridCode = 9

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
