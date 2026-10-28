package com.itz.compartido.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.itz.compartido.data.ItzRepository
import com.itz.compartido.model.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onAbrirViaje: (Int) -> Unit) {
    val yo = usuarioActual() ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mostrar: (String) -> Unit = { m -> scope.launch { snack.showSnackbar(m) } }
    val items = listOf(
        "Buscar" to Icons.Default.Search,
        "Conducir" to Icons.Default.Add,
        "Mis viajes" to Icons.Default.List,
        "Avisos" to Icons.Default.Notifications,
        "Perfil" to Icons.Default.Person
    )
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> BuscarTab(yo, mostrar)
                1 -> ConducirTab(yo, mostrar)
                2 -> MisViajesTab(yo, mostrar, onAbrirViaje)
                3 -> AvisosTab(yo)
                else -> PerfilTab(yo, mostrar)
            }
        }
    }
}

// ============ RF-04 / RF-05: buscar y solicitar ============
@Composable
private fun BuscarTab(yo: Usuario, mostrar: (String) -> Unit) {
    val viajes by ItzRepository.viajes.collectAsState()
    val solicitudes by ItzRepository.solicitudes.collectAsState()
    val usuarios by ItzRepository.usuarios.collectAsState()
    var zona by rememberSaveable { mutableStateOf("Todas") }
    var franja by rememberSaveable { mutableStateOf(Franja.TODAS) }
    var calMin by rememberSaveable { mutableStateOf(0.0) }

    val lista = viajes.filter { v ->
        val c = usuarios.firstOrNull { it.idUsuario == v.conductorId }
        v.estado == EstadoViaje.PROGRAMADO && v.asientosDisponibles > 0 && v.conductorId != yo.idUsuario &&
            (zona == "Todas" || v.zona == zona) && franja.contiene(v.horaSalida.hour) &&
            c != null && !c.suspendido && c.calificacionProm >= calMin
    }.sortedBy { it.horaSalida }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Viajes disponibles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Zona", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("Todas") + ItzRepository.ZONAS.keys).forEach {
                    FilterChip(zona == it, { zona = it }, label = { Text(it) })
                }
            }
            Text("Horario", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Franja.values().forEach {
                    FilterChip(franja == it, { franja = it }, label = { Text(it.etiqueta) })
                }
            }
            Text("Calificación del conductor", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.0 to "Todas", 4.0 to "4.0 o más", 4.5 to "4.5 o más").forEach { (v, t) ->
                    FilterChip(calMin == v, { calMin = v }, label = { Text(t) })
                }
            }
        }
        if (lista.isEmpty()) item { Vacio("No hay viajes con esos filtros") }
        items(lista, key = { it.idViaje }) { v ->
            val c = usuarios.first { it.idUsuario == v.conductorId }
            val activa = solicitudes.firstOrNull {
                it.viajeId == v.idViaje && it.pasajeroId == yo.idUsuario &&
                    (it.estado == EstadoSolicitud.PENDIENTE || it.estado == EstadoSolicitud.ACEPTADA)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${v.origen.nombre} → ${v.destino.nombre}", fontWeight = FontWeight.Bold)
                    Text("Salida ${v.horaSalida.hhmm()} · ${v.asientosDisponibles} de ${v.asientosTotales} asientos libres")
                    Text("Conductor: ${c.nombre} · ${c.estrellas()}")
                    c.vehiculo?.let { Text("${it.modelo} ${it.color}", style = MaterialTheme.typography.bodySmall) }
                    Text("Costo del trayecto ${v.costoEstimado.mxn()} (${v.tipoPago.etiqueta}); se divide entre quienes viajen",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Etiqueta("Riesgo ${v.ruta.nivelRiesgo.name.lowercase()}")
                    }
                    Button(
                        enabled = activa == null,
                        onClick = {
                            ItzRepository.solicitarLugar(v.idViaje, yo.idUsuario)
                                .onSuccess { mostrar("Solicitud enviada al conductor") }
                                .onFailure { mostrar(it.message ?: "Error") }
                        }
                    ) { Text(if (activa == null) "Solicitar lugar" else if (activa.estado == EstadoSolicitud.PENDIENTE) "Solicitud enviada" else "Lugar aceptado") }
                }
            }
        }
    }
}

// ============ RF-02 / RF-03: conducir ============
@Composable
private fun ConducirTab(yo: Usuario, mostrar: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            yo.rol == Rol.PASAJERO -> RegistroConductor(yo, mostrar)
            !yo.documentosVerificados -> {
                Text("Conducir", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Documentos en revisión", fontWeight = FontWeight.Bold)
                        Text("La administración del ITZ debe validar tu licencia y vehículo antes de que puedas publicar viajes. Te avisaremos cuando estén validados.")
                    }
                }
            }
            else -> PublicarViaje(yo, mostrar)
        }
    }
}

