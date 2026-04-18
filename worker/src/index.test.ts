import { describe, expect, it, vi } from "vitest";

import { createApp, type SamplePersistence } from "./index";
import type { Env } from "./types";

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
  existingEventIds: number[] = []
): SamplePersistence {
  const existing = new Set(existingEventIds);
  return {
    async persistSamples(samples) {
      const acceptedEventIds = [...new Set(samples.map((sample) => sample.eventId))];
      const insertedEventIds = acceptedEventIds.filter((eventId) => !existing.has(eventId));
      for (const eventId of insertedEventIds) {
        existing.add(eventId);
      }

      const insertedCount = insertedEventIds.length;
      return {
        insertedCount,
        dedupedCount: samples.length - insertedCount,
        acceptedEventIds
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
  return new Request("https://worker.test/samples/batch", {
    method: "POST",
    headers,
    body
  });
}

describe("POST /samples/batch", () => {
  it("returns 401 when authorization header missing", async () => {
    const persistSamples = vi.fn();
    const app = createApp({
      samplePersistence: {
        persistSamples
      }
    });

    const response = await invokeApp(
      app,
      requestFor(JSON.stringify({ samples: [] }), {
        contentType: "application/json"
      })
    );

    expect(response.status).toBe(401);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "Missing or malformed Authorization header"
    });
    expect(persistSamples).not.toHaveBeenCalled();
  });

  it("returns 403 when token mismatched", async () => {
    const persistSamples = vi.fn();
    const app = createApp({
      samplePersistence: {
        persistSamples
      }
    });

    const response = await invokeApp(
      app,
      requestFor(JSON.stringify({ samples: [] }), {
        token: "wrong-token",
        contentType: "application/json"
      })
    );

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "Forbidden"
    });
    expect(persistSamples).not.toHaveBeenCalled();
  });

  it("returns 400 when body is invalid", async () => {
    const app = createApp({
      samplePersistence: {
        persistSamples: vi.fn()
      }
    });

    const response = await invokeApp(
      app,
      requestFor("not-json", {
        token: "correct-token",
        contentType: "application/json"
      })
    );

    expect(response.status).toBe(400);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "Request body must be valid JSON"
    });
  });

  it("returns 400 when samples is not an array", async () => {
    const app = createApp({
      samplePersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          samples: "not-an-array"
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
      message: "`samples` must be an array"
    });
  });

  it("returns 400 when features is not an object", async () => {
    const app = createApp({
      samplePersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          samples: [
            {
              eventId: 201,
              timestampMillis: 1700000000000,
              phase: "tracking",
              finalDecision: "allow",
              features: ["not", "object"]
            }
          ]
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
      message: "samples[0].features must be an object"
    });
  });

  it("returns 400 when phase exceeds schema length", async () => {
    const app = createApp({
      samplePersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          samples: [
            {
              eventId: 301,
              timestampMillis: 1700000000000,
              phase: "x".repeat(65),
              finalDecision: "allow",
              features: { score: 0.9 }
            }
          ]
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
      message: "samples[0].phase length must be <= 64"
    });
  });

  it("returns success json when request is valid", async () => {
    const app = createApp({
      samplePersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          samples: [
            {
              eventId: 101,
              timestampMillis: 1700000000000,
              phase: "tracking",
              finalDecision: "allow",
              features: { score: 0.91 }
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
      acceptedEventIds: [101]
    });
  });

  it("returns underlying persistence error message for 500 response", async () => {
    const app = createApp({
      samplePersistence: {
        async persistSamples() {
          throw new Error("Table 'track_record.training_samples' doesn't exist");
        }
      }
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          samples: [
            {
              eventId: 101,
              timestampMillis: 1700000000000,
              phase: "tracking",
              finalDecision: "allow",
              features: { score: 0.91 }
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
      message: "Table 'track_record.training_samples' doesn't exist"
    });
  });

  it("returns deduped result when request contains duplicated event ids", async () => {
    const app = createApp({
      samplePersistence: createSemanticMockPersistence()
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          samples: [
            {
              eventId: 7,
              timestampMillis: 1700000000000,
              phase: "tracking",
              finalDecision: "allow",
              features: { score: 0.1 }
            },
            {
              eventId: 7,
              timestampMillis: 1700000000500,
              phase: "tracking",
              finalDecision: "allow",
              features: { score: 0.2 }
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
      dedupedCount: 1,
      acceptedEventIds: [7]
    });
  });

  it("counts dedupe from both in-batch duplicates and existing event ids", async () => {
    const app = createApp({
      samplePersistence: createSemanticMockPersistence([8])
    });

    const response = await invokeApp(
      app,
      requestFor(
        JSON.stringify({
          samples: [
            {
              eventId: 7,
              timestampMillis: 1700000000000,
              phase: "tracking",
              finalDecision: "allow",
              features: { score: 0.1 }
            },
            {
              eventId: 7,
              timestampMillis: 1700000000100,
              phase: "tracking",
              finalDecision: "allow",
              features: { score: 0.2 }
            },
            {
              eventId: 8,
              timestampMillis: 1700000000200,
              phase: "tracking",
              finalDecision: "allow",
              features: { score: 0.3 }
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
      acceptedEventIds: [7, 8]
    });
  });
});
