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
      } as unknown as D1Result<unknown>);
    }
  } as D1PreparedStatement;
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
    const { env, prepare, batch, bindCollector } = createQueryAwareMockEnv(
      [1],
      [
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
              sampling_tier: "IDLE"
            }
          ]
        }
      ]
    );

    const result = await createD1RawPointPersistence().persistRawPoints(
      "device-1",
      "1.0.23",
      480,
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

    expect(prepare).toHaveBeenCalledTimes(3);
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
    expect(bindCollector[1]?.args).toEqual([
      "device-1",
      startOfDay(1700000000000, 480),
      startOfDay(1700000000000, 480) + 86_400_000
    ]);
    expect(bindCollector[2]?.args).toEqual([
      "device-1",
      480,
      startOfDay(1700000000000, 480),
      1700000000000,
      1700000000000,
      1,
      18,
      0,
      0,
      0
    ]);
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
        sqlIncludes: "FROM raw_point_day_summary",
        rows: [
          {
            day_start_millis: 1699891200000,
            first_point_at: 1699891200000,
            last_point_at: 1699920000000,
            point_count: 128,
            max_point_id: 631,
            total_distance_km: 12.5,
            total_duration_seconds: 28800,
            average_speed_kmh: 1.5625
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
      "device-1",
      480
    ]);
    expect(days).toEqual([
      {
        dayStartMillis: 1699891200000,
        firstPointAt: 1699891200000,
        lastPointAt: 1699920000000,
        pointCount: 128,
        maxPointId: 631,
        totalDistanceKm: 12.5,
        totalDurationSeconds: 28800,
        averageSpeedKmh: 1.5625
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
});
