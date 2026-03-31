package com.wenhao.record.tracking

import com.wenhao.record.data.tracking.TrackPoint
import kotlin.math.cos
import kotlin.math.max

object AdaptiveTrackSmoother {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MIN_MEASUREMENT_STD_DEV_METERS = 8.0
    private const val MAX_PREDICTION_GAP_SECONDS = 8.0

    private var filterState: KalmanTrackState? = null

    fun reset() {
        filterState = null
    }

    fun seed(point: TrackPoint?) {
        filterState = point?.let(::createStateFromPoint)
    }

    fun smooth(
        previousPoint: TrackPoint?,
        candidatePoint: TrackPoint,
        speedMetersPerSecond: Float,
        accuracyMeters: Float?,
    ): TrackPoint {
        previousPoint ?: run {
            seed(candidatePoint)
            return candidatePoint
        }

        val effectiveAccuracyMeters = max(
            MIN_MEASUREMENT_STD_DEV_METERS,
            (accuracyMeters ?: candidatePoint.accuracyMeters ?: 18f).toDouble(),
        )
        val currentState = prepareState(previousPoint)
        val updatedState = updateTrackState(
            state = currentState,
            candidatePoint = candidatePoint,
            speedMetersPerSecond = speedMetersPerSecond,
            accuracyMeters = effectiveAccuracyMeters,
        )
        filterState = updatedState
        return buildFilteredPoint(updatedState, candidatePoint)
    }

    private fun prepareState(previousPoint: TrackPoint): KalmanTrackState {
        val cachedState = filterState
        if (cachedState == null || cachedState.lastTimestampMillis != previousPoint.timestampMillis) {
            return createStateFromPoint(previousPoint)
        }
        return cachedState
    }

    private fun createStateFromPoint(point: TrackPoint): KalmanTrackState {
        return KalmanTrackState(
            gcjFilter = createCoordinateFilter(point.latitude, point.longitude),
            wgsFilter = point.wgs84Latitude?.let { latitude ->
                point.wgs84Longitude?.let { longitude ->
                    createCoordinateFilter(latitude, longitude)
                }
            },
            altitudeFilter = point.altitudeMeters?.let(::createAltitudeFilter),
            lastTimestampMillis = point.timestampMillis,
            lastAccuracyMeters = point.accuracyMeters?.toDouble() ?: 18.0,
        )
    }

    private fun createCoordinateFilter(latitude: Double, longitude: Double): CoordinateKalmanFilter {
        return CoordinateKalmanFilter(
            originLatitude = latitude,
            originLongitude = longitude,
            xAxis = AxisKalmanState(position = 0.0),
            yAxis = AxisKalmanState(position = 0.0),
        )
    }

    private fun createAltitudeFilter(altitudeMeters: Double): AxisKalmanState {
        return AxisKalmanState(position = altitudeMeters)
    }

