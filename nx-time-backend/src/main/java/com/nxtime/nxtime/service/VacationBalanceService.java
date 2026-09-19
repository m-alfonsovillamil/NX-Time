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
     * Días hábiles de vacaciones ya APROBADAS de ese año, sin contar lo
     * pendiente.
     *
     * Es lo que hay que mirar al aprobar una petición: en ese momento lo
     * pendiente deja de ser una reserva prudente y la pregunta es la
     * definitiva, cuántos días se han concedido de verdad.
     */
    int contarDiasAprobados(User usuario, int anio);
}
