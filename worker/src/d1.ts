import type {
  DiagnosticLog,
  DiagnosticLogPersistence,
  Env,
  PersistDiagnosticLogsResult,
  PersistRawPointsResult,
  PersistTodaySessionsResult,
  RawLocationPoint,
  RawPointDaySummary,
  RawPointPersistence,
  TodaySessionPersistence,
  TodaySessionPoint,
  TodaySessionRecord
} from "./types";

function dedupeRawPointsById(points: RawLocationPoint[]): RawLocationPoint[] {
  const byPointId = new Map<number, RawLocationPoint>();
  for (const point of points) {
    byPointId.set(point.pointId, point);
  }
  return [...byPointId.values()];
}

function getAcceptedPointIds(points: RawLocationPoint[]): number[] {
  return [...new Set(points.map((point) => point.pointId))];
}

function dedupeTodaySessionsById(sessions: TodaySessionRecord[]): TodaySessionRecord[] {
  const bySessionId = new Map<string, TodaySessionRecord>();
  for (const session of sessions) {
    bySessionId.set(session.sessionId, session);
  }
  return [...bySessionId.values()];
}

function dedupeTodaySessionPointsByKey(points: TodaySessionPoint[]): TodaySessionPoint[] {
  const byKey = new Map<string, TodaySessionPoint>();
  for (const point of points) {
    byKey.set(`${point.sessionId}:${point.pointId}`, point);
  }
  return [...byKey.values()];
}

interface RawLocationPointRow {
  point_id: number;
  timestamp_millis: number;
  latitude: number;
  longitude: number;
  accuracy_meters: number | null;
  altitude_meters: number | null;
  speed_meters_per_second: number | null;
  bearing_degrees: number | null;
  provider: string;
  source_type: string;
  is_mock: number;
  wifi_fingerprint_digest: string | null;
  activity_type: string | null;
  activity_confidence: number | null;
  sampling_tier: string;
}

interface RawPointDaySummaryRow {
  day_start_millis: number;
  first_point_at: number;
  last_point_at: number;
  point_count: number;
  max_point_id: number;
  total_distance_km: number;
  total_duration_seconds: number;
  average_speed_kmh: number;
}

interface TodaySessionRow {
  session_id: string;
  day_start_millis: number;
  status: string;
  started_at: number;
  last_point_at: number | null;
  ended_at: number | null;
  phase: string;
  updated_at: number;
}

interface TodaySessionPointRow {
  session_id: string;
  point_id: number;
  day_start_millis: number;
  timestamp_millis: number;
  latitude: number;
  longitude: number;
  accuracy_meters: number | null;
  altitude_meters: number | null;
  speed_meters_per_second: number | null;
  provider: string;
  sampling_tier: string;
  updated_at: number;
}

interface DiagnosticLogRow {
  log_id: string;
  device_id: string;
  app_version: string;
  occurred_at: number;
  type: string;
  severity: string;
  source: string;
  message: string;
  fingerprint: string;
  payload_json: string | null;
  status: string;
  occurrence_count: number;
  first_seen_at: number;
  last_seen_at: number;
}

export function buildPersistRawPointsResult(
  rawPoints: RawLocationPoint[],
  insertedCount: number
): PersistRawPointsResult {
  const acceptedPointIds = getAcceptedPointIds(rawPoints);
  return {
    insertedCount,
    dedupedCount: rawPoints.length - insertedCount,
    acceptedMaxPointId:
      acceptedPointIds.length === 0 ? 0 : Math.max(...acceptedPointIds)
  };
}

function buildPersistTodaySessionsResult(
  rawCount: number,
  insertedCount: number
): PersistTodaySessionsResult {
  return {
    insertedCount,
    dedupedCount: rawCount - insertedCount
  };
}

function extractChanges(result: D1Result<unknown> | null | undefined): number {
  const changes = result?.meta?.changes;
  return typeof changes === "number" ? changes : 0;
}

