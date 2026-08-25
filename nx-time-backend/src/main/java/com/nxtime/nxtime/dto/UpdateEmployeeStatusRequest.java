package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo del endpoint de alta/baja de un empleado (Fase 4). "activo"
 * en true reactiva a un empleado dado de baja; en false lo da de baja
 * (no se borra, ver User.fechaBaja).
 */
public record UpdateEmployeeStatusRequest(

        @NotNull(message = "El campo 'activo' es obligatorio.")
        Boolean activo
) {
}
