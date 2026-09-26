package com.nxtime.app.push

import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Cuándo se registra el móvil para los push y cuándo se olvida (Fase B5).
 * Sin Android ni Firebase: los dos van detrás de interfaces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistroDePushTest {

    private class Preferencias(override var activo: Boolean = false, override var token: String? = null) :
        PreferenciasDePush {
        override fun cambiar(activo: Boolean) {
            this.activo = activo
        }
    }

    private class Firebase(var tokenQueDa: String? = "token-1") : TokensDePush {
        var activado = false
        var olvidos = 0
        override fun activar() {
            activado = true
        }

        override suspend fun token(): String? = tokenQueDa
        override fun olvidar() {
            olvidos++
        }
    }

    private val repositorio: AuthRepository = mock()
    private val preferencias = Preferencias()
    private val firebase = Firebase()
    private var sesion = true

    private fun TestScope.registro() =
        RegistroDePush(preferencias, { sesion }, { repositorio }, firebase, this)

    @Test
    fun `con el ajuste apagado no se registra nada, ni se despierta a Firebase`() = runTest {
        registro().registrarSiToca()
        advanceUntilIdle()

        verify(repositorio, never()).registrarDispositivoPush(any())
        assertFalse(firebase.activado)
    }

    @Test
    fun `encender registra el token y lo guarda para poder darlo de baja`() = runTest {
        whenever(repositorio.registrarDispositivoPush("token-1")).thenReturn(Response.success(Unit))

        registro().encender()
        advanceUntilIdle()

        assertTrue(preferencias.activo)
        assertTrue(firebase.activado)
        verify(repositorio).registrarDispositivoPush("token-1")
        assertEquals("token-1", preferencias.token)
    }

    @Test
    fun `si el servidor no lo acepta, no se guarda como registrado`() = runTest {
        whenever(repositorio.registrarDispositivoPush("token-1"))
            .thenReturn(Response.error(500, "{}".toResponseBody("application/json".toMediaType())))

        registro().encender()
        advanceUntilIdle()

        assertNull(preferencias.token)
    }

    @Test
    fun `sin sesion no se registra aunque el ajuste este encendido`() = runTest {
        preferencias.activo = true
        sesion = false

        registro().registrarSiToca()
        registro().tokenNuevo("token-2")
        advanceUntilIdle()

        verify(repositorio, never()).registrarDispositivoPush(any())
    }

    @Test
    fun `apagar da de baja el token guardado y lo olvida en Google`() = runTest {
        preferencias.activo = true
        preferencias.token = "token-1"
        whenever(repositorio.darDeBajaDispositivoPush("token-1")).thenReturn(Response.success(Unit))

        registro().apagar()
        advanceUntilIdle()

        assertFalse(preferencias.activo)
        assertNull(preferencias.token)
        verify(repositorio).darDeBajaDispositivoPush("token-1")
        assertEquals(1, firebase.olvidos)
    }

    @Test
    fun `apagar sin red olvida el token en Google igualmente`() = runTest {
        preferencias.activo = true
        preferencias.token = "token-1"
        whenever(repositorio.darDeBajaDispositivoPush("token-1")).thenThrow(RuntimeException("sin red"))

        registro().apagar()
        advanceUntilIdle()

        assertEquals(1, firebase.olvidos)
    }

    @Test
    fun `al cerrar sesion se olvida el token sin llamar al servidor, que ya no tiene sesion`() = runTest {
        preferencias.activo = true
        preferencias.token = "token-1"

        registro().alCerrarSesion()
        advanceUntilIdle()

        assertNull(preferencias.token)
        assertEquals(1, firebase.olvidos)
        verify(repositorio, never()).darDeBajaDispositivoPush(any())
        // El ajuste es del móvil, no de la cuenta: quien entre después lo encuentra igual.
        assertTrue(preferencias.activo)
    }

    @Test
    fun `al cerrar sesion en un movil que nunca tuvo push no se llama a Google`() = runTest {
        registro().alCerrarSesion()

        assertEquals(0, firebase.olvidos)
    }

    @Test
    fun `un push solo se ensena con el ajuste encendido y con sesion`() = runTest {
        val registro = registro()
        assertFalse(registro.debeEnsenar())
        preferencias.activo = true
        assertTrue(registro.debeEnsenar())
        sesion = false
        assertFalse(registro.debeEnsenar())
    }
}
