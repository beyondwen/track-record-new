import { authenticateRequest } from "./auth";
import {
  createD1AnalysisPersistence,
  createD1RawPointPersistence,
  createD1HistoryPersistence,
  createD1ProcessedHistoryPersistence
} from "./d1";
import type {
  AnalysisPersistence,
  AnalysisSuccessResponseBody,
  AppConfigSuccessResponseBody,
  Env,
  ErrorResponseBody,
  HistoryPersistence,
  ProcessedHistoryPersistence,
  HistoryReadSuccessResponseBody,
  HistorySuccessResponseBody,
  RawPointDayReadSuccessResponseBody,
  RawPointReadSuccessResponseBody,
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
  processedHistoryPersistence?: ProcessedHistoryPersistence;
  rawPointPersistence?: RawPointPersistence;
  analysisPersistence?: AnalysisPersistence;
}

function jsonResponse(
  status: number,
  body:
    | {
        ok: true;
        message: string;
      }
    | AppConfigSuccessResponseBody
    | HistorySuccessResponseBody
    | HistoryReadSuccessResponseBody
    | RawPointReadSuccessResponseBody
    | RawPointDayReadSuccessResponseBody
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

function readConfiguredMapboxToken(env: Env): string | null {
  const token = env.MAPBOX_PUBLIC_TOKEN?.trim();
  return token ? token : null;
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

function parseRequiredQueryString(
  value: string | null,
  label: string,
  maxLength = 128
): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new ValidationError(`${label} must be a non-empty string`);
  }
  const normalized = value.trim();
  if (normalized.length > maxLength) {
    throw new ValidationError(`${label} length must be <= ${maxLength}`);
  }
  return normalized;
}

function parseRequiredQueryInteger(value: string | null, label: string): number {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new ValidationError(`${label} must be an integer`);
  }
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) {
    throw new ValidationError(`${label} must be an integer`);
  }
  return parsed;
}

export function createApp(deps: AppDependencies = {}): ExportedHandler<Env> {
  const historyPersistence =
    deps.historyPersistence ?? createD1HistoryPersistence();
  const processedHistoryPersistence =
    deps.processedHistoryPersistence ?? createD1ProcessedHistoryPersistence();
  const rawPointPersistence =
    deps.rawPointPersistence ?? createD1RawPointPersistence();
  const analysisPersistence =
    deps.analysisPersistence ?? createD1AnalysisPersistence();

  return {
    async fetch(request, env): Promise<Response> {
      const url = new URL(request.url);
      if (url.pathname === "/health") {
        if (request.method !== "GET") {
          return jsonResponse(405, {
            ok: false,
            message: "Method not allowed"
          });
        }

        return jsonResponse(200, {
          ok: true,
          message: "ok"
        });
      }

      if (
        url.pathname !== "/histories" &&
        url.pathname !== "/histories/day" &&
        url.pathname !== "/histories/batch" &&
        url.pathname !== "/processed-histories" &&
        url.pathname !== "/processed-histories/day" &&
        url.pathname !== "/processed-histories/batch" &&
        url.pathname !== "/raw-points/days" &&
        url.pathname !== "/raw-points/day" &&
        url.pathname !== "/raw-points/batch" &&
        url.pathname !== "/analysis/batch" &&
        url.pathname !== "/app-config"
      ) {
        return jsonResponse(404, {
          ok: false,
          message: "Not found"
        });
      }

      const authFailure = authenticateRequest(request, env);
      if (authFailure) {
        return jsonResponse(authFailure.status, {
          ok: false,
          message: authFailure.message
        });
      }

      try {
        if (url.pathname === "/app-config") {
          if (request.method !== "GET") {
            return jsonResponse(405, {
              ok: false,
              message: "Method not allowed"
            });
          }

          const mapboxPublicToken = readConfiguredMapboxToken(env);
          if (!mapboxPublicToken) {
            return jsonResponse(503, {
              ok: false,
              message: "Mapbox public token is not configured"
            });
          }

          return jsonResponse(200, {
            ok: true,
            mapboxPublicToken
          });
        }

        if (url.pathname === "/histories" || url.pathname === "/histories/day") {
          if (request.method !== "GET") {
            return jsonResponse(405, {
              ok: false,
              message: "Method not allowed"
            });
          }

          const deviceId = parseRequiredQueryString(
            url.searchParams.get("deviceId"),
            "`deviceId`"
          );
          const histories =
            url.pathname === "/histories/day"
              ? await historyPersistence.readHistoriesByDay(
                  deviceId,
                  parseRequiredQueryInteger(
                    url.searchParams.get("dayStartMillis"),
                    "`dayStartMillis`"
                  ),
                  env
                )
              : await historyPersistence.readHistories(deviceId, env);

          return jsonResponse(200, {
            ok: true,
            histories
          });
        }

        if (
          url.pathname === "/processed-histories" ||
          url.pathname === "/processed-histories/day"
        ) {
          if (request.method !== "GET") {
            return jsonResponse(405, {
              ok: false,
              message: "Method not allowed"
            });
          }

          const deviceId = parseRequiredQueryString(
            url.searchParams.get("deviceId"),
            "`deviceId`"
          );
          const histories =
            url.pathname === "/processed-histories/day"
              ? await processedHistoryPersistence.readHistoriesByDay(
                  deviceId,
                  parseRequiredQueryInteger(
                    url.searchParams.get("dayStartMillis"),
                    "`dayStartMillis`"
                  ),
                  env
                )
              : await processedHistoryPersistence.readHistories(deviceId, env);

          return jsonResponse(200, {
            ok: true,
            histories
          });
        }

        if (url.pathname === "/raw-points/days") {
          if (request.method !== "GET") {
            return jsonResponse(405, {
              ok: false,
              message: "Method not allowed"
            });
          }

          const deviceId = parseRequiredQueryString(
            url.searchParams.get("deviceId"),
            "`deviceId`"
          );
          const days = await rawPointPersistence.readRawPointDays(
            deviceId,
            parseRequiredQueryInteger(
              url.searchParams.get("utcOffsetMinutes"),
              "`utcOffsetMinutes`"
            ),
            env
          );

          return jsonResponse(200, {
            ok: true,
            days
          });
        }

        if (url.pathname === "/raw-points/day") {
          if (request.method !== "GET") {
            return jsonResponse(405, {
              ok: false,
              message: "Method not allowed"
            });
          }

          const deviceId = parseRequiredQueryString(
            url.searchParams.get("deviceId"),
            "`deviceId`"
          );
          const points = await rawPointPersistence.readRawPointsByDay(
            deviceId,
            parseRequiredQueryInteger(
              url.searchParams.get("dayStartMillis"),
              "`dayStartMillis`"
            ),
            env
          );

          return jsonResponse(200, {
            ok: true,
            points
          });
        }

        if (request.method !== "POST") {
          return jsonResponse(405, {
            ok: false,
            message: "Method not allowed"
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
        if (url.pathname === "/processed-histories/batch") {
          const persisted = await processedHistoryPersistence.persistHistories(
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
        }

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
          url.pathname === "/histories/batch" || url.pathname === "/processed-histories/batch"
            ? "Failed to persist histories"
            : url.pathname === "/raw-points/day"
              ? "Failed to read raw points"
            : url.pathname === "/raw-points/batch"
              ? "Failed to persist raw points"
              : url.pathname === "/histories" ||
                  url.pathname === "/histories/day" ||
                  url.pathname === "/processed-histories" ||
                  url.pathname === "/processed-histories/day"
                ? "Failed to read histories"
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
