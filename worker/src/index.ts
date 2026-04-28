import { authenticateRequest } from "./auth";
import {
  createD1DiagnosticLogPersistence,
  createD1RawPointPersistence,
  createD1TodaySessionPersistence
} from "./d1";
import type {
  AppConfigSuccessResponseBody,
  DiagnosticLogCleanupSuccessResponseBody,
  DiagnosticLogPersistence,
  DiagnosticLogReadSuccessResponseBody,
  DiagnosticLogResolveSuccessResponseBody,
  DiagnosticLogSuccessResponseBody,
  Env,
  ErrorResponseBody,
  RawPointDayReadSuccessResponseBody,
  RawPointReadSuccessResponseBody,
  RawPointPersistence,
  RawPointSuccessResponseBody,
  TodaySessionOpenReadSuccessResponseBody,
  TodaySessionPersistence,
  TodaySessionSuccessResponseBody
} from "./types";
import {
  validateDiagnosticLogBatchRequest,
  validateDiagnosticLogResolveRequest,
  ValidationError,
  validateRawPointBatchRequest,
  validateTodaySessionBatchRequest,
  validateTodaySessionPointBatchRequest
} from "./validation";

export type {
  RawPointPersistence,
  TodaySessionPersistence
} from "./types";

interface AppDependencies {
  rawPointPersistence?: RawPointPersistence;
  diagnosticLogPersistence?: DiagnosticLogPersistence;
  todaySessionPersistence?: TodaySessionPersistence;
}

function jsonResponse(
  status: number,
  body:
    | {
        ok: true;
        message: string;
      }
    | AppConfigSuccessResponseBody
    | RawPointReadSuccessResponseBody
    | RawPointDayReadSuccessResponseBody
    | RawPointSuccessResponseBody
    | DiagnosticLogSuccessResponseBody
    | DiagnosticLogReadSuccessResponseBody
    | DiagnosticLogResolveSuccessResponseBody
    | DiagnosticLogCleanupSuccessResponseBody
    | TodaySessionSuccessResponseBody
    | TodaySessionOpenReadSuccessResponseBody
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
  const rawPointPersistence =
    deps.rawPointPersistence ?? createD1RawPointPersistence();
  const diagnosticLogPersistence =
    deps.diagnosticLogPersistence ?? createD1DiagnosticLogPersistence();
  const todaySessionPersistence =
    deps.todaySessionPersistence ?? createD1TodaySessionPersistence();

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
        url.pathname !== "/raw-points/days" &&
        url.pathname !== "/raw-points/day" &&
        url.pathname !== "/raw-points/batch" &&
        url.pathname !== "/diagnostics/logs" &&
        url.pathname !== "/diagnostics/logs/batch" &&
        url.pathname !== "/diagnostics/logs/resolve" &&
        url.pathname !== "/today-sessions/open" &&
        url.pathname !== "/today-sessions/batch" &&
        url.pathname !== "/today-session-points/batch" &&
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

        if (url.pathname === "/today-sessions/open") {
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
          const snapshot = await todaySessionPersistence.readLatestOpenSession(
            deviceId,
            env
          );

          return jsonResponse(200, {
            ok: true,
            session: snapshot?.session ?? null,
            points: snapshot?.points ?? []
          });
        }

        if (url.pathname === "/diagnostics/logs") {
          if (request.method !== "GET" && request.method !== "DELETE") {
            return jsonResponse(405, {
              ok: false,
              message: "Method not allowed"
            });
          }

          if (request.method === "DELETE") {
            const deletedCount = await diagnosticLogPersistence.deleteResolvedBefore(
              parseRequiredQueryInteger(url.searchParams.get("beforeMillis"), "`beforeMillis`"),
              env
            );
            return jsonResponse(200, {
              ok: true,
              deletedCount
            });
          }

          const deviceId = parseRequiredQueryString(
            url.searchParams.get("deviceId"),
            "`deviceId`"
          );
          const status = url.searchParams.get("status")?.trim() || "open";
          const type = url.searchParams.get("type")?.trim() || undefined;
          const logs = await diagnosticLogPersistence.readLogs(
            deviceId,
            { status, type },
            env
          );

          return jsonResponse(200, {
            ok: true,
            logs
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



        if (url.pathname === "/today-sessions/batch") {
          const validated = validateTodaySessionBatchRequest(payload);
          const persisted = await todaySessionPersistence.persistSessions(
            validated.deviceId,
            validated.appVersion,
            validated.sessions,
            env
          );

          return jsonResponse(200, {
            ok: true,
            insertedCount: persisted.insertedCount,
            dedupedCount: persisted.dedupedCount
          });
        }

        if (url.pathname === "/today-session-points/batch") {
          const validated = validateTodaySessionPointBatchRequest(payload);
          const persisted = await todaySessionPersistence.persistSessionPoints(
            validated.deviceId,
            validated.appVersion,
            validated.points,
            env
          );

          return jsonResponse(200, {
            ok: true,
            insertedCount: persisted.insertedCount,
            dedupedCount: persisted.dedupedCount
          });
        }

        if (url.pathname === "/diagnostics/logs/batch") {
          const validated = validateDiagnosticLogBatchRequest(payload);
          const persisted = await diagnosticLogPersistence.persistLogs(
            validated.deviceId,
            validated.appVersion,
            validated.logs,
            env
          );

          return jsonResponse(200, {
            ok: true,
            insertedCount: persisted.insertedCount,
            dedupedCount: persisted.dedupedCount
          });
        }

        if (url.pathname === "/diagnostics/logs/resolve") {
          const validated = validateDiagnosticLogResolveRequest(payload);
          const resolvedCount = await diagnosticLogPersistence.resolveLogs(
            validated.deviceId,
            validated.fingerprints,
            env
          );

          return jsonResponse(200, {
            ok: true,
            resolvedCount
          });
        }

        if (url.pathname === "/raw-points/batch") {
          const validated = validateRawPointBatchRequest(payload);
          const persisted = await rawPointPersistence.persistRawPoints(
            validated.deviceId,
            validated.appVersion,
            validated.utcOffsetMinutes,
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

        return jsonResponse(404, {
          ok: false,
          message: "Not found"
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
          url.pathname === "/today-sessions/batch"
              ? "Failed to persist today sessions"
            : url.pathname === "/today-session-points/batch"
              ? "Failed to persist today session points"
            : url.pathname === "/today-sessions/open"
              ? "Failed to read open today session"
            : url.pathname.startsWith("/diagnostics/logs")
              ? "Failed to process diagnostic logs"
            : url.pathname === "/raw-points/day"
              ? "Failed to read raw points"
            : url.pathname === "/raw-points/batch"
              ? "Failed to persist raw points"
              : "Failed to process request",
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
