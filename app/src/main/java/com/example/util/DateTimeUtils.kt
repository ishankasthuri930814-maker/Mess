package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val fullDateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    fun formatFullDateTime(timestamp: Long): String {
        return fullDateTimeFormat.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    fun formatDisplayDate(timestamp: Long): String {
        return displayDateFormat.format(Date(timestamp))
    }

    fun formatDisplayDateFromKey(dateKey: String): String {
        return try {
            val date = dateFormat.parse(dateKey)
            if (date != null) displayDateFormat.format(date) else dateKey
        } catch (e: Exception) {
            dateKey
        }
    }

    fun getTodayKey(): String {
        return dateFormat.format(Date())
    }

    fun getCurrentMonthYear(): String {
        return monthYearFormat.format(Date())
    }

    fun getCurrentMonthKey(): String {
        return monthKeyFormat.format(Date())
    }

    fun formatDuration(durationMillis: Long): String {
        val totalMinutes = durationMillis / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes}m"
    }

    fun getDayOfWeekSinhala(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> "ඉරිදා (Sun)"
            java.util.Calendar.MONDAY -> "සඳුදා (Mon)"
            java.util.Calendar.TUESDAY -> "අඟහරුවාදා (Tue)"
            java.util.Calendar.WEDNESDAY -> "බදාදා (Wed)"
            java.util.Calendar.THURSDAY -> "බ්‍රහස්පතින්දා (Thu)"
            java.util.Calendar.FRIDAY -> "සිකුරාදා (Fri)"
            java.util.Calendar.SATURDAY -> "සෙනසුරාදා (Sat)"
            else -> ""
        }
    }

    fun formatSinhalaDisplayDate(dateKey: String): String {
        return try {
            val date = dateFormat.parse(dateKey)
            if (date != null) {
                val dayName = getDayOfWeekSinhala(date.time)
                "$dateKey • $dayName"
            } else {
                dateKey
            }
        } catch (e: Exception) {
            dateKey
        }
    }
}
