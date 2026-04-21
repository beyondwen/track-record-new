import { beforeEach, describe, expect, it, vi } from "vitest";

function createMockPreparedStatement(
  sql: string,
  bindCollector: Array<{ sql: string; args: unknown[] }>,
  allHandler?: (sql: string, args: unknown[]) => Promise<D1Result<unknown>>
): D1PreparedStatement {
  let boundArgs: unknown[] = [];
  return {
    bind(...args: unknown[]) {
      boundArgs = args;
      bindCollector.push({ sql, args });
      return this;
    },
    all() {
      return allHandler?.(sql, boundArgs) ?? Promise.resolve({
        success: true,
        results: []
      } as D1Result<unknown>);
    }
  } as D1PreparedStatement;
}

function createMockEnv(changeCounts: number[]): {
  env: { UPLOAD_TOKEN: string; MAPBOX_PUBLIC_TOKEN: string; DB: D1Database };
  prepare: ReturnType<typeof vi.fn>;
  batch: ReturnType<typeof vi.fn>;
  bindCollector: Array<{ sql: string; args: unknown[] }>;
}

function createMockReadEnv(
  queryResults: Array<{ sqlIncludes: string; rows: unknown[] }>
): {
  env: { UPLOAD_TOKEN: string; MAPBOX_PUBLIC_TOKEN: string; DB: D1Database };
  prepare: ReturnType<typeof vi.fn>;
  bindCollector: Array<{ sql: string; args: unknown[] }>;
} {
  const bindCollector: Array<{ sql: string; args: unknown[] }> = [];
  const prepare = vi.fn((sql: string) =>
    createMockPreparedStatement(sql, bindCollector, async (boundSql) => {
      const matched = queryResults.find((item) => boundSql.includes(item.sqlIncludes));
      return {
        success: true,
        results: matched?.rows ?? []
      } as D1Result<unknown>;
    })
  );
  return {
    env: {
      UPLOAD_TOKEN: "token",
      MAPBOX_PUBLIC_TOKEN: "pk.worker-token",
      DB: ({
        prepare
      } as unknown) as D1Database
    },
    prepare,
    bindCollector
  };
}

function createMockEnv(changeCounts: number[]): {
  env: { UPLOAD_TOKEN: string; MAPBOX_PUBLIC_TOKEN: string; DB: D1Database };
  prepare: ReturnType<typeof vi.fn>;
  batch: ReturnType<typeof vi.fn>;
  bindCollector: Array<{ sql: string; args: unknown[] }>;
} {
  const bindCollector: Array<{ sql: string; args: unknown[] }> = [];
  const prepare = vi.fn((sql: string) =>
    createMockPreparedStatement(sql, bindCollector)
  );
  const batch = vi.fn(async (statements: D1PreparedStatement[]) =>
    statements.map((_, index) => ({
      success: true,
      meta: { changes: changeCounts[index] ?? 0 }
    })) as D1Result<unknown>[]
  );

  return {
    env: {
      UPLOAD_TOKEN: "token",
      MAPBOX_PUBLIC_TOKEN: "pk.worker-token",
      DB: ({
        prepare,
        batch
      } as unknown) as D1Database
    },
    prepare,
    batch,
    bindCollector
  };
}

