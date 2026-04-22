import { describe, expect, it } from "vitest";

import { createApp } from "./index";
import type { Env, RawPointPersistence } from "./types";

const baseEnv: Env = {
  UPLOAD_TOKEN: "correct-token",
  MAPBOX_PUBLIC_TOKEN: "pk.worker-token",
  DB: {} as D1Database
};

const executionContext: ExecutionContext = {
  waitUntil: () => undefined,
  passThroughOnException: () => undefined,
  props: {}
};

async function invokeApp(
  app: ReturnType<typeof createApp>,
  request: Request,
  env: Env = baseEnv
): Promise<Response> {
  return app.fetch!(request as never, env, executionContext);
}

function createPersistence(): RawPointPersistence {
  return {
    async persistRawPoints() {
      return {
        insertedCount: 0,
        dedupedCount: 0,
        acceptedMaxPointId: 0
      };
    },
    async readRawPointsByDay(deviceId, dayStartMillis) {
      if (deviceId !== "device-1" || dayStartMillis !== 1699920000000) {
        return [];
      }
      return [
        {
          pointId: 18,
          timestampMillis: 1699923600000,
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
          samplingTier: "ACTIVE"
        }
      ];
    },
    async readRawPointDays() {
      return [];
    }
  };
}

describe("GET /raw-points/day", () => {
  it("reads raw points for a day from persistence", async () => {
    const app = createApp({
      rawPointPersistence: createPersistence()
    });

    const response = await invokeApp(
      app,
      new Request(
        "https://worker.test/raw-points/day?deviceId=device-1&dayStartMillis=1699920000000",
        {
          method: "GET",
          headers: {
            Authorization: "Bearer correct-token"
          }
        }
      )
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      points: [
        {
          pointId: 18,
          timestampMillis: 1699923600000,
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
          samplingTier: "ACTIVE"
        }
      ]
    });
  });
});

describe("GET /raw-points/days", () => {
  it("reads available raw point days from persistence", async () => {
    const app = createApp({
      rawPointPersistence: {
        ...createPersistence(),
        async readRawPointDays(deviceId, utcOffsetMinutes) {
          expect(deviceId).toBe("device-1");
          expect(utcOffsetMinutes).toBe(480);
          return [
            {
              dayStartMillis: 1699891200000,
              pointCount: 128,
              maxPointId: 631
            }
          ];
        }
      }
    });

    const response = await invokeApp(
      app,
      new Request(
        "https://worker.test/raw-points/days?deviceId=device-1&utcOffsetMinutes=480",
        {
          method: "GET",
          headers: {
            Authorization: "Bearer correct-token"
          }
        }
      )
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      days: [
        {
          dayStartMillis: 1699891200000,
          pointCount: 128,
          maxPointId: 631
        }
      ]
    });
  });
});
