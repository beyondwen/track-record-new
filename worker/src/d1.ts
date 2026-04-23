import type {
  AnalysisPersistence,
  AnalysisSegment,
  Env,
  HistoryDaySummary,
  HistoryDaySummaryPersistence,
  HistoryPersistence,
  HistoryRecord,
  PersistAnalysisResult,
  PersistHistoriesResult,
  PersistRawPointsResult,
  RawLocationPoint,
  RawPointDaySummary,
  RawPointPersistence
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

function dedupeAnalysisSegmentsById(segments: AnalysisSegment[]): AnalysisSegment[] {
  const bySegmentId = new Map<number, AnalysisSegment>();
  for (const segment of segments) {
    bySegmentId.set(segment.segmentId, segment);
  }
  return [...bySegmentId.values()];
}

function getAcceptedSegmentIds(segments: AnalysisSegment[]): number[] {
  return [...new Set(segments.map((segment) => segment.segmentId))];
}

function dedupeHistoriesById(histories: HistoryRecord[]): HistoryRecord[] {
  const byHistoryId = new Map<number, HistoryRecord>();
  for (const history of histories) {
    byHistoryId.set(history.historyId, history);
  }
  return [...byHistoryId.values()];
}

function getAcceptedHistoryIds(histories: HistoryRecord[]): number[] {
  return [...new Set(histories.map((history) => history.historyId))];
}

interface UploadedHistoryRow {
  history_id: number;
  timestamp_millis: number;
  distance_km: number;
  duration_seconds: number;
  average_speed_kmh: number;
  title: string | null;
  start_source: string | null;
  stop_source: string | null;
  manual_start_at: number | null;
  manual_stop_at: number | null;
  points_json: string;
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
  point_count: number;
  max_point_id: number;
}

interface HistoryDaySummaryRow {
  day_start_millis: number;
  latest_timestamp: number;
  session_count: number;
  total_distance_km: number;
  total_duration_seconds: number;
  average_speed_kmh: number;
  source_ids_json: string;
}

interface ProcessedHistoryDaySummarySourceRow {
  history_id: number;
  timestamp_millis: number;
  distance_km: number;
  duration_seconds: number;
  points_json?: string | null;
}

export function buildPersistHistoriesResult(
  rawHistories: HistoryRecord[],
  insertedCount: number
): PersistHistoriesResult {
  return {
    insertedCount,
    dedupedCount: rawHistories.length - insertedCount,
    acceptedHistoryIds: getAcceptedHistoryIds(rawHistories)
  };
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

export function buildPersistAnalysisResult(
  rawSegments: AnalysisSegment[],
  insertedCount: number
): PersistAnalysisResult {
  const acceptedSegmentIds = getAcceptedSegmentIds(rawSegments);
  return {
    insertedCount,
    dedupedCount: rawSegments.length - insertedCount,
    acceptedMaxSegmentId:
      acceptedSegmentIds.length === 0 ? 0 : Math.max(...acceptedSegmentIds)
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

function parseHistoryPoints(pointsJson: string): HistoryRecord["points"] {
  const parsed = JSON.parse(pointsJson);
  if (!Array.isArray(parsed)) {
    return [];
  }

  return parsed.map((point) => ({
    latitude:
      typeof point?.latitude === "number" && Number.isFinite(point.latitude)
        ? point.latitude
        : 0,
    longitude:
      typeof point?.longitude === "number" && Number.isFinite(point.longitude)
        ? point.longitude
        : 0,
    timestampMillis:
      Number.isSafeInteger(point?.timestampMillis) ? point.timestampMillis : 0,
    accuracyMeters:
      typeof point?.accuracyMeters === "number" && Number.isFinite(point.accuracyMeters)
        ? point.accuracyMeters
        : null,
    altitudeMeters:
      typeof point?.altitudeMeters === "number" && Number.isFinite(point.altitudeMeters)
        ? point.altitudeMeters
        : null,
    wgs84Latitude:
      typeof point?.wgs84Latitude === "number" && Number.isFinite(point.wgs84Latitude)
        ? point.wgs84Latitude
        : null,
    wgs84Longitude:
      typeof point?.wgs84Longitude === "number" && Number.isFinite(point.wgs84Longitude)
        ? point.wgs84Longitude
        : null
  }));
}

function parseHistoryPointsOrNull(pointsJson: string | null | undefined): HistoryRecord["points"] | null {
  if (typeof pointsJson !== "string" || pointsJson.trim().length === 0) {
    return null;
  }

  try {
    return parseHistoryPoints(pointsJson);
  } catch {
    return null;
  }
}

const EARTH_RADIUS_METERS = 6_371_000;
const MAX_POOR_ACCURACY_METERS = 90;
const MAX_JUMP_DISTANCE_METERS = 220;
const MAX_JUMP_SPEED_METERS_PER_SECOND = 85;
const MAX_NO_TIMESTAMP_SPLIT_DISTANCE_METERS = 1_200;
const MIN_SPIKE_EDGE_DISTANCE_METERS = 48;
const MIN_SPIKE_EXCESS_DISTANCE_METERS = 72;
const MAX_SPIKE_EDGE_DURATION_MILLIS = 75_000;
const MAX_SPIKE_WINDOW_MILLIS = 120_000;

function isValidCoordinate(point: HistoryRecord["points"][number]): boolean {
  if (point.latitude < -90 || point.latitude > 90) return false;
  if (point.longitude < -180 || point.longitude > 180) return false;
  if (point.latitude === 0 && point.longitude === 0) return false;
  if (point.wgs84Latitude !== null && (point.wgs84Latitude < -90 || point.wgs84Latitude > 90)) {
    return false;
  }
  if (point.wgs84Longitude !== null && (point.wgs84Longitude < -180 || point.wgs84Longitude > 180)) {
    return false;
  }
  return true;
}

function latitudeForDistance(point: HistoryRecord["points"][number]): number {
  return point.wgs84Latitude ?? point.latitude;
}

function longitudeForDistance(point: HistoryRecord["points"][number]): number {
  return point.wgs84Longitude ?? point.longitude;
}

function distanceMeters(
  first: HistoryRecord["points"][number],
  second: HistoryRecord["points"][number]
): number {
  const lat1 = (latitudeForDistance(first) * Math.PI) / 180;
  const lat2 = (latitudeForDistance(second) * Math.PI) / 180;
  const dLat = lat2 - lat1;
  const dLon = ((longitudeForDistance(second) - longitudeForDistance(first)) * Math.PI) / 180;
  const sinDLat = Math.sin(dLat / 2);
  const sinDLon = Math.sin(dLon / 2);
  const haversine =
    sinDLat * sinDLat +
    Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
  return EARTH_RADIUS_METERS * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, haversine))));
}

