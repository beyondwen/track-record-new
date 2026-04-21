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
  return app.fetch!(request as any, env, executionContext);
}

function createSemanticMockPersistence(
  existingPointIds: number[] = []
): RawPointPersistence {
  const existing = new Set(existingPointIds);
  return {
    async persistRawPoints(_deviceId, _appVersion, points) {
      const acceptedPointIds = [...new Set(points.map((point) => point.pointId))];
      const insertedPointIds = acceptedPointIds.filter((pointId) => !existing.has(pointId));
      for (const pointId of insertedPointIds) {
        existing.add(pointId);
      }

      return {
        insertedCount: insertedPointIds.length,
        dedupedCount: points.length - insertedPointIds.length,
        acceptedMaxPointId: acceptedPointIds.length === 0 ? 0 : Math.max(...acceptedPointIds)
      };
    }
  };
}

function requestFor(
  body: string,
  options: {
    token?: string;
    contentType?: string;
  } = {}
): Request {
  const headers: HeadersInit = {};
  if (options.contentType) {
    headers["content-type"] = options.contentType;
  }
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }
  return new Request("https://worker.test/raw-points/batch", {
    method: "POST",
    headers,
    body
  });
}

describe("POST /raw-points/batch", () => {
  it("returns 400 when points is not an array", async () => {
    const app = createApp({
      rawPointPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.23",
          points: "not-an-array"
        }),
        {
          token: "correct-token",
          contentType: "application/json"
        }
      )
    );

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "`points` must be an array"
    });
  });

  it("returns success json when request is valid", async () => {
    const app = createApp({
      rawPointPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.23",
          points: [
            {
              pointId: 18,
              timestampMillis: 1700000000000,
              latitude: 30.1,
              longitude: 120.1,
              accuracyMeters: 5.5,
              altitudeMeters: 20.2,
              speedMetersPerSecond: 1.1,
              bearingDegrees: 90.0,
              provider: "gps",
              sourceType: "LOCATION_MANAGER",
              isMock: false,
              wifiFingerprintDigest: "wifi",
              activityType: "WALKING",
              activityConfidence: 0.9,
              samplingTier: "IDLE"
            }
          ]
        }),
        {
          token: "correct-token",
          contentType: "application/json"
        }
      )
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      insertedCount: 1,
      dedupedCount: 0,
      acceptedMaxPointId: 18
    });
  });
});
