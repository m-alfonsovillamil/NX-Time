package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.mapper.AbsenceMapper;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AbsenceService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Migración 1:1 de ServicioAusenciaImpl.kt, incluida la comprobación de
 * rol duplicada respecto al @PreAuthorize del controlador (ver
 * auditoría, defectos de diseño) -- no se limpia en esta fase.
 */
@Service
public class AbsenceServiceImpl implements AbsenceService {

    private final AbsenceRequestRepository absenceRequestRepository;
    private final UserRepository userRepository;
    private final AbsenceMapper absenceMapper;

    public AbsenceServiceImpl(
            AbsenceRequestRepository absenceRequestRepository,
            UserRepository userRepository,
            AbsenceMapper absenceMapper
    ) {
        this.absenceRequestRepository = absenceRequestRepository;
        this.userRepository = userRepository;
        this.absenceMapper = absenceMapper;
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con email: " + email));
    }

    @Override
    public AbsenceResponse createRequest(String email, AbsenceRequestDTO requestDTO) {
        User user = getUser(email);

        if (requestDTO.fechaInicio().isAfter(requestDTO.fechaFin())) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        }

        AbsenceRequest newRequest = AbsenceRequest.builder()
                .usuario(user)
                .fechaInicio(requestDTO.fechaInicio())
                .fechaFin(requestDTO.fechaFin())
                .tipo(requestDTO.tipo())
                .motivo(requestDTO.motivo())
                .build();

        return absenceMapper.toResponse(absenceRequestRepository.save(newRequest));
    }

    @Override
    public List<AbsenceResponse> getMyRequests(String email) {
        User user = getUser(email);
        return absenceRequestRepository.findByUsuario(user).stream().map(absenceMapper::toResponse).toList();
    }

    @Override
    public List<AbsenceResponse> getPendingRequests(String managerEmail) {
        User manager = getUser(managerEmail);
        if (manager.getRol() != Role.GESTOR) {
            throw new AccessDeniedException("Acción solo permitida para GESTORES.");
        }

        long companyId = manager.getEmpresa().getId();
        return absenceRequestRepository.findByUsuario_Empresa_IdAndEstado(companyId, AbsenceStatus.PENDIENTE)
                .stream().map(absenceMapper::toResponse).toList();
    }

    @Override
    public AbsenceResponse changeRequestStatus(String managerEmail, long requestId, AbsenceStatus newStatus) {
        User manager = getUser(managerEmail);
        if (manager.getRol() != Role.GESTOR) {
            throw new AccessDeniedException("Acción solo permitida para GESTORES.");
        }

        AbsenceRequest request = absenceRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Petición no encontrada."));

        if (request.getUsuario().getEmpresa().getId() != manager.getEmpresa().getId()) {
            throw new AccessDeniedException("No puedes modificar peticiones de otra empresa.");
        }

        if (request.getEstado() != AbsenceStatus.PENDIENTE) {
            throw new IllegalStateException("Solo se puede modificar una petición PENDIENTE.");
        }

        request.setEstado(newStatus);
        return absenceMapper.toResponse(absenceRequestRepository.save(request));
    }

    @Override
    public List<AbsenceResponse> getHistory(String managerEmail) {
        User manager = getUser(managerEmail);
        if (manager.getRol() != Role.GESTOR) {
            throw new AccessDeniedException("Acción solo permitida para GESTORES.");
        }

        long companyId = manager.getEmpresa().getId();
        return absenceRequestRepository.findByUsuario_Empresa_IdAndEstadoIsNot(companyId, AbsenceStatus.PENDIENTE)
                .stream().map(absenceMapper::toResponse).toList();
    }
}
