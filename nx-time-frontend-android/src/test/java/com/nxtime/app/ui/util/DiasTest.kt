package com.nxtime.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DiasTest {

    @Test
    fun `los dias se escriben sin decimal si son enteros y con coma si no`() {
        assertEquals("0", 0.0.comoDias())
        assertEquals("22", 22.0.comoDias())
        assertEquals("0,5", 0.5.comoDias())
        assertEquals("21,5", 21.5.comoDias())
    }
}
