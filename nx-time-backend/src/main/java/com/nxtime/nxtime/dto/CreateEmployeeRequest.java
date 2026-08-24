package com.nxtime.nxtime.dto;

/**
 * DTO para recibir la petición de crear un nuevo Empleado.
 */
public record CreateEmployeeRequest(String nombre, String email, String contrasena) {
}