describe("createD1 persistence", () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it("persists raw points through D1 batch statements", async () => {
    const { createD1RawPointPersistence } = await import("./d1");
    const { env, prepare, batch, bindCollector } = createMockEnv([1]);

    const result = await createD1RawPointPersistence().persistRawPoints(
      "device-1",
      "1.0.23",
      [
        {
          pointId: 18,
          timestampMillis: 1700000000000,
          latitude: 30.1,
          longitude: 120.1,
          accuracyMeters: 5.5,
          altitudeMeters: 20.2,
          speedMetersPerSecond: 1.1,
          bearingDegrees: 90,
          provider: "gps",
          sourceType: "LOCATION_MANAGER",
          isMock: false,
          wifiFingerprintDigest: "wifi",
          activityType: "WALKING",
          activityConfidence: 0.9,
          samplingTier: "IDLE"
        }
      ],
      env
    );

    expect(prepare).toHaveBeenCalledTimes(1);
    expect(prepare.mock.calls[0]?.[0]).toContain("INSERT OR IGNORE INTO raw_location_point");
    expect(batch).toHaveBeenCalledTimes(1);
    expect(bindCollector[0]?.args).toEqual([
      "device-1",
      18,
      "1.0.23",
      1700000000000,
      30.1,
      120.1,
      5.5,
      20.2,
      1.1,
      90,
      "gps",
      "LOCATION_MANAGER",
      0,
      "wifi",
      "WALKING",
      0.9,
      "IDLE"
    ]);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0,
      acceptedMaxPointId: 18
    });
  });

  it("persists analysis segments and stay clusters through D1 batches", async () => {
    const { createD1AnalysisPersistence } = await import("./d1");
    const firstBatchEnv = createMockEnv([1]);
    const secondBatch = async (statements: D1PreparedStatement[]) =>
      statements.map(() => ({
        success: true,
        meta: { changes: 1 }
      })) as D1Result<unknown>[];
    const chainedBatch = vi
      .fn()
      .mockImplementationOnce((statements: D1PreparedStatement[]) =>
        (
          firstBatchEnv.batch as unknown as (
            statements: D1PreparedStatement[]
          ) => Promise<D1Result<unknown>[]>
        )(statements)
      )
      .mockImplementationOnce((statements: D1PreparedStatement[]) =>
        secondBatch(statements)
      );
    firstBatchEnv.env.DB.batch = chainedBatch as unknown as D1Database["batch"];

    const result = await createD1AnalysisPersistence().persistAnalysis(
      "device-1",
      "1.0.23",
      [
        {
          segmentId: 31,
          startPointId: 11,
          endPointId: 19,
          startTimestamp: 1700000000000,
          endTimestamp: 1700000300000,
          segmentType: "STATIC",
          confidence: 0.95,
          distanceMeters: 18,
          durationMillis: 300000,
          avgSpeedMetersPerSecond: 0.1,
          maxSpeedMetersPerSecond: 0.3,
          analysisVersion: 1,
          stayClusters: [
            {
              stayId: 201,
              centerLat: 30.1,
              centerLng: 120.1,
              radiusMeters: 25,
              arrivalTime: 1700000000000,
              departureTime: 1700000300000,
              confidence: 0.91,
              analysisVersion: 1
            }
          ]
        }
      ],
      firstBatchEnv.env
    );

    expect(firstBatchEnv.prepare).toHaveBeenCalledTimes(2);
    expect(firstBatchEnv.prepare.mock.calls[0]?.[0]).toContain(
      "INSERT OR IGNORE INTO analysis_segment"
    );
    expect(firstBatchEnv.prepare.mock.calls[1]?.[0]).toContain(
      "INSERT OR IGNORE INTO stay_cluster"
    );
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0,
      acceptedMaxSegmentId: 31
    });
  });

  it("persists histories through D1 batch statements", async () => {
    const { createD1HistoryPersistence } = await import("./d1");
    const { env, prepare, batch } = createMockEnv([1]);

    const result = await createD1HistoryPersistence().persistHistories(
      "device-1",
      "1.0.22",
      [
        {
          historyId: 201,
          timestampMillis: 1700000000000,
          distanceKm: 1.23,
          durationSeconds: 456,
          averageSpeedKmh: 9.8,
          title: "通勤",
          startSource: "MANUAL",
          stopSource: "MANUAL",
          manualStartAt: 1700000000000,
          manualStopAt: 1700000000500,
          points: [
            {
              latitude: 30.1,
              longitude: 120.1,
              timestampMillis: 1700000000000,
              accuracyMeters: 5.5,
              altitudeMeters: 20.2,
              wgs84Latitude: 30.09,
              wgs84Longitude: 120.09
            }
          ]
        }
      ],
      env
    );

    expect(prepare).toHaveBeenCalledTimes(1);
    expect(prepare.mock.calls[0]?.[0]).toContain("INSERT OR IGNORE INTO uploaded_histories");
    expect(batch).toHaveBeenCalledTimes(1);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0,
      acceptedHistoryIds: [201]
    });
  });

  it("reads uploaded histories from D1", async () => {
    const { createD1HistoryPersistence } = await import("./d1");
    const { env, prepare, bindCollector } = createMockReadEnv([
      {
        sqlIncludes: "FROM uploaded_histories",
        rows: [
          {
            history_id: 201,
            timestamp_millis: 1700000000000,
            distance_km: 1.23,
            duration_seconds: 456,
            average_speed_kmh: 9.8,
            title: "通勤",
            start_source: "MANUAL",
            stop_source: "AUTO",
            manual_start_at: 1700000000000,
            manual_stop_at: 1700000000500,
            points_json:
              '[{"latitude":30.1,"longitude":120.1,"timestampMillis":1700000000000,"accuracyMeters":5.5,"altitudeMeters":20.2,"wgs84Latitude":30.09,"wgs84Longitude":120.09}]'
          }
        ]
      }
    ]);

    const histories = await createD1HistoryPersistence().readHistories(
      "device-1",
      env
    );

    expect(prepare).toHaveBeenCalledTimes(1);
    expect(bindCollector[0]?.args).toEqual(["device-1"]);
    expect(histories).toEqual([
      {
        historyId: 201,
        timestampMillis: 1700000000000,
        distanceKm: 1.23,
        durationSeconds: 456,
        averageSpeedKmh: 9.8,
        title: "通勤",
        startSource: "MANUAL",
        stopSource: "AUTO",
        manualStartAt: 1700000000000,
        manualStopAt: 1700000000500,
        points: [
          {
            latitude: 30.1,
            longitude: 120.1,
            timestampMillis: 1700000000000,
            accuracyMeters: 5.5,
            altitudeMeters: 20.2,
            wgs84Latitude: 30.09,
            wgs84Longitude: 120.09
          }
        ]
      }
    ]);
  });

  it("reads uploaded histories for a specific day from D1", async () => {
    const { createD1HistoryPersistence } = await import("./d1");
    const { env, bindCollector } = createMockReadEnv([
      {
        sqlIncludes: "WHERE device_id = ?",
        rows: [
          {
            history_id: 301,
            timestamp_millis: 1700003600000,
            distance_km: 3.21,
            duration_seconds: 654,
            average_speed_kmh: 17.7,
            title: null,
            start_source: null,
            stop_source: null,
            manual_start_at: null,
            manual_stop_at: null,
            points_json: "[]"
          }
        ]
      }
    ]);

    const histories = await createD1HistoryPersistence().readHistoriesByDay(
      "device-1",
      1700000000000,
      env
    );

    expect(bindCollector[0]?.args).toEqual([
      "device-1",
      1700000000000,
      1700086400000
    ]);
    expect(histories.map((history) => history.historyId)).toEqual([301]);
  });
});
