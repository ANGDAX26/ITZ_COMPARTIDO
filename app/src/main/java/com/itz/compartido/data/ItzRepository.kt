package com.itz.compartido.data

import com.itz.compartido.model.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Repositorio en memoria que simula el backend del MVP (servidor + base de datos).
 * Para producción se reemplaza por una API REST / Firebase manteniendo esta misma interfaz.
 */
object ItzRepository {

    /** RF-01: dominio de correo institucional. Ajustar si el ITZ usa otro. */
    const val DOMINIO_ITZ = "@zacatepec.tecnm.mx"

    val ITZ = GeoPunto(18.6544, -99.1853, "ITZ (Zacatepec)")

    /** Zonas de origen/destino (coordenadas aproximadas, solo para la demo). */
    val ZONAS: Map<String, GeoPunto> = linkedMapOf(
        "Zacatepec" to GeoPunto(18.6537, -99.1840, "Zacatepec centro"),
        "Jojutla" to GeoPunto(18.6150, -99.1830, "Jojutla centro"),
        "Tlaquiltenango" to GeoPunto(18.6330, -99.1670, "Tlaquiltenango"),
        "Cuautla" to GeoPunto(18.8120, -98.9540, "Cuautla centro"),
        "Jiutepec" to GeoPunto(18.8820, -99.1740, "Jiutepec"),
        "Cuernavaca" to GeoPunto(18.9186, -99.2342, "Cuernavaca centro")
    )

    private val idUsuario = AtomicInteger(100)
    private val idViaje = AtomicInteger(0)
    private val idSolicitud = AtomicInteger(0)
    private val idAviso = AtomicInteger(0)
    private val idSos = AtomicInteger(0)

    private fun nuevoViaje(
        conductorId: Int, zona: String, origen: GeoPunto, destino: GeoPunto,
        hora: LocalTime, asientos: Int, costo: Double, tipo: TipoPago
    ) = Viaje(
        idViaje = idViaje.incrementAndGet(), conductorId = conductorId,
        origen = origen, destino = destino, zona = zona, horaSalida = hora,
        asientosDisponibles = asientos, asientosTotales = asientos,
        costoEstimado = costo, tipoPago = tipo, estado = EstadoViaje.PROGRAMADO,
        ruta = RutaSegura.calcular(origen, destino, hora)
    )

    // ---------------- Estado ----------------
    private val _usuarios = MutableStateFlow(
        listOf(
            Usuario(1, "Administración ITZ", "10000001", "admin$DOMINIO_ITZ", "7341000000", "7341000001",
                "admin123", Rol.ADMIN, documentosVerificados = true),
            Usuario(2, "Carlos Ramírez", "21090101", "carlos$DOMINIO_ITZ", "7341112233", "7341112200",
                "123456", Rol.CONDUCTOR, Vehiculo("XYZ-123-A", "Nissan Versa", "Gris", 4), "MOR123456",
                true, false, 4.7, 12),
            Usuario(3, "Luis Herrera", "21090102", "luis$DOMINIO_ITZ", "7341223344", "7341223300",
                "123456", Rol.CONDUCTOR, Vehiculo("ABC-456-B", "Chevrolet Aveo", "Rojo", 3), "MOR654321",
                true, false, 4.3, 8),
            Usuario(4, "Sofía Vargas", "21090145", "sofia$DOMINIO_ITZ", "7341334455", "7341334400",
                "123456", Rol.PASAJERO, calificacionProm = 4.9, numCalificaciones = 5),
            Usuario(5, "Mariana Torres", "21090160", "mariana$DOMINIO_ITZ", "7341445566", "7341445500",
                "123456", Rol.CONDUCTOR, Vehiculo("DEF-789-C", "Kia Rio", "Blanco", 4), "MOR987654",
                false, false)
        )
    )
    private val _viajes = MutableStateFlow(
        listOf(
            nuevoViaje(2, "Jojutla", ZONAS.getValue("Jojutla"), ITZ, LocalTime.of(7, 0), 3, 45.0, TipoPago.GASOLINA),
            nuevoViaje(2, "Jojutla", ITZ, ZONAS.getValue("Jojutla"), LocalTime.of(15, 30), 3, 45.0, TipoPago.GASOLINA),
            nuevoViaje(3, "Cuautla", ZONAS.getValue("Cuautla"), ITZ, LocalTime.of(6, 30), 2, 130.0, TipoPago.MIXTO),
            nuevoViaje(3, "Cuernavaca", ZONAS.getValue("Cuernavaca"), ITZ, LocalTime.of(6, 0), 2, 220.0, TipoPago.MIXTO)
        )
    )
    private val _solicitudes = MutableStateFlow<List<Solicitud>>(emptyList())
    private val _calificaciones = MutableStateFlow<List<Calificacion>>(emptyList())
    private val _pagos = MutableStateFlow<Map<Int, Pago>>(emptyMap())
    private val _avisos = MutableStateFlow<List<Notificacion>>(emptyList())
    private val _alertas = MutableStateFlow<List<AlertaSOS>>(emptyList())
    private val _sesionId = MutableStateFlow<Int?>(null)

