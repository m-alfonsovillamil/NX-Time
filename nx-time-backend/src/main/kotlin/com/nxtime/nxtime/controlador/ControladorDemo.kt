package com.nxtime.nxtime.controlador

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controlador de prueba. Sirve solo para verificar que la seguridad y el Token funcionan correctamente.
 */

@RestController
@RequestMapping("/api/v1/demo")
class ControladorDemo {

    /**
     * Es una ruta protegida para probar que el token funciona.
     */

    @GetMapping("/hola")
    fun decirHola(): ResponseEntity<String> {

        return ResponseEntity.ok("¡Hola! ¡Tu token JWT funciona!")
    }
}