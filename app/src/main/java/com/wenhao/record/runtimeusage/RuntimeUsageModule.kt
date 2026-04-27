package com.wenhao.record.runtimeusage

enum class RuntimeUsageModule(val key: String) {
    APP_PROCESS("app.process"),
    UI_MAIN_ACTIVITY("ui.main_activity"),
    UI_TAB_RECORD("ui.tab.record"),
    UI_TAB_HISTORY("ui.tab.history"),
    UI_TAB_ABOUT("ui.tab.about"),
    UI_MAP_ACTIVITY("ui.map_activity"),
    SERVICE_BACKGROUND_TRACKING("service.background_tracking"),
    WORKER_RAW_POINT_UPLOAD("worker.raw_point_upload"),
    WORKER_ANALYSIS_UPLOAD("worker.analysis_upload"),
    WORKER_HISTORY_UPLOAD("worker.history_upload"),
    WORKER_TODAY_SESSION_SYNC("worker.today_session_sync"),
    WORKER_TRACK_MIRROR_RECOVERY("worker.track_mirror_recovery"),
    WORKER_PROCESSED_HISTORY_SYNC("worker.processed_history_sync"),
    WORKER_DIAGNOSTIC_LOG_UPLOAD("worker.diagnostic_log_upload"),
    SERVICE_WORKER_APP_CONFIG("service.worker_app_config"),
    SERVICE_WORKER_CONNECTIVITY("service.worker_connectivity"),
    SERVICE_TODAY_SESSION_REMOTE_READ("service.today_session_remote_read"),
    SERVICE_REMOTE_HISTORY_READ("service.remote_history_read"),
    SERVICE_REMOTE_RAW_POINT_READ("service.remote_raw_point_read"),
}
