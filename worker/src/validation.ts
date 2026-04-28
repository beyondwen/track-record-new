import type {
  DiagnosticLog,
  RawLocationPoint,
  TodaySessionPoint,
  TodaySessionRecord,
  ValidatedDiagnosticLogBatchRequest,
  ValidatedDiagnosticLogResolveRequest,
  ValidatedRawPointBatchRequest,
  ValidatedTodaySessionBatchRequest,
  ValidatedTodaySessionPointBatchRequest
} from "./types";

const MAX_DEVICE_ID_LENGTH = 128;
const MAX_APP_VERSION_LENGTH = 64;
const MAX_PROVIDER_LENGTH = 64;
const MAX_SOURCE_TYPE_LENGTH = 64;
const MAX_ACTIVITY_TYPE_LENGTH = 64;
const MAX_SAMPLING_TIER_LENGTH = 32;
const MAX_DIAGNOSTIC_LOG_ID_LENGTH = 128;
const MAX_DIAGNOSTIC_TYPE_LENGTH = 32;
const MAX_DIAGNOSTIC_SEVERITY_LENGTH = 16;
const MAX_DIAGNOSTIC_SOURCE_LENGTH = 128;
const MAX_DIAGNOSTIC_MESSAGE_LENGTH = 1024;
const MAX_DIAGNOSTIC_FINGERPRINT_LENGTH = 160;
const MAX_DIAGNOSTIC_LOGS_PER_BATCH = 50;
const MAX_SESSION_STATUS_LENGTH = 32;

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

function parseRequiredInteger(value: unknown, label: string): number {
  if (!Number.isSafeInteger(value)) {
    throw new ValidationError(`${label} must be an integer`);
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
    utcOffsetMinutes: parseOptionalInteger(
      payload.utcOffsetMinutes,
      "`utcOffsetMinutes`"
    ) ?? 0,
    points: payload.points.map((point, index) => parseRawPoint(point, index))
  };
}

function parseTodaySession(session: unknown, index: number): TodaySessionRecord {
  if (!isObject(session)) {
    throw new ValidationError(`sessions[${index}] must be an object`);
  }

  return {
    sessionId: parseRequiredString(session.sessionId, `sessions[${index}].sessionId`, 128),
    dayStartMillis: parseRequiredInteger(session.dayStartMillis, `sessions[${index}].dayStartMillis`),
    status: parseRequiredString(session.status, `sessions[${index}].status`, MAX_SESSION_STATUS_LENGTH),
    startedAt: parseRequiredInteger(session.startedAt, `sessions[${index}].startedAt`),
    lastPointAt: parseOptionalInteger(session.lastPointAt, `sessions[${index}].lastPointAt`),
    endedAt: parseOptionalInteger(session.endedAt, `sessions[${index}].endedAt`),
    phase: parseRequiredString(session.phase, `sessions[${index}].phase`, MAX_SESSION_STATUS_LENGTH),
    updatedAt: parseRequiredInteger(session.updatedAt, `sessions[${index}].updatedAt`)
  };
}

function parseTodaySessionPoint(point: unknown, index: number): TodaySessionPoint {
  if (!isObject(point)) {
    throw new ValidationError(`points[${index}] must be an object`);
  }

  const latitude = point.latitude;
  const longitude = point.longitude;
  if (typeof latitude !== "number" || !Number.isFinite(latitude)) {
    throw new ValidationError(`points[${index}].latitude must be a finite number`);
  }
  if (typeof longitude !== "number" || !Number.isFinite(longitude)) {
    throw new ValidationError(`points[${index}].longitude must be a finite number`);
  }

  return {
    sessionId: parseRequiredString(point.sessionId, `points[${index}].sessionId`, 128),
    pointId: parseRequiredInteger(point.pointId, `points[${index}].pointId`),
    dayStartMillis: parseRequiredInteger(point.dayStartMillis, `points[${index}].dayStartMillis`),
    timestampMillis: parseRequiredInteger(point.timestampMillis, `points[${index}].timestampMillis`),
    latitude,
    longitude,
    accuracyMeters: parseOptionalFiniteNumber(point.accuracyMeters, `points[${index}].accuracyMeters`),
    altitudeMeters: parseOptionalFiniteNumber(point.altitudeMeters, `points[${index}].altitudeMeters`),
    speedMetersPerSecond: parseOptionalFiniteNumber(point.speedMetersPerSecond, `points[${index}].speedMetersPerSecond`),
    provider: parseRequiredString(point.provider, `points[${index}].provider`, MAX_PROVIDER_LENGTH),
    samplingTier: parseRequiredString(point.samplingTier, `points[${index}].samplingTier`, MAX_SAMPLING_TIER_LENGTH),
    updatedAt: parseRequiredInteger(point.updatedAt, `points[${index}].updatedAt`)
  };
}

export function validateTodaySessionBatchRequest(
  payload: unknown
): ValidatedTodaySessionBatchRequest {
  if (!isObject(payload)) {
    throw new ValidationError("Request body must be a JSON object");
  }
  if (!Array.isArray(payload.sessions)) {
    throw new ValidationError("`sessions` must be an array");
  }

  return {
    deviceId: parseRequiredString(payload.deviceId, "`deviceId`", MAX_DEVICE_ID_LENGTH),
    appVersion: parseRequiredString(payload.appVersion, "`appVersion`", MAX_APP_VERSION_LENGTH),
    sessions: payload.sessions.map((session, index) => parseTodaySession(session, index))
  };
}