function shouldDropSpike(
  previous: HistoryRecord["points"][number],
  current: HistoryRecord["points"][number],
  next: HistoryRecord["points"][number]
): boolean {
  const previousToCurrent = distanceMeters(previous, current);
  const currentToNext = distanceMeters(current, next);
  const previousToNext = distanceMeters(previous, next);
  const maxAccuracyMeters = Math.max(
    previous.accuracyMeters ?? 0,
    current.accuracyMeters ?? 0,
    next.accuracyMeters ?? 0
  );
  const edgeDistanceThreshold = Math.max(MIN_SPIKE_EDGE_DISTANCE_METERS, maxAccuracyMeters * 1.45);
  const bridgeDistanceThreshold = Math.max(18, maxAccuracyMeters * 0.7);
  const detourExcess = previousToCurrent + currentToNext - previousToNext;

  if (previousToCurrent < edgeDistanceThreshold || currentToNext < edgeDistanceThreshold) {
    return false;
  }
  if (previousToNext > Math.max(bridgeDistanceThreshold, Math.min(previousToCurrent, currentToNext) * 0.34)) {
    return false;
  }
  if (detourExcess < Math.max(MIN_SPIKE_EXCESS_DISTANCE_METERS, maxAccuracyMeters * 1.6)) {
    return false;
  }

  const hasTimestamps =
    previous.timestampMillis > 0 &&
    current.timestampMillis > 0 &&
    next.timestampMillis > 0;
  if (!hasTimestamps) return true;

  const firstDeltaMillis = current.timestampMillis - previous.timestampMillis;
  const secondDeltaMillis = next.timestampMillis - current.timestampMillis;
  if (firstDeltaMillis <= 0 || secondDeltaMillis <= 0) return false;

  return (
    firstDeltaMillis <= MAX_SPIKE_EDGE_DURATION_MILLIS &&
    secondDeltaMillis <= MAX_SPIKE_EDGE_DURATION_MILLIS &&
    firstDeltaMillis + secondDeltaMillis <= MAX_SPIKE_WINDOW_MILLIS
  );
}

