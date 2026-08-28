package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.UpdateAbsenceStatusRequest;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.mapper.AbsenceMapper;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.service.VacationBalanceService;
import com.nxtime.nxtime.service.WorkingDayService;
import java.time.Instant;
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
 *
 * La Fase 9 añade las reglas de negocio que faltaban (ver auditoría):
 * solapamiento con otras ausencias vivas, saldo de vacaciones, días
 * hábiles reales (sin fines de semana ni festivos) y trazabilidad de
 * quién resolvió cada petición y cuándo.
 */
@Service
@Transactional(readOnly = true)
public class AbsenceServiceImpl implements AbsenceService {

    private static final Logger log = LoggerFactory.getLogger(AbsenceServiceImpl.class);

    private final AbsenceRequestRepository absenceRequestRepository;
    private final UserRepository userRepository;
    private final AbsenceMapper absenceMapper;
    private final WorkingDayService workingDayService;
    private final VacationBalanceService vacationBalanceService;

    public AbsenceServiceImpl(
            AbsenceRequestRepository absenceRequestRepository,
            UserRepository userRepository,
            AbsenceMapper absenceMapper,
            WorkingDayService workingDayService,
            VacationBalanceService vacationBalanceService
    ) {
        this.absenceRequestRepository = absenceRequestRepository;
        this.userRepository = userRepository;
        this.absenceMapper = absenceMapper;
        this.workingDayService = workingDayService;
        this.vacationBalanceService = vacationBalanceService;
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

        // Fase 9: antes no se comprobaba nada de esto (ver auditoría).
        comprobarSolapamiento(user, requestDTO);
        int diasHabiles = workingDayService.contarDiasHabiles(
                user.getEmpresa(), requestDTO.fechaInicio(), requestDTO.fechaFin());
        comprobarSaldoDeVacaciones(user, requestDTO, diasHabiles);

        AbsenceRequest newRequest = AbsenceRequest.builder()
                .usuario(user)
                .empresa(user.getEmpresa())
                .fechaInicio(requestDTO.fechaInicio())
                .fechaFin(requestDTO.fechaFin())
                .tipo(requestDTO.tipo())
                .motivo(requestDTO.motivo())
                .build();

        AbsenceRequest saved = absenceRequestRepository.save(newRequest);
        log.info("Nueva petición de ausencia de {} ({} - {}, {} días hábiles)",
                email, saved.getFechaInicio(), saved.getFechaFin(), diasHabiles);
        return toResponse(saved);
    }

    private void comprobarSolapamiento(User user, AbsenceRequestDTO requestDTO) {
        List<AbsenceRequest> solapadas =
                absenceRequestRepository.findSolapadas(user, requestDTO.fechaInicio(), requestDTO.fechaFin());
        if (!solapadas.isEmpty()) {
            AbsenceRequest primera = solapadas.get(0);
            throw new BusinessException(String.format(
                    "Ya tienes una ausencia %s del %s al %s que se solapa con estas fechas.",
                    primera.getEstado(), primera.getFechaInicio(), primera.getFechaFin()));
        }
    }

    private void comprobarSaldoDeVacaciones(User user, AbsenceRequestDTO requestDTO, int diasHabiles) {
        // El saldo solo aplica a VACACIONES: una baja médica o un
        // permiso por fallecimiento no consumen días de vacaciones, y
        // rechazarlos por "saldo insuficiente" sería absurdo.
        if (requestDTO.tipo() != AbsenceType.VACACIONES) {
            return;
        }

        if (diasHabiles == 0) {
            throw new BusinessException(
                    "Las fechas solicitadas no incluyen ningún día hábil.", HttpStatus.BAD_REQUEST);
        }

        // Se imputan al año de la fecha de INICIO: una petición a
        // caballo entre dos años es un caso de borde poco frecuente y
        // repartirla entre ambos saldos complicaría la regla sin aportar
        // gran cosa a este proyecto (queda anotado como simplificación
        // consciente, no como despiste).
        int anio = requestDTO.fechaInicio().getYear();
        VacationBalanceResponse saldo = vacationBalanceService.getBalance(user, anio);

        if (diasHabiles > saldo.diasDisponibles()) {
            throw new BusinessException(String.format(
                    "Saldo de vacaciones insuficiente para %d: pides %d días hábiles y te quedan %d.",
                    anio, diasHabiles, saldo.diasDisponibles()));
        }
    }