    private fun updateTrackState(
        state: KalmanTrackState,
        candidatePoint: TrackPoint,
        speedMetersPerSecond: Float,
        accuracyMeters: Double,
    ): KalmanTrackState {
        val deltaSeconds = ((candidatePoint.timestampMillis - state.lastTimestampMillis) / 1000.0)
            .coerceIn(0.5, MAX_PREDICTION_GAP_SECONDS)
        val processNoiseMeters = processNoiseMeters(speedMetersPerSecond, accuracyMeters)
        val measurementVariance = accuracyMeters * accuracyMeters

        val gcjFilter = updateCoordinateFilter(
            filter = state.gcjFilter,
            measurementLatitude = candidatePoint.latitude,
            measurementLongitude = candidatePoint.longitude,
            deltaSeconds = deltaSeconds,
            measurementVariance = measurementVariance,
            processNoiseMeters = processNoiseMeters,
        )
        val wgsFilter = candidatePoint.wgs84Latitude?.let { latitude ->
            candidatePoint.wgs84Longitude?.let { longitude ->
                updateCoordinateFilter(
                    filter = state.wgsFilter ?: createCoordinateFilter(latitude, longitude),
                    measurementLatitude = latitude,
                    measurementLongitude = longitude,
                    deltaSeconds = deltaSeconds,
                    measurementVariance = measurementVariance,
                    processNoiseMeters = processNoiseMeters,
                )
            }
        } ?: state.wgsFilter
        val altitudeFilter = candidatePoint.altitudeMeters?.let { altitudeMeters ->
            updateAxisState(
                state = state.altitudeFilter ?: createAltitudeFilter(altitudeMeters),
                measurement = altitudeMeters,
                deltaSeconds = deltaSeconds,
                measurementVariance = measurementVariance,
                processNoiseVariance = altitudeProcessNoiseVariance(speedMetersPerSecond, accuracyMeters),
            )
        } ?: state.altitudeFilter

        return state.copy(
            gcjFilter = gcjFilter,
            wgsFilter = wgsFilter,
            altitudeFilter = altitudeFilter,
            lastTimestampMillis = candidatePoint.timestampMillis,
            lastAccuracyMeters = accuracyMeters,
        )
    }

    private fun updateCoordinateFilter(
        filter: CoordinateKalmanFilter,
        measurementLatitude: Double,
        measurementLongitude: Double,
        deltaSeconds: Double,
        measurementVariance: Double,
        processNoiseMeters: Double,
    ): CoordinateKalmanFilter {
        val measurement = toLocalMeters(
            originLatitude = filter.originLatitude,
            originLongitude = filter.originLongitude,
            latitude = measurementLatitude,
            longitude = measurementLongitude,
        )
        return filter.copy(
            xAxis = updateAxisState(
                state = filter.xAxis,
                measurement = measurement.first,
                deltaSeconds = deltaSeconds,
                measurementVariance = measurementVariance,
                processNoiseVariance = processNoiseMeters * processNoiseMeters,
            ),
            yAxis = updateAxisState(
                state = filter.yAxis,
                measurement = measurement.second,
                deltaSeconds = deltaSeconds,
                measurementVariance = measurementVariance,
                processNoiseVariance = processNoiseMeters * processNoiseMeters,
            ),
        )
    }

    private fun updateAxisState(
        state: AxisKalmanState,
        measurement: Double,
        deltaSeconds: Double,
        measurementVariance: Double,
        processNoiseVariance: Double,
    ): AxisKalmanState {
        val predictedPosition = state.position + state.velocity * deltaSeconds
        val predictedVelocity = state.velocity

        val dt2 = deltaSeconds * deltaSeconds
        val dt3 = dt2 * deltaSeconds
        val dt4 = dt2 * dt2

        val predictedP00 = state.p00 + deltaSeconds * (state.p10 + state.p01) + dt2 * state.p11 +
            processNoiseVariance * dt4 / 4.0
        val predictedP01 = state.p01 + deltaSeconds * state.p11 + processNoiseVariance * dt3 / 2.0
        val predictedP10 = state.p10 + deltaSeconds * state.p11 + processNoiseVariance * dt3 / 2.0
        val predictedP11 = state.p11 + processNoiseVariance * dt2

        val innovation = measurement - predictedPosition
        val innovationCovariance = predictedP00 + measurementVariance
        val gainPosition = predictedP00 / innovationCovariance
        val gainVelocity = predictedP10 / innovationCovariance

        val updatedPosition = predictedPosition + gainPosition * innovation
        val updatedVelocity = predictedVelocity + gainVelocity * innovation

        return AxisKalmanState(
            position = updatedPosition,
            velocity = updatedVelocity,
            p00 = (1.0 - gainPosition) * predictedP00,
            p01 = (1.0 - gainPosition) * predictedP01,
            p10 = predictedP10 - gainVelocity * predictedP00,
            p11 = predictedP11 - gainVelocity * predictedP01,
        )
    }

