package com.wenhao.record.data.history

enum class TrackRecordSource {
    MANUAL,
    AUTO,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): TrackRecordSource {
            return entries.firstOrNull { it.name == value } ?: UNKNOWN
        }
    }
}
