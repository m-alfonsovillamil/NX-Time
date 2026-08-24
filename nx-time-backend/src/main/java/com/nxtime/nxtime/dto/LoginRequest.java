package com.nxtime.nxtime.dto;

/**
 * La app envía esto al backend al pulsar el botón de login.
 */
public record LoginRequest(String email, String contrasena) {
}
