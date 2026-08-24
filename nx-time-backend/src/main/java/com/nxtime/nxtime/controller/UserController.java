package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador para la gestión del perfil de usuario.
 */
@RestController
@RequestMapping("/api/v1/usuario")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/cambiar-contrasena")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest request, @AuthenticationPrincipal User user) {
        authService.changePassword(request, user);
        return ResponseEntity.ok().build();
    }
}
