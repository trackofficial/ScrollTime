package com.example.scrolltime

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

class TimeManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "scroll_time_prefs"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_TOTAL_TIME = "total_time_"
        private const val KEY_SHORTS_TIME = "shorts_time_" }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private fun checkAndResetIfNewDay() {
        val today = getTodayDate()
        val lastReset = prefs.getLong(KEY_LAST_RESET_DATE, 0)
        if (lastReset != today) {
            val editor = prefs.edit()
            editor.putLong(KEY_LAST_RESET_DATE, today)
            val allKeys = prefs.all.keys
            allKeys.forEach { key ->
                if (key.startsWith(KEY_TOTAL_TIME) || key.startsWith(KEY_SHORTS_TIME)) {
                    editor.putLong(key, 0)
                }
            }
            editor.apply()
        }
    }
    private fun getTodayDate(): Long {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) * 10000L +
                (calendar.get(Calendar.MONTH) + 1) * 100L +
                calendar.get(Calendar.DAY_OF_MONTH)
    }


    fun getTotalTimeForApp(packageName: String): Long {
        checkAndResetIfNewDay()
        return prefs.getLong(KEY_TOTAL_TIME + packageName, 0)
    }

    fun getShortsTimeForApp(packageName: String): Long {
        checkAndResetIfNewDay()
        return prefs.getLong(KEY_SHORTS_TIME + packageName, 0)
    }

    fun addTotalTime(packageName: String, seconds: Long) {
        val current = getTotalTimeForApp(packageName)
        prefs.edit().putLong(KEY_TOTAL_TIME + packageName, current + seconds).apply()
    }

    fun addShortsTime(packageName: String, seconds: Long) {
        val current = getShortsTimeForApp(packageName)
        prefs.edit().putLong(KEY_SHORTS_TIME + packageName, current + seconds).apply()
    }

    fun isAppLimitExceeded(packageName: String, limitMinutes: Int): Boolean {
        val totalSeconds = getTotalTimeForApp(packageName)
        val limitSeconds = limitMinutes * 60L
        return totalSeconds >= limitSeconds
    }

    fun isShortsLimitExceeded(packageName: String, shortsLimitMinutes: Int): Boolean {
        val shortsSeconds = getShortsTimeForApp(packageName)
        val limitSeconds = shortsLimitMinutes * 60L
        return shortsSeconds >= limitSeconds
    }

    fun getRemainingTotalTime(packageName: String, limitMinutes: Int): Long {
        val used = getTotalTimeForApp(packageName)
        val limit = limitMinutes * 60L
        return (limit - used).coerceAtLeast(0)
    }

    fun getRemainingShortsTime(packageName: String, shortsLimitMinutes: Int): Long {
        val used = getShortsTimeForApp(packageName)
        val limit = shortsLimitMinutes * 60L
        return (limit - used).coerceAtLeast(0)
    }
}