    private fun buildFilteredPoint(
        state: KalmanTrackState,
        candidatePoint: TrackPoint,
    ): TrackPoint {
        val gcjCoordinate = fromLocalMeters(
            originLatitude = state.gcjFilter.originLatitude,
            originLongitude = state.gcjFilter.originLongitude,
            xMeters = state.gcjFilter.xAxis.position,
            yMeters = state.gcjFilter.yAxis.position,
        )
        val wgsCoordinate = state.wgsFilter?.let { filter ->
            fromLocalMeters(
                originLatitude = filter.originLatitude,
                originLongitude = filter.originLongitude,
                xMeters = filter.xAxis.position,
                yMeters = filter.yAxis.position,
            )
        }

        return TrackPoint(
            latitude = gcjCoordinate.first,
            longitude = gcjCoordinate.second,
            timestampMillis = candidatePoint.timestampMillis,
            accuracyMeters = candidatePoint.accuracyMeters,
            altitudeMeters = state.altitudeFilter?.position ?: candidatePoint.altitudeMeters,
            wgs84Latitude = wgsCoordinate?.first ?: candidatePoint.wgs84Latitude,
            wgs84Longitude = wgsCoordinate?.second ?: candidatePoint.wgs84Longitude,
        )
    }

    private fun processNoiseMeters(speedMetersPerSecond: Float, accuracyMeters: Double): Double {
        val speedNoise = when {
            speedMetersPerSecond >= 12f -> 10.0
            speedMetersPerSecond >= 6f -> 6.0
            speedMetersPerSecond >= 2f -> 3.8
            else -> 2.2
        }
        return max(speedNoise, accuracyMeters * 0.12)
    }

    private fun altitudeProcessNoiseVariance(speedMetersPerSecond: Float, accuracyMeters: Double): Double {
        val altitudeNoiseMeters = max(1.5, processNoiseMeters(speedMetersPerSecond, accuracyMeters) * 0.6)
        return altitudeNoiseMeters * altitudeNoiseMeters
    }

    private fun toLocalMeters(
        originLatitude: Double,
        originLongitude: Double,
        latitude: Double,
        longitude: Double,
    ): Pair<Double, Double> {
        val originLatitudeRad = Math.toRadians(originLatitude)
        val originLongitudeRad = Math.toRadians(originLongitude)
        val latitudeRad = Math.toRadians(latitude)
        val longitudeRad = Math.toRadians(longitude)
        val xMeters = (longitudeRad - originLongitudeRad) * cos(originLatitudeRad) * EARTH_RADIUS_METERS
        val yMeters = (latitudeRad - originLatitudeRad) * EARTH_RADIUS_METERS
        return xMeters to yMeters
    }

    private fun fromLocalMeters(
        originLatitude: Double,
        originLongitude: Double,
        xMeters: Double,
        yMeters: Double,
    ): Pair<Double, Double> {
        val originLatitudeRad = Math.toRadians(originLatitude)
        val latitude = originLatitude + Math.toDegrees(yMeters / EARTH_RADIUS_METERS)
        val longitude = originLongitude + Math.toDegrees(
            xMeters / (EARTH_RADIUS_METERS * cos(originLatitudeRad).coerceAtLeast(1e-6))
        )
        return latitude to longitude
    }

    private data class KalmanTrackState(
        val gcjFilter: CoordinateKalmanFilter,
        val wgsFilter: CoordinateKalmanFilter?,
        val altitudeFilter: AxisKalmanState?,
        val lastTimestampMillis: Long,
        val lastAccuracyMeters: Double,
    )

    private data class CoordinateKalmanFilter(
        val originLatitude: Double,
        val originLongitude: Double,
        val xAxis: AxisKalmanState,
        val yAxis: AxisKalmanState,
    )

    private data class AxisKalmanState(
        val position: Double,
        val velocity: Double = 0.0,
        val p00: Double = 12.0,
        val p01: Double = 0.0,
        val p10: Double = 0.0,
        val p11: Double = 4.0,
    )
}
