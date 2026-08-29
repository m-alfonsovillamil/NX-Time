package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryCorrectionRequest;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio para fichar y consultar fichajes.
 *
 * `peticion.tipo` ya no es un String libre sino el enum
 * TimeEntryAction (desde la Fase 1), así que la rama "acción no
 * válida" del `when` original ya no puede darse aquí: un valor
 * inválido lo rechaza Jackson al deserializar.
 *
 * horaEntrada/horaSalida/inicioPausaActual son Instant desde la
 * Fase 3 (antes LocalDateTime): un fichaje es un instante concreto,
 * no una fecha-hora sin zona (ver TimeEntry).
 *
 * Desde la Fase 8, cada cambio de estado (fichar o corregir) publica
 * un {@link TimeEntryAuditEvent}: quien lo persiste de verdad,
 * calculando el encadenamiento de hashes, es {@link
 * com.nxtime.nxtime.audit.TimeEntryAuditListener}, no este servicio
 * -- ver ese listener para el porqué (BEFORE_COMMIT, misma
 * transacción que el fichaje).
 */
@Service
@Transactional(readOnly = true)
public class TimeEntryServiceImpl implements TimeEntryService {

    private static final Logger log = LoggerFactory.getLogger(TimeEntryServiceImpl.class);

    /** Límite de filas de los listados de historial (ver auditoría: antes no había ninguno). */
    private static final int HISTORY_PAGE_SIZE = 200;

    private final TimeEntryRepository timeEntryRepository;
    private final TimeEntryAuditRepository timeEntryAuditRepository;
    private final UserRepository userRepository;
    private final TimeEntryMapper timeEntryMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TimeEntrySnapshotSerializer snapshotSerializer;

    public TimeEntryServiceImpl(
            TimeEntryRepository timeEntryRepository,
            TimeEntryAuditRepository timeEntryAuditRepository,
            UserRepository userRepository,
            TimeEntryMapper timeEntryMapper,
            ApplicationEventPublisher eventPublisher,
            TimeEntrySnapshotSerializer snapshotSerializer
    ) {
        this.timeEntryRepository = timeEntryRepository;
        this.timeEntryAuditRepository = timeEntryAuditRepository;
        this.userRepository = userRepository;
        this.timeEntryMapper = timeEntryMapper;
        this.eventPublisher = eventPublisher;
        this.snapshotSerializer = snapshotSerializer;
    }

