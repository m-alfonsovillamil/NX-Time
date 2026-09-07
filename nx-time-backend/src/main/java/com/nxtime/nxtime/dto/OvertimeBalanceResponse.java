package com.nxtime.nxtime.dto;

/**
 * La bolsa anual de horas extra del art. 35.2 ET (Fase F).
 *
 * <b>No hay tabla detrás.</b> Esto se calcula sumando los avisos
 * ACEPTADO del año cada vez que se pide (ver
 * {@code OvertimeAlertRepository#sumarMinutosAceptados}), por la misma
 * razón que el saldo de vacaciones: un contador guardado se
 * desincroniza en cuanto una corrección de la Fase E cambia una jornada
 * del mes pasado.
 *
 * Va en minutos y no en horas decimales porque 1,75 h en una pantalla
 * se lee mal; que "1 h 45 min" se componga bien es cosa del cliente,
 * pero el dato tiene que llegarle sin haber perdido nada por el camino.
 *
 * {@code alLimite} viaja resuelto: el umbral de aviso (el 80 % del tope)
 * es una regla de negocio, no una decisión de presentación, y debe
 * significar lo mismo en la pantalla que en el proceso que notifica.
 */
public record OvertimeBalanceResponse(
        int anio,
        int minutosTope,
        int minutosConsumidos,
        int minutosDisponibles,
        /** Avisos todavía sin revisar: horas que PODRÍAN acabar contando. */
        long avisosAbiertos,
        boolean alLimite
) {
}
