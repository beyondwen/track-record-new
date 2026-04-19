CREATE TABLE IF NOT EXISTS training_samples (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  event_id INTEGER NOT NULL,
  timestamp_millis INTEGER NOT NULL,
  phase TEXT NOT NULL,
  final_decision_json TEXT NOT NULL,
  features_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_training_samples_event_id
  ON training_samples (event_id);

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
