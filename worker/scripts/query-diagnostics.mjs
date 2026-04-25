#!/usr/bin/env node
import { spawnSync } from "node:child_process";

const DATABASE = process.env.D1_DATABASE || "track-record";
const USE_REMOTE = process.env.D1_REMOTE !== "0";

const [command, ...args] = process.argv.slice(2);

function quote(value) {
  return String(value).replaceAll("'", "''");
}

function runSql(sql) {
  const wranglerArgs = ["wrangler", "d1", "execute", DATABASE];
  if (USE_REMOTE) wranglerArgs.push("--remote");
  wranglerArgs.push("--command", sql);
  const result = spawnSync("npx", wranglerArgs, { stdio: "inherit" });
  process.exit(result.status ?? 1);
}

function selectLogs({ deviceId, type }) {
  const conditions = ["status = 'open'"];
  if (deviceId) conditions.push(`device_id = '${quote(deviceId)}'`);
  if (type) conditions.push(`type = '${quote(type)}'`);
  return `
SELECT
  device_id,
  type,
  severity,
  source,
  fingerprint,
  message,
  occurrence_count,
  first_seen_at,
  last_seen_at,
  substr(coalesce(payload_json, ''), 1, 240) AS payload
FROM diagnostic_log
WHERE ${conditions.join(" AND ")}
ORDER BY last_seen_at DESC
LIMIT 50;
`.trim();
}

function usage() {
  console.log(`Usage:
  node scripts/query-diagnostics.mjs open [deviceId]
  node scripts/query-diagnostics.mjs perf [deviceId]
  node scripts/query-diagnostics.mjs resolve <deviceId> <fingerprint...>
  node scripts/query-diagnostics.mjs cleanup <beforeMillis>

Environment:
  D1_DATABASE=track-record    D1 database name
  D1_REMOTE=0                 query local D1 instead of remote
`);
  process.exit(1);
}

if (!command) usage();

switch (command) {
  case "open": {
    runSql(selectLogs({ deviceId: args[0] }));
    break;
  }
  case "perf": {
    runSql(selectLogs({ deviceId: args[0], type: "PERF_WARN" }));
    break;
  }
  case "resolve": {
    const [deviceId, ...fingerprints] = args;
    if (!deviceId || fingerprints.length === 0) usage();
    runSql(`
UPDATE diagnostic_log
SET status = 'resolved', resolved_at = unixepoch() * 1000, updated_at = CURRENT_TIMESTAMP
WHERE device_id = '${quote(deviceId)}'
  AND fingerprint IN (${fingerprints.map((item) => `'${quote(item)}'`).join(", ")});
`.trim());
    break;
  }
  case "cleanup": {
    const [beforeMillis] = args;
    if (!beforeMillis || !/^\d+$/.test(beforeMillis)) usage();
    runSql(`DELETE FROM diagnostic_log WHERE status = 'resolved' AND resolved_at < ${beforeMillis};`);
    break;
  }
  default:
    usage();
}
