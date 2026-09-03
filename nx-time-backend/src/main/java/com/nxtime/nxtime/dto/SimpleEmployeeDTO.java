package com.nxtime.nxtime.dto;

/**
 * DTO simple para representar a un empleado en una lista.
 */
public record SimpleEmployeeDTO(
        long id,
        String nombre,
        String email,

        /**
         * Si la cuenta sigue de alta.
         *
         * La lista devuelve empleados activos e inactivos (dar de baja no
         * borra: ver {@code User.fechaBaja}), pero hasta ahora no decía
         * cuáles eran cuáles. Sin este campo, un cliente que ofrezca el
         * alta/baja de {@code PATCH /gestor/empleados/{id}/estado} pinta
         * un interruptor sin saber en qué posición va.
         */
        boolean activo
) {
}
