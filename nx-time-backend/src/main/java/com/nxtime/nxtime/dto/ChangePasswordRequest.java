package com.nxtime.nxtime.dto;

/**
 * DTO para recibir la petición de cambio de contraseña de un usuario ya autenticado.
 */
public record ChangePasswordRequest(String contrasenaAntigua, String contrasenaNueva) {
}
