package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Desde la Fase 4 ya no duplica la comprobación de rol que hacía el
 * @PreAuthorize del controlador (ver auditoría, defectos de diseño,
 * y ManagerController/AbsenceController -- ahora comprueban authorities
 * granulares como "ausencia:aprobar", que GESTOR, RRHH y ADMIN tienen
 * los tres). Repetirla aquí con "!= Role.GESTOR" habría rechazado
 * incorrectamente a RRHH/ADMIN aunque su @PreAuthorize ya los hubiera
 * dejado pasar.
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

    // Desde la Fase 3 (PostgreSQL + IDENTITY) esto SÍ es una transacción
    // normal (ver el comentario homólogo en AuthServiceImpl.registerManager).
    @Override
    @Transactional
    public AbsenceResponse createRequest(String email, AbsenceRequestDTO requestDTO) {
        User user = getUser(email);

        if (requestDTO.fechaInicio().isAfter(requestDTO.fechaFin())) {
            throw new BusinessException("La fecha de inicio no puede ser posterior a la fecha de fin.", HttpStatus.BAD_REQUEST);
        }

        AbsenceRequest newRequest = AbsenceRequest.builder()
                .usuario(user)
                .empresa(user.getEmpresa())
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
        long companyId = manager.getEmpresa().getId();
        return absenceRequestRepository.findByEmpresa_IdAndEstado(companyId, AbsenceStatus.PENDIENTE)
                .stream().map(absenceMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public AbsenceResponse changeRequestStatus(String managerEmail, long requestId, AbsenceStatus newStatus) {
        User manager = getUser(managerEmail);

        AbsenceRequest request = absenceRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Petición no encontrada."));

        if (request.getEmpresa().getId() != manager.getEmpresa().getId()) {
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
        long companyId = manager.getEmpresa().getId();
        return absenceRequestRepository.findByEmpresa_IdAndEstadoIsNot(companyId, AbsenceStatus.PENDIENTE)
                .stream().map(absenceMapper::toResponse).toList();
    }
}
