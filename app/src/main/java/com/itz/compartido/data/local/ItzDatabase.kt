package com.itz.compartido.data.local

import android.content.Context
import androidx.room.*
import com.itz.compartido.model.*
import org.json.JSONArray
import java.time.LocalDateTime
import java.time.LocalTime

/** Version 1 persists the entities used by the current MVP, without destructive migrations. */
@Database(
    entities = [Usuario::class, Viaje::class, Solicitud::class, Calificacion::class,
        Pago::class, Notificacion::class, AlertaSOS::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(ItzConverters::class)
abstract class ItzDatabase : RoomDatabase() {
    abstract fun datos(): ItzDao

    companion object {
        fun abrir(context: Context, nombre: String = "itz_compartido.db"): ItzDatabase =
            Room.databaseBuilder(context.applicationContext, ItzDatabase::class.java, nombre).build()
    }
}

class ItzConverters {
    @TypeConverter fun hora(value: LocalTime): String = value.toString()
    @TypeConverter fun aHora(value: String): LocalTime = LocalTime.parse(value)
    @TypeConverter fun fecha(value: LocalDateTime): String = value.toString()
    @TypeConverter fun aFecha(value: String): LocalDateTime = LocalDateTime.parse(value)
    @TypeConverter fun puntos(value: List<Pair<Float, Float>>): String =
        JSONArray().apply { value.forEach { put(JSONArray().put(it.first).put(it.second)) } }.toString()
    @TypeConverter fun aPuntos(value: String): List<Pair<Float, Float>> =
        JSONArray(value).let { array ->
            (0 until array.length()).map {
                val point = array.getJSONArray(it)
                point.getDouble(0).toFloat() to point.getDouble(1).toFloat()
            }
        }
    @TypeConverter fun zonas(value: List<String>): String = JSONArray(value).toString()
    @TypeConverter fun aZonas(value: String): List<String> =
        JSONArray(value).let { array -> (0 until array.length()).map { array.getString(it) } }
}

@Dao
interface ItzDao {
    @Query("SELECT * FROM usuarios ORDER BY idUsuario") fun usuarios(): List<Usuario>
    @Query("SELECT * FROM viajes ORDER BY idViaje") fun viajes(): List<Viaje>
    @Query("SELECT * FROM solicitudes_viaje ORDER BY idSolicitud") fun solicitudes(): List<Solicitud>
    @Query("SELECT * FROM calificaciones") fun calificaciones(): List<Calificacion>
    @Query("SELECT * FROM pagos") fun pagos(): List<Pago>
    @Query("SELECT * FROM notificaciones ORDER BY id DESC") fun avisos(): List<Notificacion>
    @Query("SELECT * FROM alertas_sos ORDER BY id") fun alertas(): List<AlertaSOS>

    // Upsert uses UPDATE for existing rows, avoiding REPLACE's implicit DELETE and FK cascades.
    @Upsert fun usuarios(value: List<Usuario>)
    @Upsert fun viajes(value: List<Viaje>)
    @Upsert fun solicitudes(value: List<Solicitud>)
    @Upsert fun calificaciones(value: List<Calificacion>)
    @Upsert fun pagos(value: List<Pago>)
    @Upsert fun avisos(value: List<Notificacion>)
    @Upsert fun alertas(value: List<AlertaSOS>)
}
