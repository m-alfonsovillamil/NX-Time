package com.nxtime.nxtime.dto;

/**
 * Saldo de vacaciones de un empleado en un año (Fase 9).
 *
 * {@code diasDisponibles} es {@code diasTotales - diasConsumidos - diasPendientes}:
 * viaja calculado para que el cliente no tenga que repetir la resta (ni pueda
 * equivocarse al hacerla).
 *
 * Lo PENDIENTE cuenta, y va aparte a propósito. Descontarlo es lo correcto
 * --quien tiene quince días pedidos no los tiene libres para pedir otra
 * cosa--, pero esconderlo dentro de "consumidos" haría que a alguien le
 * faltaran días sin saber por qué. Son dos cifras distintas y se ven las dos.
 *
 * Los días llevan decimales desde el ADR 063: un medio día de vacaciones
 * consume 0,5. Siempre son múltiplos de 0,5, que en coma flotante son exactos.
 */
public record VacationBalanceResponse(
        int anio,
        double diasTotales,
        double diasConsumidos,
        double diasPendientes,
        double diasDisponibles,
        /* Los que vienen del año anterior y ya están dentro de diasTotales (ADR 060). */
        double diasArrastrados,
        /* Hasta cuándo se pueden usar los arrastrados, o null si no hay o ya caducaron. */
        java.time.LocalDate arrastreCaducaEl
) {
    /** Sin arrastre: como era antes de V57. */
    public VacationBalanceResponse(
            int anio, double diasTotales, double diasConsumidos, double diasPendientes, double diasDisponibles) {
        this(anio, diasTotales, diasConsumidos, diasPendientes, diasDisponibles, 0, null);
    }
}
