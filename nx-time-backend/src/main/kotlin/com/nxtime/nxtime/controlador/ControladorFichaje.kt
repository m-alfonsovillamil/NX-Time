package com.nxtime.nxtime.controlador

import com.nxtime.nxtime.dominio.Registros
import com.nxtime.nxtime.dto.PeticionFichaje
import com.nxtime.nxtime.dto.RegistroEquipoDTO
import com.nxtime.nxtime.servicio.ServicioFichaje
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controlador REST para gestionar el registro horario.
 */

@RestController
@RequestMapping("/api/v1/fichaje")
class ControladorFichaje(

    private val servicioFichaje: ServicioFichaje
) {

    /**
     * Endpoint para INICIAR/FINALIZAR jornada o PAUSAS.
     */
    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @PostMapping
    fun registrarFichaje(
        @RequestBody peticion: PeticionFichaje,
        authentication: Authentication
    ): ResponseEntity<Registros> {

        val registro = servicioFichaje.registrarFichaje(authentication.name, peticion)
        return ResponseEntity.ok(registro)
    }

    /**
     * Endpoint para que la app sepa el estado actual del usuario
     */
    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @GetMapping("/activo")
    fun getEstadoJornada(authentication: Authentication): ResponseEntity<Registros?> {

        val registro = servicioFichaje.getRegistroActivo(authentication.name)

        if (registro == null) {

            return ResponseEntity.noContent().build()
        }

        return ResponseEntity.ok(registro)
    }

    /**
     * Endpoint para que el empleado vea su propio historial.
     */
    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @GetMapping("/historial")
    fun getHistorial(authentication: Authentication): ResponseEntity<List<Registros>> {
        val historial = servicioFichaje.getHistorial(authentication.name)
        return ResponseEntity.ok(historial)
    }

    /**
     * Endpoint para que el GESTOR vea el historial de todo su equipo.
     */
    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/gestor/historial")
    fun getHistorialEquipo(authentication: Authentication): ResponseEntity<List<RegistroEquipoDTO>> {
        val historialEquipo = servicioFichaje.getHistorialEquipo(authentication.name)
        return ResponseEntity.ok(historialEquipo)
    }
}