function removeSpikeOutliers(points: HistoryRecord["points"]): HistoryRecord["points"] {
  if (points.length < 3) return points;

  const cleaned: HistoryRecord["points"] = [points[0]!];
  let index = 1;
  while (index < points.length - 1) {
    const previous = cleaned[cleaned.length - 1]!;
    const current = points[index]!;
    const next = points[index + 1]!;
    if (!shouldDropSpike(previous, current, next)) {
      cleaned.push(current);
    }
    index += 1;
  }
  cleaned.push(points[points.length - 1]!);
  return cleaned;
}

type SanitizerAction = "keep" | "start-new-segment" | "drop";

function classifyPoint(
  previous: HistoryRecord["points"][number],
  candidate: HistoryRecord["points"][number]
): SanitizerAction {
  const hasWgs84Coords =
    previous.wgs84Latitude !== null &&
    previous.wgs84Longitude !== null &&
    candidate.wgs84Latitude !== null &&
    candidate.wgs84Longitude !== null;
  const distance = distanceMeters(previous, candidate);
  const maxAccuracyMeters = Math.max(previous.accuracyMeters ?? 0, candidate.accuracyMeters ?? 0);
  const poorAccuracy = (candidate.accuracyMeters ?? 0) >= MAX_POOR_ACCURACY_METERS;
  const tinyMoveThreshold = Math.max(4, maxAccuracyMeters * 0.22);

  if (distance <= tinyMoveThreshold) return "keep";

  const hasTimestamps = previous.timestampMillis > 0 && candidate.timestampMillis > 0;
  if (!hasTimestamps) {
    if (poorAccuracy && distance >= 100) return "drop";
    if (distance >= MAX_NO_TIMESTAMP_SPLIT_DISTANCE_METERS) return "start-new-segment";
    return "keep";
  }

  const timeDeltaMillis = candidate.timestampMillis - previous.timestampMillis;
  if (timeDeltaMillis <= 0) return "drop";

  const inferredSpeedMetersPerSecond = distance / Math.max(timeDeltaMillis / 1000, 1);
  const effectiveMaxJumpDistance = hasWgs84Coords
    ? MAX_JUMP_DISTANCE_METERS
    : MAX_JUMP_DISTANCE_METERS * 3;
  const effectiveMaxJumpSpeed = hasWgs84Coords
    ? MAX_JUMP_SPEED_METERS_PER_SECOND
    : MAX_JUMP_SPEED_METERS_PER_SECOND * 3;

  if (
    poorAccuracy &&
    inferredSpeedMetersPerSecond <= 2.5 &&
    distance <= Math.max(35, maxAccuracyMeters * 0.9)
  ) {
    return "drop";
  }

  if (
    distance >= Math.max(effectiveMaxJumpDistance, maxAccuracyMeters * 2.8) &&
    inferredSpeedMetersPerSecond >= effectiveMaxJumpSpeed
  ) {
    return "start-new-segment";
  }

  if (poorAccuracy && distance >= 200) return "start-new-segment";
  return "keep";
}

