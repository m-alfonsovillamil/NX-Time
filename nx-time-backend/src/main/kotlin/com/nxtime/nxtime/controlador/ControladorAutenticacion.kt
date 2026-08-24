package com.nxtime.nxtime.controlador

import com.nxtime.nxtime.dto.PeticionLogin
import com.nxtime.nxtime.dto.RegistroGestorRequest
import com.nxtime.nxtime.dto.RespuestaAutenticacion
import com.nxtime.nxtime.servicio.ServicioAutenticacion
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controlador que gestiona el acceso público (Login y Registro).
 */

@RestController
@RequestMapping("/auth")
class ControladorAutenticacion(

    private val servicioAutenticacion: ServicioAutenticacion
) {

    /**
     * Recibe los datos de registro del gestor desde la app.
     */
    @PostMapping("/register-manager")
    fun registrarGestor(
        @RequestBody peticion: RegistroGestorRequest
    ): ResponseEntity<RespuestaAutenticacion> {

        return ResponseEntity.ok(servicioAutenticacion.registrarGestor(peticion))
    }

    /**
     * Recibe el email y contraseña desde la app.
     */
    @PostMapping("/login")
    fun login(
        @RequestBody peticion: PeticionLogin
    ): ResponseEntity<RespuestaAutenticacion> {

        return ResponseEntity.ok(servicioAutenticacion.login(peticion))
    }
}