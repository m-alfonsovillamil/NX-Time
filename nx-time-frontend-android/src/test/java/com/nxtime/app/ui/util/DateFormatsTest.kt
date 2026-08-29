package com.nxtime.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * El formateo de fechas y duraciones.
 *
 * Interesa sobre todo la duración neta: es el número que el empleado
 * lee como "lo que he trabajado hoy" y el que debe cuadrar con los
 * informes del backend.
 */
class DateFormatsTest {

    @Test
    fun `la hora se da siempre en la zona de Espana, no en la del movil`() {
        // 07:00 UTC en agosto son las 09:00 en España (CEST).
        assertEquals("09:00 h", DateFormats.hora("2026-08-29T07:00:00Z"))
    }

    @Test
    fun `la duracion bruta va de entrada a salida`() {
        assertEquals(
            "8h 00m",
            DateFormats.duracion("2026-08-29T07:00:00Z", "2026-08-29T15:00:00Z")
        )
    }

    @Test
    fun `la duracion neta descuenta las pausas`() {
        // De 09:00 a 18:00 con una hora de comida son 8h de trabajo,
        // no 9h: es la cuenta que acaba en la nómina.
        assertEquals(
            "8h 00m",
            DateFormats.duracionNeta(
                "2026-08-29T07:00:00Z",
                "2026-08-29T16:00:00Z",
                minutosPausa = 60
            )
        )
    }

    @Test
    fun `los minutos sueltos se redondean hacia abajo, sin perder la hora`() {
        assertEquals(
            "7h 30m",
            DateFormats.duracionNeta(
                "2026-08-29T07:00:00Z",
                "2026-08-29T15:00:59Z",
                minutosPausa = 30
            )
        )
    }

    @Test
    fun `una jornada sin cerrar no muestra una duracion inventada`() {
        assertEquals(DateFormats.EN_CURSO, DateFormats.duracion("2026-08-29T07:00:00Z", null))
    }

    @Test
    fun `unas pausas mayores que la jornada no dan una duracion negativa`() {
        // Dato incoherente que solo puede venir de un fichaje corregido
        // a mano; se admite que no se sabe en vez de enseñar "-1h 00m".
        assertEquals(
            DateFormats.SIN_DATO,
            DateFormats.duracionNeta(
                "2026-08-29T07:00:00Z",
                "2026-08-29T08:00:00Z",
                minutosPausa = 120
            )
        )
    }

    @Test
    fun `una fecha que no se puede leer no tira la pantalla abajo`() {
        assertEquals(DateFormats.SIN_DATO, DateFormats.hora("no-es-una-fecha"))
        assertEquals(DateFormats.SIN_DATO, DateFormats.fechaLarga("no-es-una-fecha"))
        assertEquals(DateFormats.SIN_DATO, DateFormats.fechaCorta("no-es-una-fecha"))
        assertEquals(DateFormats.SIN_DATO, DateFormats.duracion("no", "tampoco"))
    }

    @Test
    fun `un nulo se muestra como sin dato`() {
        assertEquals(DateFormats.SIN_DATO, DateFormats.hora(null))
        assertEquals(DateFormats.SIN_DATO, DateFormats.fechaCorta(null as LocalDate?))
    }

    @Test
    fun `las fechas de las ausencias son de calendario, sin zona`() {
        assertEquals("14/12/2026", DateFormats.fechaCorta("2026-12-14"))
    }
}