    val usuarios: StateFlow<List<Usuario>> = _usuarios
    val viajes: StateFlow<List<Viaje>> = _viajes
    val solicitudes: StateFlow<List<Solicitud>> = _solicitudes
    val calificaciones: StateFlow<List<Calificacion>> = _calificaciones
    val pagos: StateFlow<Map<Int, Pago>> = _pagos
    val avisos: StateFlow<List<Notificacion>> = _avisos
    val alertas: StateFlow<List<AlertaSOS>> = _alertas
    val sesionId: StateFlow<Int?> = _sesionId

    /** Eventos para disparar notificaciones push locales (RF-12). */
    val eventos: SharedFlow<Notificacion> = MutableSharedFlow<Notificacion>(extraBufferCapacity = 32)

    // ---------------- Utilidades ----------------
    private fun <T> fallo(msg: String): Result<T> = Result.failure(IllegalArgumentException(msg))

    private fun usuario(id: Int) = _usuarios.value.firstOrNull { it.idUsuario == id }
    private fun viaje(id: Int) = _viajes.value.firstOrNull { it.idViaje == id }

    private fun actualizarUsuario(id: Int, f: (Usuario) -> Usuario) =
        _usuarios.update { l -> l.map { if (it.idUsuario == id) f(it) else it } }

    private fun actualizarViaje(id: Int, f: (Viaje) -> Viaje) =
        _viajes.update { l -> l.map { if (it.idViaje == id) f(it) else it } }

    private fun actualizarSolicitud(id: Int, f: (Solicitud) -> Solicitud) =
        _solicitudes.update { l -> l.map { if (it.idSolicitud == id) f(it) else it } }

    private fun notificar(usuarioId: Int, titulo: String, mensaje: String) {
        val n = Notificacion(idAviso.incrementAndGet(), usuarioId, titulo, mensaje, LocalDateTime.now())
        _avisos.update { listOf(n) + it }
        (eventos as MutableSharedFlow<Notificacion>).tryEmit(n)
    }

    private fun notificarAdmins(titulo: String, mensaje: String) =
        _usuarios.value.filter { it.rol == Rol.ADMIN }.forEach { notificar(it.idUsuario, titulo, mensaje) }

    fun nombreDe(id: Int) = usuario(id)?.nombre ?: "Usuario"

    // ---------------- RF-01: registro y sesión ----------------
    fun registrar(
        nombre: String, matricula: String, correo: String,
        telefono: String, contacto: String, password: String
    ): Result<Usuario> {
        val c = correo.trim().lowercase()
        val m = matricula.trim()
        val msg = when {
            nombre.trim().length < 5 -> "Escribe tu nombre completo"
            !m.matches(Regex("[0-9]{6,10}")) -> "La matrícula o número de empleado debe tener de 6 a 10 dígitos"
            !c.endsWith(DOMINIO_ITZ) || c.length <= DOMINIO_ITZ.length ->
                "Usa tu correo institucional ($DOMINIO_ITZ)"
            _usuarios.value.any { it.correoInstitucional == c } -> "Ese correo ya está registrado"
            _usuarios.value.any { it.matricula == m } -> "Esa matrícula o número de empleado ya está registrado"
            !telefono.trim().matches(Regex("[0-9]{10}")) -> "El teléfono debe tener 10 dígitos"
            !contacto.trim().matches(Regex("[0-9]{10}")) -> "El contacto de confianza debe tener 10 dígitos"
            password.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
            else -> null
        }
        if (msg != null) return fallo(msg)
        val u = Usuario(
            idUsuario.incrementAndGet(), nombre.trim(), m, c,
            telefono.trim(), contacto.trim(), password
        )
        _usuarios.update { it + u }
        _sesionId.value = u.idUsuario
        return Result.success(u)
    }

