import type {
  HistoryPoint,
  HistoryRecord,
  TrainingSample,
  ValidatedBatchRequest,
  ValidatedHistoryBatchRequest
} from "./types";

const MAX_PHASE_LENGTH = 64;
const MAX_DEVICE_ID_LENGTH = 128;
const MAX_APP_VERSION_LENGTH = 64;
const MAX_TITLE_LENGTH = 255;
const MAX_SOURCE_LENGTH = 64;

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

function parseSample(sample: unknown, index: number): TrainingSample {
  if (!isObject(sample)) {
    throw new ValidationError(`samples[${index}] must be an object`);
  }

  const { eventId, timestampMillis, phase, finalDecision, features } = sample;

  if (!Number.isSafeInteger(eventId)) {
    throw new ValidationError(`samples[${index}].eventId must be an integer`);
  }

  if (!Number.isSafeInteger(timestampMillis)) {
    throw new ValidationError(
      `samples[${index}].timestampMillis must be an integer`
    );
  }

  if (typeof phase !== "string" || phase.trim().length === 0) {
    throw new ValidationError(`samples[${index}].phase must be a non-empty string`);
  }
  if (phase.trim().length > MAX_PHASE_LENGTH) {
    throw new ValidationError(
      `samples[${index}].phase length must be <= ${MAX_PHASE_LENGTH}`
    );
  }

  if (!("finalDecision" in sample)) {
    throw new ValidationError(`samples[${index}].finalDecision is required`);
  }
  if (finalDecision === undefined) {
    throw new ValidationError(`samples[${index}].finalDecision is required`);
  }

  if (!isObject(features)) {
    throw new ValidationError(`samples[${index}].features must be an object`);
  }

  return {
    eventId: eventId as number,
    timestampMillis: timestampMillis as number,
    phase: phase.trim(),
    finalDecision,
    features
  };
}

export function validateBatchRequest(payload: unknown): ValidatedBatchRequest {
  if (!isObject(payload)) {
    throw new ValidationError("Request body must be a JSON object");
  }

  if (!Array.isArray(payload.samples)) {
    throw new ValidationError("`samples` must be an array");
  }

  return {
    samples: payload.samples.map((sample, index) => parseSample(sample, index))
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
    timestampMillis,
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
