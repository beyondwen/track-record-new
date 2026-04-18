CREATE TABLE IF NOT EXISTS training_samples (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  timestamp_millis BIGINT NOT NULL,
  phase VARCHAR(64) NOT NULL,
  final_decision_json JSON NOT NULL,
  features_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_training_samples_event_id (event_id)
);

CREATE TABLE IF NOT EXISTS uploaded_histories (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  device_id VARCHAR(128) NOT NULL,
  history_id BIGINT NOT NULL,
  app_version VARCHAR(64) NOT NULL,
  timestamp_millis BIGINT NOT NULL,
  distance_km DOUBLE NOT NULL,
  duration_seconds INT NOT NULL,
  average_speed_kmh DOUBLE NOT NULL,
  title VARCHAR(255) NULL,
  start_source VARCHAR(64) NULL,
  stop_source VARCHAR(64) NULL,
  manual_start_at BIGINT NULL,
  manual_stop_at BIGINT NULL,
  points_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_uploaded_histories_device_history (device_id, history_id)
);
