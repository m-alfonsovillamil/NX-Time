package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.mapper.AbsenceMapper;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AbsenceService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migración 1:1 (Fase 1) mantenida aquí con la comprobación de rol
 * duplicada respecto al @PreAuthorize del controlador (ver auditoría,
 * defectos de diseño): sigue siendo redundante, pero no se toca en
 * esta fase -- el objetivo aquí es el manejo de errores, no eliminar
 * duplicación de lógica de autorización.
 */
@Service
@Transactional(readOnly = true)
public class AbsenceServiceImpl implements AbsenceService {

    private static final Logger log = LoggerFactory.getLogger(AbsenceServiceImpl.class);

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
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    // Sin transacción: inserta un AbsenceRequest nuevo con
    // GenerationType.TABLE (mismo problema de SQLite documentado en
    // AuthServiceImpl.registerManager).
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AbsenceResponse createRequest(String email, AbsenceRequestDTO requestDTO) {
        User user = getUser(email);

        if (requestDTO.fechaInicio().isAfter(requestDTO.fechaFin())) {
            throw new BusinessException("La fecha de inicio no puede ser posterior a la fecha de fin.", HttpStatus.BAD_REQUEST);
        }

        AbsenceRequest newRequest = AbsenceRequest.builder()
                .usuario(user)
                .fechaInicio(requestDTO.fechaInicio())
                .fechaFin(requestDTO.fechaFin())
                .tipo(requestDTO.tipo())
                .motivo(requestDTO.motivo())
                .build();

        AbsenceRequest saved = absenceRequestRepository.save(newRequest);
        log.info("Nueva petición de ausencia de {} ({} - {})", email, saved.getFechaInicio(), saved.getFechaFin());
        return absenceMapper.toResponse(saved);
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
    @Transactional
    public AbsenceResponse changeRequestStatus(String managerEmail, long requestId, AbsenceStatus newStatus) {
        User manager = getUser(managerEmail);
        if (manager.getRol() != Role.GESTOR) {
            throw new AccessDeniedException("Acción solo permitida para GESTORES.");
        }

        AbsenceRequest request = absenceRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Petición no encontrada."));

        if (request.getUsuario().getEmpresa().getId() != manager.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes modificar peticiones de otra empresa.");
        }

        if (request.getEstado() != AbsenceStatus.PENDIENTE) {
            throw new BusinessException("Solo se puede modificar una petición PENDIENTE.");
        }

        request.setEstado(newStatus);
        AbsenceRequest saved = absenceRequestRepository.save(request);
        log.info("Petición de ausencia {} cambiada a {} por {}", requestId, newStatus, managerEmail);
        return absenceMapper.toResponse(saved);
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
