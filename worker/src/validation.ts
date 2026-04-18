import type { TrainingSample, ValidatedBatchRequest } from "./types";

const MAX_PHASE_LENGTH = 64;

export class ValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ValidationError";
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function parseSample(sample: unknown, index: number): TrainingSample {
  if (!isObject(sample)) {
    throw new ValidationError(`samples[${index}] must be an object`);
  }

  const { eventId, timestampMillis, phase, finalDecision, features } = sample;

  if (!Number.isSafeInteger(eventId)) {
    throw new ValidationError(`samples[${index}].eventId must be an integer`);
  }

  if (!Number.isSafeInteger(timestampMillis)) {
    throw new ValidationError(
      `samples[${index}].timestampMillis must be an integer`
    );
  }

  if (typeof phase !== "string" || phase.trim().length === 0) {
    throw new ValidationError(`samples[${index}].phase must be a non-empty string`);
  }
  if (phase.trim().length > MAX_PHASE_LENGTH) {
    throw new ValidationError(
      `samples[${index}].phase length must be <= ${MAX_PHASE_LENGTH}`
    );
  }

  if (!("finalDecision" in sample)) {
    throw new ValidationError(`samples[${index}].finalDecision is required`);
  }
  if (finalDecision === undefined) {
    throw new ValidationError(`samples[${index}].finalDecision is required`);
  }

  if (!isObject(features)) {
    throw new ValidationError(`samples[${index}].features must be an object`);
  }

  return {
    eventId: eventId as number,
    timestampMillis: timestampMillis as number,
    phase: phase.trim(),
    finalDecision,
    features
  };
}

export function validateBatchRequest(payload: unknown): ValidatedBatchRequest {
  if (!isObject(payload)) {
    throw new ValidationError("Request body must be a JSON object");
  }

  if (!Array.isArray(payload.samples)) {
    throw new ValidationError("`samples` must be an array");
  }

  return {
    samples: payload.samples.map((sample, index) => parseSample(sample, index))
  };
}
