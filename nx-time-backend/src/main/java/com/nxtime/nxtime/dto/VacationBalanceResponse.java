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
 */
public record VacationBalanceResponse(
        int anio,
        int diasTotales,
        int diasConsumidos,
        int diasPendientes,
        int diasDisponibles
) {
}