function calculateSanitizedDistanceKm(points: HistoryRecord["points"]): number {
  const validPoints = removeSpikeOutliers(
    points
      .filter(isValidCoordinate)
      .sort((left, right) => {
        const leftTimestamp = left.timestampMillis > 0 ? left.timestampMillis : Number.MAX_SAFE_INTEGER;
        const rightTimestamp = right.timestampMillis > 0 ? right.timestampMillis : Number.MAX_SAFE_INTEGER;
        return leftTimestamp - rightTimestamp;
      })
  );
  const segments: HistoryRecord["points"][] = [];
  for (const point of validPoints) {
    const currentSegment = segments[segments.length - 1];
    if (currentSegment === undefined || currentSegment.length === 0) {
      segments.push([point]);
      continue;
    }

    const previous = currentSegment[currentSegment.length - 1]!;
    const action = classifyPoint(previous, point);
    if (action === "keep") {
      currentSegment.push(point);
    } else if (action === "start-new-segment") {
      segments.push([point]);
    }
  }

  return segments.reduce((totalKm, segment) => {
    if (segment.length < 2) return totalKm;
    let segmentMeters = 0;
    for (let index = 0; index < segment.length - 1; index += 1) {
      segmentMeters += distanceMeters(segment[index]!, segment[index + 1]!);
    }
    return totalKm + segmentMeters / 1000;
  }, 0);
}

function summaryDistanceKm(row: ProcessedHistoryDaySummarySourceRow): number {
  const points = parseHistoryPointsOrNull(row.points_json);
  if (points === null) {
    return row.distance_km;
  }
  return calculateSanitizedDistanceKm(points);
}

function mapUploadedHistoryRow(row: UploadedHistoryRow): HistoryRecord {
  return {
    historyId: row.history_id,
    timestampMillis: row.timestamp_millis,
    distanceKm: row.distance_km,
    durationSeconds: row.duration_seconds,
    averageSpeedKmh: row.average_speed_kmh,
    title: row.title,
    startSource: row.start_source,
    stopSource: row.stop_source,
    manualStartAt: row.manual_start_at,
    manualStopAt: row.manual_stop_at,
    points: parseHistoryPoints(row.points_json)
  };
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
    pointCount: row.point_count,
    maxPointId: row.max_point_id
  };
}

function parseSourceIds(sourceIdsJson: string): number[] {
  try {
    const parsed = JSON.parse(sourceIdsJson);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((value) => {
        if (typeof value === "number" && Number.isSafeInteger(value)) return value;
        if (typeof value === "string") return Number(value);
        return NaN;
      })
      .filter((value): value is number => Number.isSafeInteger(value));
  } catch {
    return [];
  }
}

function mapHistoryDaySummaryRow(row: HistoryDaySummaryRow): HistoryDaySummary {
  return {
    dayStartMillis: row.day_start_millis,
    latestTimestamp: row.latest_timestamp,
    sessionCount: row.session_count,
    totalDistanceKm: row.total_distance_km,
    totalDurationSeconds: row.total_duration_seconds,
    averageSpeedKmh: row.average_speed_kmh,
    sourceIds: parseSourceIds(row.source_ids_json)
  };
}

function startOfDay(timestampMillis: number, utcOffsetMinutes = 0): number {
  const date = new Date(timestampMillis);
  const offsetMillis = utcOffsetMinutes * 60_000;
  return Math.floor((date.getTime() + offsetMillis) / 86_400_000) * 86_400_000 - offsetMillis;
}

