import mysql from "mysql2/promise";
import type { ResultSetHeader } from "mysql2";
import type { Connection } from "mysql2/promise";

import type {
  AnalysisPersistence,
  AnalysisSegment,
  Env,
  HistoryPersistence,
  HistoryRecord,
  PersistAnalysisResult,
  PersistHistoriesResult,
  PersistRawPointsResult,
  PersistSamplesResult,
  RawLocationPoint,
  RawPointPersistence,
  SamplePersistence,
  TrainingSample
} from "./types";

function dedupeSamplesByEventId(samples: TrainingSample[]): TrainingSample[] {
  const byEventId = new Map<number, TrainingSample>();
  for (const sample of samples) {
    byEventId.set(sample.eventId, sample);
  }
  return [...byEventId.values()];
}

function getAcceptedEventIds(samples: TrainingSample[]): number[] {
  return [...new Set(samples.map((sample) => sample.eventId))];
}

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

export function buildPersistSamplesResult(
  rawSamples: TrainingSample[],
  insertedCount: number
): PersistSamplesResult {
  const acceptedEventIds = getAcceptedEventIds(rawSamples);

  return {
    insertedCount,
    dedupedCount: rawSamples.length - insertedCount,
    acceptedEventIds
  };
}

export function buildPersistHistoriesResult(
  rawHistories: HistoryRecord[],
  insertedCount: number
): PersistHistoriesResult {
  const acceptedHistoryIds = getAcceptedHistoryIds(rawHistories);

  return {
    insertedCount,
    dedupedCount: rawHistories.length - insertedCount,
    acceptedHistoryIds
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

function toConnection(env: Env): mysql.ConnectionOptions {
  return {
    host: env.HYPERDRIVE.host,
    port: Number(env.HYPERDRIVE.port),
    user: env.HYPERDRIVE.user,
    password: env.HYPERDRIVE.password,
    database: env.HYPERDRIVE.database,
    disableEval: true
  };
}

async function closeConnection(connection: Connection | undefined): Promise<void> {
  if (!connection) {
    return;
  }

  try {
    await connection.end();
  } catch (error) {
    console.warn("Failed to close MySQL connection cleanly", error);
  }
}

export function createMysqlRawPointPersistence(): RawPointPersistence {
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

      const rowPlaceholders = uniquePoints
        .map(() => "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        .join(", ");
      const sql = `
        INSERT IGNORE INTO raw_location_point
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
        VALUES ${rowPlaceholders}
      `;

      const params: Array<number | string | boolean | null> = [];
      for (const point of uniquePoints) {
        params.push(
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
          point.isMock,
          point.wifiFingerprintDigest,
          point.activityType,
          point.activityConfidence,
          point.samplingTier
        );
      }

      let connection: Connection | undefined;
      try {
        connection = await mysql.createConnection(toConnection(env));
        const [result] = await connection.query<ResultSetHeader>(sql, params);
        const insertedCount = result.affectedRows ?? 0;
        return buildPersistRawPointsResult(points, insertedCount);
      } finally {
        await closeConnection(connection);
      }
    }
  };
}

export function createMysqlAnalysisPersistence(): AnalysisPersistence {
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

      const segmentRowPlaceholders = uniqueSegments
        .map(() => "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        .join(", ");
      const segmentSql = `
        INSERT IGNORE INTO analysis_segment
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
        VALUES ${segmentRowPlaceholders}
      `;

      const segmentParams: Array<number | string> = [];
      for (const segment of uniqueSegments) {
        segmentParams.push(
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
        );
      }

      const stayClusterRows = uniqueSegments.flatMap((segment) =>
        segment.stayClusters.map((stayCluster) => ({
          segmentId: segment.segmentId,
          stayId: stayCluster.stayId,
          centerLat: stayCluster.centerLat,
          centerLng: stayCluster.centerLng,
          radiusMeters: stayCluster.radiusMeters,
          arrivalTime: stayCluster.arrivalTime,
          departureTime: stayCluster.departureTime,
          confidence: stayCluster.confidence,
          analysisVersion: stayCluster.analysisVersion
        }))
      );

      let connection: Connection | undefined;
      try {
        connection = await mysql.createConnection(toConnection(env));
        const [segmentResult] = await connection.query<ResultSetHeader>(
          segmentSql,
          segmentParams
        );

        if (stayClusterRows.length > 0) {
          const stayRowPlaceholders = stayClusterRows
            .map(() => "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .join(", ");
          const staySql = `
            INSERT IGNORE INTO stay_cluster
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
            VALUES ${stayRowPlaceholders}
          `;

          const stayParams: Array<number | string> = [];
          for (const stayCluster of stayClusterRows) {
            stayParams.push(
              deviceId,
              stayCluster.segmentId,
              stayCluster.stayId,
              stayCluster.centerLat,
              stayCluster.centerLng,
              stayCluster.radiusMeters,
              stayCluster.arrivalTime,
              stayCluster.departureTime,
              stayCluster.confidence,
              stayCluster.analysisVersion
            );
          }

          await connection.query<ResultSetHeader>(staySql, stayParams);
        }

        const insertedCount = segmentResult.affectedRows ?? 0;
        return buildPersistAnalysisResult(segments, insertedCount);
      } finally {
        await closeConnection(connection);
      }
    }
  };
}

export function createMysqlHistoryPersistence(): HistoryPersistence {
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

      const rowPlaceholders = uniqueHistories
        .map(() => "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        .join(", ");
      const sql = `
        INSERT IGNORE INTO uploaded_histories
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
        VALUES ${rowPlaceholders}
      `;

      const params: Array<number | string | null> = [];
      for (const history of uniqueHistories) {
        params.push(
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
        );
      }

      let connection: Connection | undefined;
      try {
        connection = await mysql.createConnection(toConnection(env));
        const [result] = await connection.query<ResultSetHeader>(sql, params);
        const insertedCount = result.affectedRows ?? 0;
        return buildPersistHistoriesResult(histories, insertedCount);
      } finally {
        await closeConnection(connection);
      }
    }
  };
}

export function createMysqlSamplePersistence(): SamplePersistence {
  return {
    async persistSamples(
      samples: TrainingSample[],
      env: Env
    ): Promise<PersistSamplesResult> {
      const uniqueSamples = dedupeSamplesByEventId(samples);
      if (uniqueSamples.length === 0) {
        return buildPersistSamplesResult(samples, 0);
      }

      const rowPlaceholders = uniqueSamples.map(() => "(?, ?, ?, ?, ?)").join(", ");
      const sql = `
        INSERT IGNORE INTO training_samples
          (event_id, timestamp_millis, phase, final_decision_json, features_json)
        VALUES ${rowPlaceholders}
      `;

      const params: Array<number | string> = [];
      for (const sample of uniqueSamples) {
        params.push(
          sample.eventId,
          sample.timestampMillis,
          sample.phase,
          JSON.stringify(sample.finalDecision),
          JSON.stringify(sample.features)
        );
      }

      let connection: Connection | undefined;
      try {
        connection = await mysql.createConnection(toConnection(env));
        const [result] = await connection.query<ResultSetHeader>(sql, params);
        const insertedCount = result.affectedRows ?? 0;
        return buildPersistSamplesResult(samples, insertedCount);
      } finally {
        await closeConnection(connection);
      }
    }
  };
}