async function executeBatch(
  env: Env,
  statements: D1PreparedStatement[]
): Promise<number> {
  if (statements.length === 0) {
    return 0;
  }
  const results = await env.DB.batch(statements);
  return results.reduce((sum, result) => sum + extractChanges(result), 0);
}

const EARTH_RADIUS_METERS = 6_371_000;

interface DistancePoint {
  latitude: number;
  longitude: number;
}

function isValidCoordinate(point: DistancePoint): boolean {
  if (point.latitude < -90 || point.latitude > 90) return false;
  if (point.longitude < -180 || point.longitude > 180) return false;
  if (point.latitude === 0 && point.longitude === 0) return false;
  return true;
}

function distanceMeters(first: DistancePoint, second: DistancePoint): number {
  const lat1 = (first.latitude * Math.PI) / 180;
  const lat2 = (second.latitude * Math.PI) / 180;
  const dLat = lat2 - lat1;
  const dLon = ((second.longitude - first.longitude) * Math.PI) / 180;
  const sinDLat = Math.sin(dLat / 2);
  const sinDLon = Math.sin(dLon / 2);
  const haversine =
    sinDLat * sinDLat +
    Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
  return EARTH_RADIUS_METERS * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, haversine))));
}

function mapRawLocationPointRow(row: RawLocationPointRow): RawLocationPoint {
  return {
    pointId: row.point_id,
    timestampMillis: row.timestamp_millis,
    latitude: row.latitude,
    longitude: row.longitude,
    accuracyMeters: row.accuracy_meters,
    altitudeMeters: row.altitude_meters,
    speedMetersPerSecond: row.speed_meters_per_second,
    bearingDegrees: row.bearing_degrees,
    provider: row.provider,
    sourceType: row.source_type,
    isMock: row.is_mock !== 0,
    wifiFingerprintDigest: row.wifi_fingerprint_digest,
    activityType: row.activity_type,
    activityConfidence: row.activity_confidence,
    samplingTier: row.sampling_tier
  };
}

function mapRawPointDaySummaryRow(row: RawPointDaySummaryRow): RawPointDaySummary {
  return {
    dayStartMillis: row.day_start_millis,
    firstPointAt: row.first_point_at,
    lastPointAt: row.last_point_at,
    pointCount: row.point_count,
    maxPointId: row.max_point_id,
    totalDistanceKm: row.total_distance_km,
    totalDurationSeconds: row.total_duration_seconds,
    averageSpeedKmh: row.average_speed_kmh
  };
}

function mapTodaySessionRow(row: TodaySessionRow): TodaySessionRecord {
  return {
    sessionId: row.session_id,
    dayStartMillis: row.day_start_millis,
    status: row.status,
    startedAt: row.started_at,
    lastPointAt: row.last_point_at,
    endedAt: row.ended_at,
    phase: row.phase,
    updatedAt: row.updated_at
  };
}

function mapTodaySessionPointRow(row: TodaySessionPointRow): TodaySessionPoint {
  return {
    sessionId: row.session_id,
    pointId: row.point_id,
    dayStartMillis: row.day_start_millis,
    timestampMillis: row.timestamp_millis,
    latitude: row.latitude,
    longitude: row.longitude,
    accuracyMeters: row.accuracy_meters,
    altitudeMeters: row.altitude_meters,
    speedMetersPerSecond: row.speed_meters_per_second,
    provider: row.provider,
    samplingTier: row.sampling_tier,
    updatedAt: row.updated_at
  };
}

function startOfDay(timestampMillis: number, utcOffsetMinutes = 0): number {
  const date = new Date(timestampMillis);
  const offsetMillis = utcOffsetMinutes * 60_000;
  return Math.floor((date.getTime() + offsetMillis) / 86_400_000) * 86_400_000 - offsetMillis;
}

