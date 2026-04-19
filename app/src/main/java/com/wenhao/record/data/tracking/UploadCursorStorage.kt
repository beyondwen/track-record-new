package com.wenhao.record.data.tracking

import com.wenhao.record.data.local.stream.ContinuousTrackDao
import com.wenhao.record.data.local.stream.UploadCursorEntity

class UploadCursorStorage(
    private val dao: ContinuousTrackDao,
) {
    fun load(type: UploadCursorType): UploadCursor {
        val entity = dao.loadUploadCursor(type.name)
        return UploadCursor(
            type = type,
            lastUploadedId = entity?.lastUploadedId ?: 0L,
            updatedAt = entity?.updatedAt ?: 0L,
        )
    }

    fun markUploaded(type: UploadCursorType, lastUploadedId: Long, updatedAt: Long) {
        val current = load(type)
        val nextId = maxOf(current.lastUploadedId, lastUploadedId)
        dao.upsertUploadCursor(
            UploadCursorEntity(
                cursorType = type.name,
                lastUploadedId = nextId,
                updatedAt = updatedAt,
            )
        )
    }
}
