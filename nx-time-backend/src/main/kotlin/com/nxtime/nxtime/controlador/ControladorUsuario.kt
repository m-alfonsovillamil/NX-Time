package com.nxtime.nxtime.controlador

import com.nxtime.nxtime.dominio.Usuario
import com.nxtime.nxtime.dto.CambiarContrasenaRequest
import com.nxtime.nxtime.servicio.ServicioAutenticacion
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controlador para la gestión del perfil de usuario
 */

@RestController
@RequestMapping("/api/v1/usuario")
class ControladorUsuario(
    private val servicioAutenticacion: ServicioAutenticacion
) {

    /**
     * Endpoint para que un usuario (Empleado O Gestor) cambie su contraseña.
     */

    @PostMapping("/cambiar-contrasena")
    @PreAuthorize("isAuthenticated()")
    fun cambiarContrasena(
        @RequestBody peticion: CambiarContrasenaRequest,
        @AuthenticationPrincipal usuario: Usuario
    ): ResponseEntity<Unit> {

        servicioAutenticacion.cambiarContrasena(peticion, usuario)
        return ResponseEntity.ok().build()
    }
}