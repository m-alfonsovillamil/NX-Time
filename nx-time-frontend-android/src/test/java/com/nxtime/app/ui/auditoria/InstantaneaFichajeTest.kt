package com.nxtime.app.ui.auditoria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lectura de las instantáneas de auditoría.
 *
 * Los registros de auditoría son **inmutables** (hay un trigger en la
 * base que impide UPDATE y DELETE), así que la app tiene que poder leer
 * los que se escribieron hace años aunque la forma del JSON haya
 * cambiado desde entonces. Estos tests fijan que un campo ausente, un
 * JSON roto o un nulo no tiren la pantalla abajo: en un registro de
 * cumplimiento normativo, perder la traza entera por una línea rara
 * sería el peor resultado posible.
 */
class InstantaneaFichajeTest {

    private val completa = """
        {"id":42,"horaEntrada":"2026-09-03T07:00:00Z","horaSalida":"2026-09-03T15:00:00Z",
         "enPausa":false,"inicioPausaActual":null,"segundosPausaAcumulados":1600,
         "anulado":false,"jornadaIncompleta":false}
    """.trimIndent()

    @Test
    fun `saca las dos horas de la instantanea`() {
        val leida = leerInstantanea(completa)

        assertEquals("2026-09-03T07:00:00Z", leida?.horaEntrada)
        assertEquals("2026-09-03T15:00:00Z", leida?.horaSalida)
    }

    /** Una jornada abierta: la instantánea lleva `horaSalida` a null. */
    @Test
    fun `una hora de salida nula se lee como nula, no como la cadena null`() {
        val abierta = """{"id":42,"horaEntrada":"2026-09-03T07:00:00Z","horaSalida":null}"""

        val leida = leerInstantanea(abierta)

        assertEquals("2026-09-03T07:00:00Z", leida?.horaEntrada)
        assertNull(leida?.horaSalida)
    }

    /**
     * El caso que justifica no usar un DTO tipado: un registro escrito
     * por una versión anterior, sin los campos que hoy existen.
     */
    @Test
    fun `una instantanea vieja sin los campos de hoy no revienta`() {
        val vieja = """{"id":42}"""

        val leida = leerInstantanea(vieja)

        assertNull(leida?.horaEntrada)
        assertNull(leida?.horaSalida)
    }

    @Test
    fun `un json roto o vacio devuelve null en vez de tirar la pantalla`() {
        assertNull(leerInstantanea(null))
        assertNull(leerInstantanea(""))
        assertNull(leerInstantanea("   "))
        assertNull(leerInstantanea("esto no es json"))
        assertNull(leerInstantanea("[1,2,3]"))
    }
}
