package com.atxz.coolapkdetailplus

import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {

    const val ACTION_NEW_LOG = "com.atxz.coolapkdetailplus.ACTION_NEW_LOG"
    const val EXTRA_LOG_TEXT = "extra_log_text"
    private const val PREF_NAME = "coolapk_detail_plus_logs"
    private const val KEY_LOG_BUFFER = "log_buffer"

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(context: Context?, tag: String, message: String) {
        val timeStr = dateFormat.format(Date())
        val formattedLog = "[$timeStr] [$tag] $message"

        Log.d(tag, message)

        if (context != null) {
            try {
                // Save to SharedPreferences for persistence
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val currentLogs = prefs.getString(KEY_LOG_BUFFER, "") ?: ""
                val updatedLogs = if (currentLogs.isBlank()) {
                    formattedLog
                } else {
                    // Keep max ~200 lines of logs
                    val lines = currentLogs.split("\n").takeLast(180)
                    (lines + formattedLog).joinToString("\n")
                }
                prefs.edit().putString(KEY_LOG_BUFFER, updatedLogs).apply()

                // Broadcast intent to notify MainActivity UI in real time
                val intent = Intent(ACTION_NEW_LOG).apply {
                    putExtra(EXTRA_LOG_TEXT, formattedLog)
                    setPackage("com.atxz.coolapkdetailplus")
                }
                context.sendBroadcast(intent)
            } catch (e: Throwable) {
                // Ignore broadcast/pref exceptions in target process
            }
        }
    }

    fun getLogs(context: Context): String {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_LOG_BUFFER, "") ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    fun clearLogs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_LOG_BUFFER).apply()
        } catch (e: Throwable) {
            // Ignore
        }
    }
}
