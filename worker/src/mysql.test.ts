import { describe, expect, it, vi, beforeEach } from "vitest";
import type { ResultSetHeader } from "mysql2";

const createConnection = vi.fn();

vi.mock("mysql2/promise", () => ({
  default: {
    createConnection
  }
}));

function mockResultSetHeader(affectedRows: number): ResultSetHeader {
  return {
    fieldCount: 0,
    affectedRows,
    insertId: 0,
    info: "",
    serverStatus: 0,
    warningStatus: 0,
    changedRows: 0
  } as ResultSetHeader;
}

describe("createMysqlSamplePersistence", () => {
  beforeEach(() => {
    createConnection.mockReset();
  });

  it("uses text protocol query for raw point persistence", async () => {
    const query = vi
      .fn()
      .mockResolvedValueOnce([mockResultSetHeader(1)]);
    const end = vi.fn().mockResolvedValue(undefined);
    createConnection.mockResolvedValue({
      query,
      end
    });

    const { createMysqlRawPointPersistence } = await import("./mysql");

    const result = await createMysqlRawPointPersistence().persistRawPoints(
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
      {
        UPLOAD_TOKEN: "token",
        HYPERDRIVE: {
          host: "127.0.0.1",
          port: 3306,
          user: "worker",
          password: "pass",
          database: "track_record"
        }
      }
    );

    expect(query).toHaveBeenCalledTimes(1);
    expect(query.mock.calls[0]?.[0]).toContain("INSERT IGNORE INTO raw_location_point");
    expect(end).toHaveBeenCalledTimes(1);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0,
      acceptedMaxPointId: 18
    });
  });

  it("uses text protocol query for analysis persistence and inserts stay clusters", async () => {
    const query = vi
      .fn()
      .mockResolvedValueOnce([mockResultSetHeader(1)])
      .mockResolvedValueOnce([mockResultSetHeader(1)]);
    const end = vi.fn().mockResolvedValue(undefined);
    createConnection.mockResolvedValue({
      query,
      end
    });

    const { createMysqlAnalysisPersistence } = await import("./mysql");

    const result = await createMysqlAnalysisPersistence().persistAnalysis(
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
      {
        UPLOAD_TOKEN: "token",
        HYPERDRIVE: {
          host: "127.0.0.1",
          port: 3306,
          user: "worker",
          password: "pass",
          database: "track_record"
        }
      }
    );

    expect(query).toHaveBeenCalledTimes(2);
    expect(query.mock.calls[0]?.[0]).toContain("INSERT IGNORE INTO analysis_segment");
    expect(query.mock.calls[1]?.[0]).toContain("INSERT IGNORE INTO stay_cluster");
    expect(end).toHaveBeenCalledTimes(1);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0,
      acceptedMaxSegmentId: 31
    });
  });

  it("uses text protocol query instead of execute for Hyperdrive compatibility", async () => {
    const query = vi.fn().mockResolvedValue([mockResultSetHeader(1)]);
    const end = vi.fn().mockResolvedValue(undefined);
    createConnection.mockResolvedValue({
      query,
      end
    });

    const { createMysqlSamplePersistence } = await import("./mysql");

    const result = await createMysqlSamplePersistence().persistSamples(
      [
        {
          eventId: 101,
          timestampMillis: 1700000000000,
          phase: "tracking",
          finalDecision: "allow",
          features: { score: 0.91 }
        }
      ],
      {
        UPLOAD_TOKEN: "token",
        HYPERDRIVE: {
          host: "127.0.0.1",
          port: 3306,
          user: "worker",
          password: "pass",
          database: "track_record"
        }
      }
    );

    expect(query).toHaveBeenCalledTimes(1);
    expect(end).toHaveBeenCalledTimes(1);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0,
      acceptedEventIds: [101]
    });
  });

  it("uses text protocol query for history persistence too", async () => {
    const query = vi.fn().mockResolvedValue([mockResultSetHeader(1)]);
    const end = vi.fn().mockResolvedValue(undefined);
    createConnection.mockResolvedValue({
      query,
      end
    });

    const { createMysqlHistoryPersistence } = await import("./mysql");

    const result = await createMysqlHistoryPersistence().persistHistories(
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
      {
        UPLOAD_TOKEN: "token",
        HYPERDRIVE: {
          host: "127.0.0.1",
          port: 3306,
          user: "worker",
          password: "pass",
          database: "track_record"
        }
      }
    );

    expect(query).toHaveBeenCalledTimes(1);
    expect(end).toHaveBeenCalledTimes(1);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0,
      acceptedHistoryIds: [201]
    });
  });
});
