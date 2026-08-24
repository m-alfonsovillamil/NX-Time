package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador que gestiona el acceso público (Login y Registro).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-manager")
    public ResponseEntity<AuthenticationResponse> registerManager(@RequestBody RegisterManagerRequest request) {
        return ResponseEntity.ok(authService.registerManager(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
