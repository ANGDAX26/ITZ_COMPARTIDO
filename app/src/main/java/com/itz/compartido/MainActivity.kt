package com.itz.compartido

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.itz.compartido.data.ItzRepository
import com.itz.compartido.model.Rol
import com.itz.compartido.notify.Notifier
import com.itz.compartido.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.crearCanal(this)
        setContent { ItzTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val sesion by ItzRepository.sesionId.collectAsState()
    val usuarios by ItzRepository.usuarios.collectAsState()
    val esAdmin = usuarios.firstOrNull { it.idUsuario == sesion }?.rol == Rol.ADMIN

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // RF-12: muestra como notificación del sistema los avisos dirigidos al usuario con sesión
    LaunchedEffect(Unit) {
        ItzRepository.eventos.collect { n ->
            if (n.usuarioId == ItzRepository.sesionId.value) Notifier.mostrar(ctx, n)
        }
    }

    if (sesion == null) {
        AuthScreen()
    } else {
        val nav = rememberNavController()
        NavHost(nav, startDestination = if (esAdmin) "admin" else "home") {
            composable("home") { HomeScreen(onAbrirViaje = { nav.navigate("viaje/$it") }) }
            composable("admin") { AdminScreen() }
            composable(
                "viaje/{id}",
                arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { e -> ViajeScreen(e.arguments?.getInt("id") ?: -1, onBack = { nav.popBackStack() }) }
        }
    }
}
