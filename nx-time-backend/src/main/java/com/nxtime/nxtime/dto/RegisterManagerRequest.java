package com.nxtime.nxtime.dto;

/**
 * DTO para recibir la petición de registro de un nuevo Gestor y Empresa.
 */
public record RegisterManagerRequest(String nombreEmpresa, String nombreGestor, String email, String password) {
}
