import { describe, expect, it } from "vitest";

import { createApp } from "./index";
import type { Env, TodaySessionPersistence } from "./types";

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

function createTodaySessionPersistence(
  snapshot: Awaited<ReturnType<TodaySessionPersistence["readLatestOpenSession"]>> = null
): TodaySessionPersistence {
  return {
    async persistSessions() {
      return { insertedCount: 1, dedupedCount: 0 };
    },
    async persistSessionPoints() {
      return { insertedCount: 1, dedupedCount: 0 };
    },
    async readLatestOpenSession() {
      return snapshot;
    }
  };
}

describe("today session routes", () => {
  it("accepts today session batch uploads", async () => {
    const app = createApp({
      todaySessionPersistence: createTodaySessionPersistence()
    });

    const response = await invokeApp(
      app,
      new Request("https://worker.example.com/today-sessions/batch", {
        method: "POST",
        headers: {
          Authorization: "Bearer correct-token",
          "content-type": "application/json"
        },
        body: JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.0",
          sessions: [
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
          ]
        })
      })
    );

    expect(response.status).toBe(200);
  });

  it("accepts today session point batch uploads", async () => {
    const app = createApp({
      todaySessionPersistence: createTodaySessionPersistence()
    });

    const response = await invokeApp(
      app,
      new Request("https://worker.example.com/today-session-points/batch", {
        method: "POST",
        headers: {
          Authorization: "Bearer correct-token",
          "content-type": "application/json"
        },
        body: JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.0",
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
        })
      })
    );

    expect(response.status).toBe(200);
  });

  it("reads latest open today session snapshot", async () => {
    const app = createApp({
      todaySessionPersistence: createTodaySessionPersistence({
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
      })
    });

    const response = await invokeApp(
      app,
      new Request(
        "https://worker.example.com/today-sessions/open?deviceId=device-1",
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
