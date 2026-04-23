import { describe, expect, it } from "vitest";

import { createApp } from "./index";
import type {
  Env,
  HistoryDaySummaryPersistence,
  ProcessedHistoryPersistence
} from "./types";

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

function createSemanticMockPersistence(
  existingHistoryIds: number[] = []
): ProcessedHistoryPersistence {
  const existing = new Set(existingHistoryIds);
  return {
    async persistHistories(_deviceId, _appVersion, _utcOffsetMinutes, histories) {
      const acceptedHistoryIds = [...new Set(histories.map((history) => history.historyId))];
      const insertedHistoryIds = acceptedHistoryIds.filter(
        (historyId) => !existing.has(historyId)
      );
      for (const historyId of acceptedHistoryIds) {
        existing.add(historyId);
      }

      return {
        insertedCount: insertedHistoryIds.length,
        dedupedCount: histories.length - insertedHistoryIds.length,
        acceptedHistoryIds
      };
    },
    async readHistories(deviceId) {
      if (deviceId !== "device-1") {
        return [];
      }
      return [
        {
          historyId: 501,
          timestampMillis: 1700000000000,
          distanceKm: 1.5,
          durationSeconds: 400,
          averageSpeedKmh: 13.5,
          title: "云端处理",
          startSource: "AUTO",
          stopSource: "AUTO",
          manualStartAt: null,
          manualStopAt: null,
          points: []
        }
      ];
    },
    async readHistoriesByDay(deviceId, dayStartMillis) {
      if (deviceId !== "device-1" || dayStartMillis !== 1699920000000) {
        return [];
      }
      return [
        {
          historyId: 502,
          timestampMillis: 1699923600000,
          distanceKm: 3.4,
          durationSeconds: 720,
          averageSpeedKmh: 17,
          title: "按天结果",
          startSource: "AUTO",
          stopSource: "AUTO",
          manualStartAt: null,
          manualStopAt: null,
          points: []
        }
      ];
    },
    async deleteHistoriesByDay() {
      return 1;
    }
  };
}

function createMockHistoryDaySummaryPersistence(): HistoryDaySummaryPersistence {
  return {
    async readDays(deviceId, utcOffsetMinutes) {
      if (deviceId !== "device-1") {
        return [];
      }
      expect(utcOffsetMinutes).toBe(480);
      return [
        {
          dayStartMillis: 1699920000000,
          latestTimestamp: 1699923600000,
          sessionCount: 2,
          totalDistanceKm: 4.9,
          totalDurationSeconds: 1120,
          averageSpeedKmh: 15.75,
          sourceIds: [501, 502]
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

describe("POST /processed-histories/batch", () => {
  it("returns success json when request is valid", async () => {
    const app = createApp({
      processedHistoryPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        "/processed-histories/batch",
        JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.22",
          utcOffsetMinutes: 480,
          histories: [
            {
              historyId: 501,
              timestampMillis: 1700000000000,
              distanceKm: 1.5,
              durationSeconds: 400,
              averageSpeedKmh: 13.5,
              title: "云端处理",
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
      dedupedCount: 0,
      acceptedHistoryIds: [501]
    });
  });
});

describe("GET /processed-histories", () => {
  it("reads processed histories from persistence", async () => {
    const app = createApp({
      processedHistoryPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor("/processed-histories?deviceId=device-1", "", {
        token: "correct-token",
        method: "GET"
      })
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      histories: [
        {
          historyId: 501,
          timestampMillis: 1700000000000,
          distanceKm: 1.5,
          durationSeconds: 400,
          averageSpeedKmh: 13.5,
          title: "云端处理",
          startSource: "AUTO",
          stopSource: "AUTO",
          manualStartAt: null,
          manualStopAt: null,
          points: []
        }
      ]
    });
  });
});

describe("GET /processed-histories/day", () => {
  it("reads processed histories for a day from persistence", async () => {
    const app = createApp({
      processedHistoryPersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        "/processed-histories/day?deviceId=device-1&dayStartMillis=1699920000000",
        "",
        {
          token: "correct-token",
          method: "GET"
        }
      )
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      histories: [
        {
          historyId: 502,
          timestampMillis: 1699923600000,
          distanceKm: 3.4,
          durationSeconds: 720,
          averageSpeedKmh: 17,
          title: "按天结果",
          startSource: "AUTO",
          stopSource: "AUTO",
          manualStartAt: null,
          manualStopAt: null,
          points: []
        }
      ]
    });
  });
});

describe("DELETE /processed-histories/day", () => {
  it("deletes processed histories for a day through persistence", async () => {
    let deletedArgs: { deviceId: string; dayStartMillis: number } | null = null;
    const app = createApp({
      processedHistoryPersistence: {
        ...createSemanticMockPersistence(),
        async deleteHistoriesByDay(deviceId, dayStartMillis) {
          deletedArgs = { deviceId, dayStartMillis };
          return 2;
        }
      }
    });

    const response = await invokeApp(
      app,
      requestFor(
        "/processed-histories/day?deviceId=device-1&dayStartMillis=1699920000000",
        "",
        {
          token: "correct-token",
          method: "DELETE"
        }
      )
    );

    expect(deletedArgs).toEqual({
      deviceId: "device-1",
      dayStartMillis: 1699920000000
    });
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      message: "deleted"
    });
  });
});

describe("GET /history-days", () => {
  it("reads history day summaries from persistence", async () => {
    const app = createApp({
      historyDaySummaryPersistence: createMockHistoryDaySummaryPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor("/history-days?deviceId=device-1&utcOffsetMinutes=480", "", {
        token: "correct-token",
        method: "GET"
      })
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      days: [
        {
          dayStartMillis: 1699920000000,
          latestTimestamp: 1699923600000,
          sessionCount: 2,
          totalDistanceKm: 4.9,
          totalDurationSeconds: 1120,
          averageSpeedKmh: 15.75,
          sourceIds: [501, 502]
        }
      ]
    });
  });
});
