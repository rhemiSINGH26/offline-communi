package com.indic.meshvoice

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    @Synchronized
    fun log(tag: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val logEntry = "[$timestamp] [$tag] $message"
        Log.i(tag, message)

        val current = _logs.value.toMutableList()
        current.add(0, logEntry)
        if (current.size > 200) {
            current.removeAt(current.size - 1)
        }
        _logs.value = current
    }

    @Synchronized
    fun clear() {
        _logs.value = emptyList()
    }

    fun getAllLogsText(): String {
        return _logs.value.reversed().joinToString("\n")
    }
}
