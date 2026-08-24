package com.nxtime.nxtime.servicio

import com.nxtime.nxtime.dominio.EstadoAusencia
import com.nxtime.nxtime.dto.PeticionAusenciaDTO
import com.nxtime.nxtime.dto.RespuestaAusencia

/*
 * Define las operaciones de negocio relacionadas con las Ausencias.
 */
interface ServicioAusencia {

    /*
     * Define una función para que un usuario cree una petición de ausencia.
     */
    fun crearPeticion(email: String, peticionDTO: PeticionAusenciaDTO): RespuestaAusencia

    /*
     * Define una función para que un usuario vea su propia lista de peticiones.
     */
    fun getMisPeticiones(email: String): List<RespuestaAusencia>

    /*
     * Define una función para que un Gestor vea las peticiones pendientes.
     */
    fun getPeticionesPendientes(emailGestor: String): List<RespuestaAusencia>

    /*
     * Define una función para que un Gestor apruebe o rechace una petición.
     */
    fun cambiarEstadoPeticion(emailGestor: String, peticionId: Long, nuevoEstado: EstadoAusencia): RespuestaAusencia

    /*
     * Define una función para que un Gestor vea el historial de ausencias (aprobadas/rechazadas) de su equipo.
     */
    fun getHistorialAusencias(emailGestor: String): List<RespuestaAusencia>
}