    fun login(correo: String, password: String): Result<Usuario> {
        val u = _usuarios.value.firstOrNull {
            it.correoInstitucional == correo.trim().lowercase() && it.password == password
        } ?: return fallo("Correo o contraseña incorrectos")
        if (u.suspendido) return fallo("Tu cuenta está suspendida. Contacta a la administración del ITZ")
        _sesionId.value = u.idUsuario
        return Result.success(u)
    }

    fun cerrarSesion() { _sesionId.value = null }

    fun actualizarPerfil(id: Int, telefono: String, contacto: String): Result<Unit> {
        if (!telefono.trim().matches(Regex("[0-9]{10}"))) return fallo("El teléfono debe tener 10 dígitos")
        if (!contacto.trim().matches(Regex("[0-9]{10}"))) return fallo("El contacto de confianza debe tener 10 dígitos")
        actualizarUsuario(id) { it.copy(telefono = telefono.trim(), contactoConfianza = contacto.trim()) }
        return Result.success(Unit)
    }

    // ---------------- RF-02: registro como conductor ----------------
    fun registrarConductor(
        id: Int, placas: String, modelo: String, color: String, capacidad: Int?, licencia: String
    ): Result<Unit> {
        val msg = when {
            placas.trim().length < 5 -> "Escribe las placas del vehículo"
            modelo.isBlank() -> "Escribe el modelo del vehículo"
            color.isBlank() -> "Escribe el color del vehículo"
            capacidad == null || capacidad !in 1..6 -> "Los asientos para pasajeros deben ser de 1 a 6"
            licencia.trim().length < 5 -> "Escribe el número de licencia"
            else -> null
        }
        if (msg != null) return fallo(msg)
        actualizarUsuario(id) {
            it.copy(
                rol = Rol.CONDUCTOR,
                vehiculo = Vehiculo(placas.trim().uppercase(), modelo.trim(), color.trim(), capacidad!!),
                licencia = licencia.trim().uppercase(),
                documentosVerificados = false
            )
        }
        notificarAdmins("Conductor por validar", "${nombreDe(id)} solicitó ser conductor. Revisa sus documentos.")
        return Result.success(Unit)
    }

    // ---------------- RF-03: publicar viaje ----------------
    fun publicarViaje(
        conductorId: Int, zona: String, haciaItz: Boolean, punto: String, horaTxt: String,
        asientos: Int?, costo: Double?, tipo: TipoPago
    ): Result<Viaje> {
        val u = usuario(conductorId) ?: return fallo("Usuario no encontrado")
        if (u.rol != Rol.CONDUCTOR || u.vehiculo == null) return fallo("Primero regístrate como conductor")
        if (!u.documentosVerificados) return fallo("Tus documentos aún no han sido validados")
        val m = Regex("([01]?[0-9]|2[0-3]):([0-5][0-9])").matchEntire(horaTxt.trim())
            ?: return fallo("Escribe la hora con formato HH:mm (ejemplo 07:30)")
        val hora = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        if (asientos == null || asientos !in 1..u.vehiculo.capacidadAsientos)
            return fallo("Los asientos deben ser de 1 a ${u.vehiculo.capacidadAsientos}")
        if (costo == null || costo <= 0.0) return fallo("Escribe un costo estimado mayor a cero")
        val base = ZONAS[zona] ?: return fallo("Selecciona una zona")
        val lugar = base.copy(nombre = punto.trim().ifBlank { base.nombre })
        val v = nuevoViaje(
            conductorId, zona,
            if (haciaItz) lugar else ITZ,
            if (haciaItz) ITZ else lugar,
            hora, asientos, costo, tipo
        )
        _viajes.update { it + v }
        return Result.success(v)
    }

    // ---------------- RF-04: búsqueda ----------------
    // (el filtrado por zona, horario y calificación se hace en la UI con estos flujos)

