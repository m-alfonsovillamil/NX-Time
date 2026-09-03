package com.nxtime.app.ui.gestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Los meses que se ofrecen para pedir un informe.
 *
 * Se prueba con una fecha fija y no con "hoy": un test que dependa del
 * día en que se ejecute vuelve a caer en el fallo del test del dashboard
 * que se rompía según el día del mes (ver PR #2).
 */
class MesesDisponiblesTest {

    @Test
    fun `se ofrecen el mes actual y los once anteriores`() {
        val meses = PanelEmpresaViewModel.mesesDisponibles(LocalDate.of(2026, 9, 3))

        assertEquals(12, meses.size)
        assertEquals(YearMonth.of(2026, 9), meses.first())
        assertEquals(YearMonth.of(2025, 10), meses.last())
    }

    /**
     * Hacia adelante no hay nada que informar, y ofrecerlo invitaría a
     * pedir un informe vacío.
     */
    @Test
    fun `no se ofrece ningun mes futuro`() {
        val hoy = LocalDate.of(2026, 9, 3)
        val meses = PanelEmpresaViewModel.mesesDisponibles(hoy)

        assertTrue(meses.all { !it.isAfter(YearMonth.from(hoy)) })
    }

    /** El cambio de año no debe partir la lista. */
    @Test
    fun `la lista cruza el cambio de año sin saltarse meses`() {
        val meses = PanelEmpresaViewModel.mesesDisponibles(LocalDate.of(2026, 1, 15))

        assertEquals(YearMonth.of(2026, 1), meses.first())
        assertEquals(YearMonth.of(2025, 2), meses.last())
        // Consecutivos y sin repetidos.
        meses.zipWithNext { actual, anterior ->
            assertEquals(anterior, actual.minusMonths(1))
        }
    }
}
