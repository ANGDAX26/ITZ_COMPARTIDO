package com.itz.compartido.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.itz.compartido.data.local.ItzDatabase
import com.itz.compartido.data.local.PasswordHasher
import com.itz.compartido.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistenciaTest {
    @Test
    fun flujoCompletoPersisteYUnFalloNoPublicaCambios() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nombre = "persistencia-${UUID.randomUUID()}.db"
        ItzRepository.inicializar(context, nombre)
        val repo = ItzRepository
        val nuevo = repo.registrar("Ana Prueba", "22090999", "ana${repo.DOMINIO_ITZ}",
            "7341111111", "7342222222", "prueba123").getOrThrow()
        assertNotEquals("prueba123", nuevo.password)
        assertTrue(PasswordHasher.verify("prueba123", nuevo.password))
        repo.cerrarSesion()
        assertTrue(repo.login(nuevo.correoInstitucional, "incorrecta").isFailure)
        assertEquals(nuevo.idUsuario, repo.login(nuevo.correoInstitucional, "prueba123").getOrThrow().idUsuario)
        repo.actualizarPerfil(nuevo.idUsuario, "7343333333", "7344444444").getOrThrow()
        repo.registrarConductor(nuevo.idUsuario, "TEST-101", "Versa", "Azul", 4, "LIC101").getOrThrow()
        repo.validarDocumentos(nuevo.idUsuario).getOrThrow()
        val viaje = repo.publicarViaje(nuevo.idUsuario, "Jojutla", true, "Centro", "07:30", 1,
            90.0, TipoPago.GASOLINA).getOrThrow()
        repo.solicitarLugar(viaje.idViaje, 4).getOrThrow()
        repo.solicitarLugar(viaje.idViaje, 3).getOrThrow()
        val solicitudes = repo.solicitudes.value.filter { it.viajeId == viaje.idViaje }
        val resultados = coroutineScope {
            solicitudes.map { async { repo.responderSolicitud(it.idSolicitud, true) } }.map { it.await() }
        }
        assertEquals(1, resultados.count { it.isSuccess })
        val aceptada = repo.solicitudes.value.single { it.viajeId == viaje.idViaje && it.estado == EstadoSolicitud.ACEPTADA }
        assertEquals(0, repo.viajes.value.single { it.idViaje == viaje.idViaje }.asientosDisponibles)
        repo.iniciarViaje(viaje.idViaje).getOrThrow()
        val inspector = ItzDatabase.abrir(context, nombre)
        try {
            // Force a real SQLite write failure halfway through a multi-table operation.
            inspector.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER fallo_pago BEFORE INSERT ON pagos
                BEGIN SELECT RAISE(ABORT, 'fallo de escritura de prueba'); END
            """.trimIndent())
            val avisosAntes = repo.avisos.value
            assertTrue(repo.finalizarViaje(viaje.idViaje).isFailure)
            assertEquals(EstadoViaje.EN_CURSO, repo.viajes.value.single { it.idViaje == viaje.idViaje }.estado)
            assertFalse(repo.pagos.value.containsKey(viaje.idViaje))
            assertEquals(avisosAntes, repo.avisos.value)
            inspector.openHelper.writableDatabase.execSQL("DROP TRIGGER fallo_pago")
            repo.finalizarViaje(viaje.idViaje).getOrThrow()
            repo.calificar(viaje.idViaje, aceptada.pasajeroId, nuevo.idUsuario, 5, "Muy puntual").getOrThrow()
            assertTrue(repo.calificar(viaje.idViaje, aceptada.pasajeroId, nuevo.idUsuario, 4, "Duplicada").isFailure)
            repo.emitirSOS(nuevo.idUsuario, viaje.idViaje, 18.65, -99.18).getOrThrow()
            repo.suspender(nuevo.idUsuario, true).getOrThrow()
            assertTrue(repo.login(nuevo.correoInstitucional, "prueba123").isFailure)
        } finally { inspector.close() }

        // A fresh Room connection reads all seven tables from the on-disk database.
        val reabierta = ItzDatabase.abrir(context, nombre)
        try {
            val dao = reabierta.datos()
            assertEquals(repo.usuarios.value, dao.usuarios())
            assertEquals(repo.viajes.value, dao.viajes())
            assertEquals(repo.solicitudes.value, dao.solicitudes())
            assertEquals(repo.calificaciones.value.toSet(), dao.calificaciones().toSet())
            assertEquals(repo.pagos.value, dao.pagos().associateBy { it.viajeId })
            assertEquals(repo.avisos.value, dao.avisos())
            assertEquals(repo.alertas.value, dao.alertas())
            val recuperado = dao.usuarios().single { it.idUsuario == nuevo.idUsuario }
            assertEquals("7344444444", recuperado.contactoConfianza)
            assertEquals("TEST-101", recuperado.vehiculo?.placas)
            assertEquals(45.0, dao.pagos().single().montoPorPersona, 0.001)
        } finally { reabierta.close() }
    }
}
