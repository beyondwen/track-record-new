export interface HyperdriveBinding {
  host: string;
  port: number | string;
  user: string;
  password: string;
  database: string;
}

export interface Env {
  UPLOAD_TOKEN: string;
  HYPERDRIVE: HyperdriveBinding;
}

export interface TrainingSample {
  eventId: number;
  timestampMillis: number;
  phase: string;
  finalDecision: unknown;
  features: Record<string, unknown>;
}

export interface ValidatedBatchRequest {
  samples: TrainingSample[];
}

export interface PersistSamplesResult {
  insertedCount: number;
  dedupedCount: number;
  acceptedEventIds: number[];
}

export interface SamplePersistence {
  persistSamples(
    samples: TrainingSample[],
    env: Env
  ): Promise<PersistSamplesResult>;
}

export interface SuccessResponseBody extends PersistSamplesResult {
  ok: true;
}

export interface ErrorResponseBody {
  ok: false;
  message: string;
}
