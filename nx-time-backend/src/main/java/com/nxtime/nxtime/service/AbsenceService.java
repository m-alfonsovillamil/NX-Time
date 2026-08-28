package com.nxtime.nxtime.service;

import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.UpdateAbsenceStatusRequest;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import java.util.List;

public interface AbsenceService {

    AbsenceResponse createRequest(String email, AbsenceRequestDTO requestDTO);

    List<AbsenceResponse> getMyRequests(String email);

    List<AbsenceResponse> getPendingRequests(String managerEmail);

    /**
     * Aprueba o rechaza una petición PENDIENTE, dejando constancia de
     * quién lo hizo y cuándo (Fase 9). Sustituye a los dos métodos
     * aprobar/rechazar anteriores, que no registraban nada.
     */
    AbsenceResponse changeRequestStatus(String managerEmail, long requestId, UpdateAbsenceStatusRequest request);

    List<AbsenceResponse> getHistory(String managerEmail);

    /** Saldo de vacaciones del propio usuario para ese año (Fase 9). */
    VacationBalanceResponse getMyVacationBalance(String email, int anio);
}
