package com.quakesphere.globe

/**
 * A point on Earth's surface in geographic coordinates.
 *
 * @property lat Latitude in degrees, −90° (south pole) to +90° (north pole).
 * @property lon Longitude in degrees, −180° to +180°. Increasing east.
 */
data class GeoCoord(val lat: Double, val lon: Double)
