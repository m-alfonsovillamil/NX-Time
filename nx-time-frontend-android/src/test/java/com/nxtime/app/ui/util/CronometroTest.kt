package com.nxtime.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * El cronómetro de la jornada abierta.
 *
 * Se prueba aparte y con instantes fijos porque aquí acecha el patrón de
 * bug recurrente del proyecto: **truncar antes de agregar**. Ya apareció
 * dos veces en el backend (en las pausas y en el informe mensual, donde
 * se perdían hasta 21 min/mes) y un cronómetro es justo donde más se
 * nota, porque recalcula cada segundo.
 */
class CronometroTest {

    private val entrada = "2026-09-03T07:00:00Z"

    private fun ahora(iso: String) = Instant.parse(iso)

    @Test
    fun `cuenta en horas, minutos y segundos con dos digitos`() {
        assertEquals("00:00:00", DateFormats.cronometro(0))
        assertEquals("00:00:09", DateFormats.cronometro(9))
        assertEquals("00:01:00", DateFormats.cronometro(60))
        assertEquals("02:14:38", DateFormats.cronometro(2 * 3600 + 14 * 60 + 38))
        // Más de un día fichado: se acumulan las horas, no se reinicia.
        assertEquals("25:00:01", DateFormats.cronometro(25 * 3600 + 1))
    }

    /**
     * Un reloj del móvil atrasado respecto al servidor puede dar una
     * cuenta negativa. Se enseña un cero, no "-00:00:12".
     */
    @Test
    fun `una cuenta negativa se pinta como cero`() {
        assertEquals("00:00:00", DateFormats.cronometro(-12))
    }

    @Test
    fun `los segundos sueltos no se pierden por el camino`() {
        val segundos = DateFormats.segundosTrabajados(
            entrada,
            segundosPausa = 0,
            ahora = ahora("2026-09-03T09:14:38Z")
        )
        assertEquals(2L * 3600 + 14 * 60 + 38, segundos)
        assertEquals("02:14:38", DateFormats.cronometro(segundos))
    }

    @Test
    fun `las pausas ya terminadas se descuentan`() {
        val segundos = DateFormats.segundosTrabajados(
            entrada,
            segundosPausa = 30 * 60,
            ahora = ahora("2026-09-03T09:00:00Z")
        )
        assertEquals(90L * 60, segundos)
        assertEquals("01:30:00", DateFormats.cronometro(segundos))
    }

    /**
     * El caso que un truncado intermedio se comería: 59 segundos de
     * jornada tienen que verse correr, no quedarse en 00:00:00 hasta
     * cumplir el primer minuto.
     */
    @Test
    fun `los primeros segundos de jornada ya se ven`() {
        val segundos = DateFormats.segundosTrabajados(
            entrada,
            segundosPausa = 0,
            ahora = ahora("2026-09-03T07:00:59Z")
        )
        assertEquals(59L, segundos)
        assertEquals("00:00:59", DateFormats.cronometro(segundos))
    }

    @Test
    fun `sin hora de entrada la cuenta es cero, no un fallo`() {
        assertEquals(0L, DateFormats.segundosTrabajados(null, segundosPausa = 0))
        assertEquals(0L, DateFormats.segundosTrabajados("no es una fecha", segundosPausa = 0))
    }

    /**
     * El defecto que destapó el cronómetro al ejecutarlo, y el motivo de
     * que `TimeEntryResponse` viaje ahora también con los segundos de
     * pausa en crudo.
     *
     * Una pausa de 40 s vale **0 minutos** al truncar. Restando minutos,
     * el cronómetro se comía la pausa entera y contaba como trabajados
     * 40 segundos en los que el empleado no estaba trabajando. Con una
     * pausa corta cada poco rato, el error se acumula durante toda la
     * jornada.
     */
    @Test
    fun `una pausa de menos de un minuto tambien se descuenta`() {
        val conSegundos = DateFormats.segundosTrabajados(
            entrada,
            segundosPausa = 40,
            ahora = ahora("2026-09-03T07:03:19Z")
        )
        assertEquals(159L, conSegundos)
        assertEquals("00:02:39", DateFormats.cronometro(conSegundos))

        // Lo que salía antes, restando los minutos truncados (40 s -> 0):
        // 40 segundos de más, regalados como trabajo.
        val conMinutosTruncados = DateFormats.segundosTrabajados(
            entrada,
            segundosPausa = (40L / 60) * 60,
            ahora = ahora("2026-09-03T07:03:19Z")
        )
        assertEquals(199L, conMinutosTruncados)
    }

    /**
     * Lo mismo en el total de una jornada ya cerrada, que es el número
     * que se enseña en el historial y el que cuadra (o no) con el
     * informe mensual.
     */
    @Test
    fun `el total de una jornada cerrada no regala los segundos de pausa`() {
        // 8h justas de reloj, con 26 min 40 s de pausa: 7h 33m de
        // trabajo. Restando los 26 minutos truncados salía 7h 34m.
        assertEquals(
            "7h 33m",
            DateFormats.duracionNeta(
                "2026-09-03T07:00:00Z",
                "2026-09-03T15:00:00Z",
                segundosPausa = 26 * 60 + 40
            )
        )
    }
}
