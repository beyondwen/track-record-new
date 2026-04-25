package com.wenhao.record.data.history

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.wenhao.record.data.tracking.TrackPoint
import java.util.Locale

object HistoryRouteTitleResolver {
    data class AddressParts(
        val featureName: String? = null,
        val premises: String? = null,
        val thoroughfare: String? = null,
        val subThoroughfare: String? = null,
        val subLocality: String? = null,
        val locality: String? = null,
        val adminArea: String? = null,
        val addressLine: String? = null,
    )

    fun choosePlaceName(parts: AddressParts): String? {
        val street = listOfNotNull(
            parts.thoroughfare.cleanPlaceName(),
            parts.subThoroughfare.cleanPlaceName(),
        ).joinToString(" ").takeIf { it.isNotBlank() }

        return listOf(
            parts.featureName,
            parts.premises,
            street,
            parts.thoroughfare,
            parts.subLocality,
            parts.locality,
            parts.adminArea,
            parts.addressLine,
        ).firstNotNullOfOrNull { candidate -> candidate.cleanPlaceName() }
    }

    fun resolve(context: Context, item: HistoryDayItem): String? {
        item.routeTitle.cleanPlaceName()?.let { return it }
        return resolve(context, item.segments.flatten())
    }

    fun resolve(context: Context, points: List<TrackPoint>): String? {
        if (points.isEmpty() || !Geocoder.isPresent()) return null

        val geocoder = Geocoder(context.applicationContext, Locale.CHINA)
        return points.placeLookupCandidates()
            .firstNotNullOfOrNull { point ->
                geocoder.lookupAddress(point)?.let { address ->
                    choosePlaceName(address.toAddressParts())
                }
            }
    }

    private fun List<TrackPoint>.placeLookupCandidates(): List<TrackPoint> {
        return listOfNotNull(
            lastOrNull(),
            firstOrNull(),
            getOrNull(size / 2),
        ).distinctBy { point ->
            "${point.latitude.formatCoordinateKey()},${point.longitude.formatCoordinateKey()}"
        }
    }

    @Suppress("DEPRECATION")
    private fun Geocoder.lookupAddress(point: TrackPoint): Address? {
        return runCatching {
            getFromLocation(
                point.wgs84Latitude ?: point.latitude,
                point.wgs84Longitude ?: point.longitude,
                1,
            )?.firstOrNull()
        }.getOrNull()
    }

    private fun Address.toAddressParts(): AddressParts {
        return AddressParts(
            featureName = featureName,
            premises = premises,
            thoroughfare = thoroughfare,
            subThoroughfare = subThoroughfare,
            subLocality = subLocality,
            locality = locality,
            adminArea = adminArea,
            addressLine = getAddressLine(0),
        )
    }

    private fun String?.cleanPlaceName(): String? {
        val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.equals("Unnamed Road", ignoreCase = true)) return null
        if (value.equals("unknown", ignoreCase = true)) return null
        if (value.matches(Regex("^[A-Z0-9]{4,}\\+[A-Z0-9]{2,}.*"))) return null
        if (value.matches(Regex("^-?\\d+(\\.\\d+)?[,， ]+-?\\d+(\\.\\d+)?$"))) return null
        return value
    }

    private fun Double.formatCoordinateKey(): String {
        return String.format(Locale.US, "%.5f", this)
    }
}