    // ---------------- RF-05: solicitudes ----------------
    fun solicitarLugar(viajeId: Int, pasajeroId: Int): Result<Unit> {
        val v = viaje(viajeId) ?: return fallo("Viaje no encontrado")
        if (v.estado != EstadoViaje.PROGRAMADO) return fallo("Este viaje ya no admite solicitudes")
        if (v.conductorId == pasajeroId) return fallo("No puedes solicitar lugar en tu propio viaje")
        if (v.asientosDisponibles <= 0) return fallo("No hay asientos disponibles")
        val ya = _solicitudes.value.any {
            it.viajeId == viajeId && it.pasajeroId == pasajeroId &&
                (it.estado == EstadoSolicitud.PENDIENTE || it.estado == EstadoSolicitud.ACEPTADA)
        }
        if (ya) return fallo("Ya tienes una solicitud para este viaje")
        val s = Solicitud(idSolicitud.incrementAndGet(), viajeId, pasajeroId, LocalDateTime.now(), EstadoSolicitud.PENDIENTE)
        _solicitudes.update { it + s }
        notificar(v.conductorId, "Nueva solicitud", "${nombreDe(pasajeroId)} solicitó un lugar en tu viaje de las ${v.horaSalida}.")
        return Result.success(Unit)
    }

    fun responderSolicitud(idSolicitud: Int, aceptar: Boolean): Result<Unit> {
        val s = _solicitudes.value.firstOrNull { it.idSolicitud == idSolicitud } ?: return fallo("Solicitud no encontrada")
        val v = viaje(s.viajeId) ?: return fallo("Viaje no encontrado")
        if (s.estado != EstadoSolicitud.PENDIENTE) return fallo("La solicitud ya fue atendida")
        if (aceptar) {
            if (v.asientosDisponibles <= 0) return fallo("Ya no quedan asientos disponibles")
            actualizarViaje(v.idViaje) { it.copy(asientosDisponibles = it.asientosDisponibles - 1) }
            actualizarSolicitud(idSolicitud) { it.copy(estado = EstadoSolicitud.ACEPTADA) }
            notificar(s.pasajeroId, "Solicitud aceptada", "${nombreDe(v.conductorId)} aceptó tu solicitud. Salida: ${v.horaSalida}.")
        } else {
            actualizarSolicitud(idSolicitud) { it.copy(estado = EstadoSolicitud.RECHAZADA) }
            notificar(s.pasajeroId, "Solicitud rechazada", "${nombreDe(v.conductorId)} no pudo aceptar tu solicitud.")
        }
        return Result.success(Unit)
    }

    fun cancelarSolicitud(idSolicitud: Int): Result<Unit> {
        val s = _solicitudes.value.firstOrNull { it.idSolicitud == idSolicitud } ?: return fallo("Solicitud no encontrada")
        val v = viaje(s.viajeId) ?: return fallo("Viaje no encontrado")
        if (v.estado != EstadoViaje.PROGRAMADO) return fallo("El viaje ya inició o terminó")
        if (s.estado == EstadoSolicitud.ACEPTADA)
            actualizarViaje(v.idViaje) { it.copy(asientosDisponibles = it.asientosDisponibles + 1) }
        actualizarSolicitud(idSolicitud) { it.copy(estado = EstadoSolicitud.CANCELADA) }
        notificar(v.conductorId, "Solicitud cancelada", "${nombreDe(s.pasajeroId)} canceló su lugar en tu viaje de las ${v.horaSalida}.")
        return Result.success(Unit)
    }

    // ---------------- Ciclo de vida del viaje ----------------
    fun iniciarViaje(idViaje: Int): Result<Unit> {
        val v = viaje(idViaje) ?: return fallo("Viaje no encontrado")
        if (v.estado != EstadoViaje.PROGRAMADO) return fallo("El viaje no está programado")
        val aceptadas = _solicitudes.value.filter { it.viajeId == idViaje && it.estado == EstadoSolicitud.ACEPTADA }
        if (aceptadas.isEmpty()) return fallo("Aún no tienes pasajeros aceptados")
        actualizarViaje(idViaje) { it.copy(estado = EstadoViaje.EN_CURSO) }
        aceptadas.forEach { notificar(it.pasajeroId, "Tu viaje inició", "Puedes seguir la ruta en tiempo real desde Mis viajes.") }
        return Result.success(Unit)
    }

