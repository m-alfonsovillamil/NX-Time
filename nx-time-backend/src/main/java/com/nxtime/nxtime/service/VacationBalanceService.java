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
}
