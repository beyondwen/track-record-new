import type {
  AnalysisSegment,
  AnalysisStayCluster,
  HistoryPoint,
  HistoryRecord,
  RawLocationPoint,
  ValidatedAnalysisBatchRequest,
  ValidatedRawPointBatchRequest,
  ValidatedHistoryBatchRequest
} from "./types";

const MAX_DEVICE_ID_LENGTH = 128;
const MAX_APP_VERSION_LENGTH = 64;
const MAX_TITLE_LENGTH = 255;
const MAX_SOURCE_LENGTH = 64;
const MAX_PROVIDER_LENGTH = 64;
const MAX_SOURCE_TYPE_LENGTH = 64;
const MAX_ACTIVITY_TYPE_LENGTH = 64;
const MAX_SAMPLING_TIER_LENGTH = 32;
const MAX_SEGMENT_TYPE_LENGTH = 32;

export class ValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ValidationError";
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function parseRequiredString(
  value: unknown,
  label: string,
  maxLength: number
): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new ValidationError(`${label} must be a non-empty string`);
  }
  const normalized = value.trim();
  if (normalized.length > maxLength) {
    throw new ValidationError(`${label} length must be <= ${maxLength}`);
  }
  return normalized;
}

function parseOptionalString(
  value: unknown,
  label: string,
  maxLength: number
): string | null {
  if (value === undefined || value === null) {
    return null;
  }
  if (typeof value !== "string") {
    throw new ValidationError(`${label} must be a string or null`);
  }
  const normalized = value.trim();
  if (normalized.length === 0) {
    return null;
  }
  if (normalized.length > maxLength) {
    throw new ValidationError(`${label} length must be <= ${maxLength}`);
  }
  return normalized;
}

function parseOptionalInteger(value: unknown, label: string): number | null {
  if (value === undefined || value === null) {
    return null;
  }
  if (!Number.isSafeInteger(value)) {
    throw new ValidationError(`${label} must be an integer or null`);
  }
  return value as number;
}

function parseOptionalFiniteNumber(value: unknown, label: string): number | null {
  if (value === undefined || value === null) {
    return null;
  }
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new ValidationError(`${label} must be a finite number or null`);
  }
  return value;
}

function parseRawPoint(point: unknown, index: number): RawLocationPoint {
  if (!isObject(point)) {
    throw new ValidationError(`points[${index}] must be an object`);
  }

  const {
    pointId,
    timestampMillis,
    latitude,
    longitude,
    accuracyMeters,
    altitudeMeters,
    speedMetersPerSecond,
    bearingDegrees,
    provider,
    sourceType,
    isMock,
    wifiFingerprintDigest,
    activityType,
    activityConfidence,
    samplingTier
  } = point;

  if (!Number.isSafeInteger(pointId)) {
    throw new ValidationError(`points[${index}].pointId must be an integer`);
  }
  if (!Number.isSafeInteger(timestampMillis)) {
    throw new ValidationError(`points[${index}].timestampMillis must be an integer`);
  }
  if (typeof latitude !== "number" || !Number.isFinite(latitude)) {
    throw new ValidationError(`points[${index}].latitude must be a finite number`);
  }
  if (typeof longitude !== "number" || !Number.isFinite(longitude)) {
    throw new ValidationError(`points[${index}].longitude must be a finite number`);
  }
  if (typeof isMock !== "boolean") {
    throw new ValidationError(`points[${index}].isMock must be a boolean`);
  }

  return {
    pointId: pointId as number,
    timestampMillis: timestampMillis as number,
    latitude,
    longitude,
    accuracyMeters: parseOptionalFiniteNumber(
      accuracyMeters,
      `points[${index}].accuracyMeters`
    ),
    altitudeMeters: parseOptionalFiniteNumber(
      altitudeMeters,
      `points[${index}].altitudeMeters`
    ),
    speedMetersPerSecond: parseOptionalFiniteNumber(
      speedMetersPerSecond,
      `points[${index}].speedMetersPerSecond`
    ),
    bearingDegrees: parseOptionalFiniteNumber(
      bearingDegrees,
      `points[${index}].bearingDegrees`
    ),
    provider: parseRequiredString(
      provider,
      `points[${index}].provider`,
      MAX_PROVIDER_LENGTH
    ),
    sourceType: parseRequiredString(
      sourceType,
      `points[${index}].sourceType`,
      MAX_SOURCE_TYPE_LENGTH
    ),
    isMock,
    wifiFingerprintDigest: parseOptionalString(
      wifiFingerprintDigest,
      `points[${index}].wifiFingerprintDigest`,
      255
    ),
    activityType: parseOptionalString(
      activityType,
      `points[${index}].activityType`,
      MAX_ACTIVITY_TYPE_LENGTH
    ),
    activityConfidence: parseOptionalFiniteNumber(
      activityConfidence,
      `points[${index}].activityConfidence`
    ),
    samplingTier: parseRequiredString(
      samplingTier,
      `points[${index}].samplingTier`,
      MAX_SAMPLING_TIER_LENGTH
    )
  };
}

