package com.nxtime.app.data.sinconexion

import com.nxtime.app.data.dto.FichajePendiente
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.TipoFichaje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaDelEnvioTest {

    private fun fichaje(tipo: TipoFichaje, hora: String = "2026-10-26T07:00:00Z", elapsed: Long = 1_000, arranque: Int = 5) =
        FichajePendiente(
            idCliente = "$tipo-$hora",
            dueno = "ana@x",
            tipo = tipo,
            horaDelDispositivo = hora,
            elapsedAlFichar = elapsed,
            arranqueAlFichar = arranque
        )

    private val abierta = Registro(id = 9, horaEntrada = "2026-10-26T06:00:00Z", horaSalida = null, enPausa = false)

    @Test
    fun `sin reiniciar el movil se manda cuanto hace que se ficho`() {
        assertEquals(4_000L, ReglaDelEnvio.msDesdeElFichaje(fichaje(TipoFichaje.INICIO), 5, 5_000))
    }

    /** La cuenta de `elapsedRealtime` empieza de cero al arrancar: seguir usándola daría una hora falsa. */
    @Test
    fun `si el movil se reinicio no se manda, y decide la hora del dispositivo`() {
        assertNull(ReglaDelEnvio.msDesdeElFichaje(fichaje(TipoFichaje.INICIO), 6, 5_000))
    }

    @Test
    fun `sin saber si se reinicio tampoco, ni con una cuenta negativa`() {
        assertNull(ReglaDelEnvio.msDesdeElFichaje(fichaje(TipoFichaje.INICIO, arranque = ARRANQUE_DESCONOCIDO), ARRANQUE_DESCONOCIDO, 5_000))
        assertNull(ReglaDelEnvio.msDesdeElFichaje(fichaje(TipoFichaje.INICIO, elapsed = 9_000), 5, 5_000))
    }

    @Test
    fun `una entrada sin red abre una jornada que solo existe en el movil`() {
        val jornada = ReglaDelEnvio.jornadaConCola(null, listOf(fichaje(TipoFichaje.INICIO)))

        assertEquals(JORNADA_SOLO_EN_EL_MOVIL, jornada?.id)
        assertEquals("2026-10-26T07:00:00Z", jornada?.horaEntrada)
    }

    @Test
    fun `una salida sin red cierra la jornada del servidor`() {
        assertNull(ReglaDelEnvio.jornadaConCola(abierta, listOf(fichaje(TipoFichaje.FIN))))
    }

    @Test
    fun `una jornada cerrada en el servidor no cuenta como abierta`() {
        assertNull(ReglaDelEnvio.jornadaConCola(abierta.copy(horaSalida = "2026-10-26T08:00:00Z"), emptyList()))
    }

    @Test
    fun `las pausas sin red se descuentan del cronometro`() {
        val enPausa = ReglaDelEnvio.jornadaConCola(abierta, listOf(fichaje(TipoFichaje.PAUSA_INICIO, "2026-10-26T07:00:00Z")))
        assertTrue(enPausa?.enPausa == true)

        val reanudada = ReglaDelEnvio.jornadaConCola(
            abierta,
            listOf(
                fichaje(TipoFichaje.PAUSA_INICIO, "2026-10-26T07:00:00Z"),
                fichaje(TipoFichaje.PAUSA_FIN, "2026-10-26T07:30:00Z")
            )
        )
        assertFalse(reanudada?.enPausa == true)
        assertEquals(1_800L, reanudada?.segundosPausaAcumulados)
        assertEquals(9L, reanudada?.id)
    }

    @Test
    fun `una pausa sin red dice cuando empezo, y al reanudar se olvida`() {
        val enPausa = ReglaDelEnvio.jornadaConCola(abierta, listOf(fichaje(TipoFichaje.PAUSA_INICIO, "2026-10-26T07:00:00Z")))
        assertEquals("2026-10-26T07:00:00Z", enPausa?.inicioPausaActual)

        val reanudada = ReglaDelEnvio.jornadaConCola(
            abierta,
            listOf(fichaje(TipoFichaje.PAUSA_INICIO, "2026-10-26T07:00:00Z"), fichaje(TipoFichaje.PAUSA_FIN, "2026-10-26T07:30:00Z"))
        )
        assertNull(reanudada?.inicioPausaActual)
    }

    /** Antes no se descontaba: aquí no se sabía cuándo había empezado. */
    @Test
    fun `reanudar sin red una pausa que empezo con red la descuenta`() {
        val enPausaEnElServidor = abierta.copy(enPausa = true, segundosPausaAcumulados = 60, inicioPausaActual = "2026-10-26T07:00:00Z")

        val reanudada = ReglaDelEnvio.jornadaConCola(enPausaEnElServidor, listOf(fichaje(TipoFichaje.PAUSA_FIN, "2026-10-26T07:20:00Z")))

        assertFalse(reanudada?.enPausa == true)
        assertEquals(60L + 1_200L, reanudada?.segundosPausaAcumulados)
    }

    @Test
    fun `una jornada entera sin red acaba sin jornada abierta`() {
        assertNull(
            ReglaDelEnvio.jornadaConCola(
                null,
                listOf(fichaje(TipoFichaje.INICIO), fichaje(TipoFichaje.PAUSA_INICIO), fichaje(TipoFichaje.PAUSA_FIN), fichaje(TipoFichaje.FIN))
            )
        )
    }
}