    @Override
    public List<AbsenceResponse> getMyRequests(String email) {
        User user = getUser(email);
        return absenceRequestRepository.findByUsuario(user).stream().map(this::toResponse).toList();
    }

    @Override
    public List<AbsenceResponse> getPendingRequests(String managerEmail) {
        User manager = getUser(managerEmail);
        long companyId = manager.getEmpresa().getId();
        return absenceRequestRepository.findByEmpresa_IdAndEstado(companyId, AbsenceStatus.PENDIENTE)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public AbsenceResponse changeRequestStatus(String managerEmail, long requestId, UpdateAbsenceStatusRequest request) {
        User manager = getUser(managerEmail);

        AbsenceRequest absenceRequest = absenceRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Petición no encontrada."));

        if (absenceRequest.getEmpresa().getId() != manager.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes modificar peticiones de otra empresa.");
        }

        if (absenceRequest.getEstado() != AbsenceStatus.PENDIENTE) {
            throw new BusinessException("Solo se puede modificar una petición PENDIENTE.");
        }

        if (request.estado() == AbsenceStatus.PENDIENTE) {
            throw new BusinessException(
                    "No se puede devolver una petición al estado PENDIENTE.", HttpStatus.BAD_REQUEST);
        }

        // Rechazar sin explicar por qué no es aceptable de cara al
        // empleado; aprobar sí puede ir sin comentario.
        if (request.estado() == AbsenceStatus.RECHAZADA
                && (request.comentario() == null || request.comentario().isBlank())) {
            throw new BusinessException(
                    "Es obligatorio indicar un motivo al rechazar una petición.", HttpStatus.BAD_REQUEST);
        }

        // Fase 9: antes solo cambiaba el estado -- no quedaba constancia
        // de quién resolvió la petición ni cuándo (ver auditoría).
        absenceRequest.setEstado(request.estado());
        absenceRequest.setAprobadoPor(manager);
        absenceRequest.setFechaResolucion(Instant.now());
        absenceRequest.setComentarioResolucion(request.comentario());

        AbsenceRequest saved = absenceRequestRepository.save(absenceRequest);
        log.info("Petición de ausencia {} cambiada a {} por {}", requestId, request.estado(), managerEmail);
        return toResponse(saved);
    }

    @Override
    public List<AbsenceResponse> getHistory(String managerEmail) {
        User manager = getUser(managerEmail);
        long companyId = manager.getEmpresa().getId();
        return absenceRequestRepository.findByEmpresa_IdAndEstadoIsNot(companyId, AbsenceStatus.PENDIENTE)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public VacationBalanceResponse getMyVacationBalance(String email, int anio) {
        return vacationBalanceService.getBalance(getUser(email), anio);
    }

    // Limitación conocida: en los listados, esto consulta el calendario
    // laboral una vez por petición (N+1 sobre "festivos"). Es el mismo
    // patrón que la auditoría del plan criticaba, así que conviene
    // decirlo en voz alta en vez de dejarlo escondido: aquí se acepta
    // porque el número de peticiones por empresa es pequeño (decenas) y
    // la tabla de festivos es diminuta. El arreglo natural, si algún día
    // molesta, es cachear los festivos por empresa y año con @Cacheable
    // (Fase 10 ya introduce Caffeine) en vez de complicar este método.
    private AbsenceResponse toResponse(AbsenceRequest request) {
        int diasHabiles = workingDayService.contarDiasHabiles(
                request.getEmpresa(), request.getFechaInicio(), request.getFechaFin());
        return absenceMapper.toResponse(request, diasHabiles);
    }
}
