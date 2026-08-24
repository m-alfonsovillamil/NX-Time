package com.nxtime.nxtime.dto;

/**
 * DTO para recibir la petición de que un gestor cree otro gestor.
 */
public record CreateManagerRequest(String nombre, String email, String contrasena) {
}
