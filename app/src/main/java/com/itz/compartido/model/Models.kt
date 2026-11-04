package com.itz.compartido.model

import androidx.room.*
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ---- Enumeraciones (diagrama de clases) ----
enum class Rol { PASAJERO, CONDUCTOR, ADMIN }
enum class EstadoViaje { PROGRAMADO, EN_CURSO, FINALIZADO, CANCELADO }
enum class EstadoSolicitud { PENDIENTE, ACEPTADA, RECHAZADA, CANCELADA }
enum class NivelRiesgo { BAJO, MEDIO, ALTO }
enum class TipoPago(val etiqueta: String) {
    GASOLINA("Gasolina"), CASETA("Caseta"), MIXTO("Gasolina y caseta")
}

enum class Franja(val etiqueta: String, private val desde: Int, private val hasta: Int) {
    TODAS("Cualquier hora", 0, 23),
    MANANA("Mañana (5–11)", 5, 11),
    TARDE("Tarde (12–18)", 12, 18),
    NOCHE("Noche (19–23)", 19, 23);

    fun contiene(hora: Int) = hora in desde..hasta
}

// ---- Clases del dominio ----
data class GeoPunto(val lat: Double, val lon: Double, val nombre: String = "")

data class Vehiculo(
    val placas: String,
    val modelo: String,
    val color: String,
    val capacidadAsientos: Int
)

/** Usuario + Conductor + Pasajero (la herencia del diagrama se modela con [rol] y [vehiculo]). */
@Entity(
    tableName = "usuarios",
    primaryKeys = ["idUsuario"],
    indices = [Index(value = ["matricula"], unique = true), Index(value = ["correoInstitucional"], unique = true)]
)
data class Usuario(
    val idUsuario: Int,
    val nombre: String,
    val matricula: String,
    val correoInstitucional: String,
    val telefono: String,
    val contactoConfianza: String,
    @ColumnInfo(name = "password_hash") val password: String,
    val rol: Rol = Rol.PASAJERO,
    @Embedded(prefix = "vehiculo_") val vehiculo: Vehiculo? = null,
    val licencia: String = "",
    val documentosVerificados: Boolean = false,
    val suspendido: Boolean = false,
    val calificacionProm: Double = 0.0,
    val numCalificaciones: Int = 0
)

data class RutaSegura(
    /** Puntos normalizados (0..1) para dibujar la ruta en el mapa simulado. */
    val puntosGPS: List<Pair<Float, Float>>,
    val nivelRiesgo: NivelRiesgo,
    val zonasSeguras: List<String>
) {
    companion object {
        /** calcularRutaOptima(): versión MVP simulada. Sustituir por la API de rutas real. */
        fun calcular(origen: GeoPunto, destino: GeoPunto, hora: LocalTime): RutaSegura {
            val n = 14
            val fase = (abs(origen.nombre.hashCode()) % 100) / 100f * 3f
            val pts = (0..n).map { i ->
                val t = i / n.toFloat()
                val x = 0.08f + 0.84f * t
                val y = 0.80f - 0.60f * t + 0.07f * sin(t * 2f * PI.toFloat() + fase)
                x to y
            }
            val riesgo = when {
                hora.hour >= 22 || hora.hour < 5 -> NivelRiesgo.ALTO
                hora.hour >= 20 || hora.hour < 6 -> NivelRiesgo.MEDIO
                else -> NivelRiesgo.BAJO
            }
            return RutaSegura(
                puntosGPS = pts,
                nivelRiesgo = riesgo,
                zonasSeguras = listOf(
                    "Gasolinera con vigilancia",
                    "Plaza principal",
                    "Módulo de seguridad",
                    "Acceso al ITZ"
                )
            )
        }
    }
}

