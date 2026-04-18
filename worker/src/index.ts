import { authenticateRequest } from "./auth";
import { createMysqlSamplePersistence } from "./mysql";
import type {
  Env,
  ErrorResponseBody,
  SamplePersistence,
  SuccessResponseBody
} from "./types";
import { ValidationError, validateBatchRequest } from "./validation";

export type { SamplePersistence } from "./types";

interface AppDependencies {
  samplePersistence?: SamplePersistence;
}

function jsonResponse(
  status: number,
  body: SuccessResponseBody | ErrorResponseBody
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8"
    }
  });
}

function ensureJsonContentType(request: Request): ErrorResponseBody | null {
  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().includes("application/json")) {
    return {
      ok: false,
      message: "Request body must be JSON"
    };
  }
  return null;
}

export function createApp(deps: AppDependencies = {}): ExportedHandler<Env> {
  const samplePersistence = deps.samplePersistence ?? createMysqlSamplePersistence();

  return {
    async fetch(request, env): Promise<Response> {
      const url = new URL(request.url);
      if (url.pathname !== "/samples/batch") {
        return jsonResponse(404, {
          ok: false,
          message: "Not found"
        });
      }

      if (request.method !== "POST") {
        return jsonResponse(405, {
          ok: false,
          message: "Method not allowed"
        });
      }

      const authFailure = authenticateRequest(request, env);
      if (authFailure) {
        return jsonResponse(authFailure.status, {
          ok: false,
          message: authFailure.message
        });
      }

      const contentTypeFailure = ensureJsonContentType(request);
      if (contentTypeFailure) {
        return jsonResponse(400, contentTypeFailure);
      }

      let payload: unknown;
      try {
        payload = await request.json();
      } catch {
        return jsonResponse(400, {
          ok: false,
          message: "Request body must be valid JSON"
        });
      }

      try {
        const validated = validateBatchRequest(payload);
        const persisted = await samplePersistence.persistSamples(
          validated.samples,
          env
        );

        return jsonResponse(200, {
          ok: true,
          insertedCount: persisted.insertedCount,
          dedupedCount: persisted.dedupedCount,
          acceptedEventIds: persisted.acceptedEventIds
        });
      } catch (error) {
        if (error instanceof ValidationError) {
          return jsonResponse(400, {
            ok: false,
            message: error.message
          });
        }

        const message =
          error instanceof Error && error.message.trim().length > 0
            ? error.message
            : "Internal server error";
        console.error("Failed to persist training samples", error);

        return jsonResponse(500, {
          ok: false,
          message
        });
      }
    }
  };
}

const app = createApp();

export default app;
