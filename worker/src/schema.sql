CREATE TABLE IF NOT EXISTS raw_location_point (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  point_id INTEGER NOT NULL,
  app_version TEXT NOT NULL,
  timestamp_millis INTEGER NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  accuracy_meters REAL,
  altitude_meters REAL,
  speed_meters_per_second REAL,
  bearing_degrees REAL,
  provider TEXT NOT NULL,
  source_type TEXT NOT NULL,
  is_mock INTEGER NOT NULL,
  wifi_fingerprint_digest TEXT,
  activity_type TEXT,
  activity_confidence REAL,
  sampling_tier TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_raw_location_point_device_point
  ON raw_location_point (device_id, point_id);

CREATE INDEX IF NOT EXISTS idx_raw_location_point_device_timestamp
  ON raw_location_point (device_id, timestamp_millis);

CREATE TABLE IF NOT EXISTS raw_point_day_summary (
  device_id TEXT NOT NULL,
  utc_offset_minutes INTEGER NOT NULL,
  day_start_millis INTEGER NOT NULL,
  first_point_at INTEGER NOT NULL,
  last_point_at INTEGER NOT NULL,
  point_count INTEGER NOT NULL,
  max_point_id INTEGER NOT NULL,
  total_distance_km REAL NOT NULL DEFAULT 0,
  total_duration_seconds INTEGER NOT NULL DEFAULT 0,
  average_speed_kmh REAL NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (device_id, utc_offset_minutes, day_start_millis)
);

CREATE INDEX IF NOT EXISTS idx_raw_point_day_summary_device_day
  ON raw_point_day_summary (device_id, utc_offset_minutes, day_start_millis DESC);

CREATE TABLE IF NOT EXISTS today_session (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  day_start_millis INTEGER NOT NULL,
  status TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  last_point_at INTEGER,
  ended_at INTEGER,
  phase TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_today_session_device_session
  ON today_session (device_id, session_id);

CREATE INDEX IF NOT EXISTS idx_today_session_device_day_status
  ON today_session (device_id, day_start_millis, status);

CREATE TABLE IF NOT EXISTS today_session_point (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  point_id INTEGER NOT NULL,
  day_start_millis INTEGER NOT NULL,
  timestamp_millis INTEGER NOT NULL,
  latitude REAL NOT NULL,
  longitude REAL NOT NULL,
  accuracy_meters REAL,
  altitude_meters REAL,
  speed_meters_per_second REAL,
  provider TEXT NOT NULL,
  sampling_tier TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_today_session_point_device_session_point
  ON today_session_point (device_id, session_id, point_id);

CREATE INDEX IF NOT EXISTS idx_today_session_point_device_session_time
  ON today_session_point (device_id, session_id, timestamp_millis);

CREATE TABLE IF NOT EXISTS diagnostic_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  log_id TEXT NOT NULL,
  app_version TEXT NOT NULL,
  occurred_at INTEGER NOT NULL,
  type TEXT NOT NULL,
  severity TEXT NOT NULL,
  source TEXT NOT NULL,
  message TEXT NOT NULL,
  fingerprint TEXT NOT NULL,
  payload_json TEXT,
  status TEXT NOT NULL DEFAULT 'open',
  occurrence_count INTEGER NOT NULL DEFAULT 1,
  first_seen_at INTEGER NOT NULL,
  last_seen_at INTEGER NOT NULL,
  resolved_at INTEGER,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_diagnostic_log_device_fingerprint
  ON diagnostic_log (device_id, fingerprint);

CREATE INDEX IF NOT EXISTS idx_diagnostic_log_device_status_type
  ON diagnostic_log (device_id, status, type, last_seen_at DESC);
