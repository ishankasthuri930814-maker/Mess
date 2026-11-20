package com.example.util

import java.security.MessageDigest
import kotlin.math.abs

object AdminPasscodeUtils {

    /**
     * Generates a deterministic 6-digit passcode for the Administration Branch
     * for a given police station code, optional 6-digit custom secret seed chosen
     * by the Administration Branch during registration, and date (YYYY-MM-DD).
     * Automatically rotates every day at midnight using SHA-256 mixing.
     * Guaranteed to be a 6-digit string between "100000" and "999999".
     */
    fun generateDailyAdminPin(
        oicStationCode: String,
        dateKey: String = DateTimeUtils.getTodayKey(),
        customSeed: String = ""
    ): String {
        val cleanCode = oicStationCode.trim().uppercase()
        val cleanDate = dateKey.trim()
        val cleanSeed = customSeed.trim()
        val rawInput = if (cleanSeed.isNotBlank()) {
            "POLICE_ADMIN_${cleanCode}_${cleanSeed}_$cleanDate"
        } else {
            "POLICE_ADMIN_${cleanCode}_$cleanDate"
        }
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(rawInput.toByteArray(Charsets.UTF_8))
            val value = ((digest[0].toInt() and 0x7F) shl 24) or
                ((digest[1].toInt() and 0xFF) shl 16) or
                ((digest[2].toInt() and 0xFF) shl 8) or
                (digest[3].toInt() and 0xFF)
            val pin = (abs(value) % 900000) + 100000
            pin.toString()
        } catch (_: Exception) {
            val hash = abs(rawInput.hashCode())
            val pin = (hash % 900000) + 100000
            pin.toString()
        }
    }

    /**
     * Validates whether the user-entered 6-digit PIN matches the station's passcode for the given date.
     */
    fun isValidDailyAdminPin(
        oicStationCode: String,
        enteredPin: String,
        dateKey: String = DateTimeUtils.getTodayKey(),
        customSeed: String = ""
    ): Boolean {
        val cleanEntered = enteredPin.trim()
        if (cleanEntered.length != 6 || !cleanEntered.all { it.isDigit() }) return false
        val expected = generateDailyAdminPin(oicStationCode, dateKey, customSeed)
        return cleanEntered == expected
    }
}
