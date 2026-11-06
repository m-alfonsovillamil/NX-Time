package com.nxtime.app.data.sinconexion

import com.nxtime.app.data.dto.AvisoDeEnvio
import com.nxtime.app.data.dto.ErrorDelEnvio
import com.nxtime.app.data.dto.FichajePendiente
import com.nxtime.app.data.dto.Registro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** La cola sin Android, para los tests en la JVM. */
class ColaEnMemoria : ColaDeFichajes {
    val fichajes = mutableListOf<FichajePendiente>()
    val todosLosAvisos = mutableListOf<AvisoDeEnvio>()
    private val jornadas = mutableMapOf<String, Registro?>()
    private val _cambios = MutableStateFlow(0L)
    override val cambios: StateFlow<Long> = _cambios

    override fun pendientes(dueno: String) = fichajes.filter { it.dueno == dueno }

    override fun anadir(fichaje: FichajePendiente): Boolean {
        if (pendientes(fichaje.dueno).size >= ColaDeFichajes.MAXIMO) return false
        fichajes += fichaje
        return true
    }

    override fun quitar(ids: Set<String>) {
        fichajes.removeAll { it.idCliente in ids }
        _cambios.value += 1
    }

    override fun ultimaJornada(dueno: String) = jornadas[dueno]

    override fun guardarUltimaJornada(dueno: String, jornada: Registro?) {
        jornadas[dueno] = jornada
    }

    override fun avisos(dueno: String) = todosLosAvisos.filter { it.dueno == dueno }

    override fun anadirAvisos(avisos: List<AvisoDeEnvio>) {
        todosLosAvisos += avisos
        _cambios.value += 1
    }

    override fun quitarAviso(idCliente: String) {
        todosLosAvisos.removeAll { it.idCliente == idCliente }
        _cambios.value += 1
    }

    val errores = mutableMapOf<String, ErrorDelEnvio>()

    override fun errorDelEnvio(dueno: String) = errores[dueno]

    override fun guardarErrorDelEnvio(dueno: String, error: ErrorDelEnvio?) {
        if (error == null) errores.remove(dueno) else errores[dueno] = error
        _cambios.value += 1
    }
}

/** El móvil, con la hora y la red que diga cada test. */
class EntornoFalso(
    var red: Boolean = true,
    var hora: Instant = Instant.parse("2026-10-26T07:00:00Z"),
    var elapsed: Long = 1_000_000,
    var arranqueActual: Int = 5
) : EntornoDelMovil {
    var envios = 0

    override fun hayRed() = red
    override fun ahora() = hora
    override fun elapsedMs() = elapsed
    override fun arranque() = arranqueActual
    override fun lanzarEnvio() {
        envios++
    }
}