export function validateRawPointBatchRequest(
  payload: unknown
): ValidatedRawPointBatchRequest {
  if (!isObject(payload)) {
    throw new ValidationError("Request body must be a JSON object");
  }

  if (!Array.isArray(payload.points)) {
    throw new ValidationError("`points` must be an array");
  }

  return {
    deviceId: parseRequiredString(
      payload.deviceId,
      "`deviceId`",
      MAX_DEVICE_ID_LENGTH
    ),
    appVersion: parseRequiredString(
      payload.appVersion,
      "`appVersion`",
      MAX_APP_VERSION_LENGTH
    ),
    points: payload.points.map((point, index) => parseRawPoint(point, index))
  };
}

function parseAnalysisStayCluster(
  stayCluster: unknown,
  segmentIndex: number,
  stayIndex: number
): AnalysisStayCluster {
  if (!isObject(stayCluster)) {
    throw new ValidationError(
      `segments[${segmentIndex}].stayClusters[${stayIndex}] must be an object`
    );
  }

  const {
    stayId,
    centerLat,
    centerLng,
    radiusMeters,
    arrivalTime,
    departureTime,
    confidence,
    analysisVersion
  } = stayCluster;

  if (!Number.isSafeInteger(stayId)) {
    throw new ValidationError(
      `segments[${segmentIndex}].stayClusters[${stayIndex}].stayId must be an integer`
    );
  }
  if (typeof centerLat !== "number" || !Number.isFinite(centerLat)) {
    throw new ValidationError(
      `segments[${segmentIndex}].stayClusters[${stayIndex}].centerLat must be a finite number`
    );
  }
  if (typeof centerLng !== "number" || !Number.isFinite(centerLng)) {
    throw new ValidationError(
      `segments[${segmentIndex}].stayClusters[${stayIndex}].centerLng must be a finite number`
    );
  }
  if (typeof radiusMeters !== "number" || !Number.isFinite(radiusMeters)) {
    throw new ValidationError(
      `segments[${segmentIndex}].stayClusters[${stayIndex}].radiusMeters must be a finite number`
    );
  }
  if (!Number.isSafeInteger(arrivalTime)) {
    throw new ValidationError(
      `segments[${segmentIndex}].stayClusters[${stayIndex}].arrivalTime must be an integer`
    );
  }
  if (!Number.isSafeInteger(departureTime)) {
    throw new ValidationError(
      `segments[${segmentIndex}].stayClusters[${stayIndex}].departureTime must be an integer`
    );
  }
  if (typeof confidence !== "number" || !Number.isFinite(confidence)) {
    throw new ValidationError(
      `segments[${segmentIndex}].stayClusters[${stayIndex}].confidence must be a finite number`
    );
  }
  if (!Number.isSafeInteger(analysisVersion)) {
    throw new ValidationError(
      `segments[${segmentIndex}].stayClusters[${stayIndex}].analysisVersion must be an integer`
    );
  }

  return {
    stayId: stayId as number,
    centerLat,
    centerLng,
    radiusMeters,
    arrivalTime: arrivalTime as number,
    departureTime: departureTime as number,
    confidence,
    analysisVersion: analysisVersion as number
  };
}

