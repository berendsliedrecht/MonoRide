package com.monoapps.monoride.data

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Local equirectangular projection: meters east (x) and south (y, screen-friendly)
 * of a reference point. Accurate to well under a meter at city scale.
 */
class Projection(val lat0: Double, val lon0: Double) {
    private val mPerDegLat = 111_132.0
    private val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat0))

    fun x(lon: Double): Float = ((lon - lon0) * mPerDegLon).toFloat()
    fun y(lat: Double): Float = ((lat0 - lat) * mPerDegLat).toFloat()
}

/** Distance in meters between two lat/lon points (equirectangular, fine at city scale). */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val mPerDegLat = 111_132.0
    val mPerDegLon = 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2))
    val dx = (lon2 - lon1) * mPerDegLon
    val dy = (lat2 - lat1) * mPerDegLat
    return sqrt(dx * dx + dy * dy)
}
