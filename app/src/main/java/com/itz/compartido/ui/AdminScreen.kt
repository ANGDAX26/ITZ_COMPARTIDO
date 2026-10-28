package com.itz.compartido.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itz.compartido.data.ItzRepository
import com.itz.compartido.model.Rol
import java.util.Locale

/** RF-11: validar documentos, suspender cuentas y generar reportes de uso. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administración ITZ") },
                actions = {
                    IconButton(onClick = { ItzRepository.cerrarSesion() }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Usuarios") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Reportes") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Alertas") })
            }
            when (tab) {
                0 -> UsuariosAdmin()
                1 -> ReportesAdmin()
                else -> AlertasAdmin()
            }
        }
    }
}

@Composable
private fun UsuariosAdmin() {
    val usuarios by ItzRepository.usuarios.collectAsState()
    val lista = usuarios.filter { it.rol != Rol.ADMIN }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(lista, key = { it.idUsuario }) { u ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(u.nombre, fontWeight = FontWeight.Bold)
                    Text("${u.correoInstitucional} · ${u.matricula}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Etiqueta(if (u.rol == Rol.CONDUCTOR) "Conductor" else "Pasajero")
                        Etiqueta(u.estrellas())
                        if (u.suspendido) Etiqueta("Suspendido", MaterialTheme.colorScheme.errorContainer)
                    }
                    if (u.rol == Rol.CONDUCTOR) {
                        Text("Vehículo: ${u.vehiculo?.modelo} ${u.vehiculo?.placas} · Licencia ${u.licencia}", style = MaterialTheme.typography.bodySmall)
                        Text(if (u.documentosVerificados) "Documentos validados" else "Documentos pendientes")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (u.rol == Rol.CONDUCTOR && !u.documentosVerificados)
                            Button(onClick = { ItzRepository.validarDocumentos(u.idUsuario) }) { Text("Validar documentos") }
                        OutlinedButton(onClick = { ItzRepository.suspender(u.idUsuario, !u.suspendido) }) {
                            Text(if (u.suspendido) "Reactivar" else "Suspender")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportesAdmin() {
    // Se observan los flujos para que el reporte se recalcule al cambiar los datos
    val u by ItzRepository.usuarios.collectAsState()
    val v by ItzRepository.viajes.collectAsState()
    val s by ItzRepository.solicitudes.collectAsState()
    val c by ItzRepository.calificaciones.collectAsState()
    val a by ItzRepository.alertas.collectAsState()
    val r = remember(u, v, s, c, a) { ItzRepository.reporte() }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reporte de uso", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Fila("Usuarios registrados", r.usuarios.toString())
        Fila("Conductores", r.conductores.toString())
        Fila("Viajes publicados", r.viajesPublicados.toString())
        Fila("Viajes finalizados", r.viajesFinalizados.toString())
        Fila("Solicitudes de lugar", r.solicitudes.toString())
        Fila("Calificación promedio", if (r.calificacionGlobal == 0.0) "—" else String.format(Locale.US, "%.2f", r.calificacionGlobal))
        Fila("Alertas SOS", r.alertasSOS.toString())
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(etiqueta)
            Text(valor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AlertasAdmin() {
    val alertas by ItzRepository.alertas.collectAsState()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (alertas.isEmpty()) item { Vacio("Sin alertas SOS registradas") }
        items(alertas.reversed(), key = { it.id }) { al ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("SOS de ${ItzRepository.nombreDe(al.usuarioId)} · viaje #${al.viajeId}", fontWeight = FontWeight.Bold)
                    Text("Ubicación: ${String.format(Locale.US, "%.5f, %.5f", al.lat, al.lon)}")
                    Text(al.hora.fechaHora(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
