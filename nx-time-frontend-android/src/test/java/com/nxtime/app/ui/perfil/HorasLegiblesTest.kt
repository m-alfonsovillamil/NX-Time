package com.nxtime.app.ui.perfil

import org.junit.Assert.assertEquals
import org.junit.Test

/** El perfil decía «40.0 horas»: el servidor manda la jornada como texto con punto. */
class HorasLegiblesTest {

    @Test
    fun `sin decimales de sobra y con coma`() {
        assertEquals("40", horasLegibles("40.0"))
        assertEquals("37,5", horasLegibles("37.5"))
        assertEquals("20", horasLegibles("20"))
    }

    @Test
    fun `lo que no es un numero se deja como viene`() {
        assertEquals("—", horasLegibles("—"))
    }
}