function calculateDistanceKm(points: DistancePoint[]): number {
  const validPoints = points.filter(isValidCoordinate);
  let totalMeters = 0;
  for (let index = 0; index < validPoints.length - 1; index += 1) {
    totalMeters += distanceMeters(validPoints[index]!, validPoints[index + 1]!);
  }
  return totalMeters / 1000;
}

async function refreshRawPointDaySummaries(
  deviceId: string,
  utcOffsetMinutes: number,
  affectedDayStarts: number[],
  env: Env
): Promise<void> {
  const uniqueDays = [...new Set(affectedDayStarts)];
  for (const dayStartMillis of uniqueDays) {
    const pointsResult = await env.DB.prepare(
      `SELECT
           point_id,
           timestamp_millis,
           latitude,
           longitude,
           accuracy_meters,
           altitude_meters,
           speed_meters_per_second,
           bearing_degrees,
           provider,
           source_type,
           is_mock,
           wifi_fingerprint_digest,
           activity_type,
           activity_confidence,
           sampling_tier
       FROM raw_location_point
       WHERE device_id = ?
         AND timestamp_millis >= ?
         AND timestamp_millis < ?
       ORDER BY timestamp_millis ASC, point_id ASC`
    )
      .bind(deviceId, dayStartMillis, dayStartMillis + 86_400_000)
      .all<RawLocationPointRow>();
    const rows = pointsResult.results ?? [];

    if (rows.length === 0) {
      await env.DB.prepare(
        `DELETE FROM raw_point_day_summary
         WHERE device_id = ?
           AND utc_offset_minutes = ?
           AND day_start_millis = ?`
      )
        .bind(deviceId, utcOffsetMinutes, dayStartMillis)
        .all();
      continue;
    }

    const firstPointAt = rows[0]!.timestamp_millis;
    const lastPointAt = rows[rows.length - 1]!.timestamp_millis;
    const pointCount = rows.length;
    const maxPointId = rows.reduce((max, row) => Math.max(max, row.point_id), 0);
    const summaryPoints = rows.map((row) => ({
      latitude: row.latitude,
      longitude: row.longitude
    }));
    const totalDistanceKm = calculateDistanceKm(summaryPoints);
    const totalDurationSeconds = Math.max(0, Math.floor((lastPointAt - firstPointAt) / 1000));
    const averageSpeedKmh =
      totalDurationSeconds > 0 ? totalDistanceKm / (totalDurationSeconds / 3600) : 0;

    await env.DB.prepare(
      `INSERT INTO raw_point_day_summary
         (
           device_id,
           utc_offset_minutes,
           day_start_millis,
           first_point_at,
           last_point_at,
           point_count,
           max_point_id,
           total_distance_km,
           total_duration_seconds,
           average_speed_kmh
         )
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(device_id, utc_offset_minutes, day_start_millis) DO UPDATE SET
         first_point_at = excluded.first_point_at,
         last_point_at = excluded.last_point_at,
         point_count = excluded.point_count,
         max_point_id = excluded.max_point_id,
         total_distance_km = excluded.total_distance_km,
         total_duration_seconds = excluded.total_duration_seconds,
         average_speed_kmh = excluded.average_speed_kmh,
         updated_at = CURRENT_TIMESTAMP`
    )
      .bind(
        deviceId,
        utcOffsetMinutes,
        dayStartMillis,
        firstPointAt,
        lastPointAt,
        pointCount,
        maxPointId,
        totalDistanceKm,
        totalDurationSeconds,
        averageSpeedKmh
      )
      .all();
  }
}

