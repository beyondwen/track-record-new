import { describe, expect, it, vi, beforeEach } from "vitest";
import type { ResultSetHeader } from "mysql2";

const createConnection = vi.fn();

vi.mock("mysql2/promise", () => ({
  default: {
    createConnection
  }
}));

describe("createMysqlSamplePersistence", () => {
  beforeEach(() => {
    createConnection.mockReset();
  });

  it("uses text protocol query instead of execute for Hyperdrive compatibility", async () => {
    const query = vi.fn().mockResolvedValue([
      { affectedRows: 1 } satisfies Partial<ResultSetHeader>
    ]);
    const end = vi.fn().mockResolvedValue(undefined);
    createConnection.mockResolvedValue({
      query,
      end
    });

    const { createMysqlSamplePersistence } = await import("./mysql");

    const result = await createMysqlSamplePersistence().persistSamples(
      [
        {
          eventId: 101,
          timestampMillis: 1700000000000,
          phase: "tracking",
          finalDecision: "allow",
          features: { score: 0.91 }
        }
      ],
      {
        UPLOAD_TOKEN: "token",
        HYPERDRIVE: {
          host: "127.0.0.1",
          port: 3306,
          user: "worker",
          password: "pass",
          database: "track_record"
        }
      }
    );

    expect(query).toHaveBeenCalledTimes(1);
    expect(end).toHaveBeenCalledTimes(1);
    expect(result).toEqual({
      insertedCount: 1,
      dedupedCount: 0,
      acceptedEventIds: [101]
    });
  });
});
