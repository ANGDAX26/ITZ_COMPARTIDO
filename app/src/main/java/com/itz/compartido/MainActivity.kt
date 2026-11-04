package com.itz.compartido

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    val listo by ItzRepository.listo.collectAsState()
    var errorInicio by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableIntStateOf(0) }
    LaunchedEffect(intento) {
        errorInicio = null
        try {
            ItzRepository.inicializar(ctx)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            errorInicio = "No se pudo abrir la base de datos. Tus datos no se han borrado."
        }
    }
    if (!listo) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                if (errorInicio == null) CircularProgressIndicator()
                else {
                    Text(errorInicio!!)
                    Button(onClick = { intento++ }) { Text("Reintentar") }
                }
            }
        }
        return
    }
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
