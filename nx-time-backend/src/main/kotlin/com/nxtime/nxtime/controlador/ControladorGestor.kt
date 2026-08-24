package com.nxtime.nxtime.controlador

import com.nxtime.nxtime.dominio.Usuario
import com.nxtime.nxtime.dto.CrearEmpleadoRequest
import com.nxtime.nxtime.dto.CrearGestorRequest
import com.nxtime.nxtime.dto.EmpleadoSimpleDTO
import com.nxtime.nxtime.dto.RespuestaAusencia
import com.nxtime.nxtime.servicio.ServicioAutenticacion
import com.nxtime.nxtime.servicio.ServicioAusencia
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controlador exclusivo para operaciones administrativas.
 */

@RestController
@RequestMapping("/api/v1/gestor")
class ControladorGestor(
    private val servicioAutenticacion: ServicioAutenticacion,
    private val servicioAusencia: ServicioAusencia
) {

    /**
     * Endpoint para que un GESTOR cree un nuevo empleado.
     */
    @PostMapping("/empleados")
    @PreAuthorize("hasRole('GESTOR')")
    fun crearEmpleado(
        @RequestBody peticion: CrearEmpleadoRequest,
        @AuthenticationPrincipal gestor: Usuario
    ): ResponseEntity<Unit> {
        servicioAutenticacion.crearEmpleado(peticion, gestor)
        return ResponseEntity.ok().build()
    }


    /**
     * Endpoint para que un GESTOR cree otro GESTOR
     */
    @PostMapping("/gestores")
    @PreAuthorize("hasRole('GESTOR')")
    fun crearGestor(
        @RequestBody peticion: CrearGestorRequest,
        @AuthenticationPrincipal gestor: Usuario
    ): ResponseEntity<Unit> {
        servicioAutenticacion.crearGestor(peticion, gestor)
        return ResponseEntity.ok().build()
    }


    /**
     * Endpoint para que el GESTOR obtenga la lista de sus empleados
     */
    @GetMapping("/mis-empleados")
    @PreAuthorize("hasRole('GESTOR')")
    fun getMisEmpleados(
        @AuthenticationPrincipal gestor: Usuario
    ): ResponseEntity<List<EmpleadoSimpleDTO>> {
        val empleados = servicioAutenticacion.getMisEmpleados(gestor)
        return ResponseEntity.ok(empleados)
    }

    /**
     * Endpoint para que el GESTOR vea el historial de ausencias (aprobadas/rechazadas).
     */
    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/ausencias-historial")
    fun getHistorialAusencias(authentication: Authentication): ResponseEntity<List<RespuestaAusencia>> {
        val historial = servicioAusencia.getHistorialAusencias(authentication.name)
        return ResponseEntity.ok(historial)
    }
}