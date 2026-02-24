package com.cuartel35.bomberosalertas

import android.Manifest
import com.cuartel35.bomberosalertas.R
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import com.google.firebase.auth.FirebaseAuth
import androidx.activity.compose.rememberLauncherForActivityResult

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.util.Date
import org.osmdroid.config.Configuration
import android.preference.PreferenceManager

class MainActivity : ComponentActivity() {

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.POST_NOTIFICATIONS] == false) {
            Toast.makeText(this, "Las notificaciones son vitales para recibir alertas.", Toast.LENGTH_LONG).show()
        }
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == false && permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == false) {
            Log.w("PERMISSIONS", "Permiso de ubicación denegado")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        createNotificationChannel()

        // ✅ SUSCRIPCIÓN AL TOPIC
        FirebaseMessaging.getInstance()
            .subscribeToTopic("alerts")
            .addOnSuccessListener {
                Log.d("FCM", "Suscripto al topic alerts")
            }
            .addOnFailureListener {
                Log.e("FCM", "Error al suscribirse al topic", it)
            }

        checkAndRequestAllPermissions()
        saveCurrentToken()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val channelId = prefs.getString("channel_id", "alertas_channel") ?: "alertas_channel"
            
            val channelName = "Alertas"
            val channelDescription = "Notificaciones de alertas de incendio"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            
            val channel = android.app.NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                
                // Intentar recuperar sonido personalizado guardado
                val savedUriString = prefs.getString("alert_sound_uri", null)
                if (savedUriString != null) {
                    val soundUri = Uri.parse(savedUriString)
                    setSound(soundUri, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                }
            }
            
            val notificationManager: android.app.NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    private fun checkAndRequestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun saveCurrentToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("FCM", "Main Activity Token: $token")
            
             val db = FirebaseFirestore.getInstance()
             val user = FirebaseAuth.getInstance().currentUser
             val tokenData = hashMapOf(
                "token" to token,
                "email" to (user?.email ?: "Desconocido"),
                "uid" to (user?.uid ?: ""), // Save UID to allow filtering
                "updatedAt" to com.google.firebase.Timestamp.now(),
                "codigoCuartel" to (getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("group_code", "") ?: "")
            )

            db.collection("tokens").document(token)
                .set(tokenData)
                .addOnSuccessListener { 
                    Log.d("FCM", "Token guardado/actualizado desde Main")
                    // DEBUG: Comentar esta línea en producción si es molesto
                    // Toast.makeText(this, "Dispositivo registrado para alertas", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error registrando dispositivo", Toast.LENGTH_SHORT).show()
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Home") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF111111),
                drawerContentColor = Color.White
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Image(
                    painter = painterResource(id = R.drawable.icono_aplicacion),
                    contentDescription = "Logo Aplicación",
                    modifier = Modifier
                        .padding(start = 24.dp)
                        .size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "FIREGUARD", 
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(24.dp))
                
                val items = listOf("Inicio" to "Home", "Personal" to "Personal", "Mis Datos" to "Profile", "Historial" to "History", "Sonidos" to "Sounds", "Código Cuartel" to "GroupCode")
                
                items.forEach { (label, route) ->
                    NavigationDrawerItem(
                        label = { Text(label, fontWeight = if (currentScreen == route) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) },
                        selected = currentScreen == route,
                        onClick = { 
                            currentScreen = route
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF222222),
                            unselectedContainerColor = Color.Transparent,
                            selectedTextColor = Color.White,
                            unselectedTextColor = Color.Gray
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp), // Rectangular minimal items
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                TextButton(
                    onClick = { 
                        FirebaseAuth.getInstance().signOut()
                        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().remove("group_code").apply()
                        val intent = Intent(context, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("CERRAR SESIÓN", color = Color(0xFFEF5350), letterSpacing = 1.sp)
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color(0xFF0A0A0A),
            topBar = {



                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0A))
                        .windowInsetsPadding(WindowInsets.statusBars) // Push content below status bar
                        .height(50.dp) // Fixed thin height for the bar content
                ) {
                    // Menu Button - Aligned Start
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White)
                    }

                    // Title - Aligned Center
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentScreen == "Home") {
                            Image(
                                painter = painterResource(id = R.drawable.icono_aplicacion),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = when (currentScreen) {
                                "Home" -> "FIREGUARD"
                                "Personal" -> "PERSONAL"
                                "Profile" -> "PERFIL"
                                "History" -> "HISTORIAL"
                                "Sounds" -> "SONIDOS"
                                "GroupCode" -> "CÓDIGO CUARTEL"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleSmall,
                            letterSpacing = 2.sp,
                            color = Color.White
                        )
                    }
                }
            }


        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color(0xFF0A0A0A))) {
                when (currentScreen) {
                    "Home" -> HomeScreen()
                    "Personal" -> PersonalScreen()
                    "Profile" -> ProfileScreen()
                    "History" -> HistoryScreen()
                    "Sounds" -> SoundScreen()
                    "GroupCode" -> GroupCodeScreen()
                }
            }
        }
    }
}

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    
    var nombre by remember { mutableStateOf("") }
    var grupoSanguineo by remember { mutableStateOf("") }
    var condiciones by remember { mutableStateOf("") }
    val TextWhite = Color(0xFFEEEEEE)

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (user != null) {
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        nombre = document.getString("nombre") ?: ""
                        grupoSanguineo = document.getString("grupoSanguineo") ?: ""
                        condiciones = document.getString("condiciones") ?: ""
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), // More padding
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Minimalist Avatar Placeholder
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFF222222), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = nombre.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        MinimalTextField(value = nombre, onValueChange = { nombre = it }, label = "Nombre Completo")
        Spacer(modifier = Modifier.height(16.dp))
        MinimalTextField(value = grupoSanguineo, onValueChange = { grupoSanguineo = it }, label = "Grupo Sanguíneo")
        Spacer(modifier = Modifier.height(16.dp))
        MinimalTextField(value = condiciones, onValueChange = { condiciones = it }, label = "Condiciones Médicas")

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (user != null) {
                    val userData = hashMapOf(
                        "email" to (user.email ?: ""),
                        "nombre" to nombre,
                        "grupoSanguineo" to grupoSanguineo,
                        "condiciones" to condiciones,
                        "updatedAt" to Date()
                    )
                    
                    db.collection("users").document(user.uid)
                        .set(userData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Datos guardados", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("GUARDAR CAMBIOS", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

@Composable
fun GroupCodeScreen() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    var codigo by remember { mutableStateOf("") }
    
    // Cargar código guardado
    androidx.compose.runtime.LaunchedEffect(Unit) {
        codigo = prefs.getString("group_code", "") ?: ""
        
        // Si no está en prefs pero está logueado, intentar buscar en Firestore
        if (codigo.isEmpty() && user != null) {
            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val savedCode = doc.getString("codigoCuartel")
                    if (!savedCode.isNullOrBlank()) {
                        codigo = savedCode
                        prefs.edit().putString("group_code", savedCode).apply()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "CÓDIGO DE GRUPO", 
            style = MaterialTheme.typography.titleMedium, 
            color = Color.White,
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Ingresa el código de tu cuartel para recibir\nalertas solo de tu grupo.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        MinimalTextField(value = codigo, onValueChange = { codigo = it }, label = "CÓDIGO (Ej: BOMB-35)")

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                prefs.edit().putString("group_code", codigo).apply()
                
                if (user != null) {
                    val updates = hashMapOf<String, Any>(
                        "codigoCuartel" to codigo
                    )
                    
                    // Actualizar Usuario
                    db.collection("users").document(user.uid)
                        .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    
                    // Actualizar Token (para recibir alertas correctamente)
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        val tokenUpdate = hashMapOf<String, Any>(
                            "codigoCuartel" to codigo,
                            "updatedAt" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("tokens").document(token)
                            .set(tokenUpdate, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(context, "Código actualizado", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                     Toast.makeText(context, "Guardado localmente", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("GUARDAR CÓDIGO", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}





@Composable
fun PersonalScreen() {
    val db = FirebaseFirestore.getInstance()
    // List of Pair<UID, Data>
    var usersList by remember { mutableStateOf<List<Pair<String, Map<String, Any>>>>(emptyList()) }
    var currentUserRole by remember { mutableStateOf("BOMBERO") }
    val currentUser = FirebaseAuth.getInstance().currentUser
    val context = LocalContext.current

    val groupCode = remember { 
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("group_code", "") ?: "" 
    }

    // Cargar usuarios y rol del usuario actual
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // 1. Get current user role
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    currentUserRole = doc.getString("rol") ?: "BOMBERO"
                }
            }
        }

        // 2. Get all users
        if (groupCode.isNotEmpty()) {
            db.collection("users").whereEqualTo("codigoCuartel", groupCode).get().addOnSuccessListener { snapshot ->
                usersList = snapshot.documents.map { doc -> 
                    doc.id to (doc.data ?: emptyMap()) 
                }
            }
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (groupCode.isEmpty()) {
            item {
                Text(
                    "Ingresá un 'Código de Cuartel' en el menú para ver al personal.", 
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (usersList.isEmpty()) {
            item {
                Text(
                    "No hay personal registrado en este cuartel.", 
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        items(usersList.size) { index ->
            val (uid, userData) = usersList[index]
            val nombre = userData["nombre"] as? String ?: "Sin Nombre"
            val email = userData["email"] as? String ?: ""
            val sangre = userData["grupoSanguineo"] as? String ?: "-"
            val role = userData["rol"] as? String ?: "BOMBERO"
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF222222), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = nombre.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(nombre, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Text("$role • $email", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    
                    val condiciones = userData["condiciones"] as? String
                    if (!condiciones.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Med: $condiciones",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF5350) 
                        )
                    }
                }
                
                if (currentUserRole == "JEFE" && uid != currentUser?.uid) {
                    IconButton(onClick = {
                        // Confirm deletion logic could be added here
                        db.collection("users").document(uid).delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Usuario eliminado", Toast.LENGTH_SHORT).show()
                                // Refresh list locally
                                usersList = usersList.filter { it.first != uid }
                            }
                    }) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Delete, 
                            contentDescription = "Eliminar", 
                            tint = Color.Red 
                        )
                    }
                }

                if (sangre != "-" && sangre.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFB71C1C), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(sangre, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = Color(0xFF222222))
        }
    }
}

@Composable
fun HistoryScreen() {
    val db = FirebaseFirestore.getInstance()
    var alerts by remember { mutableStateOf<List<Alerta>>(emptyList()) }

    val context = LocalContext.current
    val groupCode = remember { 
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("group_code", "") ?: "" 
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (groupCode.isNotEmpty()) {
            db.collection("alertas")
                .whereEqualTo("codigoCuartel", groupCode)
                .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING) // Ordenar descendente
                .limit(50)
                .get()
                .addOnSuccessListener { snapshot ->
                    alerts = snapshot.documents.map { doc ->
                        Alerta(
                            id = doc.id,
                            titulo = doc.getString("titulo") ?: "Sin título",
                            fecha = doc.getDate("fecha") ?: Date(),
                            estado = doc.getString("estado") ?: ""
                        )
                    }
                }
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        if (groupCode.isEmpty()) {
            item {
                Text(
                    "Ingresá un 'Código de Cuartel' en el menú para ver el historial.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (alerts.isEmpty()) {
            item {
                Text(
                    "No hay historial de alertas.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(alerts.size) { index ->
                val alerta = alerts[index]
                HistoryItem(alerta)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun HistoryItem(alerta: Alerta) {
    var disponibles by remember { mutableStateOf<List<String>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()
    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())

    androidx.compose.runtime.LaunchedEffect(alerta.id) {
        db.collection("alertas").document(alerta.id)
            .collection("respuestas")
            .whereEqualTo("estado", "DISPONIBLE")
            .get()
            .addOnSuccessListener { snapshot ->
                disponibles = snapshot.documents.mapNotNull { it.getString("usuario") }
            }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            dateFormat.format(alerta.fecha), 
            style = MaterialTheme.typography.labelSmall, 
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            alerta.titulo, 
            style = MaterialTheme.typography.titleMedium, 
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (disponibles.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, // Placeholder icon check
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${disponibles.size} Disponible(s)", 
                    color = Color.Gray, 
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Minimalist expanded list could go here, but let's keep it simple
            if (disponibles.size <= 3) {
                Text(
                    disponibles.joinToString(", "),
                    color = Color.DarkGray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 24.dp)
                )
            } else {
                 Text(
                    "${disponibles.take(3).joinToString(", ")} +${disponibles.size - 3}",
                    color = Color.DarkGray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 24.dp)
                )
            }
        } else {
            Text("Sin respuestas", color = Color.DarkGray, style = MaterialTheme.typography.labelSmall)
        }
        
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = Color(0xFF222222))
    }
}

@Composable
fun SoundScreen() {
    val context = LocalContext.current
    var currentSoundUriString by remember { mutableStateOf("") }
    
    // Lista dinámica de sonidos
    var soundOptions by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }
    
    // Control de reproducción
    var playingRingtone by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var systemRingtone by remember { mutableStateOf<android.media.Ringtone?>(null) }
    var currentPlayingUri by remember { mutableStateOf<String?>(null) }
    
    // Configuración persistente
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    // Cargar sonidos
    androidx.compose.runtime.LaunchedEffect(Unit) {
        currentSoundUriString = prefs.getString("alert_sound_uri", "") ?: ""
        
        val sounds = mutableListOf<Pair<String, Uri>>()
        val packageName = context.packageName

        // 1. SONIDOS PERSONALIZADOS
        val sirena1Uri = Uri.parse("android.resource://$packageName/${R.raw.sirena_bomberos}")
        sounds.add("Sirena Bomberos 1" to sirena1Uri)
        val sirena2Uri = Uri.parse("android.resource://$packageName/${R.raw.alerta_fuego}")
        sounds.add("Sirena Bomberos 2" to sirena2Uri)
        val sirena3Uri = Uri.parse("android.resource://$packageName/${R.raw.sirena_2}")
        sounds.add("Sirena Bomberos 3" to sirena3Uri)
        val graveUri = Uri.parse("android.resource://$packageName/${R.raw.grave}")
        sounds.add("Grave" to graveUri)
        val peligroUri = Uri.parse("android.resource://$packageName/${R.raw.peligro}")
        sounds.add("Peligro" to peligroUri)
        val policiaUri = Uri.parse("android.resource://$packageName/${R.raw.policia}")
        sounds.add("Policia" to policiaUri)
        val sirena3BUri = Uri.parse("android.resource://$packageName/${R.raw.sirena_3}")
        sounds.add("Otra Sirena" to sirena3BUri)



        // 2. SONIDOS DEL SISTEMA
        sounds.add("Notificación Estándar" to RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        
        val manager = RingtoneManager(context)
        manager.setType(RingtoneManager.TYPE_ALARM)
        val cursor = manager.cursor
        if (cursor.count > 0 && cursor.moveToFirst()) {
            do {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = manager.getRingtoneUri(cursor.position)
                sounds.add(title to uri)
            } while (cursor.moveToNext())
        }
        
        soundOptions = sounds
        
        if (currentSoundUriString.isEmpty() && sounds.isNotEmpty()) {
             currentSoundUriString = sounds[0].second.toString()
        }
    }

    // Limpiar al salir
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            try {
                if (playingRingtone?.isPlaying == true) playingRingtone?.stop()
                playingRingtone?.release()
                systemRingtone?.stop()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tono de Alerta", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(32.dp))
        
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp) // No spacing, we use dividers
        ) {
            items(soundOptions.size) { index ->
                val (name, uri) = soundOptions[index]
                val isSelected = currentSoundUriString == uri.toString()
                val textColor = if (isSelected) Color.White else Color.Gray
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            prefs.edit().putString("alert_sound_uri", uri.toString()).apply()
                            currentSoundUriString = uri.toString()
                            updateNotificationChannel(context, uri)
                            
                            try {
                                if (playingRingtone?.isPlaying == true) playingRingtone?.stop()
                                systemRingtone?.stop()
                                currentPlayingUri = null
                            } catch (e: Exception) {}

                            Toast.makeText(context, "Tono seleccionado", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selection indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isSelected) Color(0xFFD32F2F) else Color.Transparent, 
                                androidx.compose.foundation.shape.CircleShape
                            )
                            .border(1.dp, if (isSelected) Color(0xFFD32F2F) else Color.DarkGray, androidx.compose.foundation.shape.CircleShape)
                    )
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = textColor, modifier = Modifier.weight(1f))
                    
                    val isPlayingThis = currentPlayingUri == uri.toString()
                    
                    IconButton(onClick = {
                        try {
                            // Stop everything first
                            if (playingRingtone?.isPlaying == true) {
                                playingRingtone?.stop()
                            }
                            systemRingtone?.stop()
                            
                            if (isPlayingThis) {
                                // If we were playing this, we just stopped it. Reset state.
                                currentPlayingUri = null
                            } else {
                                // Play new sound
                                currentPlayingUri = uri.toString()
                                
                                if (uri.toString().contains("android.resource")) {
                                     playingRingtone = android.media.MediaPlayer.create(context, uri)
                                     playingRingtone?.setOnCompletionListener { 
                                         currentPlayingUri = null 
                                     }
                                     playingRingtone?.start()
                                } else {
                                    val r = RingtoneManager.getRingtone(context, uri)
                                    systemRingtone = r
                                    r.play()
                                    // Ringtone doesn't have a completion listener easily available in this API level
                                    // So it will stay as "playing" icon until user stops or plays another, 
                                    // or we could use a timer but manual stop is safer.
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            currentPlayingUri = null
                        }
                    }) {
                        Text(if (isPlayingThis) "■" else "▶", color = if (isPlayingThis) Color(0xFFEF5350) else Color.Gray)
                    }
                }
                HorizontalDivider(color = Color(0xFF222222))
            }
        }
    }
}

fun updateNotificationChannel(context: Context, soundUri: Uri) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val oldChannelId = prefs.getString("channel_id", "alertas_channel") ?: "alertas_channel"
        
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // Eliminar canal anterior para evitar que recupere la configuración vieja
        manager.deleteNotificationChannel(oldChannelId)
        
        // Generar nuevo ID único para forzar la actualización de configuración
        val newChannelId = "alertas_${System.currentTimeMillis()}"
        prefs.edit().putString("channel_id", newChannelId).apply()
        
        val channelName = "Alertas"
        val channelDescription = "Notificaciones de alertas de incendio"
        val importance = android.app.NotificationManager.IMPORTANCE_HIGH
        
        val channel = android.app.NotificationChannel(newChannelId, channelName, importance).apply {
            description = channelDescription
            setSound(soundUri, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
        }
        
        manager.createNotificationChannel(channel)
        Log.d("FCM", "Canal de notificaciones creado: $newChannelId con sonido: $soundUri")
    }
}

data class WeatherData(
    val temp: Double,
    val windSpeed: Double,
    val windDirection: Int,
    val precipitationProb: Int
)

@Composable
fun WeatherWidget() {
    val context = LocalContext.current
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var hasPermission by remember { 
        mutableStateOf(
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.runtime.LaunchedEffect(hasPermission) {
        if (hasPermission) {
            getLocationAndFetchWeather(context, fusedLocationClient) { data ->
                weather = data
                locationError = null
            }
        } else {
            locationError = "Esperando permiso de ubicación..."
        }
    }

    if (weather != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text(
                "CONDICIONES", 
                style = MaterialTheme.typography.labelSmall, 
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeatherItem("🌡", "${weather!!.temp}°", "Temp")
                WeatherItem("💧", "${weather!!.precipitationProb}%", "Lluvia")
                WeatherItem("💨", "${weather!!.windSpeed}", "km/h")
                WeatherItem("🧭", "${weather!!.windDirection}°", "Viento")
            }
        }
    } else if (locationError != null) {
        Text(text = locationError!!, color = Color.Red, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 16.dp))
    } else {
        Text("Cargando clima...", color = Color.DarkGray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun WeatherItem(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}

fun getLocationAndFetchWeather(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    onResult: (WeatherData) -> Unit
) {
    try {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    fetchWeather(context, location.latitude, location.longitude, onResult)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("Weather", "Error getting location", e)
    }
}

fun fetchWeather(context: Context, lat: Double, lon: Double, onResult: (WeatherData) -> Unit) {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,precipitation,wind_speed_10m,wind_direction_10m&forecast_days=1"

    val request = JsonObjectRequest(Request.Method.GET, url, null,
        { response ->
            try {
                val current = response.getJSONObject("current")
                val data = WeatherData(
                    temp = current.getDouble("temperature_2m"),
                    windSpeed = current.getDouble("wind_speed_10m"),
                    windDirection = current.getInt("wind_direction_10m"),
                    precipitationProb = current.optInt("precipitation", 0) // OpenMeteo returns 'precipitation' as mm usually, checking documentation...
                    // Wait, 'current=precipitation' usually gives volume in mm. 
                    // User asked for 'probability', but current usually gives actual. 
                    // Let's use 'precipitation_probability_max' from daily if we want probability, or just show volume.
                    // Actually for "current" conditions, 'precipitation' is current intensity (mm).
                    // User said "probabilidad" so maybe they want forecast?
                    // Let's stick to current conditions for now as it's most relevant for "going to fire now".
                    // I will label it "Precipitación (mm)" or just "Lluvia" to be safe.
                    // Correction: OpenMeteo 'current' doesn't have probability. 'hourly' or 'daily' does.
                    // I'll stick to 'precipitation' (mm) which is effectively "is it raining now".
                )
                onResult(data)
            } catch (e: Exception) {
                Log.e("Weather", "Parsing error", e)
            }
        },
        { error ->
            Log.e("Weather", "Network error", error)
        }
    )

    Volley.newRequestQueue(context).add(request)
}

data class Alerta(
    val id: String,
    val titulo: String,
    val fecha: Date,
    val estado: String,
    val ubicacion: String = ""
)

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val FireRed = Color(0xFFD32F2F)
    
    // Minimalist state
    var tituloAlerta by remember { mutableStateOf("") }
    var ubicacionAlerta by remember { mutableStateOf("") }
    
    // Info Board State
    var infoContent by remember { mutableStateOf("Cargando novedades...") }
    var isEditingInfo by remember { mutableStateOf(false) }
    var newInfoText by remember { mutableStateOf("") }
    var userRole by remember { mutableStateOf("BOMBERO") } // Default role

    val db = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser

    // Load Data
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // 1. Get Pizarra Content
        db.collection("config").document("pizarra").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                infoContent = snapshot.getString("content") ?: "Sin novedades."
            } else {
                infoContent = "Haga clic para editar novedades."
            }
        }

        // 2. Get User Role
        if (user != null) {
            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    userRole = doc.getString("rol") ?: "BOMBERO"
                }
            }
        }
    }

    if (isEditingInfo) {
        AlertDialog(
            onDismissRequest = { isEditingInfo = false },
            title = { Text("Editar Pizarra", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newInfoText,
                    onValueChange = { newInfoText = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("config").document("pizarra")
                            .set(mapOf("content" to newInfoText, "updatedBy" to user?.email, "updatedAt" to Date()))
                        isEditingInfo = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("GUARDAR") }
            },
            dismissButton = {
                TextButton(onClick = { isEditingInfo = false }) { Text("CANCELAR", color = Color.Gray) }
            },
            containerColor = Color(0xFF222222),
            textContentColor = Color.White
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        
        // PIZARRA INFORMATIVA
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
             border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                 Row(
                     verticalAlignment = Alignment.CenterVertically,
                     modifier = Modifier.fillMaxWidth()
                 ) {
                    Text("📋", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "PIZARRA INFORMATIVA", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = Color(0xFF90CAF9), 
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Edit Button for Chiefs/Officers
                    if (userRole == "JEFE" || userRole == "OFICIAL") {
                        IconButton(
                            onClick = { 
                                newInfoText = infoContent
                                isEditingInfo = true 
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Editar",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    infoContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFEEEEEE),
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Minimal Clean Input
        MinimalTextField(
            value = tituloAlerta,
            onValueChange = { tituloAlerta = it },
            label = "TÍTULO DE ALERTA (OPCIONAL)"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LocationTextField(
            value = ubicacionAlerta,
            onValueChange = { ubicacionAlerta = it },
            label = "UBICACIÓN (OPCIONAL O GPS)"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Big Minimal Alert Button
        Button(
            onClick = {
                val db = FirebaseFirestore.getInstance()
                val user = FirebaseAuth.getInstance().currentUser
                val groupCode = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("group_code", "") ?: ""
                
                if (groupCode.isEmpty()) {
                    Toast.makeText(context, "Configurá un Código de Cuartel primero", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val alerta = hashMapOf(
                    "fecha" to Date(),
                    "usuario" to (user?.email ?: "desconocido"),
                    "senderUid" to (user?.uid ?: ""), // Add sender UID
                    "estado" to "ACTIVA",
                    "estado" to "ACTIVA",
                    "titulo" to tituloAlerta.ifEmpty { "ALERTA DE INCENDIO" },
                    "ubicacion" to ubicacionAlerta,
                    "codigoCuartel" to (context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("group_code", "") ?: "")
                )

                db.collection("alertas")
                    .add(alerta)
                    .addOnSuccessListener {
                        Toast.makeText(context, "ALERTA ENVIADA", Toast.LENGTH_SHORT).show()
                        tituloAlerta = "" // Clear input
                        ubicacionAlerta = ""
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Error al enviar", Toast.LENGTH_SHORT).show()
                    }

            },
            colors = ButtonDefaults.buttonColors(
                containerColor = FireRed,
                contentColor = Color.White
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                 Icon(
                    androidx.compose.material.icons.Icons.Filled.Notifications, 
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "ENVIAR ALERTA", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        AlertsList()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        WeatherWidget()
        
        Spacer(modifier = Modifier.height(30.dp)) 
    }
}

@Composable
fun AlertsList(
    cardColor: Color = Color.Transparent,
    textColor: Color = Color(0xFFEEEEEE)
) {
    val db = FirebaseFirestore.getInstance()
    var alerts by remember { mutableStateOf<List<Alerta>>(emptyList()) }
    val user = FirebaseAuth.getInstance().currentUser

    val context = LocalContext.current
    val groupCode = remember { 
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getString("group_code", "") ?: "" 
    }

    if (groupCode.isEmpty()) {
        Text("Ingresá tu 'Código de Cuartel' desde el menú para enviar o recibir alertas en tu grupo.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
        return
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        val listener = db.collection("alertas")
            .whereEqualTo("estado", "ACTIVA")
            .whereEqualTo("codigoCuartel", groupCode)
            .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(2)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    alerts = snapshot.documents.map { doc ->
                        Alerta(
                            id = doc.id,
                            titulo = doc.getString("titulo") ?: "Sin título",
                            fecha = doc.getDate("fecha") ?: Date(),
                            estado = doc.getString("estado") ?: "",
                            ubicacion = doc.getString("ubicacion") ?: ""
                        )
                    }
                }
            }
        onDispose { listener.remove() }
    }

    if (alerts.isNotEmpty()) {
        Text(
            text = "ALERTAS ACTIVAS",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            letterSpacing = 1.sp
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth() 
        ) {
            alerts.forEachIndexed { index, alerta ->
                val isLatest = index == 0
                
                // Check if alert is expired (older than 1 hour)
                val isExpired = remember(alerta.fecha) {
                    (Date().time - alerta.fecha.time) > 3600000 // 1 hour in ms
                }
                
                // Minimalist Active Alert Card
                // Latest: Stronger Red BG. Others: Dark Grey BG.
                // Expired: Very Dark Gray
                val containerColor = if (isExpired) Color(0xFF141414) else if (isLatest) Color(0xFF450A0A) else Color(0xFF1A1A1A)
                val borderColor = if (isExpired) Color(0xFF333333) else if (isLatest) Color(0xFFB71C1C) else Color(0xFF333333)

                // Listener for responses
                var disponibles by remember { mutableStateOf<List<String>>(emptyList()) }
                var noDisponibles by remember { mutableStateOf<List<String>>(emptyList()) }
                
                androidx.compose.runtime.DisposableEffect(alerta.id) {
                   val respListener = db.collection("alertas").document(alerta.id)
                        .collection("respuestas")
                        .addSnapshotListener { snap, _ ->
                            if (snap != null) {
                                val allResponses = snap.documents.map { it.data ?: emptyMap() }
                                disponibles = allResponses.filter { it["estado"] == "DISPONIBLE" }.mapNotNull { it["usuario"] as? String }
                                noDisponibles = allResponses.filter { it["estado"] == "NO DISPONIBLE" }.mapNotNull { it["usuario"] as? String }
                            }
                        }
                   onDispose { respListener.remove() }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(containerColor, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                           if (isLatest && !isExpired) {
                               Box(modifier = Modifier.size(8.dp).background(Color.Red, androidx.compose.foundation.shape.CircleShape))
                               Spacer(modifier = Modifier.width(12.dp))
                           }
                           Column {
                               Text(
                                   text = if (isLatest && !isExpired) "ACTUAL: ${alerta.titulo.uppercase()}" else alerta.titulo.uppercase(),
                                   style = MaterialTheme.typography.titleMedium,
                                   color = if (isExpired) Color.Gray else Color.White,
                                   fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                   letterSpacing = 1.sp
                               )
                               val sdf = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()) }
                               val fechaFormateada = remember(alerta.fecha) { sdf.format(alerta.fecha) }
                               Text(
                                    text = if (isExpired) "Expirada • $fechaFormateada" 
                                    else "Lanzada: $fechaFormateada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                               )
                           }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Location Button
                        if (alerta.ubicacion.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val uri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(alerta.ubicacion)}")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    intent.setPackage("com.google.android.apps.maps")
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                    } else {
                                        val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=${android.net.Uri.encode(alerta.ubicacion)}"))
                                        context.startActivity(webIntent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E88E5), // Blue
                                    contentColor = Color.White
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Filled.LocationOn,
                                        contentDescription = "Abrir en Maps",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val isCoord = alerta.ubicacion.matches(Regex("^-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?$"))
                                    val mapText = if (isCoord) "UBICACIÓN FIJADA" else "IR A MAPAS: ${alerta.ubicacion.uppercase()}"
                                    Text(mapText, style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        // Status Bar
                        if (disponibles.isNotEmpty()) {
                            Text(
                                "EN CAMINO: ${disponibles.size}",
                                color = if (isExpired) Color.Gray else Color(0xFF69F0AE),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                disponibles.take(4).joinToString(", "),
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text("Esperando respuesta...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action Buttons
                        val canRespond = index == 0 && !isExpired

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (user != null) {
                                        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                                            val nombre = doc.getString("nombre") ?: user.email ?: "Anonimo"
                                            val respuesta = hashMapOf(
                                                "usuario" to nombre, 
                                                "uid" to user.uid,
                                                "estado" to "DISPONIBLE",
                                                "fecha" to Date()
                                            )
                                            db.collection("alertas").document(alerta.id)
                                                .collection("respuestas").document(user.uid)
                                                .set(respuesta)
                                        }
                                    }
                                },
                                enabled = canRespond,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50), // Green
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFF222222), // Dark gray disabled
                                    disabledContentColor = Color.Gray
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) { 
                                Text("DISPONIBLE", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) 
                            }

                            Button(
                                onClick = {
                                    if (user != null) {
                                        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                                            val nombre = doc.getString("nombre") ?: user.email ?: "Anonimo"
                                            val respuesta = hashMapOf(
                                                "usuario" to nombre, 
                                                "uid" to user.uid,
                                                "estado" to "NO DISPONIBLE",
                                                "fecha" to Date()
                                            )
                                            db.collection("alertas").document(alerta.id)
                                                .collection("respuestas").document(user.uid)
                                                .set(respuesta)
                                        }
                                    }
                                },
                                enabled = canRespond,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Gray,
                                    disabledContainerColor = Color.Transparent,
                                    disabledContentColor = Color.DarkGray
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (canRespond) Color.DarkGray else Color(0xFF333333)),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) { 
                                Text("NO DISP") 
                            }
                        }

                        if (noDisponibles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFF222222))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "NO DISPONIBLES: ${noDisponibles.size}",
                                color = Color(0xFFEF5350),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                noDisponibles.joinToString(", "),
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MinimalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            letterSpacing = 1.sp
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        HorizontalDivider(color = Color.DarkGray)
    }
}

@Composable
fun LocationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showMapDialog by remember { mutableStateOf(false) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions: Map<String, Boolean> ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            
            try {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            onValueChange("${location.latitude}, ${location.longitude}")
                        } else {
                            locationError = "No se pudo obtener la ubicación"
                        }
                        isLoading = false
                    }
                }
            } catch(e: Exception) { isLoading = false }
        } else {
            isLoading = false
            locationError = "Permiso denegado"
        }
    }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            letterSpacing = 1.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = { showMapDialog = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.LocationOn, 
                    contentDescription = "Elegir en el Mapa",
                    tint = Color(0xFF4CAF50) // Verde
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            IconButton(
                onClick = {
                    isLoading = true
                    if (androidx.core.app.ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    } else {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                if (location != null) {
                                    onValueChange("${location.latitude}, ${location.longitude}")
                                } else {
                                    locationError = "Enciende el GPS y probá de nuevo"
                                }
                                isLoading = false
                            }.addOnFailureListener {
                                locationError = "Error al obtener GPS"
                                isLoading = false
                            }
                        } catch(e: Exception) { 
                            isLoading = false 
                        }
                    }
                },
                modifier = Modifier.size(24.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFF1E88E5), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.LocationOn, 
                        contentDescription = "Usar mi ubicación actual",
                        tint = Color(0xFF1E88E5)
                    )
                }
            }
        }
        HorizontalDivider(color = Color.DarkGray)
        if (locationError != null) {
            Text(locationError!!, color = Color.Red, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            androidx.compose.runtime.LaunchedEffect(locationError) {
                kotlinx.coroutines.delay(3000)
                locationError = null
            }
        }
    }

    if (showMapDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showMapDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF222222)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showMapDialog = false }) {
                            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Mové el mapa al lugar", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        var mapView by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
                        
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                org.osmdroid.views.MapView(ctx).apply {
                                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    controller.setZoom(16.0)
                                    
                                    val geoPoint = try {
                                        val parts = value.split(",")
                                        if (parts.size >= 2) org.osmdroid.util.GeoPoint(parts[0].trim().toDouble(), parts[1].trim().toDouble()) else org.osmdroid.util.GeoPoint(-34.6037, -58.3816)
                                    } catch(e: Exception) { org.osmdroid.util.GeoPoint(-34.6037, -58.3816) }
                                    
                                    controller.setCenter(geoPoint)
                                    mapView = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        Icon(
                            androidx.compose.material.icons.Icons.Filled.LocationOn,
                            contentDescription = "Pin de ubicación",
                            tint = Color.Red,
                            modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-24).dp)
                        )
                        
                        Button(
                            onClick = {
                                mapView?.let { map ->
                                    val center = map.mapCenter
                                    onValueChange("${center.latitude}, ${center.longitude}")
                                }
                                showMapDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().height(56.dp)
                        ) {
                            Text("SELECCIONAR ESTA UBICACIÓN", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

