export interface Env {
  UPLOAD_TOKEN: string;
  MAPBOX_PUBLIC_TOKEN: string;
  DB: D1Database;
}

export interface RawLocationPoint {
  pointId: number;
  timestampMillis: number;
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  altitudeMeters: number | null;
  speedMetersPerSecond: number | null;
  bearingDegrees: number | null;
  provider: string;
  sourceType: string;
  isMock: boolean;
  wifiFingerprintDigest: string | null;
  activityType: string | null;
  activityConfidence: number | null;
  samplingTier: string;
}

export interface RawPointDaySummary {
  dayStartMillis: number;
  firstPointAt: number;
  lastPointAt: number;
  pointCount: number;
  maxPointId: number;
  totalDistanceKm: number;
  totalDurationSeconds: number;
  averageSpeedKmh: number;
}

export interface TodaySessionRecord {
  sessionId: string;
  dayStartMillis: number;
  status: string;
  startedAt: number;
  lastPointAt: number | null;
  endedAt: number | null;
  phase: string;
  updatedAt: number;
}

export interface TodaySessionPoint {
  sessionId: string;
  pointId: number;
  dayStartMillis: number;
  timestampMillis: number;
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  altitudeMeters: number | null;
  speedMetersPerSecond: number | null;
  provider: string;
  samplingTier: string;
  updatedAt: number;
}


export type DiagnosticLogType = "ERROR" | "PERF_WARN";

export type DiagnosticLogSeverity = "ERROR" | "WARN";

export type DiagnosticLogStatus = "open" | "resolved";

export interface DiagnosticLog {
  logId: string;
  deviceId: string;
  appVersion: string;
  occurredAt: number;
  type: DiagnosticLogType;
  severity: DiagnosticLogSeverity;
  source: string;
  message: string;
  fingerprint: string;
  payload: unknown | null;
  status: DiagnosticLogStatus;
  occurrenceCount: number;
  firstSeenAt: number;
  lastSeenAt: number;
}

export interface ValidatedRawPointBatchRequest {
  deviceId: string;
  appVersion: string;
  utcOffsetMinutes: number;
  points: RawLocationPoint[];
}

export interface ValidatedTodaySessionBatchRequest {
  deviceId: string;
  appVersion: string;
  sessions: TodaySessionRecord[];
}

export interface ValidatedTodaySessionPointBatchRequest {
  deviceId: string;
  appVersion: string;
  points: TodaySessionPoint[];
}

export interface ValidatedDiagnosticLogBatchRequest {
  deviceId: string;
  appVersion: string;
  logs: DiagnosticLog[];
}

export interface ValidatedDiagnosticLogResolveRequest {
  deviceId: string;
  fingerprints: string[];
}


export interface PersistRawPointsResult {
  insertedCount: number;
  dedupedCount: number;
  acceptedMaxPointId: number;
}

export interface PersistTodaySessionsResult {
  insertedCount: number;
  dedupedCount: number;
}

export interface PersistDiagnosticLogsResult {
  insertedCount: number;
  dedupedCount: number;
}


export interface RawPointPersistence {
  persistRawPoints(
    deviceId: string,
    appVersion: string,
    utcOffsetMinutes: number,
    points: RawLocationPoint[],
    env: Env
  ): Promise<PersistRawPointsResult>;

  readRawPointsByDay(
    deviceId: string,
    dayStartMillis: number,
    env: Env
  ): Promise<RawLocationPoint[]>;

  readRawPointDays(
    deviceId: string,
    utcOffsetMinutes: number,
    env: Env
  ): Promise<RawPointDaySummary[]>;
}

export interface TodaySessionPersistence {
  persistSessions(
    deviceId: string,
    appVersion: string,
    sessions: TodaySessionRecord[],
    env: Env
  ): Promise<PersistTodaySessionsResult>;

  persistSessionPoints(
    deviceId: string,
    appVersion: string,
    points: TodaySessionPoint[],
    env: Env
  ): Promise<PersistTodaySessionsResult>;

  readLatestOpenSession(
    deviceId: string,
    env: Env
  ): Promise<{
    session: TodaySessionRecord;
    points: TodaySessionPoint[];
  } | null>;
}


export interface RawPointSuccessResponseBody extends PersistRawPointsResult {
  ok: true;
}

export interface DiagnosticLogSuccessResponseBody extends PersistDiagnosticLogsResult {
  ok: true;
}

export interface DiagnosticLogPersistence {
  persistLogs(
    deviceId: string,
    appVersion: string,
    logs: DiagnosticLog[],
    env: Env
  ): Promise<PersistDiagnosticLogsResult>;

  readLogs(
    deviceId: string,
    filters: { status?: string; type?: string },
    env: Env
  ): Promise<DiagnosticLog[]>;

  resolveLogs(
    deviceId: string,
    fingerprints: string[],
    env: Env
  ): Promise<number>;

  deleteResolvedBefore(beforeMillis: number, env: Env): Promise<number>;
}

export interface TodaySessionSuccessResponseBody extends PersistTodaySessionsResult {
  ok: true;
}


export interface DiagnosticLogReadSuccessResponseBody {
  ok: true;
  logs: DiagnosticLog[];
}

export interface DiagnosticLogResolveSuccessResponseBody {
  ok: true;
  resolvedCount: number;
}

export interface DiagnosticLogCleanupSuccessResponseBody {
  ok: true;
  deletedCount: number;
}

export interface TodaySessionOpenReadSuccessResponseBody {
  ok: true;
  session: TodaySessionRecord | null;
  points: TodaySessionPoint[];
}

export interface RawPointReadSuccessResponseBody {
  ok: true;
  points: RawLocationPoint[];
}

export interface RawPointDayReadSuccessResponseBody {
  ok: true;
  days: RawPointDaySummary[];
}

export interface AppConfigSuccessResponseBody {
  ok: true;
  mapboxPublicToken: string;
}

export interface ErrorResponseBody {
  ok: false;
  message: string;
}
