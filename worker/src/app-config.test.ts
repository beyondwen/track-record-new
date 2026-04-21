import { describe, expect, it } from "vitest";

import { createApp } from "./index";
import type { Env } from "./types";

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

async function invoke(request: Request, env: Env = baseEnv): Promise<Response> {
  const app = createApp();
  return app.fetch!(request as any, env, executionContext);
}

describe("GET /app-config", () => {
  it("returns mapbox public token when bearer token is valid", async () => {
    const response = await invoke(
      new Request("https://worker.test/app-config", {
        method: "GET",
        headers: {
          Authorization: "Bearer correct-token"
        }
      })
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      mapboxPublicToken: "pk.worker-token"
    });
  });

  it("returns forbidden when bearer token is invalid", async () => {
    const response = await invoke(
      new Request("https://worker.test/app-config", {
        method: "GET",
        headers: {
          Authorization: "Bearer wrong-token"
        }
      })
    );

    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "Forbidden"
    });
  });

  it("returns 503 when mapbox public token is not configured", async () => {
    const response = await invoke(
      new Request("https://worker.test/app-config", {
        method: "GET",
        headers: {
          Authorization: "Bearer correct-token"
        }
      }),
      {
        ...baseEnv,
        MAPBOX_PUBLIC_TOKEN: "   "
      }
    );

    expect(response.status).toBe(503);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "Mapbox public token is not configured"
    });
  });
});
