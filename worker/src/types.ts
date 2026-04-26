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
  pointCount: number;
  maxPointId: number;
}

export interface AnalysisStayCluster {
  stayId: number;
  centerLat: number;
  centerLng: number;
  radiusMeters: number;
  arrivalTime: number;
  departureTime: number;
  confidence: number;
  analysisVersion: number;
}

export interface AnalysisSegment {
  segmentId: number;
  startPointId: number;
  endPointId: number;
  startTimestamp: number;
  endTimestamp: number;
  segmentType: string;
  confidence: number;
  distanceMeters: number;
  durationMillis: number;
  avgSpeedMetersPerSecond: number;
  maxSpeedMetersPerSecond: number;
  analysisVersion: number;
  stayClusters: AnalysisStayCluster[];
}

export interface HistoryPoint {
  latitude: number;
  longitude: number;
  timestampMillis: number;
  accuracyMeters: number | null;
  altitudeMeters: number | null;
  wgs84Latitude: number | null;
  wgs84Longitude: number | null;
}

export interface HistoryRecord {
  historyId: number;
  timestampMillis: number;
  distanceKm: number;
  durationSeconds: number;
  averageSpeedKmh: number;
  title: string | null;
  startSource: string | null;
  stopSource: string | null;
  manualStartAt: number | null;
  manualStopAt: number | null;
  points: HistoryPoint[];
}

export interface HistoryDaySummary {
  dayStartMillis: number;
  latestTimestamp: number;
  sessionCount: number;
  totalDistanceKm: number;
  totalDurationSeconds: number;
  averageSpeedKmh: number;
  sourceIds: number[];
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
  points: RawLocationPoint[];
}

export interface ValidatedAnalysisBatchRequest {
  deviceId: string;
  appVersion: string;
  segments: AnalysisSegment[];
}

export interface ValidatedHistoryBatchRequest {
  deviceId: string;
  appVersion: string;
  utcOffsetMinutes: number;
  histories: HistoryRecord[];
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

export interface PersistAnalysisResult {
  insertedCount: number;
  dedupedCount: number;
  acceptedMaxSegmentId: number;
}

export interface PersistHistoriesResult {
  insertedCount: number;
  dedupedCount: number;
  acceptedHistoryIds: number[];
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

export interface AnalysisPersistence {
  persistAnalysis(
    deviceId: string,
    appVersion: string,
    segments: AnalysisSegment[],
    env: Env
  ): Promise<PersistAnalysisResult>;
}

export interface HistoryPersistence {
  persistHistories(
    deviceId: string,
    appVersion: string,
    histories: HistoryRecord[],
    env: Env
  ): Promise<PersistHistoriesResult>;

  readHistories(deviceId: string, env: Env): Promise<HistoryRecord[]>;

  readHistoriesByDay(
    deviceId: string,
    dayStartMillis: number,
    env: Env
  ): Promise<HistoryRecord[]>;
}

export interface ProcessedHistoryPersistence {
  persistHistories(
    deviceId: string,
    appVersion: string,
    utcOffsetMinutes: number,
    histories: HistoryRecord[],
    env: Env
  ): Promise<PersistHistoriesResult>;

  readHistories(deviceId: string, env: Env): Promise<HistoryRecord[]>;

  readHistoriesByDay(
    deviceId: string,
    dayStartMillis: number,
    env: Env
  ): Promise<HistoryRecord[]>;

  deleteHistoriesByDay(
    deviceId: string,
    dayStartMillis: number,
    env: Env
  ): Promise<number>;
}

export interface HistoryDaySummaryPersistence {
  readDays(
    deviceId: string,
    utcOffsetMinutes: number,
    env: Env
  ): Promise<HistoryDaySummary[]>;
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

export interface AnalysisSuccessResponseBody extends PersistAnalysisResult {
  ok: true;
}

export interface HistorySuccessResponseBody extends PersistHistoriesResult {
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

export interface HistoryReadSuccessResponseBody {
  ok: true;
  histories: HistoryRecord[];
}

export interface HistoryDaySummaryReadSuccessResponseBody {
  ok: true;
  days: HistoryDaySummary[];
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
