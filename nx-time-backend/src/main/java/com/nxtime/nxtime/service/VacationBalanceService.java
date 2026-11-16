package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.VacationBalanceResponse;

public interface VacationBalanceService {

    /**
     * Saldo de vacaciones del usuario en ese año. Los días consumidos se
     * calculan sobre sus peticiones APROBADAS, no se leen de un contador
     * guardado (ver {@link com.nxtime.nxtime.domain.VacationBalance}).
     */
    VacationBalanceResponse getBalance(User usuario, int anio);

    /**
     * El saldo de unas vacaciones que empiezan en {@code inicioDeLaAusencia}
     * (ADR 060): si es después de la fecha tope, lo arrastrado del año anterior
     * solo cuenta en lo que ya se había usado antes. Es lo que hay que mirar al
     * pedir y al aprobar; {@link #getBalance(User, int)} mira desde hoy.
     */
    VacationBalanceResponse getBalance(User usuario, int anio, java.time.LocalDate inicioDeLaAusencia);

    /**
     * Días hábiles de vacaciones ya APROBADAS de ese año, sin contar lo
     * pendiente.
     *
     * Es lo que hay que mirar al aprobar una petición: en ese momento lo
     * pendiente deja de ser una reserva prudente y la pregunta es la
     * definitiva, cuántos días se han concedido de verdad.
     */
    double contarDiasAprobados(User usuario, int anio);
}
