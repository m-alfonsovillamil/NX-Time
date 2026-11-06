package com.nxtime.app.data.sinconexion

import com.nxtime.app.data.dto.FichajePendiente
import com.nxtime.app.data.dto.FichajeSinConexion
import com.nxtime.app.data.dto.Registro
import com.nxtime.app.data.dto.TipoFichaje
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** `Settings.Global.BOOT_COUNT` si el móvil no lo da: entonces no se puede saber si se reinició. */
const val ARRANQUE_DESCONOCIDO = -1

/** Id de una jornada que todavía solo existe en el móvil: no se le puede añadir pausa ni cambiar proyecto. */
const val JORNADA_SOLO_EN_EL_MOVIL = 0L

/**
 * Las dos decisiones de fichar sin conexión, sin Android, para probarlas en la
 * JVM: como `ReglaDelRecordatorio`.
 */
object ReglaDelEnvio {

    /**
     * Cuánto hace que se fichó, con el reloj que no se puede cambiar a mano.
     * Null si el móvil se reinició, porque esa cuenta empieza de cero al arrancar:
     * el servidor usa entonces la hora del dispositivo, y lo deja dicho.
     */
    fun msDesdeElFichaje(fichaje: FichajePendiente, arranqueAhora: Int, elapsedAhora: Long): Long? {
        if (arranqueAhora == ARRANQUE_DESCONOCIDO || fichaje.arranqueAlFichar != arranqueAhora) return null
        return (elapsedAhora - fichaje.elapsedAlFichar).takeIf { it >= 0 }
    }

    /** Se calcula justo antes de mandarlo: lo que tarde en despertar el servidor no cuenta. */
    fun paraEnviar(fichaje: FichajePendiente, arranqueAhora: Int, elapsedAhora: Long) = FichajeSinConexion(
        idCliente = fichaje.idCliente,
        tipo = fichaje.tipo,
        proyectoId = fichaje.proyectoId,
        horaDelDispositivo = fichaje.horaDelDispositivo,
        msDesdeElFichaje = msDesdeElFichaje(fichaje, arranqueAhora, elapsedAhora),
        modalidad = fichaje.modalidad,
        ubicacion = fichaje.ubicacion
    )

    /**
     * Cómo queda la jornada: la última que dijo el servidor más lo que espera en
     * la cola, en orden. Null si no queda ninguna abierta.
     *
     * Las pausas hechas sin red se descuentan para que el cronómetro no las
     * cuente, también la que ya estaba abierta en el servidor: dice cuándo
     * empezó (`inicioPausaActual`).
     */
    fun jornadaConCola(servidor: Registro?, cola: List<FichajePendiente>): Registro? {
        var jornada = servidor?.takeIf { it.horaSalida == null }
        var inicioDePausa: Instant? = jornada?.takeIf { it.enPausa }?.inicioPausaActual?.let(::instanteONull)
        for (fichaje in cola) {
            val hora = fichaje.horaDelDispositivo
            when (fichaje.tipo) {
                TipoFichaje.INICIO -> {
                    jornada = Registro(
                        id = JORNADA_SOLO_EN_EL_MOVIL,
                        horaEntrada = hora,
                        horaSalida = null,
                        enPausa = false,
                        modalidad = fichaje.modalidad,
                        ubicacionEntrada = fichaje.ubicacion
                    )
                    inicioDePausa = null
                }
                TipoFichaje.PAUSA_INICIO -> {
                    jornada = jornada?.copy(enPausa = true, inicioPausaActual = hora)
                    inicioDePausa = Instant.parse(hora)
                }
                TipoFichaje.PAUSA_FIN -> {
                    val segundos = inicioDePausa?.let { Duration.between(it, Instant.parse(hora)).seconds } ?: 0
                    jornada = jornada?.copy(
                        enPausa = false,
                        inicioPausaActual = null,
                        segundosPausaAcumulados = (jornada?.segundosPausaAcumulados ?: 0) + segundos,
                        minutosPausaAcumulados = ((jornada?.segundosPausaAcumulados ?: 0) + segundos) / 60
                    )
                    inicioDePausa = null
                }
                TipoFichaje.FIN -> jornada = null
            }
        }
        return jornada
    }

    private fun instanteONull(texto: String): Instant? =
        try { Instant.parse(texto) } catch (e: DateTimeParseException) { null }
}
