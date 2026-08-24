package com.nxtime.nxtime.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de prueba, sin protección explícita propia (solo el
 * authenticated() genérico de "/api/v1/**"). Se elimina en la Fase 2
 * (ver auditoría, defectos de diseño): se mantiene aquí para no alterar
 * el contrato durante la migración pura de lenguaje.
 */
@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    @GetMapping("/hola")
    public ResponseEntity<String> sayHello() {
        return ResponseEntity.ok("¡Hola! ¡Tu token JWT funciona!");
    }
}
