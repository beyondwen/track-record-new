CREATE TABLE IF NOT EXISTS uploaded_histories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  history_id INTEGER NOT NULL,
  app_version TEXT NOT NULL,
  timestamp_millis INTEGER NOT NULL,
  distance_km REAL NOT NULL,
  duration_seconds INTEGER NOT NULL,
  average_speed_kmh REAL NOT NULL,
  title TEXT,
  start_source TEXT,
  stop_source TEXT,
  manual_start_at INTEGER,
  manual_stop_at INTEGER,
  points_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_uploaded_histories_device_history
  ON uploaded_histories (device_id, history_id);

CREATE TABLE IF NOT EXISTS processed_histories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  history_id INTEGER NOT NULL,
  app_version TEXT NOT NULL,
  timestamp_millis INTEGER NOT NULL,
  distance_km REAL NOT NULL,
  duration_seconds INTEGER NOT NULL,
  average_speed_kmh REAL NOT NULL,
  title TEXT,
  start_source TEXT,
  stop_source TEXT,
  manual_start_at INTEGER,
  manual_stop_at INTEGER,
  points_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_processed_histories_device_history
  ON processed_histories (device_id, history_id);

CREATE INDEX IF NOT EXISTS idx_processed_histories_device_timestamp
  ON processed_histories (device_id, timestamp_millis);

CREATE TABLE IF NOT EXISTS history_day_summary (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  day_start_millis INTEGER NOT NULL,
  latest_timestamp INTEGER NOT NULL,
  session_count INTEGER NOT NULL,
  total_distance_km REAL NOT NULL,
  total_duration_seconds INTEGER NOT NULL,
  average_speed_kmh REAL NOT NULL,
  source_ids_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_history_day_summary_device_day
  ON history_day_summary (device_id, day_start_millis);

CREATE INDEX IF NOT EXISTS idx_history_day_summary_device_day
  ON history_day_summary (device_id, day_start_millis DESC);

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

CREATE TABLE IF NOT EXISTS analysis_segment (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  segment_id INTEGER NOT NULL,
  app_version TEXT NOT NULL,
  start_point_id INTEGER NOT NULL,
  end_point_id INTEGER NOT NULL,
  start_timestamp INTEGER NOT NULL,
  end_timestamp INTEGER NOT NULL,
  segment_type TEXT NOT NULL,
  confidence REAL NOT NULL,
  distance_meters REAL NOT NULL,
  duration_millis INTEGER NOT NULL,
  avg_speed_meters_per_second REAL NOT NULL,
  max_speed_meters_per_second REAL NOT NULL,
  analysis_version INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_analysis_segment_device_segment
  ON analysis_segment (device_id, segment_id);

CREATE INDEX IF NOT EXISTS idx_analysis_segment_device_start_timestamp
  ON analysis_segment (device_id, start_timestamp);

CREATE TABLE IF NOT EXISTS stay_cluster (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  segment_id INTEGER NOT NULL,
  stay_id INTEGER NOT NULL,
  center_lat REAL NOT NULL,
  center_lng REAL NOT NULL,
  radius_meters REAL NOT NULL,
  arrival_time INTEGER NOT NULL,
  departure_time INTEGER NOT NULL,
  confidence REAL NOT NULL,
  analysis_version INTEGER NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_stay_cluster_device_stay
  ON stay_cluster (device_id, stay_id);

CREATE INDEX IF NOT EXISTS idx_stay_cluster_device_segment
  ON stay_cluster (device_id, segment_id);


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
