package com.itz.compartido.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itz.compartido.data.ItzRepository
import com.itz.compartido.model.*
import kotlinx.coroutines.delay
import kotlin.math.floor

/**
 * Mapa simulado (RF-06 / RF-07). Dibuja la ruta segura y la posición del vehículo.
 * Se reemplaza por Google Maps / OpenStreetMap en la fase de integración.
 */
@Composable
fun MapaSimulado(ruta: RutaSegura, progreso: Float, modifier: Modifier = Modifier) {
    val colorRuta = when (ruta.nivelRiesgo) {
        NivelRiesgo.BAJO -> Color(0xFF2E7D32)
        NivelRiesgo.MEDIO -> Color(0xFFF9A825)
        NivelRiesgo.ALTO -> Color(0xFFC62828)
    }
    Canvas(modifier.fillMaxWidth().height(240.dp)) {
        drawRect(Color(0xFFEAF1E4), size = size)
        for (i in 1..7) {
            drawLine(Color(0xFFD5DFCB), Offset(size.width * i / 8f, 0f), Offset(size.width * i / 8f, size.height), 2f)
            drawLine(Color(0xFFD5DFCB), Offset(0f, size.height * i / 8f), Offset(size.width, size.height * i / 8f), 2f)
        }
        val pts = ruta.puntosGPS.map { Offset(it.first * size.width, it.second * size.height) }
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, colorRuta, style = Stroke(width = 10f, cap = StrokeCap.Round))
        drawCircle(Color(0xFF1B396A), 16f, pts.first())
        drawCircle(Color(0xFFC62828), 16f, pts.last())
        val pos = (progreso.coerceIn(0f, 1f) * (pts.size - 1))
        val i = floor(pos).toInt().coerceAtMost(pts.size - 2)
        val f = pos - i
        val a = pts[i]; val b = pts[i + 1]
        val car = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
        drawCircle(Color.White, 22f, car)
        drawCircle(Color(0xFFF2B705), 15f, car)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViajeScreen(idViaje: Int, onBack: () -> Unit) {
    val yo = usuarioActual() ?: return
    val ctx = LocalContext.current
    val viajes by ItzRepository.viajes.collectAsState()
    val solicitudes by ItzRepository.solicitudes.collectAsState()
    val usuarios by ItzRepository.usuarios.collectAsState()
    val pagos by ItzRepository.pagos.collectAsState()
    val viaje = viajes.firstOrNull { it.idViaje == idViaje }

    var progreso by remember(idViaje) { mutableFloatStateOf(0f) }
    var confirmarSOS by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    val estado = viaje?.estado

    // Simulación de GPS: avanza el vehículo cada segundo (RNF-04: latencia < 5 s)
    LaunchedEffect(estado) {
        if (estado == EstadoViaje.EN_CURSO) {
            while (progreso < 1f) {
                delay(1000)
                progreso = minOf(1f, progreso + 0.02f)
            }
        }
        if (estado == EstadoViaje.FINALIZADO) progreso = 1f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viaje #$idViaje") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { pad ->
        if (viaje == null) {
            Box(Modifier.padding(pad)) { Vacio("Viaje no encontrado") }
            return@Scaffold
        }
        val esConductor = viaje.conductorId == yo.idUsuario
        val conductor = usuarios.firstOrNull { it.idUsuario == viaje.conductorId }
        val pasajeros = solicitudes
            .filter { it.viajeId == idViaje && it.estado == EstadoSolicitud.ACEPTADA }
            .mapNotNull { s -> usuarios.firstOrNull { it.idUsuario == s.pasajeroId } }
        val lat = viaje.origen.lat + (viaje.destino.lat - viaje.origen.lat) * progreso
        val lon = viaje.origen.lon + (viaje.destino.lon - viaje.origen.lon) * progreso

        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("${viaje.origen.nombre} → ${viaje.destino.nombre}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiqueta("Salida ${viaje.horaSalida.hhmm()}")
                Etiqueta(viaje.estado.name.lowercase().replace('_', ' '))
                Etiqueta("Riesgo ${viaje.ruta.nivelRiesgo.name.lowercase()}")
            }

            MapaSimulado(viaje.ruta, progreso)
            Text(
                when (viaje.estado) {
                    EstadoViaje.EN_CURSO -> "Ubicación actualizada hace 1 s · ${(progreso * 100).toInt()}% del trayecto (posición simulada)"
                    EstadoViaje.PROGRAMADO -> "La ruta se activará cuando el conductor inicie el viaje"
                    else -> "Trayecto concluido"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Titulo("Ruta segura sugerida")
                    Text("Puntos de apoyo: " + viaje.ruta.zonasSeguras.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    Text("Conductor: ${conductor?.nombre} · ${conductor?.estrellas()}")
                    conductor?.vehiculo?.let { Text("${it.modelo} ${it.color} · placas ${it.placas}") }
                    Text("Pasajeros: " + if (pasajeros.isEmpty()) "ninguno" else pasajeros.joinToString { it.nombre })
                }
            }

            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // Acciones del conductor
            if (esConductor && viaje.estado == EstadoViaje.EN_CURSO) {
                Button(onClick = {
                    ItzRepository.finalizarViaje(idViaje).onFailure { mensaje = it.message }
                }, modifier = Modifier.fillMaxWidth()) { Text("Finalizar viaje") }
            }
            if (esConductor && viaje.estado == EstadoViaje.PROGRAMADO) {
                Button(onClick = {
                    ItzRepository.iniciarViaje(idViaje).onFailure { mensaje = it.message }
                }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar viaje") }
            }

            // RF-10: botón SOS
            if (viaje.estado == EstadoViaje.EN_CURSO) {
                Button(
                    onClick = { confirmarSOS = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("SOS · Emergencia", fontWeight = FontWeight.Bold)
                }
            }

            // RF-08: costo dividido
            if (viaje.estado == EstadoViaje.FINALIZADO) {
                pagos[idViaje]?.let { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Titulo("División del costo")
                            Text("Total (${p.tipo.etiqueta.lowercase()}): ${p.monto.mxn()}")
                            Text("Personas en el viaje: ${p.personas}")
                            Text("Aportación por persona: ${p.montoPorPersona.mxn()}", fontWeight = FontWeight.Bold)
                            Text("Es un apoyo para gastos compartidos; el pago se acuerda entre los usuarios.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                // RF-09: calificar
                val aCalificar = if (esConductor) pasajeros else listOfNotNull(conductor)
                aCalificar.filter { it.idUsuario != yo.idUsuario }.forEach { u ->
                    CalificarCard(idViaje, yo.idUsuario, u)
                }
            }
        }

        if (confirmarSOS) {
            AlertDialog(
                onDismissRequest = { confirmarSOS = false },
                title = { Text("¿Enviar alerta de emergencia?") },
                text = { Text("Se avisará a la administración del ITZ y se abrirá un mensaje para tu contacto de confianza (${yo.contactoConfianza}) con tu ubicación.") },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmarSOS = false
                            ItzRepository.emitirSOS(yo.idUsuario, idViaje, lat, lon)
                            val cuerpo = "ALERTA ITZ-Compartido: ${yo.nombre} necesita ayuda en el viaje " +
                                "${viaje.origen.nombre} a ${viaje.destino.nombre}. " +
                                "Ubicación: https://maps.google.com/?q=$lat,$lon"
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${yo.contactoConfianza}"))
                                .putExtra("sms_body", cuerpo)
                            try { ctx.startActivity(intent) } catch (e: ActivityNotFoundException) { }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Enviar SOS") }
                },
                dismissButton = { TextButton(onClick = { confirmarSOS = false }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable
private fun CalificarCard(viajeId: Int, yoId: Int, a: Usuario) {
    val calificaciones by ItzRepository.calificaciones.collectAsState()
    val ya = calificaciones.any { it.viajeId == viajeId && it.deUsuarioId == yoId && it.aUsuarioId == a.idUsuario }
    var puntos by remember { mutableIntStateOf(0) }
    var comentario by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Titulo("Calificar a ${a.nombre}")
            if (ya) {
                Text("Gracias por tu calificación")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { i ->
                        IconButton(onClick = { puntos = i }) {
                            Icon(
                                Icons.Default.Star, contentDescription = "$i estrellas",
                                tint = if (i <= puntos) Color(0xFFF2B705) else Color(0xFFBDBDBD)
                            )
                        }
                    }
                }
                OutlinedTextField(comentario, { comentario = it }, label = { Text("Comentario (opcional)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    ItzRepository.calificar(viajeId, yoId, a.idUsuario, puntos, comentario).onFailure { error = it.message }
                }) { Text("Enviar calificación") }
            }
        }
    }
}
