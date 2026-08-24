package com.nxtime.nxtime.dto

import com.nxtime.nxtime.dominio.EstadoAusencia
import com.nxtime.nxtime.dominio.PeticionAusencia
import com.nxtime.nxtime.dominio.TipoAusencia
import java.time.LocalDate

/*
 * DTO para enviar la información de una ausencia a la app.
 */
data class RespuestaAusencia(
    val id: Long,
    val fechaInicio: LocalDate,
    val fechaFin: LocalDate,
    val tipo: TipoAusencia,
    val motivo: String?,
    val estado: EstadoAusencia,
    val usuario: UsuarioSimpleDTO
)

/*
 * Función que usamos para convertir la entidad de la BBDD (PeticionAusencia) en el DTO que enviamos a la app (RespuestaAusencia).
 */
fun PeticionAusencia.toDTO(): RespuestaAusencia {
    return RespuestaAusencia(
        id = this.id,
        fechaInicio = this.fechaInicio,
        fechaFin = this.fechaFin,
        tipo = this.tipo,
        motivo = this.motivo,
        estado = this.estado,


        usuario = UsuarioSimpleDTO(
            nombre = this.usuario.nombre
        )
    )
}