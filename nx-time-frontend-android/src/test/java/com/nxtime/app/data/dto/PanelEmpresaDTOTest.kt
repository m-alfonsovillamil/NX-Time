package com.nxtime.app.data.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `activo` de las horas por empleado: lo que llega con Gson, que es como lo lee
 * Retrofit. Gson no aplica los valores por defecto de Kotlin.
 */
class PanelEmpresaDTOTest {

    private fun leer(json: String) = Gson().fromJson(json, HorasEmpleadoDTO::class.java)

    @Test
    fun `un backend anterior no manda activo, y nadie sale de baja`() {
        // Con un Boolean a secas habría llegado false: todos «de baja».
        assertNull(leer("""{"usuarioId":1,"nombre":"Ana","minutos":60}""").activo)
    }

    @Test
    fun `con activo, llega tal cual`() {
        assertEquals(false, leer("""{"usuarioId":1,"nombre":"Ana","minutos":60,"activo":false}""").activo)
        assertEquals(true, leer("""{"usuarioId":1,"nombre":"Ana","minutos":60,"activo":true}""").activo)
    }
}
