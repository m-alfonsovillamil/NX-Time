package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Role;

/**
 * DTO que el backend devuelve tras un login o registro exitoso.
 */
public record AuthenticationResponse(String token, String nombre, Role rol) {
}
