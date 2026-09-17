package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.dto.ClockProjectsResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.notification.Destinatarios;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.service.NonWorkingDayService;
import com.nxtime.nxtime.service.ProjectAllocationService;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    /** Un año bisiesto entero. Ver {@link #getHistory(String, LocalDate, LocalDate)}. */
    static final int MAXIMO_DIAS_HISTORIAL = 366;

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final TimeEntryRepository timeEntryRepository;
    private final TimeEntryAuditRepository timeEntryAuditRepository;
    private final UserRepository userRepository;
    private final TimeEntryMapper timeEntryMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TimeEntrySnapshotSerializer snapshotSerializer;
    private final NonWorkingDayService nonWorkingDayService;
    private final ProjectAllocationService projectAllocationService;

    public TimeEntryServiceImpl(
            TimeEntryRepository timeEntryRepository,
            TimeEntryAuditRepository timeEntryAuditRepository,
            UserRepository userRepository,
            TimeEntryMapper timeEntryMapper,
            ApplicationEventPublisher eventPublisher,
            TimeEntrySnapshotSerializer snapshotSerializer,
            NonWorkingDayService nonWorkingDayService,
            ProjectAllocationService projectAllocationService
    ) {
        this.timeEntryRepository = timeEntryRepository;
        this.timeEntryAuditRepository = timeEntryAuditRepository;
        this.userRepository = userRepository;
        this.timeEntryMapper = timeEntryMapper;
        this.eventPublisher = eventPublisher;
        this.snapshotSerializer = snapshotSerializer;
        this.nonWorkingDayService = nonWorkingDayService;
        this.projectAllocationService = projectAllocationService;
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
        String motivoAuditoria = null;
        TimeEntry result = switch (request.tipo()) {
            case INICIO -> {
                if (activeEntry != null) {
                    throw new BusinessException("Ya hay una jornada activa.");
                }
                Project proyecto = proyectoAlIniciar(user, request.proyectoId());
                motivoAuditoria = proyecto != null ? "Proyecto: " + proyecto.getCodigo() : null;
                TimeEntry newEntry = TimeEntry.builder()
                        .usuario(user)
                        .empresa(user.getEmpresa())
                        .horaEntrada(Instant.now())
                        .build();
                accion = AuditAction.CREACION;
                avisarSiNoEsLaborable(user);
                TimeEntry creada = timeEntryRepository.save(newEntry);
                if (proyecto != null) {
                    projectAllocationService.abrirTramo(creada, proyecto);
                }
                yield creada;
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
                TimeEntry cerrada = timeEntryRepository.save(activeEntry);
                // Con la salida ya hay neto: se reparte entre proyectos (ADR 017).
                projectAllocationService.alCerrar(cerrada);
                yield cerrada;
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
                // La pausa cae entera en el tramo en curso: no se puede cambiar
                // de proyecto en pausa (ver cambiarProyecto).
                projectAllocationService.sumarPausaAlTramo(activeEntry, duracionPausa.getSeconds());
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
                .motivo(motivoAuditoria)
                .build();
        eventPublisher.publishEvent(new TimeEntryAuditEvent(auditRow));

        log.info("Fichaje {} registrado para {} (fichaje id={})", request.tipo(), userEmail, result.getId());
        return result;
    }

    /**
     * En qué proyecto empieza la jornada (ADR 017).
     *
     * <ul>
     *   <li>Con proyecto elegido: tiene que ser uno de los suyos de hoy (403).</li>
     *   <li>Sin elegir y con uno solo: ese.</li>
     *   <li>Sin elegir y con varios: sin proyecto. <b>No se rechaza el
     *       fichaje</b>: la app 1.4 siempre pregunta, pero alguien con la 1.3
     *       instalada no podría fichar en absoluto, y fichar es lo único que
     *       nunca puede quedar bloqueado. Esas horas quedan sin proyecto hasta
     *       que se cambie o se repartan.</li>
     * </ul>
     */
    private Project proyectoAlIniciar(User user, Long proyectoId) {
        List<Project> disponibles = projectAllocationService.proyectosParaFichar(user, LocalDate.now(MADRID));
        if (proyectoId != null) {
            return disponibles.stream()
                    .filter(p -> p.getId() == proyectoId)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "No tienes asignado ese proyecto hoy.", HttpStatus.FORBIDDEN));
        }
        return disponibles.size() == 1 ? disponibles.get(0) : null;
    }

    @Override
    public ClockProjectsResponse proyectosParaFichar(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return respuestaDeProyectos(user, timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user).orElse(null));
    }

    @Override
    @Transactional
    public ClockProjectsResponse cambiarProyecto(String userEmail, long registroId, long proyectoId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        TimeEntry registro = timeEntryRepository.findById(registroId)
                .orElseThrow(() -> new ResourceNotFoundException("Fichaje no encontrado."));
        if (registro.getUsuario().getId() != user.getId()) {
            throw new TenantAccessException("Solo puedes cambiar de proyecto en tu propia jornada.");
        }
        if (registro.getHoraSalida() != null) {
            throw new BusinessException("La jornada ya está cerrada. Para repartir sus horas entre proyectos, "
                    + "hazlo desde el historial.");
        }
        // En pausa no: el tramo que se cierra no sabría cuánto de esa pausa es suyo.
        if (registro.isEnPausa()) {
            throw new BusinessException("Reanuda la jornada antes de cambiar de proyecto.");
        }
        Project nuevo = projectAllocationService.proyectosParaFichar(user, LocalDate.now(MADRID)).stream()
                .filter(p -> p.getId() == proyectoId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("No tienes asignado ese proyecto hoy.", HttpStatus.FORBIDDEN));
        Project anterior = projectAllocationService.proyectoEnCurso(registro).orElse(null);
        if (anterior != null && anterior.getId() == nuevo.getId()) {
            throw new BusinessException("Ya estás trabajando en " + nuevo.getCodigo() + ".");
        }

        String antes = toJson(registro);
        projectAllocationService.cambiarDeProyecto(registro, nuevo, Instant.now());
        eventPublisher.publishEvent(new TimeEntryAuditEvent(TimeEntryAudit.builder()
                .registro(registro)
                .usuario(user)
                .modificadoPor(user)
                .accion(AuditAction.PROYECTO_CAMBIADO)
                .valorAnterior(antes)
                .valorNuevo(toJson(registro))
                .motivo(anterior != null
                        ? "De " + anterior.getCodigo() + " a " + nuevo.getCodigo()
                        : "Proyecto: " + nuevo.getCodigo())
                .build()));
        log.info("Usuario {} cambia de proyecto en el fichaje {}: {} -> {}",
                user.getId(), registroId, anterior != null ? anterior.getCodigo() : "-", nuevo.getCodigo());
        return respuestaDeProyectos(user, registro);
    }

    private ClockProjectsResponse respuestaDeProyectos(User user, TimeEntry abierta) {
        List<ClockProjectsResponse.ProjectOption> disponibles =
                projectAllocationService.proyectosParaFichar(user, LocalDate.now(MADRID)).stream()
                        .map(TimeEntryServiceImpl::opcion)
                        .toList();
        ClockProjectsResponse.ProjectOption enCurso = abierta == null ? null
                : projectAllocationService.proyectoEnCurso(abierta).map(TimeEntryServiceImpl::opcion).orElse(null);
        return new ClockProjectsResponse(disponibles, enCurso);
    }

    private static ClockProjectsResponse.ProjectOption opcion(Project proyecto) {
        return new ClockProjectsResponse.ProjectOption(proyecto.getId(), proyecto.getCodigo(), proyecto.getNombre());
    }

    /**
     * Si hoy no es laborable para esta persona, avisa a quien aprueba
     * ausencias. No impide fichar: una guardia en festivo existe, y el
     * registro horario tiene que recoger lo que se trabajó, no lo que se
     * esperaba. El aviso sale después del commit, así que si el INICIO
     * falla no se avisa de nada.
     */
    private void avisarSiNoEsLaborable(User user) {
        LocalDate hoy = LocalDate.now(MADRID);
        nonWorkingDayService.motivo(user, hoy).ifPresent(motivo -> {
            List<User> destinatarios = Destinatarios.conAuthorityMenos(
                    userRepository.findByEmpresa(user.getEmpresa()), "ausencia:aprobar", user);
            eventPublisher.publishEvent(new NotificationEvents.WorkedOnNonWorkingDay(
                    user.getEmpresa().getId(), user.getNombre(), hoy,
                    motivo.texto(), motivo.vacaciones(), destinatarios));
            log.info("Jornada iniciada en día no laborable por el usuario {}: {}", user.getId(), motivo.texto());
        });
    }

    @Override
    public Optional<NonWorkingDayService.Motivo> motivoNoLaborableHoy(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return nonWorkingDayService.motivo(user, LocalDate.now(MADRID));
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
    public List<TimeEntry> getHistory(String userEmail, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new BusinessException("La fecha de inicio no puede ser posterior a la de fin.", HttpStatus.BAD_REQUEST);
        }
        // Un año como mucho: sin el límite de 200 filas, un rango abierto
        // devolvería la vida laboral entera de alguien en una sola respuesta.
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS_HISTORIAL) {
            throw new BusinessException("El periodo no puede pasar de un año.", HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        // Días de España: un fichaje a las 23:30 UTC del día 31 es del día 1.
        return timeEntryRepository.findHistoryByUsuarioEntre(
                user,
                desde.atStartOfDay(MADRID).toInstant(),
                hasta.plusDays(1).atStartOfDay(MADRID).toInstant());
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
