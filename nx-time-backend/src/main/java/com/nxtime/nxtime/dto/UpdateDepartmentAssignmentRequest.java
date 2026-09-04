package com.nxtime.nxtime.dto;

/**
 * Cuerpo de {@code PATCH /api/v1/departamentos/empleados/{usuarioId}}.
 *
 * Aquí null **sí** es un valor con significado -- sacar a alguien del
 * departamento que tuviera --, al revés que en el resto de los PATCH del
 * proyecto. Por eso es un objeto con un campo y no un id suelto en la
 * ruta: `{"departamentoId": null}` se distingue de no mandar nada, y una
 * ruta `/departamentos/null/empleados/7` no se distinguiría de un error.
 */
public record UpdateDepartmentAssignmentRequest(Long departamentoId) {
}