function parseAnalysisSegment(segment: unknown, index: number): AnalysisSegment {
  if (!isObject(segment)) {
    throw new ValidationError(`segments[${index}] must be an object`);
  }

  const {
    segmentId,
    startPointId,
    endPointId,
    startTimestamp,
    endTimestamp,
    segmentType,
    confidence,
    distanceMeters,
    durationMillis,
    avgSpeedMetersPerSecond,
    maxSpeedMetersPerSecond,
    analysisVersion,
    stayClusters
  } = segment;

  if (!Number.isSafeInteger(segmentId)) {
    throw new ValidationError(`segments[${index}].segmentId must be an integer`);
  }
  if (!Number.isSafeInteger(startPointId)) {
    throw new ValidationError(`segments[${index}].startPointId must be an integer`);
  }
  if (!Number.isSafeInteger(endPointId)) {
    throw new ValidationError(`segments[${index}].endPointId must be an integer`);
  }
  if (!Number.isSafeInteger(startTimestamp)) {
    throw new ValidationError(`segments[${index}].startTimestamp must be an integer`);
  }
  if (!Number.isSafeInteger(endTimestamp)) {
    throw new ValidationError(`segments[${index}].endTimestamp must be an integer`);
  }
  if (typeof confidence !== "number" || !Number.isFinite(confidence)) {
    throw new ValidationError(`segments[${index}].confidence must be a finite number`);
  }
  if (typeof distanceMeters !== "number" || !Number.isFinite(distanceMeters)) {
    throw new ValidationError(`segments[${index}].distanceMeters must be a finite number`);
  }
  if (!Number.isSafeInteger(durationMillis)) {
    throw new ValidationError(`segments[${index}].durationMillis must be an integer`);
  }
  if (
    typeof avgSpeedMetersPerSecond !== "number" ||
    !Number.isFinite(avgSpeedMetersPerSecond)
  ) {
    throw new ValidationError(
      `segments[${index}].avgSpeedMetersPerSecond must be a finite number`
    );
  }
  if (
    typeof maxSpeedMetersPerSecond !== "number" ||
    !Number.isFinite(maxSpeedMetersPerSecond)
  ) {
    throw new ValidationError(
      `segments[${index}].maxSpeedMetersPerSecond must be a finite number`
    );
  }
  if (!Number.isSafeInteger(analysisVersion)) {
    throw new ValidationError(`segments[${index}].analysisVersion must be an integer`);
  }
  if (!Array.isArray(stayClusters)) {
    throw new ValidationError(`segments[${index}].stayClusters must be an array`);
  }

  return {
    segmentId: segmentId as number,
    startPointId: startPointId as number,
    endPointId: endPointId as number,
    startTimestamp: startTimestamp as number,
    endTimestamp: endTimestamp as number,
    segmentType: parseRequiredString(
      segmentType,
      `segments[${index}].segmentType`,
      MAX_SEGMENT_TYPE_LENGTH
    ),
    confidence,
    distanceMeters,
    durationMillis: durationMillis as number,
    avgSpeedMetersPerSecond,
    maxSpeedMetersPerSecond,
    analysisVersion: analysisVersion as number,
    stayClusters: stayClusters.map((stayCluster, stayIndex) =>
      parseAnalysisStayCluster(stayCluster, index, stayIndex)
    )
  };
}

export function validateAnalysisBatchRequest(
  payload: unknown
): ValidatedAnalysisBatchRequest {
  if (!isObject(payload)) {
    throw new ValidationError("Request body must be a JSON object");
  }

  if (!Array.isArray(payload.segments)) {
    throw new ValidationError("`segments` must be an array");
  }

  return {
    deviceId: parseRequiredString(
      payload.deviceId,
      "`deviceId`",
      MAX_DEVICE_ID_LENGTH
    ),
    appVersion: parseRequiredString(
      payload.appVersion,
      "`appVersion`",
      MAX_APP_VERSION_LENGTH
    ),
    segments: payload.segments.map((segment, index) =>
      parseAnalysisSegment(segment, index)
    )
  };
}

