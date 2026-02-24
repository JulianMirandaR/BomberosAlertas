package com.cuartel35.bomberosalertas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class AlertResponseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alertaId = intent.getStringExtra("alerta_id")
        val estado = intent.getStringExtra("estado") // "DISPONIBLE" or "NO DISPONIBLE"
        val notificationId = intent.getIntExtra("notification_id", -1)
        
        Log.d("AlertReceiver", "Action received: $estado for alert: $alertaId")

        if (alertaId == null || estado == null) {
            cancelNotification(context, notificationId)
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("AlertReceiver", "No user logged in")
            Toast.makeText(context, "Debes iniciar sesión para responder", Toast.LENGTH_SHORT).show()
            cancelNotification(context, notificationId)
            return
        }

        val db = FirebaseFirestore.getInstance()
        
        // Show a quick toast to the user
        Toast.makeText(context, "Respuesta enviada: $estado", Toast.LENGTH_SHORT).show()
        
        // Cancel the notification immediately so user knows something happened
        cancelNotification(context, notificationId)

        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
            val nombre = doc.getString("nombre") ?: user.email ?: "Anonimo"
            val respuesta = hashMapOf(
                "usuario" to nombre,
                "uid" to user.uid,
                "estado" to estado,
                "fecha" to Date()
            )
            
            db.collection("alertas").document(alertaId)
                .collection("respuestas").document(user.uid)
                .set(respuesta)
                .addOnSuccessListener {
                    Log.d("AlertReceiver", "Respuesta guardada con éxito: $estado")
                }
                .addOnFailureListener { e ->
                    Log.e("AlertReceiver", "Error al guardar respuesta", e)
                }
        }.addOnFailureListener { e ->
            Log.e("AlertReceiver", "Error obteniendo usuario", e)
            val respuesta = hashMapOf(
                "usuario" to (user.email ?: "Anonimo"),
                "uid" to user.uid,
                "estado" to estado,
                "fecha" to Date()
            )
            db.collection("alertas").document(alertaId)
                .collection("respuestas").document(user.uid)
                .set(respuesta)
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
