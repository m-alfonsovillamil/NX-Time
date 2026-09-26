package com.nxtime.nxtime.service;

import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.UpdateAbsenceStatusRequest;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.domain.User;
import java.time.LocalDate;
import java.util.List;

public interface AbsenceService {

    AbsenceResponse createRequest(String email, AbsenceRequestDTO requestDTO);


    /**
     * Mis ausencias de un periodo. Las dos fechas son de España y "hasta"
     * entra. Sin rango se comportan como siempre (lo manda todo), para no
     * romper la app ya instalada.
     */
    com.nxtime.nxtime.dto.PaginaDTO<AbsenceResponse> getMyRequests(String email, LocalDate desde, LocalDate hasta, org.springframework.data.domain.Pageable pagina);

    List<AbsenceResponse> getPendingRequests(String managerEmail);

    /** Cuántas peticiones esperan decisión, sin traerlas ni mapearlas (Fase A6). */
    long contarPendientes(User actor);

    /**
     * Aprueba o rechaza una petición PENDIENTE, dejando constancia de
     * quién lo hizo y cuándo (Fase 9). Sustituye a los dos métodos
     * aprobar/rechazar anteriores, que no registraban nada.
     */
    AbsenceResponse changeRequestStatus(String managerEmail, long requestId, UpdateAbsenceStatusRequest request);

    com.nxtime.nxtime.dto.PaginaDTO<AbsenceResponse> getHistory(
            String managerEmail, org.springframework.data.domain.Pageable pagina);

    /** Saldo de vacaciones del propio usuario para ese año (Fase 9). */
    VacationBalanceResponse getMyVacationBalance(String email, int anio);
}
