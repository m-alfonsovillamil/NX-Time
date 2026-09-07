package com.nxtime.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

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
                segundosPausa = 60 * 60
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
                segundosPausa = 30 * 60
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
                segundosPausa = 120 * 60
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

    // -----------------------------------------------------------------
    // Turno de noche y calendario (Fase C)
    // -----------------------------------------------------------------

    @Test
    fun `una jornada que cruza la medianoche cuenta un dia de diferencia`() {
        // 22:52 del 3 de septiembre a 00:29 del 4, en hora española.
        assertEquals(
            1,
            DateFormats.diasDeDiferencia("2026-09-03T20:52:00Z", "2026-09-03T22:29:00Z")
        )
    }

    /**
     * El caso que descarta restar instantes: entre las 23:00 y las 00:30
     * hay hora y media, pero el día SÍ ha cambiado, y eso es lo que hay
     * que decir en pantalla.
     */
    @Test
    fun `hora y media a caballo de la medianoche sigue siendo otro dia`() {
        assertEquals(
            1,
            DateFormats.diasDeDiferencia("2026-09-03T21:00:00Z", "2026-09-03T22:30:00Z")
        )
    }

    @Test
    fun `una jornada normal no cambia de dia`() {
        assertEquals(
            0,
            DateFormats.diasDeDiferencia("2026-09-03T07:00:00Z", "2026-09-03T15:00:00Z")
        )
    }

    @Test
    fun `una jornada sin cerrar o ilegible no inventa dias`() {
        assertEquals(0, DateFormats.diasDeDiferencia("2026-09-03T07:00:00Z", null))
        assertEquals(0, DateFormats.diasDeDiferencia(null, "2026-09-03T15:00:00Z"))
        assertEquals(0, DateFormats.diasDeDiferencia("no", "tampoco"))
    }

    @Test
    fun `una fecha ISO se lee como dia de calendario`() {
        assertEquals(LocalDate.of(2026, 5, 15), DateFormats.fechaIso("2026-05-15"))
        assertNull(DateFormats.fechaIso("no-es-una-fecha"))
        assertNull(DateFormats.fechaIso(null))
    }

    /**
     * El mes va con la inicial en mayúscula y en español, pase lo que
     * pase con el idioma del móvil: es el título de la pantalla del
     * calendario.
     */
    @Test
    fun `el mes de la cabecera sale en espanol y con mayuscula inicial`() {
        assertEquals("Mayo de 2026", DateFormats.mesYAnio(YearMonth.of(2026, 5)))
        assertEquals("Diciembre de 2026", DateFormats.mesYAnio(YearMonth.of(2026, 12)))
    }

    @Test
    fun `las iniciales de los dias son las espanolas, empezando en lunes`() {
        assertEquals("L", DateFormats.inicialDelDia(DayOfWeek.MONDAY))
        // La X del miércoles es justo la que delata un `Locale` inglés,
        // donde saldría "W".
        assertEquals("X", DateFormats.inicialDelDia(DayOfWeek.WEDNESDAY))
        assertEquals("D", DateFormats.inicialDelDia(DayOfWeek.SUNDAY))
    }
}
