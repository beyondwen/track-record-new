package com.wenhao.record.stability

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogStore {
    private const val CRASH_DIR = "stability"
    private const val CRASH_FILE = "last_crash.txt"
    private const val MAX_STACKTRACE_LINES = 18

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(appContext, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun latestSummary(context: Context): String? {
        val crashFile = crashFile(context)
        if (!crashFile.exists()) return null
        return crashFile
            .readLines()
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun record(context: Context, throwable: Throwable) {
        runCatching {
            val crashFile = crashFile(context)
            crashFile.parentFile?.mkdirs()
            crashFile.bufferedWriter().use { writer ->
                writer.appendLine(buildSummary(throwable))
                writer.appendLine()
                buildStackTraceLines(throwable).forEach(writer::appendLine)
            }
        }
    }

    private fun crashFile(context: Context): File {
        return File(context.filesDir, "$CRASH_DIR/$CRASH_FILE")
    }

    internal fun buildSummary(
        throwable: Throwable,
        timestampMillis: Long = System.currentTimeMillis()
    ): String {
        val timestamp = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestampMillis))
        val message = throwable.message?.takeIf { it.isNotBlank() } ?: "无详细信息"
        return "上次异常退出：$timestamp ${throwable.javaClass.simpleName} - $message"
    }

    private fun buildStackTraceLines(throwable: Throwable): List<String> {
        return throwable.stackTrace
            .take(MAX_STACKTRACE_LINES)
            .map { element -> "at $element" }
    }
}
