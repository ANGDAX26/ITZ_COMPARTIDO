package com.itz.compartido.data

import android.content.Context
import androidx.room.withTransaction
import com.itz.compartido.data.local.ItzDatabase
import com.itz.compartido.data.local.PasswordHasher
import com.itz.compartido.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.itz.compartido.data.local.EstadoPersistente
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Repositorio local Room. Serializa operaciones y confirma su persistencia antes de devolver éxito.
 * Los StateFlow mantienen la interfaz reactiva de las pantallas del MVP.
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
    private val _usuarios = EstadoPersistente(
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
    private val _viajes = EstadoPersistente(
        listOf(
            nuevoViaje(2, "Jojutla", ZONAS.getValue("Jojutla"), ITZ, LocalTime.of(7, 0), 3, 45.0, TipoPago.GASOLINA),
            nuevoViaje(2, "Jojutla", ITZ, ZONAS.getValue("Jojutla"), LocalTime.of(15, 30), 3, 45.0, TipoPago.GASOLINA),
            nuevoViaje(3, "Cuautla", ZONAS.getValue("Cuautla"), ITZ, LocalTime.of(6, 30), 2, 130.0, TipoPago.MIXTO),
            nuevoViaje(3, "Cuernavaca", ZONAS.getValue("Cuernavaca"), ITZ, LocalTime.of(6, 0), 2, 220.0, TipoPago.MIXTO)
        )
    )
    private val _solicitudes = EstadoPersistente<List<Solicitud>>(emptyList())
    private val _calificaciones = EstadoPersistente<List<Calificacion>>(emptyList())
    private val _pagos = EstadoPersistente<Map<Int, Pago>>(emptyMap())
    private val _avisos = EstadoPersistente<List<Notificacion>>(emptyList())
    private val _alertas = EstadoPersistente<List<AlertaSOS>>(emptyList())
    private val _sesionId = EstadoPersistente<Int?>(null)

    val usuarios: StateFlow<List<Usuario>> = _usuarios.flow
    val viajes: StateFlow<List<Viaje>> = _viajes.flow
    val solicitudes: StateFlow<List<Solicitud>> = _solicitudes.flow
    val calificaciones: StateFlow<List<Calificacion>> = _calificaciones.flow
    val pagos: StateFlow<Map<Int, Pago>> = _pagos.flow
    val avisos: StateFlow<List<Notificacion>> = _avisos.flow
    val alertas: StateFlow<List<AlertaSOS>> = _alertas.flow
    val sesionId: StateFlow<Int?> = _sesionId.flow

    /** Eventos para disparar notificaciones push locales (RF-12). */
    val eventos: SharedFlow<Notificacion> = MutableSharedFlow<Notificacion>(extraBufferCapacity = 32)


    private val estados by lazy { listOf(_usuarios, _viajes, _solicitudes, _calificaciones, _pagos, _avisos, _alertas) }
    private val mutex = Mutex()
    private lateinit var database: ItzDatabase
    private val eventosPendientes = mutableListOf<Notificacion>()
    private val _listo = MutableStateFlow(false)
    val listo: StateFlow<Boolean> = _listo

    /** Called before exposing forms; sample data is inserted only on an empty database. */
    suspend fun inicializar(context: Context, nombreBaseDatos: String = "itz_compartido.db") = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_listo.value) return@withLock
            if (!::database.isInitialized) database = ItzDatabase.abrir(context, nombreBaseDatos)
            database.withTransaction {
                val dao = database.datos()
                if (dao.usuarios().isEmpty()) {
                    _usuarios.value = _usuarios.value.map {
                        if (it.password.startsWith("pbkdf2:")) it
                        else it.copy(password = PasswordHasher.hash(it.password))
                    }
                    guardar(null)
                }
                restaurar(Estado(dao.usuarios(), dao.viajes(), dao.solicitudes(),
                    dao.calificaciones(), dao.pagos().associateBy { it.viajeId },
                    dao.avisos(), dao.alertas(), null))
            }
            idUsuario.set(maxOf(100, _usuarios.value.maxOfOrNull { it.idUsuario } ?: 0))
            idViaje.set(_viajes.value.maxOfOrNull { it.idViaje } ?: 0)
            idSolicitud.set(_solicitudes.value.maxOfOrNull { it.idSolicitud } ?: 0)
            idAviso.set(_avisos.value.maxOfOrNull { it.id } ?: 0)
            idSos.set(_alertas.value.maxOfOrNull { it.id } ?: 0)
            _listo.value = true
        }
    }

    private data class Estado(
        val usuarios: List<Usuario>, val viajes: List<Viaje>, val solicitudes: List<Solicitud>,
        val calificaciones: List<Calificacion>, val pagos: Map<Int, Pago>,
        val avisos: List<Notificacion>, val alertas: List<AlertaSOS>, val sesion: Int?
    )

    private fun estado() = Estado(_usuarios.value, _viajes.value, _solicitudes.value,
        _calificaciones.value, _pagos.value, _avisos.value, _alertas.value, _sesionId.value)

    private fun restaurar(e: Estado) {
        _usuarios.value = e.usuarios; _viajes.value = e.viajes
        _solicitudes.value = e.solicitudes; _calificaciones.value = e.calificaciones
        _pagos.value = e.pagos; _avisos.value = e.avisos; _alertas.value = e.alertas
        _sesionId.value = e.sesion
    }

    private fun guardar(anterior: Estado?) {
        val dao = database.datos()
        dao.usuarios(_usuarios.value.filter { it !in (anterior?.usuarios ?: emptyList()) })
        dao.viajes(_viajes.value.filter { it !in (anterior?.viajes ?: emptyList()) })
        dao.solicitudes(_solicitudes.value.filter { it !in (anterior?.solicitudes ?: emptyList()) })
        dao.calificaciones(_calificaciones.value.filter { it !in (anterior?.calificaciones ?: emptyList()) })
        dao.pagos(_pagos.value.values.filter { it !in (anterior?.pagos?.values ?: emptyList()) })
        dao.avisos(_avisos.value.filter { it !in (anterior?.avisos ?: emptyList()) })
        dao.alertas(_alertas.value.filter { it !in (anterior?.alertas ?: emptyList()) })
    }

    /** A failed write rolls back both SQLite and the in-memory view; notifications follow commit. */
    private suspend fun <T> persistir(operacion: () -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Finish commit and publication together even if navigation cancels the calling scope.
            withContext(NonCancellable) {
                check(_listo.value) { "La base de datos todavía no está lista" }
                val anterior = estado()
                eventosPendientes.clear()
                estados.forEach { it.comenzar() }
                try {
                    val result = database.withTransaction {
                        operacion().also { if (it.isSuccess) guardar(anterior) }
                    }
                    if (result.isFailure) estados.forEach { it.cancelar() }
                    else {
                        estados.forEach { it.confirmar() }
                        eventosPendientes.forEach { (eventos as MutableSharedFlow<Notificacion>).tryEmit(it) }
                    }
                    result
                } catch (e: CancellationException) {
                    estados.forEach { it.cancelar() }
                    throw e
                } catch (e: Exception) {
                    estados.forEach { it.cancelar() }
                    Result.failure(IllegalStateException("No se pudieron guardar los cambios. Intenta de nuevo.", e))
                } finally {
                    eventosPendientes.clear()
                }
            }
        }
    }

    suspend fun registrar(
        nombre: String, matricula: String, correo: String,
        telefono: String, contacto: String, password: String
    ): Result<Usuario> = persistir { registrarEnMemoria(nombre, matricula, correo, telefono, contacto, password) }
        .onSuccess { _sesionId.value = it.idUsuario }
    suspend fun actualizarPerfil(id: Int, telefono: String, contacto: String): Result<Unit> = persistir { actualizarPerfilEnMemoria(id, telefono, contacto) }
    suspend fun registrarConductor(
        id: Int, placas: String, modelo: String, color: String, capacidad: Int?, licencia: String
    ): Result<Unit> = persistir { registrarConductorEnMemoria(id, placas, modelo, color, capacidad, licencia) }
    suspend fun publicarViaje(
        conductorId: Int, zona: String, haciaItz: Boolean, punto: String, horaTxt: String,
        asientos: Int?, costo: Double?, tipo: TipoPago
    ): Result<Viaje> = persistir { publicarViajeEnMemoria(conductorId, zona, haciaItz, punto, horaTxt, asientos, costo, tipo) }
    suspend fun solicitarLugar(viajeId: Int, pasajeroId: Int): Result<Unit> = persistir { solicitarLugarEnMemoria(viajeId, pasajeroId) }
    suspend fun responderSolicitud(idSolicitud: Int, aceptar: Boolean): Result<Unit> = persistir { responderSolicitudEnMemoria(idSolicitud, aceptar) }
    suspend fun cancelarSolicitud(idSolicitud: Int): Result<Unit> = persistir { cancelarSolicitudEnMemoria(idSolicitud) }
    suspend fun iniciarViaje(idViaje: Int): Result<Unit> = persistir { iniciarViajeEnMemoria(idViaje) }
    suspend fun finalizarViaje(idViaje: Int): Result<Pago> = persistir { finalizarViajeEnMemoria(idViaje) }
    suspend fun cancelarViaje(idViaje: Int): Result<Unit> = persistir { cancelarViajeEnMemoria(idViaje) }
    suspend fun calificar(viajeId: Int, de: Int, a: Int, puntos: Int, comentario: String): Result<Unit> = persistir { calificarEnMemoria(viajeId, de, a, puntos, comentario) }
    suspend fun emitirSOS(usuarioId: Int, viajeId: Int, lat: Double, lon: Double): Result<Unit> = persistir { emitirSOSEnMemoria(usuarioId, viajeId, lat, lon); Result.success(Unit) }
    suspend fun validarDocumentos(id: Int): Result<Unit> = persistir { validarDocumentosEnMemoria(id); Result.success(Unit) }
    suspend fun suspender(id: Int, suspender: Boolean): Result<Unit> = persistir { suspenderEnMemoria(id, suspender); Result.success(Unit) }

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
        eventosPendientes.add(n)
    }

    private fun notificarAdmins(titulo: String, mensaje: String) =
        _usuarios.value.filter { it.rol == Rol.ADMIN }.forEach { notificar(it.idUsuario, titulo, mensaje) }

    fun nombreDe(id: Int) = usuario(id)?.nombre ?: "Usuario"

    // ---------------- RF-01: registro y sesión ----------------
    private fun registrarEnMemoria(
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
            telefono.trim(), contacto.trim(), PasswordHasher.hash(password)
        )
        _usuarios.update { it + u }
        return Result.success(u)
    }

    suspend fun login(correo: String, password: String): Result<Usuario> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val u = _usuarios.value.firstOrNull {
                it.correoInstitucional == correo.trim().lowercase() && PasswordHasher.verify(password, it.password)
            } ?: return@withContext fallo("Correo o contraseña incorrectos")
            if (u.suspendido) return@withContext fallo("Tu cuenta está suspendida. Contacta a la administración del ITZ")
            _sesionId.value = u.idUsuario
            Result.success(u)
        }
    }

    fun cerrarSesion() { _sesionId.value = null }

    private fun actualizarPerfilEnMemoria(id: Int, telefono: String, contacto: String): Result<Unit> {
        if (!telefono.trim().matches(Regex("[0-9]{10}"))) return fallo("El teléfono debe tener 10 dígitos")
        if (!contacto.trim().matches(Regex("[0-9]{10}"))) return fallo("El contacto de confianza debe tener 10 dígitos")
        actualizarUsuario(id) { it.copy(telefono = telefono.trim(), contactoConfianza = contacto.trim()) }
        return Result.success(Unit)
    }

    // ---------------- RF-02: registro como conductor ----------------
    private fun registrarConductorEnMemoria(
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
    private fun publicarViajeEnMemoria(
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
    private fun solicitarLugarEnMemoria(viajeId: Int, pasajeroId: Int): Result<Unit> {
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

    private fun responderSolicitudEnMemoria(idSolicitud: Int, aceptar: Boolean): Result<Unit> {
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

    private fun cancelarSolicitudEnMemoria(idSolicitud: Int): Result<Unit> {
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
    private fun iniciarViajeEnMemoria(idViaje: Int): Result<Unit> {
        val v = viaje(idViaje) ?: return fallo("Viaje no encontrado")
        if (v.estado != EstadoViaje.PROGRAMADO) return fallo("El viaje no está programado")
        val aceptadas = _solicitudes.value.filter { it.viajeId == idViaje && it.estado == EstadoSolicitud.ACEPTADA }
        if (aceptadas.isEmpty()) return fallo("Aún no tienes pasajeros aceptados")
        actualizarViaje(idViaje) { it.copy(estado = EstadoViaje.EN_CURSO) }
        aceptadas.forEach { notificar(it.pasajeroId, "Tu viaje inició", "Puedes seguir la ruta en tiempo real desde Mis viajes.") }
        return Result.success(Unit)
    }

    /** RF-08: el costo se divide entre el conductor y los pasajeros del viaje. */
    private fun finalizarViajeEnMemoria(idViaje: Int): Result<Pago> {
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

    private fun cancelarViajeEnMemoria(idViaje: Int): Result<Unit> {
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

    private fun calificarEnMemoria(viajeId: Int, de: Int, a: Int, puntos: Int, comentario: String): Result<Unit> {
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
    private fun emitirSOSEnMemoria(usuarioId: Int, viajeId: Int, lat: Double, lon: Double) {
        _alertas.update { it + AlertaSOS(idSos.incrementAndGet(), usuarioId, viajeId, lat, lon, LocalDateTime.now()) }
        notificarAdmins("ALERTA SOS", "${nombreDe(usuarioId)} activó el botón de emergencia en el viaje #$viajeId.")
    }

    // ---------------- RF-11: administración ----------------
    private fun validarDocumentosEnMemoria(id: Int) {
        actualizarUsuario(id) { it.copy(documentosVerificados = true) }
        notificar(id, "Documentos validados", "Ya puedes publicar viajes como conductor.")
    }

    private fun suspenderEnMemoria(id: Int, suspender: Boolean) = actualizarUsuario(id) { it.copy(suspendido = suspender) }

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