async function refreshHistoryDaySummaries(
  deviceId: string,
  affectedDayStarts: number[],
  env: Env
): Promise<void> {
  const uniqueDays = [...new Set(affectedDayStarts)];
  for (const dayStartMillis of uniqueDays) {
    const result = await env.DB.prepare(
      `SELECT
         history_id,
         timestamp_millis,
         distance_km,
         duration_seconds
       FROM processed_histories
       WHERE device_id = ?
         AND timestamp_millis >= ?
         AND timestamp_millis < ?
       ORDER BY timestamp_millis DESC, history_id DESC`
    )
      .bind(deviceId, dayStartMillis, dayStartMillis + 86_400_000)
      .all<{
        history_id: number;
        timestamp_millis: number;
        distance_km: number;
        duration_seconds: number;
      }>();

    const rows = result.results ?? [];
    if (rows.length === 0) {
      await env.DB.prepare(
        `DELETE FROM history_day_summary
         WHERE device_id = ?
           AND day_start_millis = ?`
      )
        .bind(deviceId, dayStartMillis)
        .all();
      continue;
    }

    const latestTimestamp = rows.reduce(
      (latest, row) => Math.max(latest, row.timestamp_millis),
      0
    );
    const totalDistanceKm = rows.reduce((sum, row) => sum + row.distance_km, 0);
    const totalDurationSeconds = rows.reduce(
      (sum, row) => sum + row.duration_seconds,
      0
    );
    const averageSpeedKmh =
      totalDurationSeconds > 0
        ? totalDistanceKm / (totalDurationSeconds / 3600)
        : 0;
    const sourceIds = rows.map((row) => row.history_id);

    await env.DB.prepare(
      `INSERT INTO history_day_summary
         (
           device_id,
           day_start_millis,
           latest_timestamp,
           session_count,
           total_distance_km,
           total_duration_seconds,
           average_speed_kmh,
           source_ids_json
         )
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(device_id, day_start_millis) DO UPDATE SET
         latest_timestamp = excluded.latest_timestamp,
         session_count = excluded.session_count,
         total_distance_km = excluded.total_distance_km,
         total_duration_seconds = excluded.total_duration_seconds,
         average_speed_kmh = excluded.average_speed_kmh,
         source_ids_json = excluded.source_ids_json,
         updated_at = CURRENT_TIMESTAMP`
    )
      .bind(
        deviceId,
        dayStartMillis,
        latestTimestamp,
        rows.length,
        totalDistanceKm,
        totalDurationSeconds,
        averageSpeedKmh,
        JSON.stringify(sourceIds)
      )
      .all();
  }
}

async function readExistingProcessedHistoryTimestamps(
  deviceId: string,
  historyIds: number[],
  env: Env
): Promise<Map<number, number>> {
  const timestampsByHistoryId = new Map<number, number>();
  const uniqueHistoryIds = [...new Set(historyIds)];
  const chunkSize = 64;
  for (let index = 0; index < uniqueHistoryIds.length; index += chunkSize) {
    const chunk = uniqueHistoryIds.slice(index, index + chunkSize);
    if (chunk.length === 0) continue;
    const placeholders = chunk.map(() => "?").join(", ");
    const result = await env.DB.prepare(
      `SELECT history_id, timestamp_millis
       FROM processed_histories
       WHERE device_id = ?
         AND history_id IN (${placeholders})`
    )
      .bind(deviceId, ...chunk)
      .all<{ history_id: number; timestamp_millis: number }>();

    (result.results ?? []).forEach((row) => {
      timestampsByHistoryId.set(row.history_id, row.timestamp_millis);
    });
  }
  return timestampsByHistoryId;
}

