import { describe, expect, it } from "vitest";

import { createApp } from "./index";
import type {
  DiagnosticLog,
  DiagnosticLogPersistence,
  Env,
  PersistDiagnosticLogsResult
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
  return app.fetch!(request as any, env, executionContext);
}

class MemoryDiagnosticLogPersistence implements DiagnosticLogPersistence {
  private readonly logs: DiagnosticLog[] = [];

  async persistLogs(
    deviceId: string,
    appVersion: string,
    logs: DiagnosticLog[]
  ): Promise<PersistDiagnosticLogsResult> {
    let insertedCount = 0;
    let dedupedCount = 0;
    for (const log of logs) {
      const existing = this.logs.find(
        (item) => item.deviceId === deviceId && item.fingerprint === log.fingerprint
      );
      if (existing) {
        dedupedCount += 1;
        existing.lastSeenAt = log.occurredAt;
        existing.occurrenceCount += 1;
      } else {
        insertedCount += 1;
        this.logs.push({
          ...log,
          deviceId,
          appVersion,
          status: "open",
          occurrenceCount: 1,
          firstSeenAt: log.occurredAt,
          lastSeenAt: log.occurredAt
        });
      }
    }
    return { insertedCount, dedupedCount };
  }

  async readLogs(deviceId: string, filters: { status?: string; type?: string }): Promise<DiagnosticLog[]> {
    return this.logs.filter(
      (log) =>
        log.deviceId === deviceId &&
        (filters.status == null || log.status === filters.status) &&
        (filters.type == null || log.type === filters.type)
    );
  }

  async resolveLogs(deviceId: string, fingerprints: string[]): Promise<number> {
    let resolvedCount = 0;
    for (const log of this.logs) {
      if (log.deviceId === deviceId && fingerprints.includes(log.fingerprint) && log.status !== "resolved") {
        log.status = "resolved";
        resolvedCount += 1;
      }
    }
    return resolvedCount;
  }

  async deleteResolvedBefore(): Promise<number> {
    return 0;
  }
}

function authHeaders(): HeadersInit {
  return {
    Authorization: "Bearer correct-token",
    "content-type": "application/json"
  };
}

describe("diagnostic log routes", () => {
  it("stores diagnostic logs and returns insert counts", async () => {
    const app = createApp({ diagnosticLogPersistence: new MemoryDiagnosticLogPersistence() });

    const response = await invokeApp(
      app,
      new Request("https://worker.test/diagnostics/logs/batch", {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify({
          deviceId: "device-1",
          appVersion: "1.0.23",
          logs: [
            {
              logId: "log-1",
              occurredAt: 1700000000000,
              type: "ERROR",
              severity: "ERROR",
              source: "AnalysisUploadWorker",
              message: "upload failed",
              fingerprint: "analysis-upload-failed",
              payload: { worker: "analysis" }
            }
          ]
        })
      })
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      insertedCount: 1,
      dedupedCount: 0
    });
  });

  it("reads open performance logs by device", async () => {
    const persistence = new MemoryDiagnosticLogPersistence();
    await persistence.persistLogs("device-1", "1.0.23", [
      {
        logId: "perf-1",
        deviceId: "device-1",
        appVersion: "1.0.23",
        occurredAt: 1700000000000,
        type: "PERF_WARN",
        severity: "WARN",
        source: "RawPointUploadWorker",
        message: "raw upload took 2800ms",
        fingerprint: "raw-upload-slow",
        payload: { durationMs: 2800 },
        status: "open",
        occurrenceCount: 1,
        firstSeenAt: 1700000000000,
        lastSeenAt: 1700000000000
      }
    ]);
    const app = createApp({ diagnosticLogPersistence: persistence });

    const response = await invokeApp(
      app,
      new Request("https://worker.test/diagnostics/logs?deviceId=device-1&status=open&type=PERF_WARN", {
        method: "GET",
        headers: { Authorization: "Bearer correct-token" }
      })
    );

    expect(response.status).toBe(200);
    const body = await response.json() as { ok: true; logs: DiagnosticLog[] };
    expect(body.ok).toBe(true);
    expect(body.logs).toHaveLength(1);
    expect(body.logs[0].fingerprint).toBe("raw-upload-slow");
  });

  it("resolves logs by fingerprint", async () => {
    const persistence = new MemoryDiagnosticLogPersistence();
    await persistence.persistLogs("device-1", "1.0.23", [
      {
        logId: "error-1",
        deviceId: "device-1",
        appVersion: "1.0.23",
        occurredAt: 1700000000000,
        type: "ERROR",
        severity: "ERROR",
        source: "RawPointUploadWorker",
        message: "timeout",
        fingerprint: "raw-upload-timeout",
        payload: null,
        status: "open",
        occurrenceCount: 1,
        firstSeenAt: 1700000000000,
        lastSeenAt: 1700000000000
      }
    ]);
    const app = createApp({ diagnosticLogPersistence: persistence });

    const response = await invokeApp(
      app,
      new Request("https://worker.test/diagnostics/logs/resolve", {
        method: "POST",
        headers: authHeaders(),
        body: JSON.stringify({
          deviceId: "device-1",
          fingerprints: ["raw-upload-timeout"]
        })
      })
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({ ok: true, resolvedCount: 1 });
  });
});