export function createD1RawPointPersistence(): RawPointPersistence {
  return {
    async persistRawPoints(
      deviceId: string,
      appVersion: string,
      utcOffsetMinutes: number,
      points: RawLocationPoint[],
      env: Env
    ): Promise<PersistRawPointsResult> {
      const uniquePoints = dedupeRawPointsById(points);
      if (uniquePoints.length === 0) {
        return buildPersistRawPointsResult(points, 0);
      }

      const statements = uniquePoints.map((point) =>
        env.DB.prepare(
          `INSERT OR IGNORE INTO raw_location_point
             (
               device_id,
               point_id,
               app_version,
               timestamp_millis,
               latitude,
               longitude,
               accuracy_meters,
               altitude_meters,
               speed_meters_per_second,
               bearing_degrees,
               provider,
               source_type,
               is_mock,
               wifi_fingerprint_digest,
               activity_type,
               activity_confidence,
               sampling_tier
             )
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        ).bind(
          deviceId,
          point.pointId,
          appVersion,
          point.timestampMillis,
          point.latitude,
          point.longitude,
          point.accuracyMeters,
          point.altitudeMeters,
          point.speedMetersPerSecond,
          point.bearingDegrees,
          point.provider,
          point.sourceType,
          point.isMock ? 1 : 0,
          point.wifiFingerprintDigest,
          point.activityType,
          point.activityConfidence,
          point.samplingTier
        )
      );

      const insertedCount = await executeBatch(env, statements);
      const affectedDayStarts = uniquePoints.map((point) =>
        startOfDay(point.timestampMillis, utcOffsetMinutes)
      );
      await refreshRawPointDaySummaries(
        deviceId,
        utcOffsetMinutes,
        affectedDayStarts,
        env
      );
      return buildPersistRawPointsResult(points, insertedCount);
    },

    async readRawPointsByDay(
      deviceId: string,
      dayStartMillis: number,
      env: Env
    ): Promise<RawLocationPoint[]> {
      const result = await env.DB.prepare(
        `SELECT
           point_id,
           timestamp_millis,
           latitude,
           longitude,
           accuracy_meters,
           altitude_meters,
           speed_meters_per_second,
           bearing_degrees,
           provider,
           source_type,
           is_mock,
           wifi_fingerprint_digest,
           activity_type,
           activity_confidence,
           sampling_tier
         FROM raw_location_point
         WHERE device_id = ?
           AND timestamp_millis >= ?
           AND timestamp_millis < ?
         ORDER BY point_id ASC`
      )
        .bind(deviceId, dayStartMillis, dayStartMillis + 86_400_000)
        .all<RawLocationPointRow>();

      return (result.results ?? []).map(mapRawLocationPointRow);
    },

    async readRawPointDays(
      deviceId: string,
      utcOffsetMinutes: number,
      env: Env
    ): Promise<RawPointDaySummary[]> {
      const result = await env.DB.prepare(
        `SELECT
           day_start_millis,
           first_point_at,
           last_point_at,
           point_count,
           max_point_id,
           total_distance_km,
           total_duration_seconds,
           average_speed_kmh
         FROM raw_point_day_summary
         WHERE device_id = ?
           AND utc_offset_minutes = ?
         ORDER BY day_start_millis DESC`
      )
        .bind(deviceId, utcOffsetMinutes)
        .all<RawPointDaySummaryRow>();

      return (result.results ?? []).map(mapRawPointDaySummaryRow);
    }
  };
}

export function createD1TodaySessionPersistence(): TodaySessionPersistence {
  return {
    async persistSessions(
      deviceId: string,
      _appVersion: string,
      sessions: TodaySessionRecord[],
      env: Env
    ): Promise<PersistTodaySessionsResult> {
      const uniqueSessions = dedupeTodaySessionsById(sessions);
      if (uniqueSessions.length === 0) {
        return buildPersistTodaySessionsResult(sessions.length, 0);
      }

      const statements = uniqueSessions.map((session) =>
        env.DB.prepare(
          `INSERT INTO today_session
             (
               device_id,
               session_id,
               day_start_millis,
               status,
               started_at,
               last_point_at,
               ended_at,
               phase,
               updated_at
             )
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(device_id, session_id) DO UPDATE SET
             day_start_millis = excluded.day_start_millis,
             status = excluded.status,
             started_at = excluded.started_at,
             last_point_at = excluded.last_point_at,
             ended_at = excluded.ended_at,
             phase = excluded.phase,
             updated_at = excluded.updated_at`
        ).bind(
          deviceId,
          session.sessionId,
          session.dayStartMillis,
          session.status,
          session.startedAt,
          session.lastPointAt,
          session.endedAt,
          session.phase,
          session.updatedAt
        )
      );

      const insertedCount = await executeBatch(env, statements);
      return buildPersistTodaySessionsResult(sessions.length, insertedCount);
    },

    async persistSessionPoints(
      deviceId: string,
      _appVersion: string,
      points: TodaySessionPoint[],
      env: Env
    ): Promise<PersistTodaySessionsResult> {
      const uniquePoints = dedupeTodaySessionPointsByKey(points);
      if (uniquePoints.length === 0) {
        return buildPersistTodaySessionsResult(points.length, 0);
      }

      const statements = uniquePoints.map((point) =>
        env.DB.prepare(
          `INSERT OR IGNORE INTO today_session_point
             (
               device_id,
               session_id,
               point_id,
               day_start_millis,
               timestamp_millis,
               latitude,
               longitude,
               accuracy_meters,
               altitude_meters,
               speed_meters_per_second,
               provider,
               sampling_tier,
               updated_at
             )
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        ).bind(
          deviceId,
          point.sessionId,
          point.pointId,
          point.dayStartMillis,
          point.timestampMillis,
          point.latitude,
          point.longitude,
          point.accuracyMeters,
          point.altitudeMeters,
          point.speedMetersPerSecond,
          point.provider,
          point.samplingTier,
          point.updatedAt
        )
      );

      const insertedCount = await executeBatch(env, statements);
      return buildPersistTodaySessionsResult(points.length, insertedCount);
    },

    async readLatestOpenSession(
      deviceId: string,
      env: Env
    ): Promise<{ session: TodaySessionRecord; points: TodaySessionPoint[] } | null> {
      const sessionResult = await env.DB.prepare(
        `SELECT
           session_id,
           day_start_millis,
           status,
           started_at,
           last_point_at,
           ended_at,
           phase,
           updated_at
         FROM today_session
         WHERE device_id = ?
           AND status != 'COMPLETED'
         ORDER BY updated_at DESC, started_at DESC
         LIMIT 1`
      )
        .bind(deviceId)
        .all<TodaySessionRow>();

      const sessionRow = sessionResult.results?.[0];
      if (!sessionRow) {
        return null;
      }

      const pointResult = await env.DB.prepare(
        `SELECT
           session_id,
           point_id,
           day_start_millis,
           timestamp_millis,
           latitude,
           longitude,
           accuracy_meters,
           altitude_meters,
           speed_meters_per_second,
           provider,
           sampling_tier,
           updated_at
         FROM today_session_point
         WHERE device_id = ?
           AND session_id = ?
         ORDER BY timestamp_millis ASC, point_id ASC`
      )
        .bind(deviceId, sessionRow.session_id)
        .all<TodaySessionPointRow>();

      return {
        session: mapTodaySessionRow(sessionRow),
        points: (pointResult.results ?? []).map(mapTodaySessionPointRow)
      };
    }
  };
}

