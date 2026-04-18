import mysql from "mysql2/promise";
import type { ResultSetHeader } from "mysql2";
import type { Connection } from "mysql2/promise";

import type {
  Env,
  PersistSamplesResult,
  SamplePersistence,
  TrainingSample
} from "./types";

function dedupeSamplesByEventId(samples: TrainingSample[]): TrainingSample[] {
  const byEventId = new Map<number, TrainingSample>();
  for (const sample of samples) {
    byEventId.set(sample.eventId, sample);
  }
  return [...byEventId.values()];
}

function getAcceptedEventIds(samples: TrainingSample[]): number[] {
  return [...new Set(samples.map((sample) => sample.eventId))];
}

export function buildPersistSamplesResult(
  rawSamples: TrainingSample[],
  insertedCount: number
): PersistSamplesResult {
  const acceptedEventIds = getAcceptedEventIds(rawSamples);

  return {
    insertedCount,
    dedupedCount: rawSamples.length - insertedCount,
    acceptedEventIds
  };
}

function toConnection(env: Env): mysql.ConnectionOptions {
  return {
    host: env.HYPERDRIVE.host,
    port: Number(env.HYPERDRIVE.port),
    user: env.HYPERDRIVE.user,
    password: env.HYPERDRIVE.password,
    database: env.HYPERDRIVE.database,
    disableEval: true
  };
}

export function createMysqlSamplePersistence(): SamplePersistence {
  return {
    async persistSamples(
      samples: TrainingSample[],
      env: Env
    ): Promise<PersistSamplesResult> {
      const uniqueSamples = dedupeSamplesByEventId(samples);
      if (uniqueSamples.length === 0) {
        return buildPersistSamplesResult(samples, 0);
      }

      const rowPlaceholders = uniqueSamples.map(() => "(?, ?, ?, ?, ?)").join(", ");
      const sql = `
        INSERT IGNORE INTO training_samples
          (event_id, timestamp_millis, phase, final_decision_json, features_json)
        VALUES ${rowPlaceholders}
      `;

      const params: Array<number | string> = [];
      for (const sample of uniqueSamples) {
        params.push(
          sample.eventId,
          sample.timestampMillis,
          sample.phase,
          JSON.stringify(sample.finalDecision),
          JSON.stringify(sample.features)
        );
      }

      let connection: Connection | undefined;
      try {
        connection = await mysql.createConnection(toConnection(env));
        const [result] = await connection.execute<ResultSetHeader>(sql, params);
        const insertedCount = result.affectedRows ?? 0;
        return buildPersistSamplesResult(samples, insertedCount);
      } finally {
        if (connection) {
          try {
            await connection.end();
          } catch (error) {
            console.warn("Failed to close MySQL connection cleanly", error);
          }
        }
      }
    }
  };
}
