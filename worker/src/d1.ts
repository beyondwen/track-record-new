import type {
  AnalysisPersistence,
  AnalysisSegment,
  Env,
  HistoryPersistence,
  HistoryRecord,
  PersistAnalysisResult,
  PersistHistoriesResult,
  PersistRawPointsResult,
  RawLocationPoint,
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
