import { describe, expect, it } from "vitest";

import { createApp } from "./index";
import type { Env, HistoryPersistence } from "./types";

const baseEnv: Env = {
  UPLOAD_TOKEN: "correct-token",
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
  existingHistoryIds: number[] = []
): HistoryPersistence {
  const existing = new Set(existingHistoryIds);
  return {
    async persistHistories(_deviceId, _appVersion, histories) {
      const acceptedHistoryIds = [...new Set(histories.map((history) => history.historyId))];
      const insertedHistoryIds = acceptedHistoryIds.filter(
        (historyId) => !existing.has(historyId)
      );
      for (const historyId of insertedHistoryIds) {
        existing.add(historyId);
      }

      const insertedCount = insertedHistoryIds.length;
      return {
        insertedCount,
        dedupedCount: histories.length - insertedCount,
        acceptedHistoryIds
      };
    },
    async readHistories(deviceId) {
      if (deviceId !== "device-1") {
        return [];
      }
      return [
        {
          historyId: 101,
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
      ];
    },
    async readHistoriesByDay(deviceId, dayStartMillis) {
      if (deviceId !== "device-1" || dayStartMillis !== 1699920000000) {
        return [];
      }
      return [
        {
          historyId: 102,
          timestampMillis: 1699923600000,
          distanceKm: 2.34,
          durationSeconds: 789,
          averageSpeedKmh: 10.7,
          title: "日常",
          startSource: "AUTO",
          stopSource: "AUTO",
          manualStartAt: null,
          manualStopAt: null,
          points: []
        }
      ];
    }
  };
}

function requestFor(
  pathname: string,
  body: string,
  options: {
    token?: string;
    contentType?: string;
    method?: string;
  } = {}
): Request {
  const headers: HeadersInit = {};
  if (options.contentType) {
    headers["content-type"] = options.contentType;
  }
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }
  const method = options.method ?? "POST";
  return new Request(`https://worker.test${pathname}`, {
    method,
    headers,
    ...(method === "GET" || method === "HEAD" ? {} : { body })
  });
}

describe("POST /histories/batch", () => {
  it("returns 400 when histories is not an array", async () => {
    const app = createApp({
      historyPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        "/histories/batch",
        JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.22",
          histories: "not-an-array"
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
      message: "`histories` must be an array"
    });
  });

  it("returns success json when request is valid", async () => {
    const app = createApp({
      historyPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        "/histories/batch",
        JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.22",
          histories: [
            {
              historyId: 101,
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
      acceptedHistoryIds: [101]
    });
  });

  it("returns underlying persistence error message for 500 response", async () => {
    const app = createApp({
      historyPersistence: {
        async persistHistories() {
          throw new Error("Table 'track_record.uploaded_histories' doesn't exist");
        }
      }
    });

    const response = await invokeApp(
      app,
      requestFor(
        "/histories/batch",
        JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.22",
          histories: [
            {
              historyId: 101,
              timestampMillis: 1700000000000,
              distanceKm: 1.23,
              durationSeconds: 456,
              averageSpeedKmh: 9.8,
              points: []
            }
          ]
        }),
        {
          token: "correct-token",
          contentType: "application/json"
        }
      )
    );

    expect(response.status).toBe(500);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "Table 'track_record.uploaded_histories' doesn't exist"
    });
  });

  it("counts dedupe from both in-batch duplicates and existing history ids", async () => {
    const app = createApp({
      historyPersistence: createSemanticMockPersistence([8])
    });

    const response = await invokeApp(
      app,
      requestFor(
        "/histories/batch",
        JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.22",
          histories: [
            {
              historyId: 7,
              timestampMillis: 1700000000000,
              distanceKm: 1.23,
              durationSeconds: 456,
              averageSpeedKmh: 9.8,
              points: []
            },
            {
              historyId: 7,
              timestampMillis: 1700000000100,
              distanceKm: 1.24,
              durationSeconds: 457,
              averageSpeedKmh: 9.9,
              points: []
            },
            {
              historyId: 8,
              timestampMillis: 1700000000200,
              distanceKm: 1.25,
              durationSeconds: 458,
              averageSpeedKmh: 10.0,
              points: []
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
      dedupedCount: 2,
      acceptedHistoryIds: [7, 8]
    });
  });
});

describe("GET /histories", () => {
  it("returns uploaded histories for the device", async () => {
    const app = createApp({
      historyPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor("/histories?deviceId=device-1", "", {
        method: "GET",
        token: "correct-token"
      })
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      histories: [
        {
          historyId: 101,
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
      ]
    });
  });

  it("returns 400 when deviceId is blank", async () => {
    const app = createApp({
      historyPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor("/histories?deviceId=%20%20", "", {
        method: "GET",
        token: "correct-token"
      })
    );

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "`deviceId` must be a non-empty string"
    });
  });
});

describe("GET /histories/day", () => {
  it("returns uploaded histories for a specific day", async () => {
    const app = createApp({
      historyPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        "/histories/day?deviceId=device-1&dayStartMillis=1699920000000",
        "",
        {
          method: "GET",
          token: "correct-token"
        }
      )
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      histories: [
        {
          historyId: 102,
          timestampMillis: 1699923600000,
          distanceKm: 2.34,
          durationSeconds: 789,
          averageSpeedKmh: 10.7,
          title: "日常",
          startSource: "AUTO",
          stopSource: "AUTO",
          manualStartAt: null,
          manualStopAt: null,
          points: []
        }
      ]
    });
  });

  it("returns 400 when dayStartMillis is invalid", async () => {
    const app = createApp({
      historyPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor("/histories/day?deviceId=device-1&dayStartMillis=oops", "", {
        method: "GET",
        token: "correct-token"
      })
    );

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "`dayStartMillis` must be an integer"
    });
  });
});

describe("GET /health", () => {
  it("returns health response without authentication", async () => {
    const app = createApp({
      historyPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor("/health", "", {
        method: "GET"
      })
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      message: "ok"
    });
  });
});
