package com.nxtime.app.widget

import com.nxtime.app.data.dto.Registro
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo que enseña el widget de la jornada (plan P12). */
class EstadoDelWidgetTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val hoy = LocalDate.of(2026, 11, 30)

    private fun jornada(entrada: String?, salida: String? = null, enPausa: Boolean = false, pausa: String? = null) =
        Registro(id = 1, horaEntrada = entrada, horaSalida = salida, enPausa = enPausa, inicioPausaActual = pausa)

    @Test
    fun `sin sesion, a entrar, tenga la jornada que tenga`() {
        assertEquals(EstadoDelWidget.SinSesion, estadoDelWidget(false, jornada("2026-11-30T07:05:00Z"), hoy, madrid))
    }

    @Test
    fun `una jornada abierta hoy dice desde cuando, en hora de Espana`() {
        // 7:05 UTC son las 8:05 en Madrid en invierno.
        assertEquals(
            EstadoDelWidget.Trabajando("8:05"),
            estadoDelWidget(true, jornada("2026-11-30T07:05:00Z"), hoy, madrid)
        )
    }

    @Test
    fun `en pausa dice desde cuando es la pausa`() {
        assertEquals(
            EstadoDelWidget.EnPausa("14:00"),
            estadoDelWidget(true, jornada("2026-11-30T07:05:00Z", enPausa = true, pausa = "2026-11-30T13:00:00Z"), hoy, madrid)
        )
    }

    @Test
    fun `sin jornada, cerrada o de otro dia, no hay jornada abierta`() {
        assertEquals(EstadoDelWidget.SinJornada, estadoDelWidget(true, null, hoy, madrid))
        assertEquals(
            EstadoDelWidget.SinJornada,
            estadoDelWidget(true, jornada("2026-11-30T07:05:00Z", salida = "2026-11-30T15:00:00Z"), hoy, madrid)
        )
        // Abierta ayer: el servidor la cierra solo; el widget no dice «Trabajando».
        assertEquals(EstadoDelWidget.SinJornada, estadoDelWidget(true, jornada("2026-11-29T07:05:00Z"), hoy, madrid))
        // Una hora que no se entiende no rompe nada.
        assertEquals(EstadoDelWidget.SinJornada, estadoDelWidget(true, jornada("ayer"), hoy, madrid))
    }

    @Test
    fun `la medianoche cuenta en la hora de Espana, no en UTC`() {
        // 23:30 UTC del 29 son las 0:30 del 30 en Madrid: es de hoy.
        assertEquals(
            EstadoDelWidget.Trabajando("0:30"),
            estadoDelWidget(true, jornada("2026-11-29T23:30:00Z"), hoy, madrid)
        )
    }
}
