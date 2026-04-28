import { describe, expect, it } from "vitest";

import { createApp } from "./index";
import type { Env } from "./types";

const env: Env = {
  UPLOAD_TOKEN: "correct-token",
  MAPBOX_PUBLIC_TOKEN: "pk.worker-token",
  DB: {} as D1Database
};

const executionContext: ExecutionContext = {
  waitUntil: () => undefined,
  passThroughOnException: () => undefined,
  props: {}
};

async function invoke(request: Request): Promise<Response> {
  return createApp().fetch!(request as any, env, executionContext);
}

describe("GET /health", () => {
  it("returns health response without authentication", async () => {
    const response = await invoke(
      new Request("https://worker.test/health", { method: "GET" }) as any
    );

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      ok: true,
      message: "ok"
    });
  });
});
