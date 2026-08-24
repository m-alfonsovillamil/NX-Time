package com.nxtime.nxtime.controlador

import com.nxtime.nxtime.dominio.EstadoAusencia
import com.nxtime.nxtime.dto.PeticionAusenciaDTO
import com.nxtime.nxtime.dto.RespuestaAusencia
import com.nxtime.nxtime.servicio.ServicioAusencia
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * Controlador REST que gestiona las operaciones de vacaciones y bajas.
 */

@RestController
@RequestMapping("/api/v1/ausencias")
class ControladorAusencia(

    private val servicioAusencia: ServicioAusencia
) {

    /**
     * Endpoint para que un empleado/gestor cree una solicitud.
     */
    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @PostMapping
    fun solicitarAusencia(
        @RequestBody peticionDTO: PeticionAusenciaDTO,
        authentication: Authentication
    ): ResponseEntity<RespuestaAusencia> {

        val peticionCreada = servicioAusencia.crearPeticion(authentication.name, peticionDTO)
        return ResponseEntity.ok(peticionCreada)
    }

    /**
     * Endpoint para que un empleado/gestor vea sus propias peticiones.
     */
    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @GetMapping("/mis-peticiones")
    fun getMisPeticiones(authentication: Authentication): ResponseEntity<List<RespuestaAusencia>> {
        val peticiones = servicioAusencia.getMisPeticiones(authentication.name)
        return ResponseEntity.ok(peticiones)
    }

    /**
     * Endpoint para que un GESTOR vea las peticiones pendientes de su equipo.
     */
    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/gestor/pendientes")
    fun getPeticionesPendientesGestor(authentication: Authentication): ResponseEntity<List<RespuestaAusencia>> {
        val peticiones = servicioAusencia.getPeticionesPendientes(authentication.name)
        return ResponseEntity.ok(peticiones)
    }

    /**
     * Endpoint para que un GESTOR apruebe una petición.
     */
    @PreAuthorize("hasRole('GESTOR')")
    @PostMapping("/gestor/aprobar/{id}")
    fun aprobarPeticion(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<RespuestaAusencia> {
        val peticion = servicioAusencia.cambiarEstadoPeticion(authentication.name, id, EstadoAusencia.APROBADA)
        return ResponseEntity.ok(peticion)
    }

    /**
     * Endpoint para que un GESTOR rechace una petición.
     */
    @PreAuthorize("hasRole('GESTOR')")
    @PostMapping("/gestor/rechazar/{id}")
    fun rechazarPeticion(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<RespuestaAusencia> {
        val peticion = servicioAusencia.cambiarEstadoPeticion(authentication.name, id, EstadoAusencia.RECHAZADA)
        return ResponseEntity.ok(peticion)
    }
}