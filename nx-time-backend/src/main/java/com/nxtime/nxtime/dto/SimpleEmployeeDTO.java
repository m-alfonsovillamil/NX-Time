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
        boolean activo,

        /**
         * Jornada contractual semanal, en horas.
         *
         * Se lee desde la Fase 9 (para calcular el progreso semanal) pero
         * hasta la Fase A no había forma de escribirla: todo el mundo se
         * quedaba en las 40 h por defecto. Viaja en el listado para que
         * quien vaya a editarla vea el valor actual sin una petición más
         * por empleado.
         */
        java.math.BigDecimal horasSemanales,

        /**
         * Días de vacaciones EFECTIVOS del año en curso: la fila de
         * "saldo_vacaciones" si existe, y si no el valor por defecto de
         * {@code VacationBalanceServiceImpl.DIAS_POR_DEFECTO}.
         *
         * Efectivos y no "los de la fila" a propósito: antes del primer
         * PATCH no hay fila para casi nadie, y un null aquí obligaría a
         * cada cliente a repetir la regla del valor por defecto.
         */
        int diasVacaciones,

        /**
         * Departamento al que pertenece, o null. Viaja con el listado
         * porque el diálogo que edita la ficha necesita saber en cuál
         * está para precargar el selector, y la relación ya viene
         * cargada con el usuario: no cuesta ninguna consulta más.
         */
        Long departamentoId,

        String departamentoNombre
) {
}
