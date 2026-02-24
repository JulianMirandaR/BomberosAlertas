package com.cuartel35.bomberosalertas

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvBackToLogin = findViewById<TextView>(R.id.tvBackToLogin)

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(
                    this,
                    "Completá todos los campos",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(
                    this,
                    "Las contraseñas no coinciden",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(
                    this,
                    "La contraseña debe tener al menos 6 caracteres",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("REGISTER", "Registro exitoso")
                        
                        // Limpiar cualquier código de cuartel viejo en el dispositivo
                        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        prefs.edit().remove("group_code").apply()

                        // Crear documento de usuario en Firestore
                        val user = auth.currentUser
                        if (user != null) {
                            val db = FirebaseFirestore.getInstance()
                            val userData = hashMapOf(
                                "email" to email,
                                "codigoCuartel" to "",
                                "rol" to "BOMBERO",
                                "nombre" to "",
                                "grupoSanguineo" to "",
                                "condiciones" to ""
                            )
                            db.collection("users").document(user.uid).set(userData)
                            
                            // Send email verification
                            user.sendEmailVerification().addOnCompleteListener { verifyTask ->
                                if (verifyTask.isSuccessful) {
                                    Toast.makeText(
                                        this,
                                        "Se envió un correo de verificación a tu casilla.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        this,
                                        "Cuenta creada, pero hubo un error al enviar el correo de verificación.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                
                                // Continue to main activity immediately
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                        }
                    } else {
                        Toast.makeText(
                            this,
                            "Error al crear la cuenta",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        tvBackToLogin.setOnClickListener {
            finish()
        }
    }
}