export function validateTodaySessionPointBatchRequest(
  payload: unknown
): ValidatedTodaySessionPointBatchRequest {
  if (!isObject(payload)) {
    throw new ValidationError("Request body must be a JSON object");
  }
  if (!Array.isArray(payload.points)) {
    throw new ValidationError("`points` must be an array");
  }

  return {
    deviceId: parseRequiredString(payload.deviceId, "`deviceId`", MAX_DEVICE_ID_LENGTH),
    appVersion: parseRequiredString(payload.appVersion, "`appVersion`", MAX_APP_VERSION_LENGTH),
    points: payload.points.map((point, index) => parseTodaySessionPoint(point, index))
  };
}

function parseDiagnosticPayload(value: unknown, label: string): unknown | null {
  if (value === undefined || value === null) {
    return null;
  }
  try {
    JSON.stringify(value);
  } catch {
    throw new ValidationError(`${label} must be JSON serializable`);
  }
  return value;
}

function parseDiagnosticLog(
  log: unknown,
  index: number,
  deviceId: string,
  appVersion: string
): DiagnosticLog {
  if (!isObject(log)) {
    throw new ValidationError(`logs[${index}] must be an object`);
  }

  const {
    logId,
    occurredAt,
    type,
    severity,
    source,
    message,
    fingerprint,
    payload
  } = log;

  const normalizedType = parseRequiredString(
    type,
    `logs[${index}].type`,
    MAX_DIAGNOSTIC_TYPE_LENGTH
  );
  if (normalizedType !== "ERROR" && normalizedType !== "PERF_WARN") {
    throw new ValidationError(`logs[${index}].type must be ERROR or PERF_WARN`);
  }

  const normalizedSeverity = parseRequiredString(
    severity,
    `logs[${index}].severity`,
    MAX_DIAGNOSTIC_SEVERITY_LENGTH
  );
  if (normalizedSeverity !== "ERROR" && normalizedSeverity !== "WARN") {
    throw new ValidationError(`logs[${index}].severity must be ERROR or WARN`);
  }

  return {
    logId: parseRequiredString(
      logId,
      `logs[${index}].logId`,
      MAX_DIAGNOSTIC_LOG_ID_LENGTH
    ),
    deviceId,
    appVersion,
    occurredAt: parseRequiredInteger(occurredAt, `logs[${index}].occurredAt`),
    type: normalizedType,
    severity: normalizedSeverity,
    source: parseRequiredString(
      source,
      `logs[${index}].source`,
      MAX_DIAGNOSTIC_SOURCE_LENGTH
    ),
    message: parseRequiredString(
      message,
      `logs[${index}].message`,
      MAX_DIAGNOSTIC_MESSAGE_LENGTH
    ),
    fingerprint: parseRequiredString(
      fingerprint,
      `logs[${index}].fingerprint`,
      MAX_DIAGNOSTIC_FINGERPRINT_LENGTH
    ),
    payload: parseDiagnosticPayload(payload, `logs[${index}].payload`),
    status: "open",
    occurrenceCount: 1,
    firstSeenAt: parseRequiredInteger(occurredAt, `logs[${index}].occurredAt`),
    lastSeenAt: parseRequiredInteger(occurredAt, `logs[${index}].occurredAt`)
  };
}

export function validateDiagnosticLogBatchRequest(
  payload: unknown
): ValidatedDiagnosticLogBatchRequest {
  if (!isObject(payload)) {
    throw new ValidationError("Request body must be a JSON object");
  }
  if (!Array.isArray(payload.logs)) {
    throw new ValidationError("`logs` must be an array");
  }
  if (payload.logs.length > MAX_DIAGNOSTIC_LOGS_PER_BATCH) {
    throw new ValidationError(`logs length must be <= ${MAX_DIAGNOSTIC_LOGS_PER_BATCH}`);
  }

  const deviceId = parseRequiredString(
    payload.deviceId,
    "`deviceId`",
    MAX_DEVICE_ID_LENGTH
  );
  const appVersion = parseRequiredString(
    payload.appVersion,
    "`appVersion`",
    MAX_APP_VERSION_LENGTH
  );

  return {
    deviceId,
    appVersion,
    logs: payload.logs.map((log, index) =>
      parseDiagnosticLog(log, index, deviceId, appVersion)
    )
  };
}

export function validateDiagnosticLogResolveRequest(
  payload: unknown
): ValidatedDiagnosticLogResolveRequest {
  if (!isObject(payload)) {
    throw new ValidationError("Request body must be a JSON object");
  }
  if (!Array.isArray(payload.fingerprints)) {
    throw new ValidationError("`fingerprints` must be an array");
  }

  return {
    deviceId: parseRequiredString(
      payload.deviceId,
      "`deviceId`",
      MAX_DEVICE_ID_LENGTH
    ),
    fingerprints: payload.fingerprints.map((fingerprint, index) =>
      parseRequiredString(
        fingerprint,
        `fingerprints[${index}]`,
        MAX_DIAGNOSTIC_FINGERPRINT_LENGTH
      )
    )
  };
}
