import { authenticateRequest } from "./auth";
import {
  createD1AnalysisPersistence,
  createD1RawPointPersistence,
  createD1HistoryPersistence
} from "./d1";
import type {
  AnalysisPersistence,
  AnalysisSuccessResponseBody,
  Env,
  ErrorResponseBody,
  HistoryPersistence,
  HistorySuccessResponseBody,
  RawPointPersistence,
  RawPointSuccessResponseBody
} from "./types";
import {
  validateAnalysisBatchRequest,
  ValidationError,
  validateRawPointBatchRequest,
  validateHistoryBatchRequest
} from "./validation";

export type {
  AnalysisPersistence,
  HistoryPersistence,
  RawPointPersistence
} from "./types";

interface AppDependencies {
  historyPersistence?: HistoryPersistence;
  rawPointPersistence?: RawPointPersistence;
  analysisPersistence?: AnalysisPersistence;
}

function jsonResponse(
  status: number,
  body:
    | HistorySuccessResponseBody
    | RawPointSuccessResponseBody
    | AnalysisSuccessResponseBody
    | ErrorResponseBody
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
  const historyPersistence =
    deps.historyPersistence ?? createD1HistoryPersistence();
  const rawPointPersistence =
    deps.rawPointPersistence ?? createD1RawPointPersistence();
  const analysisPersistence =
    deps.analysisPersistence ?? createD1AnalysisPersistence();

  return {
    async fetch(request, env): Promise<Response> {
      const url = new URL(request.url);
      if (
        url.pathname !== "/histories/batch" &&
        url.pathname !== "/raw-points/batch" &&
        url.pathname !== "/analysis/batch"
      ) {
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
        if (url.pathname === "/raw-points/batch") {
          const validated = validateRawPointBatchRequest(payload);
          const persisted = await rawPointPersistence.persistRawPoints(
            validated.deviceId,
            validated.appVersion,
            validated.points,
            env
          );

          return jsonResponse(200, {
            ok: true,
            insertedCount: persisted.insertedCount,
            dedupedCount: persisted.dedupedCount,
            acceptedMaxPointId: persisted.acceptedMaxPointId
          });
        }

        if (url.pathname === "/analysis/batch") {
          const validated = validateAnalysisBatchRequest(payload);
          const persisted = await analysisPersistence.persistAnalysis(
            validated.deviceId,
            validated.appVersion,
            validated.segments,
            env
          );

          return jsonResponse(200, {
            ok: true,
            insertedCount: persisted.insertedCount,
            dedupedCount: persisted.dedupedCount,
            acceptedMaxSegmentId: persisted.acceptedMaxSegmentId
          });
        }

        const validated = validateHistoryBatchRequest(payload);
        const persisted = await historyPersistence.persistHistories(
          validated.deviceId,
          validated.appVersion,
          validated.histories,
          env
        );

        return jsonResponse(200, {
          ok: true,
          insertedCount: persisted.insertedCount,
          dedupedCount: persisted.dedupedCount,
          acceptedHistoryIds: persisted.acceptedHistoryIds
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
        console.error(
          url.pathname === "/histories/batch"
            ? "Failed to persist histories"
            : url.pathname === "/raw-points/batch"
              ? "Failed to persist raw points"
              : "Failed to persist analysis results",
          error
        );

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
