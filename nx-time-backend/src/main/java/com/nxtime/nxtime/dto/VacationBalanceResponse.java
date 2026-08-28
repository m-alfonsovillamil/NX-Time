package com.nxtime.nxtime.dto;

/**
 * Saldo de vacaciones de un empleado en un año (Fase 9).
 * {@code diasDisponibles} es siempre {@code diasTotales - diasConsumidos}:
 * viaja calculado para que el cliente no tenga que repetir la resta (ni
 * pueda equivocarse al hacerla).
 */
public record VacationBalanceResponse(
        int anio,
        int diasTotales,
        int diasConsumidos,
        int diasDisponibles
) {
}
