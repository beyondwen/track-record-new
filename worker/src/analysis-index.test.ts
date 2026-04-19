import { describe, expect, it } from "vitest";

import { createApp } from "./index";
import type { AnalysisPersistence, Env } from "./types";

const baseEnv: Env = {
  UPLOAD_TOKEN: "correct-token",
  HYPERDRIVE: {
    host: "127.0.0.1",
    port: 3306,
    user: "worker",
    password: "worker-pass",
    database: "track_record"
  }
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
  existingSegmentIds: number[] = []
): AnalysisPersistence {
  const existing = new Set(existingSegmentIds);
  return {
    async persistAnalysis(_deviceId, _appVersion, segments) {
      const acceptedSegmentIds = [...new Set(segments.map((segment) => segment.segmentId))];
      const insertedSegmentIds = acceptedSegmentIds.filter(
        (segmentId) => !existing.has(segmentId)
      );
      for (const segmentId of insertedSegmentIds) {
        existing.add(segmentId);
      }

      return {
        insertedCount: insertedSegmentIds.length,
        dedupedCount: segments.length - insertedSegmentIds.length,
        acceptedMaxSegmentId:
          acceptedSegmentIds.length === 0 ? 0 : Math.max(...acceptedSegmentIds)
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
  return new Request("https://worker.test/analysis/batch", {
    method: "POST",
    headers,
    body
  });
}

describe("POST /analysis/batch", () => {
  it("returns 400 when segments is not an array", async () => {
    const app = createApp({
      analysisPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.23",
          segments: "not-an-array"
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
      message: "`segments` must be an array"
    });
  });

  it("returns success json when request is valid", async () => {
    const app = createApp({
      analysisPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.23",
          segments: [
            {
              segmentId: 31,
              startPointId: 11,
              endPointId: 19,
              startTimestamp: 1700000000000,
              endTimestamp: 1700000300000,
              segmentType: "STATIC",
              confidence: 0.95,
              distanceMeters: 18.0,
              durationMillis: 300000,
              avgSpeedMetersPerSecond: 0.1,
              maxSpeedMetersPerSecond: 0.3,
              analysisVersion: 1,
              stayClusters: [
                {
                  stayId: 201,
                  centerLat: 30.1,
                  centerLng: 120.1,
                  radiusMeters: 25.0,
                  arrivalTime: 1700000000000,
                  departureTime: 1700000300000,
                  confidence: 0.91,
                  analysisVersion: 1
                }
              ]
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
      acceptedMaxSegmentId: 31
    });
  });
});
