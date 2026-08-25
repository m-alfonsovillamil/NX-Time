package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Role;

/**
 * DTO que el backend devuelve tras un login o registro exitoso.
 *
 * refreshToken desde la Fase 4: el access token (campo "token") dura
 * poco (15 min); refreshToken es de larga duración y sirve para pedir
 * uno nuevo por /auth/refresh sin volver a pedir contraseña -- ver
 * RefreshToken.
 */
public record AuthenticationResponse(String token, String refreshToken, String nombre, Role rol) {
}