    /** RF-08: el costo se divide entre el conductor y los pasajeros del viaje. */
    fun finalizarViaje(idViaje: Int): Result<Pago> {
        val v = viaje(idViaje) ?: return fallo("Viaje no encontrado")
        if (v.estado != EstadoViaje.EN_CURSO) return fallo("El viaje no está en curso")
        val aceptadas = _solicitudes.value.filter { it.viajeId == idViaje && it.estado == EstadoSolicitud.ACEPTADA }
        val personas = aceptadas.size + 1
        val pago = Pago(idViaje, v.costoEstimado, v.tipoPago, personas, v.costoEstimado / personas, LocalDateTime.now())
        _pagos.update { it + (idViaje to pago) }
        actualizarViaje(idViaje) { it.copy(estado = EstadoViaje.FINALIZADO) }
        aceptadas.forEach {
            notificar(it.pasajeroId, "Viaje finalizado", "Tu aportación es de ${"%.2f".format(pago.montoPorPersona)} MXN. Califica tu experiencia.")
        }
        return Result.success(pago)
    }

    fun cancelarViaje(idViaje: Int): Result<Unit> {
        val v = viaje(idViaje) ?: return fallo("Viaje no encontrado")
        if (v.estado != EstadoViaje.PROGRAMADO) return fallo("Solo se pueden cancelar viajes programados")
        val afectadas = _solicitudes.value.filter {
            it.viajeId == idViaje && (it.estado == EstadoSolicitud.PENDIENTE || it.estado == EstadoSolicitud.ACEPTADA)
        }
        _solicitudes.update { l -> l.map { if (it in afectadas) it.copy(estado = EstadoSolicitud.CANCELADA) else it } }
        actualizarViaje(idViaje) { it.copy(estado = EstadoViaje.CANCELADO) }
        afectadas.forEach {
            notificar(it.pasajeroId, "Viaje cancelado", "${nombreDe(v.conductorId)} canceló el viaje de las ${v.horaSalida}.")
        }
        return Result.success(Unit)
    }

    // ---------------- RF-09: calificaciones ----------------
    fun yaCalifico(viajeId: Int, de: Int, a: Int) =
        _calificaciones.value.any { it.viajeId == viajeId && it.deUsuarioId == de && it.aUsuarioId == a }

    fun calificar(viajeId: Int, de: Int, a: Int, puntos: Int, comentario: String): Result<Unit> {
        if (puntos !in 1..5) return fallo("Elige de 1 a 5 estrellas")
        if (yaCalifico(viajeId, de, a)) return fallo("Ya calificaste a este usuario en este viaje")
        _calificaciones.update { it + Calificacion(viajeId, de, a, puntos, comentario.trim()) }
        actualizarUsuario(a) {
            val total = it.calificacionProm * it.numCalificaciones + puntos
            val n = it.numCalificaciones + 1
            it.copy(calificacionProm = total / n, numCalificaciones = n)
        }
        return Result.success(Unit)
    }

    // ---------------- RF-10: SOS ----------------
    fun emitirSOS(usuarioId: Int, viajeId: Int, lat: Double, lon: Double) {
        _alertas.update { it + AlertaSOS(idSos.incrementAndGet(), usuarioId, viajeId, lat, lon, LocalDateTime.now()) }
        notificarAdmins("ALERTA SOS", "${nombreDe(usuarioId)} activó el botón de emergencia en el viaje #$viajeId.")
    }

    // ---------------- RF-11: administración ----------------
    fun validarDocumentos(id: Int) {
        actualizarUsuario(id) { it.copy(documentosVerificados = true) }
        notificar(id, "Documentos validados", "Ya puedes publicar viajes como conductor.")
    }

    fun suspender(id: Int, suspender: Boolean) = actualizarUsuario(id) { it.copy(suspendido = suspender) }

    fun reporte(): ReporteUso {
        val califs = _calificaciones.value
        return ReporteUso(
            usuarios = _usuarios.value.count { it.rol != Rol.ADMIN },
            conductores = _usuarios.value.count { it.rol == Rol.CONDUCTOR },
            viajesPublicados = _viajes.value.size,
            viajesFinalizados = _viajes.value.count { it.estado == EstadoViaje.FINALIZADO },
            solicitudes = _solicitudes.value.size,
            calificacionGlobal = if (califs.isEmpty()) 0.0 else califs.map { it.puntuacion }.average(),
            alertasSOS = _alertas.value.size
        )
    }
}
