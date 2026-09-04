package com.nxtime.nxtime.dto;

/**
 * Un departamento y cuánta gente tiene dentro.
 *
 * {@code empleados} viaja con el listado porque es lo que decide si el
 * botón de borrar puede ofrecerse: un departamento con plantilla no se
 * puede borrar, y enterarse por un 409 después de pulsar es peor que no
 * poder pulsar.
 */
public record DepartmentResponse(long id, String nombre, long empleados) {
}
