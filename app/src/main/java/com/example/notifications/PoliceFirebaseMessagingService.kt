package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PoliceFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("PoliceFCM", "Refreshed FCM token")
        getSharedPreferences("police_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pending_fcm_token", token)
            .apply()

        val uid = Firebase.auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            try {
                val db = FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
                db.collection("users").document(uid)
                    .update(
                        mapOf(
                            "fcmToken" to token,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
            } catch (e: Exception) {
                Log.w("PoliceFCM", "Could not sync refreshed FCM token immediately", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "All In One Police"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: ""
        if (body.isBlank()) return

        val isUrgent = message.data["priority"]?.equals("URGENT", ignoreCase = true) == true
        val notifId = (message.messageId?.hashCode() ?: System.currentTimeMillis().toInt())
        NotificationHelper.showSystemNotification(
            context = applicationContext,
            title = title,
            body = body,
            notificationId = notifId,
            isUrgent = isUrgent
        )
    }
}
