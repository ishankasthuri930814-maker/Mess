package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object MapMarkerBitmapHelper {

    fun createOfficerPinDrawable(
        context: Context,
        label: String,
        isOnDuty: Boolean,
        isSelected: Boolean = false
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subText = if (label.length > 14) label.take(13) + "…" else label
        val textWidth = textPaint.measureText(subText)
        val badgeDiameter = (24f * density).toInt()
        val padH = (8f * density).toInt()
        val pillWidth = maxOf((textWidth + padH * 2).toInt(), (48f * density).toInt())
        val pillHeight = (20f * density).toInt()
        val totalWidth = maxOf(pillWidth, badgeDiameter + (8f * density).toInt())
        val totalHeight = pillHeight + badgeDiameter + (10f * density).toInt()

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val primaryColor = when {
            isSelected -> Color.parseColor("#0284C7")
            isOnDuty -> Color.parseColor("#16A34A")
            else -> Color.parseColor("#EA580C")
        }
        val borderColor = if (isSelected) Color.parseColor("#FACC15") else Color.WHITE

        // Top name pill
        val pillLeft = (totalWidth - pillWidth) / 2f
        val pillRect = RectF(pillLeft, 0f, pillLeft + pillWidth, pillHeight.toFloat())
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primaryColor }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }
        canvas.drawRoundRect(pillRect, 8f * density, 8f * density, bgPaint)
        canvas.drawRoundRect(pillRect, 8f * density, 8f * density, strokePaint)

        val textY = pillHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        val textX = totalWidth / 2f - textWidth / 2f
        canvas.drawText(subText, textX, textY, textPaint)

        // Bottom circular dot/shield pin
        val cx = totalWidth / 2f
        val cy = pillHeight + (4f * density) + badgeDiameter / 2f
        val r = badgeDiameter / 2f
        canvas.drawCircle(cx, cy, r, bgPaint)
        canvas.drawCircle(cx, cy, r - 1f * density, strokePaint)

        val statusLetter = if (isOnDuty) "ON" else "OFF"
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (if (isOnDuty) 9f else 8f) * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val dy = cy - (dotPaint.descent() + dotPaint.ascent()) / 2f
        canvas.drawText(statusLetter, cx, dy, dotPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    fun createStayPointDrawable(context: Context, durationLabel: String): Drawable {
        val density = context.resources.displayMetrics.density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val tw = textPaint.measureText(durationLabel)
        val w = maxOf((tw + 14f * density).toInt(), (36f * density).toInt())
        val h = (22f * density).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val rect = RectF(1f, 1f, w - 1f, h - 1f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6D28D9") }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawRoundRect(rect, 11f * density, 11f * density, fill)
        canvas.drawRoundRect(rect, 11f * density, 11f * density, stroke)
        val ty = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(durationLabel, w / 2f, ty, textPaint)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun createEmergencyPinDrawable(context: Context, label: String = "SOS"): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (38f * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        val outerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(95, 220, 38, 38) }
        val innerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DC2626") }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
        }
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawCircle(cx, cy, size / 2f, outerRing)
        canvas.drawCircle(cx, cy, size * 0.36f, innerFill)
        canvas.drawCircle(cx, cy, size * 0.36f, stroke)
        val ty = cy - (txt.descent() + txt.ascent()) / 2f
        canvas.drawText(label, cx, ty, txt)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun createMyLocationDotDrawable(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (24f * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 37, 99, 235) }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
        }
        canvas.drawCircle(cx, cy, size / 2f, halo)
        canvas.drawCircle(cx, cy, size * 0.30f, core)
        canvas.drawCircle(cx, cy, size * 0.30f, border)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun getTrailColorForOfficer(officerId: String): Int {
        val palette = listOf(
            Color.parseColor("#2563EB"), // Blue
            Color.parseColor("#059669"), // Emerald
            Color.parseColor("#9333EA"), // Purple
            Color.parseColor("#D97706"), // Amber
            Color.parseColor("#0891B2"), // Cyan
            Color.parseColor("#DB2777"), // Pink
            Color.parseColor("#4F46E5")  // Indigo
        )
        val idx = (officerId.hashCode() and 0x7FFFFFFF) % palette.size
        return palette[idx]
    }
}