@Entity(
    tableName = "viajes",
    primaryKeys = ["idViaje"],
    indices = [Index(value = ["conductorId"])],
    foreignKeys = [ForeignKey(entity = Usuario::class, parentColumns = ["idUsuario"], childColumns = ["conductorId"], onDelete = ForeignKey.RESTRICT)]
)
data class Viaje(
    val idViaje: Int,
    val conductorId: Int,
    @Embedded(prefix = "origen_") val origen: GeoPunto,
    @Embedded(prefix = "destino_") val destino: GeoPunto,
    val zona: String,
    val horaSalida: LocalTime,
    val asientosDisponibles: Int,
    val asientosTotales: Int,
    val costoEstimado: Double,
    val tipoPago: TipoPago,
    val estado: EstadoViaje,
    @Embedded(prefix = "ruta_") val ruta: RutaSegura
)

@Entity(
    tableName = "solicitudes_viaje",
    primaryKeys = ["idSolicitud"],
    indices = [Index(value = ["viajeId"]), Index(value = ["pasajeroId"])],
    foreignKeys = [ForeignKey(entity = Viaje::class, parentColumns = ["idViaje"], childColumns = ["viajeId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = Usuario::class, parentColumns = ["idUsuario"], childColumns = ["pasajeroId"], onDelete = ForeignKey.RESTRICT)]
)
data class Solicitud(
    val idSolicitud: Int,
    val viajeId: Int,
    val pasajeroId: Int,
    val fecha: LocalDateTime,
    val estado: EstadoSolicitud
)

@Entity(
    tableName = "calificaciones",
    primaryKeys = ["viajeId", "deUsuarioId", "aUsuarioId"],
    indices = [Index(value = ["viajeId"]), Index(value = ["deUsuarioId"]), Index(value = ["aUsuarioId"])],
    foreignKeys = [ForeignKey(entity = Viaje::class, parentColumns = ["idViaje"], childColumns = ["viajeId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = Usuario::class, parentColumns = ["idUsuario"], childColumns = ["deUsuarioId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = Usuario::class, parentColumns = ["idUsuario"], childColumns = ["aUsuarioId"], onDelete = ForeignKey.RESTRICT)]
)
data class Calificacion(
    val viajeId: Int,
    val deUsuarioId: Int,
    val aUsuarioId: Int,
    val puntuacion: Int,
    val comentario: String
)

@Entity(
    tableName = "pagos",
    primaryKeys = ["viajeId"],
    indices = [Index(value = ["viajeId"])],
    foreignKeys = [ForeignKey(entity = Viaje::class, parentColumns = ["idViaje"], childColumns = ["viajeId"], onDelete = ForeignKey.RESTRICT)]
)
data class Pago(
    val viajeId: Int,
    val monto: Double,
    val tipo: TipoPago,
    val personas: Int,
    val montoPorPersona: Double,
    val fecha: LocalDateTime
)

@Entity(
    tableName = "notificaciones",
    primaryKeys = ["id"],
    indices = [Index(value = ["usuarioId"])],
    foreignKeys = [ForeignKey(entity = Usuario::class, parentColumns = ["idUsuario"], childColumns = ["usuarioId"], onDelete = ForeignKey.RESTRICT)]
)
data class Notificacion(
    val id: Int,
    val usuarioId: Int,
    val titulo: String,
    val mensaje: String,
    val hora: LocalDateTime
)

@Entity(
    tableName = "alertas_sos",
    primaryKeys = ["id"],
    indices = [Index(value = ["usuarioId"]), Index(value = ["viajeId"])],
    foreignKeys = [ForeignKey(entity = Usuario::class, parentColumns = ["idUsuario"], childColumns = ["usuarioId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = Viaje::class, parentColumns = ["idViaje"], childColumns = ["viajeId"], onDelete = ForeignKey.RESTRICT)]
)
data class AlertaSOS(
    val id: Int,
    val usuarioId: Int,
    val viajeId: Int,
    val lat: Double,
    val lon: Double,
    val hora: LocalDateTime
)

data class ReporteUso(
    val usuarios: Int,
    val conductores: Int,
    val viajesPublicados: Int,
    val viajesFinalizados: Int,
    val solicitudes: Int,
    val calificacionGlobal: Double,
    val alertasSOS: Int
)
