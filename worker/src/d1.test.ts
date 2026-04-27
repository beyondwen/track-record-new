import { beforeEach, describe, expect, it, vi } from "vitest";

function startOfDay(timestampMillis: number, utcOffsetMinutes = 0): number {
  return (
    Math.floor((timestampMillis + utcOffsetMinutes * 60_000) / 86_400_000) * 86_400_000 -
    utcOffsetMinutes * 60_000
  );
}

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

function createQueryAwareMockEnv(
  changeCounts: number[],
  queryResults: Array<{ sqlIncludes: string; rows: unknown[] }>
): {
  env: { UPLOAD_TOKEN: string; MAPBOX_PUBLIC_TOKEN: string; DB: D1Database };
  prepare: ReturnType<typeof vi.fn>;
  batch: ReturnType<typeof vi.fn>;
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

  it("reads raw points for a specific day from D1", async () => {
    const { createD1RawPointPersistence } = await import("./d1");
    const { env, bindCollector } = createMockReadEnv([
      {
        sqlIncludes: "FROM raw_location_point",
        rows: [
          {
            point_id: 18,
            timestamp_millis: 1700000000000,
            latitude: 30.1,
            longitude: 120.1,
            accuracy_meters: 5.5,
            altitude_meters: 20.2,
            speed_meters_per_second: 1.1,
            bearing_degrees: 90,
            provider: "gps",
            source_type: "LOCATION_MANAGER",
            is_mock: 0,
            wifi_fingerprint_digest: "wifi",
            activity_type: "WALKING",
            activity_confidence: 0.9,
            sampling_tier: "ACTIVE"
          }
        ]
      }
    ]);

    const points = await createD1RawPointPersistence().readRawPointsByDay(
      "device-1",
      1700000000000,
      env
    );

    expect(bindCollector[0]?.args).toEqual([
      "device-1",
      1700000000000,
      1700086400000
    ]);
    expect(points.map((point) => point.pointId)).toEqual([18]);
  });

  it("reads raw point day summaries from D1", async () => {
    const { createD1RawPointPersistence } = await import("./d1");
    const { env, bindCollector } = createMockReadEnv([
      {
        sqlIncludes: "GROUP BY day_start_millis",
        rows: [
          {
            day_start_millis: 1699891200000,
            point_count: 128,
            max_point_id: 631
          }
        ]
      }
    ]);

    const days = await createD1RawPointPersistence().readRawPointDays(
      "device-1",
      480,
      env
    );

    expect(bindCollector[0]?.args).toEqual([
      28_800_000,
      28_800_000,
      "device-1"
    ]);
    expect(days).toEqual([
      {
        dayStartMillis: 1699891200000,
        pointCount: 128,
        maxPointId: 631
      }
    ]);
  });

  it("persists today sessions through D1 batch statements", async () => {
    const { createD1TodaySessionPersistence } = await import("./d1");
    const { env, prepare, batch, bindCollector } = createMockEnv([1]);

    const result = await createD1TodaySessionPersistence().persistSessions(
      "device-1",
      "1.0.23",
      [
        {
          sessionId: "session_1",
          dayStartMillis: 1714300800000,
          status: "ACTIVE",
          startedAt: 1714300900000,
          lastPointAt: 1714300910000,
          endedAt: null,
          phase: "ACTIVE",
          updatedAt: 1714300910000
        }
      ],
      env
    );

    expect(prepare).toHaveBeenCalledTimes(1);
    expect(prepare.mock.calls[0]?.[0]).toContain("INSERT INTO today_session");
    expect(batch).toHaveBeenCalledTimes(1);
    expect(bindCollector[0]?.args).toEqual([
      "device-1",
      "session_1",
      1714300800000,
      "ACTIVE",
      1714300900000,
      1714300910000,
      null,
      "ACTIVE",
      1714300910000
    ]);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0
    });
  });

  it("persists today session points through D1 batch statements", async () => {
    const { createD1TodaySessionPersistence } = await import("./d1");
    const { env, prepare, batch, bindCollector } = createMockEnv([1]);

    const result = await createD1TodaySessionPersistence().persistSessionPoints(
      "device-1",
      "1.0.23",
      [
        {
          sessionId: "session_1",
          pointId: 18,
          dayStartMillis: 1714300800000,
          timestampMillis: 1714300910000,
          latitude: 30.1,
          longitude: 120.1,
          accuracyMeters: 8,
          altitudeMeters: 12,
          speedMetersPerSecond: 1.2,
          provider: "gps",
          samplingTier: "ACTIVE",
          updatedAt: 1714300910000
        }
      ],
      env
    );

    expect(prepare).toHaveBeenCalledTimes(1);
    expect(prepare.mock.calls[0]?.[0]).toContain("INSERT OR IGNORE INTO today_session_point");
    expect(batch).toHaveBeenCalledTimes(1);
    expect(bindCollector[0]?.args).toEqual([
      "device-1",
      "session_1",
      18,
      1714300800000,
      1714300910000,
      30.1,
      120.1,
      8,
      12,
      1.2,
      "gps",
      "ACTIVE",
      1714300910000
    ]);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0
    });
  });

  it("reads latest open today session snapshot from D1", async () => {
    const { createD1TodaySessionPersistence } = await import("./d1");
    const { env, bindCollector } = createMockReadEnv([
      {
        sqlIncludes: "FROM today_session\n         WHERE device_id = ?",
        rows: [
          {
            session_id: "session_1",
            day_start_millis: 1714300800000,
            status: "ACTIVE",
            started_at: 1714300900000,
            last_point_at: 1714300910000,
            ended_at: null,
            phase: "ACTIVE",
            updated_at: 1714300910000
          }
        ]
      },
      {
        sqlIncludes: "FROM today_session_point",
        rows: [
          {
            session_id: "session_1",
            point_id: 18,
            day_start_millis: 1714300800000,
            timestamp_millis: 1714300910000,
            latitude: 30.1,
            longitude: 120.1,
            accuracy_meters: 8,
            altitude_meters: 12,
            speed_meters_per_second: 1.2,
            provider: "gps",
            sampling_tier: "ACTIVE",
            updated_at: 1714300910000
          }
        ]
      }
    ]);

    const snapshot = await createD1TodaySessionPersistence().readLatestOpenSession(
      "device-1",
      env
    );

    expect(bindCollector[0]?.args).toEqual(["device-1"]);
    expect(bindCollector[1]?.args).toEqual(["device-1", "session_1"]);
    expect(snapshot).toEqual({
      session: {
        sessionId: "session_1",
        dayStartMillis: 1714300800000,
        status: "ACTIVE",
        startedAt: 1714300900000,
        lastPointAt: 1714300910000,
        endedAt: null,
        phase: "ACTIVE",
        updatedAt: 1714300910000
      },
      points: [
        {
          sessionId: "session_1",
          pointId: 18,
          dayStartMillis: 1714300800000,
          timestampMillis: 1714300910000,
          latitude: 30.1,
          longitude: 120.1,
          accuracyMeters: 8,
          altitudeMeters: 12,
          speedMetersPerSecond: 1.2,
          provider: "gps",
          samplingTier: "ACTIVE",
          updatedAt: 1714300910000
        }
      ]
    });
  });
  it("persists processed histories and refreshes day summaries", async () => {
    const { createD1ProcessedHistoryPersistence } = await import("./d1");
    const timestampMillis = 1700000000000;
    const utcOffsetMinutes = 480;
    const expectedDayStart = startOfDay(timestampMillis, utcOffsetMinutes);
    const bindCollector: Array<{ sql: string; args: unknown[] }> = [];
    const prepare = vi.fn((sql: string) =>
      createMockPreparedStatement(sql, bindCollector, async (boundSql) => {
        if (boundSql.includes("FROM processed_histories")) {
          return {
            success: true,
            results: [
              {
                history_id: 601,
                timestamp_millis: timestampMillis,
                distance_km: 2.5,
                duration_seconds: 600
              }
            ]
          } as D1Result<unknown>;
        }
        return {
          success: true,
          results: []
        } as D1Result<unknown>;
      })
    );
    const batch = vi.fn(async (statements: D1PreparedStatement[]) =>
      statements.map(() => ({
        success: true,
        meta: { changes: 1 }
      })) as D1Result<unknown>[]
    );
    const env = {
      UPLOAD_TOKEN: "token",
      MAPBOX_PUBLIC_TOKEN: "pk.worker-token",
      DB: ({
        prepare,
        batch
      } as unknown) as D1Database
    };

    const result = await createD1ProcessedHistoryPersistence().persistHistories(
      "device-1",
      "1.0.22",
      utcOffsetMinutes,
      [
        {
          historyId: 601,
          timestampMillis,
          distanceKm: 2.5,
          durationSeconds: 600,
          averageSpeedKmh: 15.0,
          title: "整理后",
          startSource: "AUTO",
          stopSource: "AUTO",
          manualStartAt: null,
          manualStopAt: null,
          points: []
        }
      ],
      env
    );

    expect(prepare).toHaveBeenCalledTimes(4);
    expect(prepare.mock.calls[0]?.[0]).toContain("SELECT history_id, timestamp_millis");
    expect(prepare.mock.calls[1]?.[0]).toContain("INSERT INTO processed_histories");
    expect(prepare.mock.calls[2]?.[0]).toContain("FROM processed_histories");
    expect(prepare.mock.calls[3]?.[0]).toContain("INSERT INTO history_day_summary");
    expect(batch).toHaveBeenCalledTimes(1);
    expect(bindCollector[2]?.args).toEqual([
      "device-1",
      expectedDayStart,
      expectedDayStart + 86_400_000
    ]);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0,
      acceptedHistoryIds: [601]
    });
  });

  it("reads history day summaries by aggregating processed histories", async () => {
    const { createD1HistoryDaySummaryPersistence } = await import("./d1");
    const timestampA = 1_700_000_000_000;
    const timestampB = 1_700_000_600_000;
    const dayStart = startOfDay(timestampA, 480);
    const { env, bindCollector } = createMockReadEnv([
      {
        sqlIncludes: "FROM processed_histories",
        rows: [
          {
            history_id: 502,
            timestamp_millis: timestampB,
            distance_km: 2.4,
            duration_seconds: 520
          },
          {
            history_id: 501,
            timestamp_millis: timestampA,
            distance_km: 2.5,
            duration_seconds: 600
          }
        ]
      }
    ]);

    const days = await createD1HistoryDaySummaryPersistence().readDays(
      "device-1",
      480,
      env
    );

    expect(bindCollector[0]?.args).toEqual(["device-1"]);
    expect(days).toHaveLength(1);
    expect(days[0]).toMatchObject({
      dayStartMillis: dayStart,
      latestTimestamp: timestampB,
      sessionCount: 2,
      totalDistanceKm: 4.9,
      totalDurationSeconds: 1120,
      sourceIds: [502, 501]
    });
    expect(days[0]?.averageSpeedKmh).toBeCloseTo(15.75);
  });

  it("recalculates history day summary distance from stored points when available", async () => {
    const { createD1HistoryDaySummaryPersistence } = await import("./d1");
    const timestamp = 1_700_000_000_000;
    const { env } = createMockReadEnv([
      {
        sqlIncludes: "FROM processed_histories",
        rows: [
          {
            history_id: 701,
            timestamp_millis: timestamp,
            distance_km: 99,
            duration_seconds: 600,
            points_json: JSON.stringify([
              {
                latitude: 30,
                longitude: 104,
                timestampMillis: timestamp,
                wgs84Latitude: 30,
                wgs84Longitude: 104
              },
              {
                latitude: 30,
                longitude: 104,
                timestampMillis: timestamp + 10_000,
                wgs84Latitude: 30,
                wgs84Longitude: 104
              }
            ])
          }
        ]
      }
    ]);

    const days = await createD1HistoryDaySummaryPersistence().readDays(
      "device-1",
      480,
      env
    );

    expect(days[0]?.totalDistanceKm).toBe(0);
    expect(days[0]?.averageSpeedKmh).toBe(0);
  });

  it("refreshes both old day and new day summaries when a processed history moves across days", async () => {
    const { createD1ProcessedHistoryPersistence } = await import("./d1");
    const originalTimestamp = 1_700_000_000_000;
    const movedTimestamp = originalTimestamp + 86_400_000;
    const { env, bindCollector } = createQueryAwareMockEnv(
      [1],
      [
        {
          sqlIncludes: "SELECT history_id, timestamp_millis",
          rows: [
            {
              history_id: 801,
              timestamp_millis: originalTimestamp
            }
          ]
        }
      ]
    );

    await createD1ProcessedHistoryPersistence().persistHistories(
      "device-1",
      "1.0.23",
      480,
      [
        {
          historyId: 801,
          timestampMillis: movedTimestamp,
          distanceKm: 2.8,
          durationSeconds: 600,
          averageSpeedKmh: 16.8,
          title: "跨天修正",
          startSource: "AUTO",
          stopSource: "AUTO",
          manualStartAt: null,
          manualStopAt: null,
          points: []
        }
      ],
      env
    );

    const refreshedDayStarts = bindCollector
      .filter(
        ({ sql }) =>
          sql.includes("FROM processed_histories") &&
          sql.includes("ORDER BY timestamp_millis DESC")
      )
      .map(({ args }) => args[1] as number);

    expect(refreshedDayStarts).toEqual(
      expect.arrayContaining([
        startOfDay(originalTimestamp, 480),
        startOfDay(movedTimestamp, 480)
      ])
    );
  });

  it("reads existing processed history timestamps in a single batched query", async () => {
    const { createD1ProcessedHistoryPersistence } = await import("./d1");
    const bindCollector: Array<{ sql: string; args: unknown[] }> = [];
    const prepare = vi.fn((sql: string) =>
      createMockPreparedStatement(sql, bindCollector, async (boundSql) => {
        if (boundSql.includes("SELECT history_id, timestamp_millis")) {
          return {
            success: true,
            results: [
              {
                history_id: 901,
                timestamp_millis: 1_700_000_000_000
              },
              {
                history_id: 902,
                timestamp_millis: 1_700_010_000_000
              }
            ]
          } as D1Result<unknown>;
        }
        if (boundSql.includes("FROM processed_histories")) {
          return {
            success: true,
            results: []
          } as D1Result<unknown>;
        }
        return {
          success: true,
          results: []
        } as D1Result<unknown>;
      })
    );
    const batch = vi.fn(async (statements: D1PreparedStatement[]) =>
      statements.map(() => ({
        success: true,
        meta: { changes: 1 }
      })) as D1Result<unknown>[]
    );
    const env = {
      UPLOAD_TOKEN: "token",
      MAPBOX_PUBLIC_TOKEN: "pk.worker-token",
      DB: ({
        prepare,
        batch
      } as unknown) as D1Database
    };

    await createD1ProcessedHistoryPersistence().persistHistories(
      "device-1",
      "1.0.24",
      480,
      [
        {
          historyId: 901,
          timestampMillis: 1_700_020_000_000,
          distanceKm: 1.2,
          durationSeconds: 300,
          averageSpeedKmh: 14.4,
          title: null,
          startSource: null,
          stopSource: null,
          manualStartAt: null,
          manualStopAt: null,
          points: []
        },
        {
          historyId: 902,
          timestampMillis: 1_700_030_000_000,
          distanceKm: 1.8,
          durationSeconds: 480,
          averageSpeedKmh: 13.5,
          title: null,
          startSource: null,
          stopSource: null,
          manualStartAt: null,
          manualStopAt: null,
          points: []
        }
      ],
      env
    );

    const timestampQueryBindings = bindCollector.filter(({ sql }) =>
      sql.includes("SELECT history_id, timestamp_millis")
    );
    expect(timestampQueryBindings).toHaveLength(1);
    expect(timestampQueryBindings[0]?.args).toEqual(["device-1", 901, 902]);
  });
});
