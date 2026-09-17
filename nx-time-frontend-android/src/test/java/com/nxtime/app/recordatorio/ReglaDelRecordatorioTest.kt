package com.nxtime.app.recordatorio

import com.nxtime.app.data.dto.Registro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * La regla del recordatorio de fichar.
 *
 * Está separada del `Worker` justo para poder probar esto sin emulador, y
 * lo que se fija aquí es sobre todo **cuándo NO se avisa**: un aviso que
 * llega cuando ya has fichado es lo que enseña a silenciar una aplicación.
 */
class ReglaDelRecordatorioTest {

    private fun registro(horaSalida: String?) = Registro(
        id = 1L,
        horaEntrada = "2026-09-12T09:31:00Z",
        horaSalida = horaSalida,
        enPausa = false
    )

    @Test
    fun `hay jornada abierta si el registro no tiene hora de salida`() {
        assertTrue(ReglaDelRecordatorio.jornadaAbierta(registro(horaSalida = null)))
    }

    @Test
    fun `una jornada ya cerrada no cuenta como abierta`() {
        assertFalse(ReglaDelRecordatorio.jornadaAbierta(registro("2026-09-12T18:02:00Z")))
    }

    @Test
    fun `sin registro no hay jornada abierta`() {
        assertFalse(ReglaDelRecordatorio.jornadaAbierta(null))
    }

    @Test
    fun `el aviso de entrada solo salta si NO has fichado`() {
        val lunes = DayOfWeek.MONDAY
        assertTrue(ReglaDelRecordatorio.toca(TipoDeAviso.ENTRADA, hayJornadaAbierta = false, dia = lunes))
        assertFalse(ReglaDelRecordatorio.toca(TipoDeAviso.ENTRADA, hayJornadaAbierta = true, dia = lunes))
    }

    @Test
    fun `el aviso de salida solo salta si te dejaste la jornada abierta`() {
        val lunes = DayOfWeek.MONDAY
        assertTrue(ReglaDelRecordatorio.toca(TipoDeAviso.SALIDA, hayJornadaAbierta = true, dia = lunes))
        assertFalse(ReglaDelRecordatorio.toca(TipoDeAviso.SALIDA, hayJornadaAbierta = false, dia = lunes))
    }

    @Test
    fun `el fin de semana no se avisa de nada`() {
        for (dia in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            assertFalse(ReglaDelRecordatorio.toca(TipoDeAviso.ENTRADA, false, dia))
            assertFalse(ReglaDelRecordatorio.toca(TipoDeAviso.SALIDA, true, dia))
        }
    }

    @Test
    fun `una hora mal escrita no se acepta`() {
        assertTrue(ReglaDelRecordatorio.esHoraValida("09:30"))
        assertTrue(ReglaDelRecordatorio.esHoraValida("23:59"))
        assertFalse(ReglaDelRecordatorio.esHoraValida("25:70"))
        assertFalse(ReglaDelRecordatorio.esHoraValida("9.30"))
        assertFalse(ReglaDelRecordatorio.esHoraValida(""))
    }

    @Test
    fun `si la hora ya pasó hoy, se cuenta hasta mañana`() {
        val minutos = ReglaDelRecordatorio.minutosHasta(
            objetivo = LocalTime.of(9, 30),
            ahora = LocalTime.of(18, 0)
        )
        // De las 18:00 a las 9:30 del día siguiente: 15 h y media.
        assertEquals(15 * 60L + 30, minutos)
    }

    @Test
    fun `una hora futura del mismo día se cuenta directa`() {
        val minutos = ReglaDelRecordatorio.minutosHasta(
            objetivo = LocalTime.of(18, 30),
            ahora = LocalTime.of(18, 0)
        )
        assertEquals(30L, minutos)
    }

    @Test
    fun `programar justo a la hora no dispara el aviso en ese instante`() {
        // Con 0 minutos de retardo, WorkManager lanzaria el aviso al
        // programarlo: quien activa el recordatorio a las 09:30 no espera
        // que le salte una notificacion en ese mismo momento.
        val minutos = ReglaDelRecordatorio.minutosHasta(
            objetivo = LocalTime.of(9, 30),
            ahora = LocalTime.of(9, 30)
        )
        assertEquals(24 * 60L, minutos)
        assertTrue(minutos >= 1)
    }
}
