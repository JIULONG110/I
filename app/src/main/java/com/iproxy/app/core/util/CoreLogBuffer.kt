package com.iproxy.app.core.util

import com.iproxy.app.data.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * 进程内线程安全日志环形缓冲区，供设置/日志页面实时展示与导出排查
 */
object CoreLogBuffer {

    private const val MAX_ENTRIES = 500
    private val lock = Any()
    private val queue = ArrayDeque<LogEntry>(MAX_ENTRIES)

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    fun i(tag: String, message: String) = append("INFO", tag, message)

    fun w(tag: String, message: String) = append("WARN", tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message: ${throwable.message}" else message
        append("ERROR", tag, full)
    }

    fun append(level: String, tag: String, message: String) {
        synchronized(lock) {
            if (queue.size >= MAX_ENTRIES) {
                queue.removeFirst()
            }
            queue.addLast(LogEntry(level = level, tag = tag, message = message))
            _logsFlow.value = queue.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            queue.clear()
            _logsFlow.value = emptyList()
        }
    }

    fun exportAsText(): String {
        synchronized(lock) {
            return queue.joinToString("\n") { entry ->
                "[${entry.level}][${entry.tag}] ${entry.message}"
            }
        }
    }
}
