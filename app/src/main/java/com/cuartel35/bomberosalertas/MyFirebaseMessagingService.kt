package com.cuartel35.bomberosalertas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM", "Mensaje recibido: ${message.data}")

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "🚨 Alerta"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Nueva alerta recibida"

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val channelId = prefs.getString("channel_id", "alertas_channel") ?: "alertas_channel"
        val soundUriString = prefs.getString("alert_sound_uri", null)
        val soundUri = if (soundUriString != null) android.net.Uri.parse(soundUriString) else null

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Check if channel needs to be created (or recreated if missing)
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Alertas",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificaciones de alertas de incendio"
                    if (soundUri != null) {
                         setSound(soundUri, android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    }
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                }
                manager.createNotificationChannel(channel)
            }
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (soundUri != null) {
            notificationBuilder.setSound(soundUri)
        }

        manager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Nuevo token: $token")
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        // Guardar token en Firestore para que las Cloud Functions puedan leerlo
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        val tokenData = hashMapOf(
            "token" to token,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        // Usamos el token como ID del documento para evitar duplicados fácilmente
        db.collection("tokens").document(token)
            .set(tokenData)
            .addOnSuccessListener { 
                Log.d("FCM", "Token guardado en Firestore") 
            }
            .addOnFailureListener { e -> 
                Log.e("FCM", "Error guardando token", e) 
            }
    }
}
