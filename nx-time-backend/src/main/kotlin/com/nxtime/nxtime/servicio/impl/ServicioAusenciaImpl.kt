package com.nxtime.nxtime.servicio.impl

import com.nxtime.nxtime.dominio.EstadoAusencia
import com.nxtime.nxtime.dominio.PeticionAusencia
import com.nxtime.nxtime.dominio.Rol
import com.nxtime.nxtime.dto.PeticionAusenciaDTO
import com.nxtime.nxtime.dto.RespuestaAusencia
import com.nxtime.nxtime.dto.toDTO
import com.nxtime.nxtime.repositorio.PeticionAusenciaRepositorio
import com.nxtime.nxtime.repositorio.UsuarioRepositorio
import com.nxtime.nxtime.servicio.ServicioAusencia
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.util.NoSuchElementException

/*
 * Lógica de ServicioAusencia.
 */
@Service
class ServicioAusenciaImpl(

    private val peticionAusenciaRepositorio: PeticionAusenciaRepositorio,
    private val usuarioRepositorio: UsuarioRepositorio
) : ServicioAusencia {

    /*
     * Función privada de ayuda para buscar un usuario por email. Si no lo encuentra, lanza un error.
     */
    private fun getUsuario(email: String) = usuarioRepositorio.findByEmail(email)
        .orElseThrow { NoSuchElementException("Usuario no encontrado con email: $email") }


    /*
     * Lógica para crear una nueva petición de ausencia.
     */
    override fun crearPeticion(email: String, peticionDTO: PeticionAusenciaDTO): RespuestaAusencia {
        val usuario = getUsuario(email)

        if (peticionDTO.fechaInicio.isAfter(peticionDTO.fechaFin)) {
            throw IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin.")
        }
        val nuevaPeticion = PeticionAusencia(
            usuario = usuario,
            fechaInicio = peticionDTO.fechaInicio,
            fechaFin = peticionDTO.fechaFin,
            tipo = peticionDTO.tipo,
            motivo = peticionDTO.motivo
        )

        return peticionAusenciaRepositorio.save(nuevaPeticion).toDTO()
    }

    /*
     * Lógica para que un empleado vea sus propias peticiones.
     */
    override fun getMisPeticiones(email: String): List<RespuestaAusencia> {
        val usuario = getUsuario(email)

        return peticionAusenciaRepositorio.findByUsuario(usuario).map { it.toDTO() }
    }

    /*
     * Lógica para que un GESTOR vea las peticiones pendientes de su equipo.
     */
    override fun getPeticionesPendientes(emailGestor: String): List<RespuestaAusencia> {
        val gestor = getUsuario(emailGestor)

        if (gestor.rol != Rol.GESTOR) {
            throw AccessDeniedException("Acción solo permitida para GESTORES.")
        }
        val empresaId = gestor.empresa.id

        return peticionAusenciaRepositorio.findByUsuario_Empresa_IdAndEstado(empresaId, EstadoAusencia.PENDIENTE)
            .map { it.toDTO() }
    }

    /*
     * Lógica para que un GESTOR apruebe o rechace una petición.
     */
    override fun cambiarEstadoPeticion(emailGestor: String, peticionId: Long, nuevoEstado: EstadoAusencia): RespuestaAusencia {
        val gestor = getUsuario(emailGestor)

        if (gestor.rol != Rol.GESTOR) {
            throw AccessDeniedException("Acción solo permitida para GESTORES.")
        }


        val peticion = peticionAusenciaRepositorio.findById(peticionId)
            .orElseThrow { NoSuchElementException("Petición no encontrada.") }


        if (peticion.usuario.empresa.id != gestor.empresa.id) {
            throw AccessDeniedException("No puedes modificar peticiones de otra empresa.")
        }

        if (peticion.estado != EstadoAusencia.PENDIENTE) {
            throw IllegalStateException("Solo se puede modificar una petición PENDIENTE.")
        }


        peticion.estado = nuevoEstado
        return peticionAusenciaRepositorio.save(peticion).toDTO()
    }

    /*
     * Lógica para que un GESTOR vea el historial (Aprobadas/Rechazadas).
     */
    override fun getHistorialAusencias(emailGestor: String): List<RespuestaAusencia> {
        val gestor = getUsuario(emailGestor)

        if (gestor.rol != Rol.GESTOR) {
            throw AccessDeniedException("Acción solo permitida para GESTORES.")
        }
        val empresaId = gestor.empresa.id


        return peticionAusenciaRepositorio.findByUsuario_Empresa_IdAndEstadoIsNot(empresaId, EstadoAusencia.PENDIENTE)
            .map { it.toDTO() }
    }
}