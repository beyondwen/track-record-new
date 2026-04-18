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

export interface HistoryPoint {
  latitude: number;
  longitude: number;
  timestampMillis: number;
  accuracyMeters: number | null;
  altitudeMeters: number | null;
  wgs84Latitude: number | null;
  wgs84Longitude: number | null;
}

export interface HistoryRecord {
  historyId: number;
  timestampMillis: number;
  distanceKm: number;
  durationSeconds: number;
  averageSpeedKmh: number;
  title: string | null;
  startSource: string | null;
  stopSource: string | null;
  manualStartAt: number | null;
  manualStopAt: number | null;
  points: HistoryPoint[];
}

export interface ValidatedBatchRequest {
  samples: TrainingSample[];
}

export interface ValidatedHistoryBatchRequest {
  deviceId: string;
  appVersion: string;
  histories: HistoryRecord[];
}

export interface PersistSamplesResult {
  insertedCount: number;
  dedupedCount: number;
  acceptedEventIds: number[];
}

export interface PersistHistoriesResult {
  insertedCount: number;
  dedupedCount: number;
  acceptedHistoryIds: number[];
}

export interface SamplePersistence {
  persistSamples(
    samples: TrainingSample[],
    env: Env
  ): Promise<PersistSamplesResult>;
}

export interface HistoryPersistence {
  persistHistories(
    deviceId: string,
    appVersion: string,
    histories: HistoryRecord[],
    env: Env
  ): Promise<PersistHistoriesResult>;
}

export interface SampleSuccessResponseBody extends PersistSamplesResult {
  ok: true;
}

export interface HistorySuccessResponseBody extends PersistHistoriesResult {
  ok: true;
}

export interface ErrorResponseBody {
  ok: false;
  message: string;
}
