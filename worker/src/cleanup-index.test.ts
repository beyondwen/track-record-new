import { describe, expect, it } from "vitest";

import { createApp } from "./index";
import type { Env } from "./types";

const env: Env = {
  UPLOAD_TOKEN: "correct-token",
  DB: {} as D1Database
};

const executionContext: ExecutionContext = {
  waitUntil: () => undefined,
  passThroughOnException: () => undefined,
  props: {}
};

describe("worker cleanup", () => {
  it("returns 404 for removed training sample endpoint", async () => {
    const app = createApp();
    const response = await app.fetch!(
      new Request("https://worker.test/samples/batch", {
        method: "POST",
        headers: {
          Authorization: "Bearer correct-token",
          "content-type": "application/json"
        },
        body: JSON.stringify({ samples: [] })
      }) as any,
      env,
      executionContext
    );

    expect(response.status).toBe(404);
    await expect(response.json()).resolves.toEqual({
      ok: false,
      message: "Not found"
    });
  });
});
