import type { Env } from "./types";

export interface AuthFailure {
  status: 401 | 403;
  message: string;
}

export function authenticateRequest(
  request: Request,
  env: Env
): AuthFailure | null {
  const authorization = request.headers.get("Authorization");
  if (!authorization) {
    return {
      status: 401,
      message: "Missing or malformed Authorization header"
    };
  }

  const match = authorization.match(/^Bearer\s+(.+)$/);
  if (!match || !match[1]?.trim()) {
    return {
      status: 401,
      message: "Missing or malformed Authorization header"
    };
  }

  const token = match[1].trim();
  if (token !== env.UPLOAD_TOKEN) {
    return {
      status: 403,
      message: "Forbidden"
    };
  }

  return null;
}