function parseHistoryPoint(point: unknown, historyIndex: number, pointIndex: number): HistoryPoint {
  if (!isObject(point)) {
    throw new ValidationError(`histories[${historyIndex}].points[${pointIndex}] must be an object`);
  }

  const {
    latitude,
    longitude,
    timestampMillis,
    accuracyMeters,
    altitudeMeters,
    wgs84Latitude,
    wgs84Longitude
  } = point;

  if (typeof latitude !== "number" || !Number.isFinite(latitude)) {
    throw new ValidationError(
      `histories[${historyIndex}].points[${pointIndex}].latitude must be a finite number`
    );
  }
  if (typeof longitude !== "number" || !Number.isFinite(longitude)) {
    throw new ValidationError(
      `histories[${historyIndex}].points[${pointIndex}].longitude must be a finite number`
    );
  }
  if (!Number.isSafeInteger(timestampMillis)) {
    throw new ValidationError(
      `histories[${historyIndex}].points[${pointIndex}].timestampMillis must be an integer`
    );
  }

  return {
    latitude,
    longitude,
    timestampMillis: timestampMillis as number,
    accuracyMeters: parseOptionalFiniteNumber(
      accuracyMeters,
      `histories[${historyIndex}].points[${pointIndex}].accuracyMeters`
    ),
    altitudeMeters: parseOptionalFiniteNumber(
      altitudeMeters,
      `histories[${historyIndex}].points[${pointIndex}].altitudeMeters`
    ),
    wgs84Latitude: parseOptionalFiniteNumber(
      wgs84Latitude,
      `histories[${historyIndex}].points[${pointIndex}].wgs84Latitude`
    ),
    wgs84Longitude: parseOptionalFiniteNumber(
      wgs84Longitude,
      `histories[${historyIndex}].points[${pointIndex}].wgs84Longitude`
    )
  };
}

function parseHistory(history: unknown, index: number): HistoryRecord {
  if (!isObject(history)) {
    throw new ValidationError(`histories[${index}] must be an object`);
  }

  const {
    historyId,
    timestampMillis,
    distanceKm,
    durationSeconds,
    averageSpeedKmh,
    title,
    startSource,
    stopSource,
    manualStartAt,
    manualStopAt,
    points
  } = history;

  if (!Number.isSafeInteger(historyId)) {
    throw new ValidationError(`histories[${index}].historyId must be an integer`);
  }
  if (!Number.isSafeInteger(timestampMillis)) {
    throw new ValidationError(`histories[${index}].timestampMillis must be an integer`);
  }
  if (typeof distanceKm !== "number" || !Number.isFinite(distanceKm)) {
    throw new ValidationError(`histories[${index}].distanceKm must be a finite number`);
  }
  if (!Number.isSafeInteger(durationSeconds)) {
    throw new ValidationError(`histories[${index}].durationSeconds must be an integer`);
  }
  if (typeof averageSpeedKmh !== "number" || !Number.isFinite(averageSpeedKmh)) {
    throw new ValidationError(`histories[${index}].averageSpeedKmh must be a finite number`);
  }
  if (!Array.isArray(points)) {
    throw new ValidationError(`histories[${index}].points must be an array`);
  }

  return {
    historyId: historyId as number,
    timestampMillis: timestampMillis as number,
    distanceKm,
    durationSeconds: durationSeconds as number,
    averageSpeedKmh,
    title: parseOptionalString(title, `histories[${index}].title`, MAX_TITLE_LENGTH),
    startSource: parseOptionalString(
      startSource,
      `histories[${index}].startSource`,
      MAX_SOURCE_LENGTH
    ),
    stopSource: parseOptionalString(
      stopSource,
      `histories[${index}].stopSource`,
      MAX_SOURCE_LENGTH
    ),
    manualStartAt: parseOptionalInteger(
      manualStartAt,
      `histories[${index}].manualStartAt`
    ),
    manualStopAt: parseOptionalInteger(
      manualStopAt,
      `histories[${index}].manualStopAt`
    ),
    points: points.map((point, pointIndex) => parseHistoryPoint(point, index, pointIndex))
  };
}

export function validateHistoryBatchRequest(
  payload: unknown
): ValidatedHistoryBatchRequest {
  if (!isObject(payload)) {
    throw new ValidationError("Request body must be a JSON object");
  }

  if (!Array.isArray(payload.histories)) {
    throw new ValidationError("`histories` must be an array");
  }

  return {
    deviceId: parseRequiredString(
      payload.deviceId,
      "`deviceId`",
      MAX_DEVICE_ID_LENGTH
    ),
    appVersion: parseRequiredString(
      payload.appVersion,
      "`appVersion`",
      MAX_APP_VERSION_LENGTH
    ),
    histories: payload.histories.map((history, index) => parseHistory(history, index))
  };
}
