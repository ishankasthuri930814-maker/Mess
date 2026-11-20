package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_ID_ALERTS = "police_alerts_channel"
    const val CHANNEL_ID_EMERGENCY = "police_emergency_channel"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "පොලිස් දැනුම්දීම් (Police Alerts)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "OIC සහ මූලස්ථානය මගින් එවන හදිසි හා සාමාන්‍ය දැනුම්දීම්"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)

            val emergencyChannel = NotificationChannel(
                CHANNEL_ID_EMERGENCY,
                "හදිසි GPS ඇමතුම් (Emergency SOS)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ආසන්නයේ සිටින නිලධාරීන්ගෙන් සහ OIC වෙතින් ලැබෙන හදිසි GPS ඇමතුම්"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 500)
            }
            manager.createNotificationChannel(emergencyChannel)
        }
    }

    fun showSystemNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
        isUrgent: Boolean = false
    ) {
        createNotificationChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title.ifBlank { "All In One Police" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (isUrgent) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between check and notify
        }
    }

    fun showEmergencyNotification(
        context: Context,
        alertId: String,
        senderName: String,
        message: String,
        lat: Double,
        lng: Double,
        phone: String = ""
    ) {
        createNotificationChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notifId = alertId.hashCode()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_sos_alert_id", alertId)
            putExtra("open_sos_lat", lat)
            putExtra("open_sos_lng", lng)
            putExtra("open_sos_title", "🚨 හදිසි SOS: $senderName")
            putExtra("open_sos_msg", message)
            putExtra("open_sos_phone", phone)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = "$message\n📍 සිතියමේ මාර්ගය බැලීමට මෙය Click කරන්න."
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_EMERGENCY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🚨 හදිසි ආධාර ඇමතුමක් ($senderName)")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // Ignore
        }
    }
}
