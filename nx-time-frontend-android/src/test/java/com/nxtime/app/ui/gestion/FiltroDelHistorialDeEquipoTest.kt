package com.nxtime.app.ui.gestion

import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.data.dto.RegistroEquipoDTO
import com.nxtime.app.data.dto.UsuarioSimpleDTO
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El filtro por persona del historial del equipo.
 *
 * Se comparaba por nombre, y dos personas que se llamaran igual mezclaban sus
 * jornadas: al filtrar por una Laura salían las de las dos. Es el caso que
 * este test tiene delante.
 */
class FiltroDelHistorialDeEquipoTest {

    private fun jornada(id: Long, nombre: String, usuarioId: Long?) = RegistroEquipoDTO(
        id = id,
        horaEntrada = "2026-10-05T07:00:00Z",
        horaSalida = "2026-10-05T15:00:00Z",
        fecha = "2026-10-05",
        usuario = UsuarioSimpleDTO(nombre),
        usuarioId = usuarioId
    )

    private fun empleado(id: Long, nombre: String) =
        EmpleadoSimpleDTO(id = id, nombre = nombre, email = "persona$id@test.example")

    @Test
    fun `dos personas con el mismo nombre no mezclan sus jornadas`() {
        val estado = HistorialEquipoUiState(
            registros = listOf(jornada(1, "Laura", 10), jornada(2, "Laura", 11), jornada(3, "Pedro", 12)),
            empleadoFiltrado = empleado(11, "Laura")
        )

        assertEquals(listOf(2L), estado.registrosVisibles.map { it.id })
    }

    @Test
    fun `sin filtro se ven todas`() {
        val estado = HistorialEquipoUiState(
            registros = listOf(jornada(1, "Laura", 10), jornada(2, "Laura", 11))
        )

        assertEquals(listOf(1L, 2L), estado.registrosVisibles.map { it.id })
    }

    /** Un servidor anterior a `usuarioId`: se filtra como antes, que es mejor que no filtrar. */
    @Test
    fun `una jornada sin usuarioId se filtra por el nombre`() {
        val estado = HistorialEquipoUiState(
            registros = listOf(jornada(1, "Laura", null), jornada(2, "Pedro", null)),
            empleadoFiltrado = empleado(11, "Laura")
        )

        assertEquals(listOf(1L), estado.registrosVisibles.map { it.id })
    }
}