function parseDiagnosticPayload(payloadJson: string | null): unknown | null {
  if (payloadJson === null || payloadJson.trim().length === 0) {
    return null;
  }
  try {
    return JSON.parse(payloadJson);
  } catch {
    return null;
  }
}

function mapDiagnosticLogRow(row: DiagnosticLogRow): DiagnosticLog {
  return {
    logId: row.log_id,
    deviceId: row.device_id,
    appVersion: row.app_version,
    occurredAt: row.occurred_at,
    type: row.type === "PERF_WARN" ? "PERF_WARN" : "ERROR",
    severity: row.severity === "WARN" ? "WARN" : "ERROR",
    source: row.source,
    message: row.message,
    fingerprint: row.fingerprint,
    payload: parseDiagnosticPayload(row.payload_json),
    status: row.status === "resolved" ? "resolved" : "open",
    occurrenceCount: row.occurrence_count,
    firstSeenAt: row.first_seen_at,
    lastSeenAt: row.last_seen_at
  };
}

export function createD1DiagnosticLogPersistence(): DiagnosticLogPersistence {
  return {
    async persistLogs(
      deviceId: string,
      appVersion: string,
      logs: DiagnosticLog[],
      env: Env
    ): Promise<PersistDiagnosticLogsResult> {
      const statements = logs.map((log) =>
        env.DB.prepare(
          `INSERT INTO diagnostic_log
             (
               device_id,
               log_id,
               app_version,
               occurred_at,
               type,
               severity,
               source,
               message,
               fingerprint,
               payload_json,
               status,
               occurrence_count,
               first_seen_at,
               last_seen_at
             )
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'open', 1, ?, ?)
           ON CONFLICT(device_id, fingerprint) DO UPDATE SET
             log_id = excluded.log_id,
             app_version = excluded.app_version,
             occurred_at = excluded.occurred_at,
             type = excluded.type,
             severity = excluded.severity,
             source = excluded.source,
             message = excluded.message,
             payload_json = excluded.payload_json,
             status = 'open',
             occurrence_count = diagnostic_log.occurrence_count + 1,
             last_seen_at = excluded.last_seen_at,
             updated_at = CURRENT_TIMESTAMP`
        ).bind(
          deviceId,
          log.logId,
          appVersion,
          log.occurredAt,
          log.type,
          log.severity,
          log.source,
          log.message,
          log.fingerprint,
          log.payload === null ? null : JSON.stringify(log.payload),
          log.occurredAt,
          log.occurredAt
        )
      );
      const affectedCount = await executeBatch(env, statements);
      return {
        insertedCount: Math.min(affectedCount, logs.length),
        dedupedCount: Math.max(0, logs.length - Math.min(affectedCount, logs.length))
      };
    },

    async readLogs(
      deviceId: string,
      filters: { status?: string; type?: string },
      env: Env
    ): Promise<DiagnosticLog[]> {
      const status = filters.status ?? "open";
      const type = filters.type ?? null;
      const result = type === null
        ? await env.DB.prepare(
            `SELECT log_id, device_id, app_version, occurred_at, type, severity,
                    source, message, fingerprint, payload_json, status,
                    occurrence_count, first_seen_at, last_seen_at
             FROM diagnostic_log
             WHERE device_id = ? AND status = ?
             ORDER BY last_seen_at DESC
             LIMIT 200`
          ).bind(deviceId, status).all<DiagnosticLogRow>()
        : await env.DB.prepare(
            `SELECT log_id, device_id, app_version, occurred_at, type, severity,
                    source, message, fingerprint, payload_json, status,
                    occurrence_count, first_seen_at, last_seen_at
             FROM diagnostic_log
             WHERE device_id = ? AND status = ? AND type = ?
             ORDER BY last_seen_at DESC
             LIMIT 200`
          ).bind(deviceId, status, type).all<DiagnosticLogRow>();
      return (result.results ?? []).map(mapDiagnosticLogRow);
    },

    async resolveLogs(
      deviceId: string,
      fingerprints: string[],
      env: Env
    ): Promise<number> {
      const statements = fingerprints.map((fingerprint) =>
        env.DB.prepare(
          `UPDATE diagnostic_log
           SET status = 'resolved', resolved_at = ?, updated_at = CURRENT_TIMESTAMP
           WHERE device_id = ? AND fingerprint = ? AND status != 'resolved'`
        ).bind(Date.now(), deviceId, fingerprint)
      );
      return executeBatch(env, statements);
    },

    async deleteResolvedBefore(beforeMillis: number, env: Env): Promise<number> {
      const result = await env.DB.prepare(
        `DELETE FROM diagnostic_log
         WHERE status = 'resolved' AND COALESCE(resolved_at, last_seen_at) < ?`
      ).bind(beforeMillis).all();
      return extractChanges(result);
    }
  };
}