export function createD1HistoryPersistence(): HistoryPersistence {
  return {
    async persistHistories(
      deviceId: string,
      appVersion: string,
      histories: HistoryRecord[],
      env: Env
    ): Promise<PersistHistoriesResult> {
      const uniqueHistories = dedupeHistoriesById(histories);
      if (uniqueHistories.length === 0) {
        return buildPersistHistoriesResult(histories, 0);
      }

      const statements = uniqueHistories.map((history) =>
        env.DB.prepare(
          `INSERT OR IGNORE INTO uploaded_histories
             (
               device_id,
               history_id,
               app_version,
               timestamp_millis,
               distance_km,
               duration_seconds,
               average_speed_kmh,
               title,
               start_source,
               stop_source,
               manual_start_at,
               manual_stop_at,
               points_json
             )
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        ).bind(
          deviceId,
          history.historyId,
          appVersion,
          history.timestampMillis,
          history.distanceKm,
          history.durationSeconds,
          history.averageSpeedKmh,
          history.title,
          history.startSource,
          history.stopSource,
          history.manualStartAt,
          history.manualStopAt,
          JSON.stringify(history.points)
        )
      );

      const insertedCount = await executeBatch(env, statements);
      return buildPersistHistoriesResult(histories, insertedCount);
    },

    async readHistories(deviceId: string, env: Env): Promise<HistoryRecord[]> {
      const result = await env.DB.prepare(
        `SELECT
           history_id,
           timestamp_millis,
           distance_km,
           duration_seconds,
           average_speed_kmh,
           title,
           start_source,
           stop_source,
           manual_start_at,
           manual_stop_at,
           points_json
         FROM uploaded_histories
         WHERE device_id = ?
         ORDER BY timestamp_millis DESC, history_id DESC`
      )
        .bind(deviceId)
        .all<UploadedHistoryRow>();

      return (result.results ?? []).map(mapUploadedHistoryRow);
    },

    async readHistoriesByDay(
      deviceId: string,
      dayStartMillis: number,
      env: Env
    ): Promise<HistoryRecord[]> {
      const result = await env.DB.prepare(
        `SELECT
           history_id,
           timestamp_millis,
           distance_km,
           duration_seconds,
           average_speed_kmh,
           title,
           start_source,
           stop_source,
           manual_start_at,
           manual_stop_at,
           points_json
         FROM uploaded_histories
         WHERE device_id = ?
           AND timestamp_millis >= ?
           AND timestamp_millis < ?
         ORDER BY timestamp_millis DESC, history_id DESC`
      )
        .bind(deviceId, dayStartMillis, dayStartMillis + 86_400_000)
        .all<UploadedHistoryRow>();

      return (result.results ?? []).map(mapUploadedHistoryRow);
    }
  };
}

export function createD1RawPointPersistence(): RawPointPersistence {
  return {
    async persistRawPoints(
      deviceId: string,
      appVersion: string,
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
      const offsetMillis = utcOffsetMinutes * 60_000;
      const result = await env.DB.prepare(
        `SELECT
           ((CAST((timestamp_millis + ?) / 86400000 AS INTEGER) * 86400000) - ?) AS day_start_millis,
           COUNT(*) AS point_count,
           MAX(point_id) AS max_point_id
         FROM raw_location_point
         WHERE device_id = ?
         GROUP BY day_start_millis
         ORDER BY day_start_millis DESC`
      )
        .bind(offsetMillis, offsetMillis, deviceId)
        .all<RawPointDaySummaryRow>();

      return (result.results ?? []).map(mapRawPointDaySummaryRow);
    }
  };
}

export function createD1AnalysisPersistence(): AnalysisPersistence {
  return {
    async persistAnalysis(
      deviceId: string,
      appVersion: string,
      segments: AnalysisSegment[],
      env: Env
    ): Promise<PersistAnalysisResult> {
      const uniqueSegments = dedupeAnalysisSegmentsById(segments);
      if (uniqueSegments.length === 0) {
        return buildPersistAnalysisResult(segments, 0);
      }

      const segmentStatements = uniqueSegments.map((segment) =>
        env.DB.prepare(
          `INSERT OR IGNORE INTO analysis_segment
             (
               device_id,
               segment_id,
               app_version,
               start_point_id,
               end_point_id,
               start_timestamp,
               end_timestamp,
               segment_type,
               confidence,
               distance_meters,
               duration_millis,
               avg_speed_meters_per_second,
               max_speed_meters_per_second,
               analysis_version
             )
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
        ).bind(
          deviceId,
          segment.segmentId,
          appVersion,
          segment.startPointId,
          segment.endPointId,
          segment.startTimestamp,
          segment.endTimestamp,
          segment.segmentType,
          segment.confidence,
          segment.distanceMeters,
          segment.durationMillis,
          segment.avgSpeedMetersPerSecond,
          segment.maxSpeedMetersPerSecond,
          segment.analysisVersion
        )
      );

      const stayStatements = uniqueSegments.flatMap((segment) =>
        segment.stayClusters.map((stayCluster) =>
          env.DB.prepare(
            `INSERT OR IGNORE INTO stay_cluster
               (
                 device_id,
                 segment_id,
                 stay_id,
                 center_lat,
                 center_lng,
                 radius_meters,
                 arrival_time,
                 departure_time,
                 confidence,
                 analysis_version
               )
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
          ).bind(
            deviceId,
            segment.segmentId,
            stayCluster.stayId,
            stayCluster.centerLat,
            stayCluster.centerLng,
            stayCluster.radiusMeters,
            stayCluster.arrivalTime,
            stayCluster.departureTime,
            stayCluster.confidence,
            stayCluster.analysisVersion
          )
        )
      );

      const insertedCount = await executeBatch(env, segmentStatements);
      await executeBatch(env, stayStatements);
      return buildPersistAnalysisResult(segments, insertedCount);
    }
  };
}

export function createD1ProcessedHistoryPersistence() {
  return {
    async persistHistories(
      deviceId: string,
      appVersion: string,
      utcOffsetMinutes: number,
      histories: HistoryRecord[],
      env: Env
    ): Promise<PersistHistoriesResult> {
      const uniqueHistories = dedupeHistoriesById(histories);
      if (uniqueHistories.length === 0) {
        return buildPersistHistoriesResult(histories, 0);
      }
      const existingTimestamps = await readExistingProcessedHistoryTimestamps(
        deviceId,
        uniqueHistories.map((history) => history.historyId),
        env
      );

      const statements = uniqueHistories.map((history) =>
        env.DB.prepare(
          `INSERT INTO processed_histories
             (
               device_id,
               history_id,
               app_version,
               timestamp_millis,
               distance_km,
               duration_seconds,
               average_speed_kmh,
               title,
               start_source,
               stop_source,
               manual_start_at,
               manual_stop_at,
               points_json
             )
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(device_id, history_id) DO UPDATE SET
             app_version = excluded.app_version,
             timestamp_millis = excluded.timestamp_millis,
             distance_km = excluded.distance_km,
             duration_seconds = excluded.duration_seconds,
             average_speed_kmh = excluded.average_speed_kmh,
             title = excluded.title,
             start_source = excluded.start_source,
             stop_source = excluded.stop_source,
             manual_start_at = excluded.manual_start_at,
             manual_stop_at = excluded.manual_stop_at,
             points_json = excluded.points_json,
             updated_at = CURRENT_TIMESTAMP`
        ).bind(
          deviceId,
          history.historyId,
          appVersion,
          history.timestampMillis,
          history.distanceKm,
          history.durationSeconds,
          history.averageSpeedKmh,
          history.title,
          history.startSource,
          history.stopSource,
          history.manualStartAt,
          history.manualStopAt,
          JSON.stringify(history.points)
        )
      );

      const affectedCount = await executeBatch(env, statements);
      const affectedDayStarts = new Set<number>();
      uniqueHistories.forEach((history) => {
        affectedDayStarts.add(startOfDay(history.timestampMillis, utcOffsetMinutes));
        const previousTimestamp = existingTimestamps.get(history.historyId);
        if (typeof previousTimestamp === "number") {
          affectedDayStarts.add(startOfDay(previousTimestamp, utcOffsetMinutes));
        }
      });
      await refreshHistoryDaySummaries(
        deviceId,
        [...affectedDayStarts],
        env
      );
      return buildPersistHistoriesResult(histories, affectedCount);
    },

    async readHistories(deviceId: string, env: Env): Promise<HistoryRecord[]> {
      const result = await env.DB.prepare(
        `SELECT
           history_id,
           timestamp_millis,
           distance_km,
           duration_seconds,
           average_speed_kmh,
           title,
           start_source,
           stop_source,
           manual_start_at,
           manual_stop_at,
           points_json
         FROM processed_histories
         WHERE device_id = ?
         ORDER BY timestamp_millis DESC, history_id DESC`
      )
        .bind(deviceId)
        .all<UploadedHistoryRow>();

      return (result.results ?? []).map(mapUploadedHistoryRow);
    },

    async readHistoriesByDay(
      deviceId: string,
      dayStartMillis: number,
      env: Env
    ): Promise<HistoryRecord[]> {
      const result = await env.DB.prepare(
        `SELECT
           history_id,
           timestamp_millis,
           distance_km,
           duration_seconds,
           average_speed_kmh,
           title,
           start_source,
           stop_source,
           manual_start_at,
           manual_stop_at,
           points_json
         FROM processed_histories
         WHERE device_id = ?
           AND timestamp_millis >= ?
           AND timestamp_millis < ?
         ORDER BY timestamp_millis DESC, history_id DESC`
      )
        .bind(deviceId, dayStartMillis, dayStartMillis + 86_400_000)
        .all<UploadedHistoryRow>();

      return (result.results ?? []).map(mapUploadedHistoryRow);
    },

    async deleteHistoriesByDay(
      deviceId: string,
      dayStartMillis: number,
      env: Env
    ): Promise<number> {
      const processedDeleteResult = await env.DB.prepare(
        `DELETE FROM processed_histories
         WHERE device_id = ?
           AND timestamp_millis >= ?
           AND timestamp_millis < ?`
      )
        .bind(deviceId, dayStartMillis, dayStartMillis + 86_400_000)
        .all();

      await env.DB.prepare(
        `DELETE FROM uploaded_histories
         WHERE device_id = ?
           AND timestamp_millis >= ?
           AND timestamp_millis < ?`
      )
        .bind(deviceId, dayStartMillis, dayStartMillis + 86_400_000)
        .all();

      await env.DB.prepare(
        `DELETE FROM history_day_summary
         WHERE device_id = ?
           AND day_start_millis = ?`
      )
        .bind(deviceId, dayStartMillis)
        .all();

      return extractChanges(processedDeleteResult);
    }
  };
}

export function createD1HistoryDaySummaryPersistence(): HistoryDaySummaryPersistence {
  return {
    async readDays(
      deviceId: string,
      utcOffsetMinutes: number,
      env: Env
    ): Promise<HistoryDaySummary[]> {
      const result = await env.DB.prepare(
        `SELECT
           history_id,
           timestamp_millis,
           distance_km,
           duration_seconds,
           points_json
         FROM processed_histories
         WHERE device_id = ?
         ORDER BY timestamp_millis DESC, history_id DESC`
      )
        .bind(deviceId)
        .all<ProcessedHistoryDaySummarySourceRow>();

      const summariesByDay = new Map<number, HistoryDaySummary>();
      (result.results ?? []).forEach((row) => {
        const dayStartMillis = startOfDay(row.timestamp_millis, utcOffsetMinutes);
        const existing = summariesByDay.get(dayStartMillis);
        const distanceKm = summaryDistanceKm(row);
        if (existing === undefined) {
          summariesByDay.set(dayStartMillis, {
            dayStartMillis,
            latestTimestamp: row.timestamp_millis,
            sessionCount: 1,
            totalDistanceKm: distanceKm,
            totalDurationSeconds: row.duration_seconds,
            averageSpeedKmh:
              row.duration_seconds > 0
                ? distanceKm / (row.duration_seconds / 3600)
                : 0,
            sourceIds: [row.history_id]
          });
          return;
        }

        const totalDistanceKm = existing.totalDistanceKm + distanceKm;
        const totalDurationSeconds =
          existing.totalDurationSeconds + row.duration_seconds;
        summariesByDay.set(dayStartMillis, {
          ...existing,
          latestTimestamp: Math.max(existing.latestTimestamp, row.timestamp_millis),
          sessionCount: existing.sessionCount + 1,
          totalDistanceKm,
          totalDurationSeconds,
          averageSpeedKmh:
            totalDurationSeconds > 0
              ? totalDistanceKm / (totalDurationSeconds / 3600)
              : 0,
          sourceIds: [...existing.sourceIds, row.history_id]
        });
      });

      return [...summariesByDay.values()].sort(
        (left, right) => right.dayStartMillis - left.dayStartMillis
      );
    }
  };
}