@Composable
private fun RegistroConductor(yo: Usuario, mostrar: (String) -> Unit) {
    var placas by rememberSaveable { mutableStateOf("") }
    var modelo by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }
    var asientos by rememberSaveable { mutableStateOf("") }
    var licencia by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Text("Registrarme como conductor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Captura los datos de tu vehículo y licencia. Un administrador validará tus documentos.")
    Campo(placas, { placas = it }, "Placas")
    Campo(modelo, { modelo = it }, "Modelo del vehículo")
    Campo(color, { color = it }, "Color")
    Campo(asientos, { asientos = it }, "Asientos para pasajeros (1 a 6)", KeyboardType.Number)
    Campo(licencia, { licencia = it }, "Número de licencia")
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        ItzRepository.registrarConductor(yo.idUsuario, placas, modelo, color, asientos.toIntOrNull(), licencia)
            .onSuccess { mostrar("Solicitud enviada. Documentos en revisión") }
            .onFailure { error = it.message }
    }, modifier = Modifier.fillMaxWidth()) { Text("Enviar para validación") }
}

@Composable
private fun PublicarViaje(yo: Usuario, mostrar: (String) -> Unit) {
    var haciaItz by rememberSaveable { mutableStateOf(true) }
    var zona by rememberSaveable { mutableStateOf(ItzRepository.ZONAS.keys.first()) }
    var punto by rememberSaveable { mutableStateOf("") }
    var hora by rememberSaveable { mutableStateOf("07:00") }
    var asientos by rememberSaveable { mutableStateOf("") }
    var costo by rememberSaveable { mutableStateOf("") }
    var tipo by rememberSaveable { mutableStateOf(TipoPago.GASOLINA) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("Publicar viaje", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    yo.vehiculo?.let { Text("${it.modelo} ${it.color} · ${it.placas} · hasta ${it.capacidadAsientos} pasajeros", style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(haciaItz, { haciaItz = true }, label = { Text("Hacia el ITZ") })
        FilterChip(!haciaItz, { haciaItz = false }, label = { Text("Desde el ITZ") })
    }
    Text(if (haciaItz) "Zona de origen" else "Zona de destino", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ItzRepository.ZONAS.keys.forEach { FilterChip(zona == it, { zona = it }, label = { Text(it) }) }
    }
    Campo(punto, { punto = it }, if (haciaItz) "Punto de salida (opcional)" else "Punto de llegada (opcional)")
    Campo(hora, { hora = it }, "Hora de salida (HH:mm)", KeyboardType.Number)
    Campo(asientos, { asientos = it }, "Asientos disponibles", KeyboardType.Number)
    Campo(costo, { costo = it }, "Costo estimado del trayecto (MXN)", KeyboardType.Decimal)
    Text("El costo es un apoyo para gastos compartidos, no una tarifa comercial.", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TipoPago.values().forEach { FilterChip(tipo == it, { tipo = it }, label = { Text(it.etiqueta) }) }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        ItzRepository.publicarViaje(yo.idUsuario, zona, haciaItz, punto, hora, asientos.toIntOrNull(), costo.toDoubleOrNull(), tipo)
            .onSuccess { error = null; mostrar("Viaje publicado. Búscalo en Mis viajes"); punto = ""; asientos = ""; costo = "" }
            .onFailure { error = it.message }
    }, modifier = Modifier.fillMaxWidth()) { Text("Publicar viaje") }
}

// ============ Mis viajes ============
@Composable
private fun MisViajesTab(yo: Usuario, mostrar: (String) -> Unit, abrir: (Int) -> Unit) {
    val viajes by ItzRepository.viajes.collectAsState()
    val solicitudes by ItzRepository.solicitudes.collectAsState()
    val usuarios by ItzRepository.usuarios.collectAsState()

    val misSolicitudes = solicitudes.filter { it.pasajeroId == yo.idUsuario }.reversed()
    val misViajes = viajes.filter { it.conductorId == yo.idUsuario }.reversed()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Como pasajero", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (misSolicitudes.isEmpty()) item { Vacio("Aún no has solicitado viajes") }
        items(misSolicitudes, key = { "s${it.idSolicitud}" }) { s ->
            val v = viajes.first { it.idViaje == s.viajeId }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${v.origen.nombre} → ${v.destino.nombre}", fontWeight = FontWeight.Bold)
                    Text("Salida ${v.horaSalida.hhmm()} · Conductor: ${usuarios.firstOrNull { it.idUsuario == v.conductorId }?.nombre}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Etiqueta("Solicitud: ${s.estado.name.lowercase()}")
                        Etiqueta("Viaje: ${v.estado.name.lowercase().replace('_', ' ')}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.estado == EstadoSolicitud.ACEPTADA && v.estado != EstadoViaje.PROGRAMADO && v.estado != EstadoViaje.CANCELADO)
                            Button(onClick = { abrir(v.idViaje) }) { Text(if (v.estado == EstadoViaje.EN_CURSO) "Ver ruta en vivo" else "Resumen y calificar") }
                        if ((s.estado == EstadoSolicitud.PENDIENTE || s.estado == EstadoSolicitud.ACEPTADA) && v.estado == EstadoViaje.PROGRAMADO)
                            OutlinedButton(onClick = {
                                ItzRepository.cancelarSolicitud(s.idSolicitud).onFailure { mostrar(it.message ?: "Error") }
                            }) { Text("Cancelar") }
                    }
                }
            }
        }

        if (yo.rol == Rol.CONDUCTOR) {
            item { Text("Como conductor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            if (misViajes.isEmpty()) item { Vacio("Aún no has publicado viajes") }
            items(misViajes, key = { "v${it.idViaje}" }) { v ->
                val pendientes = solicitudes.filter { it.viajeId == v.idViaje && it.estado == EstadoSolicitud.PENDIENTE }
                val aceptadas = solicitudes.filter { it.viajeId == v.idViaje && it.estado == EstadoSolicitud.ACEPTADA }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${v.origen.nombre} → ${v.destino.nombre}", fontWeight = FontWeight.Bold)
                        Text("Salida ${v.horaSalida.hhmm()} · ${v.asientosDisponibles} de ${v.asientosTotales} asientos libres · ${v.costoEstimado.mxn()}")
                        Etiqueta("Viaje: ${v.estado.name.lowercase().replace('_', ' ')}")
                        if (aceptadas.isNotEmpty())
                            Text("Pasajeros: " + aceptadas.joinToString { ItzRepository.nombreDe(it.pasajeroId) }, style = MaterialTheme.typography.bodySmall)
                        pendientes.forEach { s ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(ItzRepository.nombreDe(s.pasajeroId) + " solicita lugar", Modifier.weight(1f))
                                IconButton(onClick = { ItzRepository.responderSolicitud(s.idSolicitud, true).onFailure { mostrar(it.message ?: "Error") } }) {
                                    Icon(Icons.Default.Check, contentDescription = "Aceptar", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { ItzRepository.responderSolicitud(s.idSolicitud, false) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Rechazar", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (v.estado) {
                                EstadoViaje.PROGRAMADO -> {
                                    Button(onClick = {
                                        ItzRepository.iniciarViaje(v.idViaje)
                                            .onSuccess { abrir(v.idViaje) }
                                            .onFailure { mostrar(it.message ?: "Error") }
                                    }) { Text("Iniciar viaje") }
                                    OutlinedButton(onClick = { ItzRepository.cancelarViaje(v.idViaje).onFailure { mostrar(it.message ?: "Error") } }) { Text("Cancelar viaje") }
                                }
                                EstadoViaje.EN_CURSO -> Button(onClick = { abrir(v.idViaje) }) { Text("Ver viaje en curso") }
                                EstadoViaje.FINALIZADO -> Button(onClick = { abrir(v.idViaje) }) { Text("Resumen y calificar") }
                                EstadoViaje.CANCELADO -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============ RF-12: avisos ============
@Composable
private fun AvisosTab(yo: Usuario) {
    val avisos by ItzRepository.avisos.collectAsState()
    val mios = avisos.filter { it.usuarioId == yo.idUsuario }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Avisos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (mios.isEmpty()) item { Vacio("Sin avisos por ahora") }
        items(mios, key = { it.id }) { n ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(n.titulo, fontWeight = FontWeight.Bold)
                    Text(n.mensaje)
                    Text(n.hora.fechaHora(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ============ Perfil ============
@Composable
private fun PerfilTab(yo: Usuario, mostrar: (String) -> Unit) {
    var tel by remember(yo.idUsuario) { mutableStateOf(yo.telefono) }
    var contacto by remember(yo.idUsuario) { mutableStateOf(yo.contactoConfianza) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(yo.nombre, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(yo.correoInstitucional)
        Text("Matrícula / No. de empleado: ${yo.matricula}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiqueta(if (yo.rol == Rol.CONDUCTOR) "Conductor" else "Pasajero")
            Etiqueta(yo.estrellas())
        }
        if (yo.rol == Rol.CONDUCTOR) {
            yo.vehiculo?.let { Text("Vehículo: ${it.modelo} ${it.color} · ${it.placas} · ${it.capacidadAsientos} asientos") }
            Text(if (yo.documentosVerificados) "Documentos validados" else "Documentos pendientes de validación")
        }
        HorizontalDivider()
        Titulo("Datos de contacto")
        Campo(tel, { tel = it }, "Teléfono", KeyboardType.Phone)
        Campo(contacto, { contacto = it }, "Contacto de confianza (SOS)", KeyboardType.Phone)
        Button(onClick = {
            ItzRepository.actualizarPerfil(yo.idUsuario, tel, contacto)
                .onSuccess { mostrar("Perfil actualizado") }
                .onFailure { mostrar(it.message ?: "Error") }
        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar cambios") }
        OutlinedButton(onClick = { ItzRepository.cerrarSesion() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesión")
        }
    }
}