    // Desde la Fase 3 (PostgreSQL + IDENTITY) esto SÍ es una transacción
    // normal (ver el comentario homólogo en AuthServiceImpl.registerManager
    // sobre por qué antes no lo era).
    @Override
    @Transactional
    public TimeEntry registerTimeEntry(String userEmail, TimeEntryRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + userEmail));

        TimeEntry activeEntry = timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user).orElse(null);
        // Instantánea de "antes" para la auditoría: se toma ya, antes de
        // que ninguna de las ramas de abajo mute activeEntry.
        String beforeJson = (activeEntry != null) ? toJson(activeEntry) : null;

        AuditAction accion;
        TimeEntry result = switch (request.tipo()) {
            case INICIO -> {
                if (activeEntry != null) {
                    throw new BusinessException("Ya hay una jornada activa.");
                }
                TimeEntry newEntry = TimeEntry.builder()
                        .usuario(user)
                        .empresa(user.getEmpresa())
                        .horaEntrada(Instant.now())
                        .build();
                accion = AuditAction.CREACION;
                yield timeEntryRepository.save(newEntry);
            }
            case FIN -> {
                if (activeEntry == null) {
                    throw new BusinessException("No hay jornada activa para finalizar.");
                }
                if (activeEntry.isEnPausa()) {
                    throw new BusinessException("No se puede finalizar la jornada mientras está en pausa.");
                }
                activeEntry.setHoraSalida(Instant.now());
                accion = AuditAction.MODIFICACION;
                yield timeEntryRepository.save(activeEntry);
            }
            case PAUSA_INICIO -> {
                if (activeEntry == null) {
                    throw new BusinessException("No hay jornada activa para pausar.");
                }
                if (activeEntry.isEnPausa()) {
                    throw new BusinessException("La jornada ya está en pausa.");
                }
                activeEntry.setEnPausa(true);
                activeEntry.setInicioPausaActual(Instant.now());
                accion = AuditAction.MODIFICACION;
                yield timeEntryRepository.save(activeEntry);
            }
            case PAUSA_FIN -> {
                if (activeEntry == null) {
                    throw new BusinessException("No hay jornada activa.");
                }
                if (!activeEntry.isEnPausa()) {
                    throw new BusinessException("La jornada no está en pausa.");
                }

                Instant inicioPausa = activeEntry.getInicioPausaActual();
                if (inicioPausa == null) {
                    // Invariante interna rota (no es un caso de negocio esperable): 500 genérico.
                    throw new IllegalStateException("No se encontró el inicio de la pausa para el fichaje " + activeEntry.getId());
                }

                Instant ahora = Instant.now();
                Duration duracionPausa = Duration.between(inicioPausa, ahora);

                activeEntry.setSegundosPausaAcumulados(activeEntry.getSegundosPausaAcumulados() + duracionPausa.getSeconds());
                activeEntry.setEnPausa(false);
                activeEntry.setInicioPausaActual(null);
                accion = AuditAction.MODIFICACION;

                yield timeEntryRepository.save(activeEntry);
            }
        };

        TimeEntryAudit auditRow = TimeEntryAudit.builder()
                .registro(result)
                .usuario(user)
                .modificadoPor(user)
                .accion(accion)
                .valorAnterior(beforeJson)
                .valorNuevo(toJson(result))
                .build();
        eventPublisher.publishEvent(new TimeEntryAuditEvent(auditRow));

        log.info("Fichaje {} registrado para {} (fichaje id={})", request.tipo(), userEmail, result.getId());
        return result;
    }

    @Override
    public Optional<TimeEntry> getActiveTimeEntry(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user);
    }

    @Override
    public List<TimeEntry> getHistory(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Pageable pageable = PageRequest.of(0, HISTORY_PAGE_SIZE);
        return timeEntryRepository.findHistoryByUsuario(user, pageable);
    }

    @Override
    public List<TeamTimeEntryDTO> getTeamHistory(String managerEmail) {
        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Gestor no encontrado con email: " + managerEmail));

        Company company = manager.getEmpresa();
        Pageable pageable = PageRequest.of(0, HISTORY_PAGE_SIZE);
        List<TimeEntry> companyEntries = timeEntryRepository.findTeamHistory(company, pageable);

        return companyEntries.stream().map(timeEntryMapper::toTeamDTO).toList();
    }

    // Fase 8: una corrección NUNCA sobrescribe horaEntrada/horaSalida en
    // la fila original -- eso destruiría el propio dato que la
    // auditoría existe para conservar. En su lugar, la original se
    // anula (registros.anulado = true) y se crea una fila nueva con los
    // valores correctos, enlazada por registro_original_id.
    @Override
    @Transactional
    public TimeEntry correctTimeEntry(String actorEmail, long timeEntryId, TimeEntryCorrectionRequest request) {
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + actorEmail));

        TimeEntry original = timeEntryRepository.findById(timeEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Fichaje no encontrado."));

        if (original.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes corregir fichajes de otra empresa.");
        }
        if (original.isAnulado()) {
            throw new BusinessException("Este fichaje ya fue corregido antes; corrige la nueva versión, no la original.");
        }
        if (original.getHoraSalida() == null) {
            throw new BusinessException("No se puede corregir una jornada activa; ciérrala primero.");
        }
        if (!request.horaSalida().isAfter(request.horaEntrada())) {
            throw new BusinessException(
                    "La hora de salida corregida debe ser posterior a la de entrada.", HttpStatus.BAD_REQUEST);
        }

        String beforeJson = toJson(original);

        TimeEntry correction = timeEntryRepository.save(TimeEntry.builder()
                .usuario(original.getUsuario())
                .empresa(original.getEmpresa())
                .horaEntrada(request.horaEntrada())
                .horaSalida(request.horaSalida())
                .registroOriginal(original)
                .build());

        original.setAnulado(true);
        timeEntryRepository.save(original);

        TimeEntryAudit auditRow = TimeEntryAudit.builder()
                .registro(original)
                .usuario(original.getUsuario())
                .modificadoPor(actor)
                .accion(AuditAction.CORRECCION)
                .valorAnterior(beforeJson)
                .valorNuevo(toJson(correction))
                .motivo(request.motivo())
                .build();
        eventPublisher.publishEvent(new TimeEntryAuditEvent(auditRow));

        log.info("Fichaje {} corregido por {} (fichaje corregido id={})", timeEntryId, actorEmail, correction.getId());
        return correction;
    }

    @Override
    public List<TimeEntryAudit> getAuditTrail(String actorEmail, long timeEntryId) {
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + actorEmail));

        TimeEntry entry = timeEntryRepository.findById(timeEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Fichaje no encontrado."));

        if (entry.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes ver la auditoría de fichajes de otra empresa.");
        }

        return timeEntryAuditRepository.findByRegistro_IdInOrderByFechaHoraAsc(cadenaDeCorrecciones(entry));
    }

    /**
     * Los ids de TODOS los fichajes que cuentan la historia de esta
     * jornada: el original, sus correcciones, y las correcciones de
     * estas.
     *
     * Sin esto la auditoría se partía en dos justo en el caso para el
     * que existe. Una corrección (Fase 8) no sobrescribe: anula el
     * fichaje original y crea uno nuevo, así que la traza se queda bajo
     * el id ANULADO -- que es precisamente el que el historial oculta.
     * Preguntar por el fichaje válido, el único que se ve en el
     * historial y en el informe, devolvía una lista VACÍA: el registro
     * aparecía sin procedencia ante quien viniera a comprobarla.
     */
    private List<Long> cadenaDeCorrecciones(TimeEntry entry) {
        TimeEntry raiz = entry;
        while (raiz.getRegistroOriginal() != null) {
            raiz = raiz.getRegistroOriginal();
        }

        List<Long> ids = new ArrayList<>();
        for (TimeEntry actual = raiz; actual != null;
                actual = timeEntryRepository.findByRegistroOriginal_Id(actual.getId()).orElse(null)) {
            ids.add(actual.getId());
        }
        return ids;
    }

    // Delegado en TimeEntrySnapshotSerializer desde la Fase 9: el
    // cierre automático de jornadas olvidadas necesita generar
    // instantáneas con exactamente la misma forma que estas.
    private String toJson(TimeEntry entry) {
        return snapshotSerializer.toJson(entry);
    }
}
