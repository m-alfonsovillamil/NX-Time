package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.config.CacheConfig;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Kiosk;
import com.nxtime.nxtime.domain.OrigenDelFichaje;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAction;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
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

    /** Un año bisiesto entero. Ver {@link #getHistory(String, LocalDate, LocalDate)}. */
    static final int MAXIMO_DIAS_HISTORIAL = 366;

    private final TimeEntryRepository timeEntryRepository;
    private final TimeEntryAuditRepository timeEntryAuditRepository;
    private final UserRepository userRepository;
    private final TimeEntryMapper timeEntryMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TimeEntrySnapshotSerializer snapshotSerializer;
    private final NonWorkingDayService nonWorkingDayService;
    private final ProjectAllocationService projectAllocationService;
    private final IncompleteTimeEntryCloser cierreDeOlvidadas;
    private final Clock clock;

    @Autowired
    public TimeEntryServiceImpl(
            TimeEntryRepository timeEntryRepository,
            TimeEntryAuditRepository timeEntryAuditRepository,
            UserRepository userRepository,
            TimeEntryMapper timeEntryMapper,
            ApplicationEventPublisher eventPublisher,
            TimeEntrySnapshotSerializer snapshotSerializer,
            NonWorkingDayService nonWorkingDayService,
            ProjectAllocationService projectAllocationService,
            IncompleteTimeEntryCloser cierreDeOlvidadas
    ) {
        this(timeEntryRepository, timeEntryAuditRepository, userRepository, timeEntryMapper, eventPublisher,
                snapshotSerializer, nonWorkingDayService, projectAllocationService, cierreDeOlvidadas,
                Clock.systemUTC());
    }

    /** Con un reloj fijo, para los tests. */
    TimeEntryServiceImpl(
            TimeEntryRepository timeEntryRepository,
            TimeEntryAuditRepository timeEntryAuditRepository,
            UserRepository userRepository,
            TimeEntryMapper timeEntryMapper,
            ApplicationEventPublisher eventPublisher,
            TimeEntrySnapshotSerializer snapshotSerializer,
            NonWorkingDayService nonWorkingDayService,
            ProjectAllocationService projectAllocationService,
            IncompleteTimeEntryCloser cierreDeOlvidadas,
            Clock clock
    ) {
        this.timeEntryRepository = timeEntryRepository;
        this.timeEntryAuditRepository = timeEntryAuditRepository;
        this.userRepository = userRepository;
        this.timeEntryMapper = timeEntryMapper;
        this.eventPublisher = eventPublisher;
        this.snapshotSerializer = snapshotSerializer;
        this.nonWorkingDayService = nonWorkingDayService;
        this.projectAllocationService = projectAllocationService;
        this.cierreDeOlvidadas = cierreDeOlvidadas;
        this.clock = clock;
    }

    /**
     * Cuándo y desde dónde se ficha (ADR 054). Con conexión, ahora; sin
     * conexión, la hora en que ocurrió, que puede ser de hace horas.
     *
     * @param motivo      lo que añade a la auditoría (de dónde sale la hora), o null
     * @param sinConexion llegó de la cola del móvil: se comprueba el orden y se
     *                    puede reabrir una jornada que cerró el sistema
     * @param llegaTarde  llegó pasadas 72 h: la jornada queda para revisar
     */
    record Momento(Instant instante, OrigenDelFichaje origen, String motivo, boolean sinConexion,
            boolean llegaTarde) {

        static Momento ahora(Instant ahora, OrigenDelFichaje origen) {
            return new Momento(ahora, origen, null, false, false);
        }
    }

    // Desde la Fase 3 (PostgreSQL + IDENTITY) esto SÍ es una transacción
    // normal (ver el comentario homólogo en AuthServiceImpl.registerManager
    // sobre por qué antes no lo era).
    // Fichar cambia lo que dice el panel personal, y el panel se cachea un
    // minuto (CacheConfig). Sin borrarlo, tras fichar la salida seguía un
    // minuto el de antes: "trabajando" y 0 minutos hoy (la jornada abierta
    // no suma), justo cuando alguien mira cuánto ha hecho.
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD,
            key = "T(com.nxtime.nxtime.config.CacheConfig).clavePanelPersonal(#userEmail)")
    public TimeEntry registerTimeEntry(String userEmail, TimeEntryRequest request) {
        return registerTimeEntry(userEmail, request, null);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD,
            key = "T(com.nxtime.nxtime.config.CacheConfig).clavePanelPersonal(#userEmail)")
    public TimeEntry registerTimeEntry(String userEmail, TimeEntryRequest request, OrigenDelFichaje origen) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + userEmail));
        return registrar(user, request, null, Momento.ahora(clock.instant(), origen));
    }

    /**
     * Un fichaje que se hizo sin conexión y llega ahora (ADR 054). Las mismas
     * reglas que con conexión, con la hora en que ocurrió. Lo llama
     * {@link RecepcionDeFichajesSinConexion}, que pone la transacción y decide
     * la hora y si llega tarde.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD,
            key = "T(com.nxtime.nxtime.config.CacheConfig).clavePanelPersonal(#persona.email)")
    public TimeEntry registrarSinConexion(User persona, TimeEntryRequest request, Instant ocurrido, String motivo,
            boolean llegaTarde) {
        return registrar(persona, request, null,
                new Momento(ocurrido, OrigenDelFichaje.APP_SIN_CONEXION, motivo, true, llegaTarde));
    }

    /**
     * Lo mismo, para quien se identificó en un kiosco (ADR 033). El fichaje es
     * de la persona, igual que desde su sesión: el kiosco solo queda dicho en
     * el motivo de la auditoría y, si abre la jornada, en {@code kiosco_id}.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DASHBOARD,
            key = "T(com.nxtime.nxtime.config.CacheConfig).clavePanelPersonal(#persona.email)")
    public TimeEntry registrarDesdeKiosco(User persona, Kiosk kiosco, TimeEntryRequest request) {
        return registrar(persona, request, kiosco, Momento.ahora(clock.instant(), OrigenDelFichaje.KIOSCO));
    }

    private TimeEntry registrar(User user, TimeEntryRequest request, Kiosk kiosco, Momento momento) {
        Instant cuando = momento.instante();
        TimeEntry activeEntry = timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user).orElse(null);

        if (activeEntry == null && momento.sinConexion() && request.tipo() != TimeEntryAction.INICIO) {
            // La salida (o una pausa) de una jornada que el proceso de las
            // 3:00 cerró mientras el móvil no tenía red: ese cierre era un
            // tope, y ahora llega el dato de verdad.
            activeEntry = reabrirSiLaCerroElSistema(user, cuando).orElse(null);
        }

        if (activeEntry != null && IncompleteTimeEntryCloser.estaOlvidada(activeEntry, cuando)) {
            // Una jornada que se quedó abierta más del límite ya no se puede
            // seguir como si nada: se cierra aquí como la cerraría el proceso
            // de las 3:00, con la salida en el tope y marcada para corregir.
            // Antes, qué quedaba registrado dependía de si las 3:00 habían
            // caído en medio: terminarla a mano por la tarde dejaba una
            // jornada de veinte horas sin ninguna marca.
            cierreDeOlvidadas.cerrar(activeEntry);
            log.warn("Jornada {} de {} cerrada al fichar: llevaba abierta más de {} horas.",
                    activeEntry.getId(), user.getId(), IncompleteTimeEntryCloser.HORAS_PARA_CONSIDERARLA_OLVIDADA);
            if (request.tipo() != TimeEntryAction.INICIO) {
                // Terminar, pausar o reanudar una jornada olvidada es
                // cerrarla: no hay nada que seguir.
                return activeEntry;
            }
            // Antes de insertar la nueva: con IDENTITY el INSERT sale en el
            // save, por delante del UPDATE pendiente de la que se acaba de
            // cerrar, y chocaría con uq_registros_jornada_abierta.
            timeEntryRepository.flush();
            activeEntry = null;
        }

        if (momento.sinConexion()) {
            comprobarElOrden(user, activeEntry, request.tipo(), cuando);
        }

        // Instantánea de "antes" para la auditoría: se toma ya, antes de
        // que ninguna de las ramas de abajo mute activeEntry.
        String beforeJson = (activeEntry != null) ? toJson(activeEntry) : null;
        if (activeEntry != null && momento.llegaTarde()) {
            activeEntry.setRevisarPorLlegarTarde(true);
        }
        LocalDate dia = cuando.atZone(user.zona()).toLocalDate();

        AuditAction accion;
        String motivoAuditoria = null;
        TimeEntry result = switch (request.tipo()) {
            case INICIO -> {
                if (activeEntry != null) {
                    throw new BusinessException("Ya hay una jornada activa.");
                }
                Project proyecto = proyectoAlIniciar(user, request.proyectoId(), dia);
                motivoAuditoria = proyecto != null ? "Proyecto: " + proyecto.getCodigo() : null;
                TimeEntry newEntry = TimeEntry.builder()
                        .usuario(user)
                        .empresa(user.getEmpresa())
                        .horaEntrada(cuando)
                        .kiosco(kiosco)
                        .origen(momento.origen())
                        .revisarPorLlegarTarde(momento.llegaTarde())
                        .modalidad(modalidadAlEntrar(user, request, kiosco))
                        .build();
                accion = AuditAction.CREACION;
                avisarSiNoEsLaborable(user, dia);
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
                activeEntry.setHoraSalida(cuando);
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
                activeEntry.setInicioPausaActual(cuando);
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

                Duration duracionPausa = Duration.between(inicioPausa, cuando);

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
                .motivo(juntar(conKiosco(motivoAuditoria, kiosco), momento.motivo()))
                .build();
        eventPublisher.publishEvent(new TimeEntryAuditEvent(auditRow));

        log.info("Fichaje {} registrado para {} (fichaje id={}{}{})", request.tipo(), user.getId(), result.getId(),
                kiosco == null ? "" : ", kiosco " + kiosco.getId(),
                momento.sinConexion() ? ", sin conexión" : "");
        return result;
    }

    /**
     * El motivo de la auditoría, con el kiosco si se fichó en uno. Va en el
     * motivo y no en otra columna porque el motivo ya entra en la huella de la
     * cadena (ADR 003 y 018): queda dicho desde dónde se fichó sin cambiar la
     * fórmula, y las filas antiguas se siguen verificando igual (ADR 033).
     */
    static String conKiosco(String motivo, Kiosk kiosco) {
        if (kiosco == null) {
            return motivo;
        }
        String desde = "Desde el kiosco «" + kiosco.getNombre() + "» (id " + kiosco.getId() + ")";
        return motivo == null ? desde : motivo + " · " + desde;
    }

    /**
     * Presencial o a distancia (ADR 055): solo si la empresa la pregunta. En
     * un kiosco es presencial por definición. Si la pregunta y no llega (una
     * app anterior), la jornada queda sin ella: no se deja de fichar por eso.
     */
    static com.nxtime.nxtime.domain.Modalidad modalidadAlEntrar(User user, TimeEntryRequest request, Kiosk kiosco) {
        if (user.getEmpresa() == null || !user.getEmpresa().isPreguntarModalidad()) {
            return null;
        }
        return kiosco != null ? com.nxtime.nxtime.domain.Modalidad.PRESENCIAL : request.modalidad();
    }

    @Override
    public boolean preguntaModalidad(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .map(user -> user.getEmpresa() != null && user.getEmpresa().isPreguntarModalidad())
                .orElse(false);
    }

    private static String juntar(String motivo, String mas) {
        if (mas == null) {
            return motivo;
        }
        return motivo == null ? mas : motivo + " · " + mas;
    }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static String hora(Instant instante, ZoneId zona) {
        return HORA.format(instante.atZone(zona));
    }

    /**
     * Lo que llega sin conexión no puede caer antes de lo que ya consta (ADR
     * 054): una pausa anterior a la entrada, o una entrada que pisa la jornada
     * anterior, no son un fichaje sino un reloj mal puesto o una cola
     * desordenada. Con conexión no hace falta: la hora es la del servidor y
     * siempre va hacia delante.
     */
    private void comprobarElOrden(User user, TimeEntry abierta, TimeEntryAction tipo, Instant cuando) {
        if (abierta != null) {
            Instant ultimo = abierta.isEnPausa() && abierta.getInicioPausaActual() != null
                    ? abierta.getInicioPausaActual() : abierta.getHoraEntrada();
            if (cuando.isBefore(ultimo)) {
                throw new BusinessException("Este fichaje (" + hora(cuando, user.zona())
                        + ") es anterior al último de esa jornada (" + hora(ultimo, user.zona()) + ").");
            }
            return;
        }
        if (tipo != TimeEntryAction.INICIO) {
            return; // sin jornada abierta, la máquina de estados ya lo rechaza
        }
        timeEntryRepository.findFirstByUsuarioAndAnuladoFalseOrderByHoraEntradaDesc(user)
                .filter(anterior -> anterior.getHoraSalida() != null && cuando.isBefore(anterior.getHoraSalida()))
                .ifPresent(anterior -> {
                    throw new BusinessException("Esta entrada (" + hora(cuando, user.zona())
                            + ") se solapa con la jornada anterior, que acaba el "
                            + hora(anterior.getHoraSalida(), user.zona()) + ". Pide que te la corrijan.");
                });
    }

    /**
     * Si la última jornada la cerró el proceso de las 3:00 y este fichaje cae
     * dentro de ella, se vuelve a abrir (ADR 054): la salida del cierre era un
     * tope a falta de dato, y el dato ha llegado. Queda en la auditoría como
     * acción del sistema, igual que el cierre.
     */
    private Optional<TimeEntry> reabrirSiLaCerroElSistema(User user, Instant cuando) {
        return timeEntryRepository.findFirstByUsuarioAndAnuladoFalseOrderByHoraEntradaDesc(user)
                .filter(TimeEntry::isJornadaIncompleta)
                .filter(ultima -> ultima.getHoraSalida() != null
                        && cuando.isAfter(ultima.getHoraEntrada())
                        && cuando.isBefore(ultima.getHoraSalida()))
                .map(ultima -> {
                    String antes = toJson(ultima);
                    ultima.setHoraSalida(null);
                    ultima.setJornadaIncompleta(false);
                    projectAllocationService.alReabrir(ultima);
                    TimeEntry reabierta = timeEntryRepository.save(ultima);
                    eventPublisher.publishEvent(new TimeEntryAuditEvent(TimeEntryAudit.builder()
                            .registro(reabierta)
                            .usuario(user)
                            .modificadoPor(null) // como el cierre: lo hace el sistema
                            .accion(AuditAction.MODIFICACION)
                            .valorAnterior(antes)
                            .valorNuevo(toJson(reabierta))
                            .motivo("Reabierta: llega sin conexión un fichaje de antes del cierre automático.")
                            .build()));
                    log.info("Jornada {} de {} reabierta: llega sin conexión un fichaje de antes del cierre.",
                            reabierta.getId(), user.getId());
                    return reabierta;
                });
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
    private Project proyectoAlIniciar(User user, Long proyectoId, LocalDate dia) {
        List<Project> disponibles = projectAllocationService.proyectosParaFichar(user, dia);
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

    // noRollbackFor: sobre una jornada olvidada esto la cierra Y lanza, y el
    // cierre tiene que quedarse (como el intento fallido de un código de
    // acceso). Las demás reglas lanzan antes de escribir nada.
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
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
        if (IncompleteTimeEntryCloser.estaOlvidada(registro, clock.instant())) {
            // Como al fichar sobre una jornada olvidada (ver registrar): se
            // cierra en su tope. Abrirle un tramo ahora sería imputar a un
            // proyecto horas que caen fuera de la jornada. La excepción no
            // deshace el cierre: ver noRollbackFor.
            cierreDeOlvidadas.cerrar(registro);
            log.warn("Jornada {} de {} cerrada al cambiar de proyecto: llevaba abierta más de {} horas.",
                    registro.getId(), user.getId(), IncompleteTimeEntryCloser.HORAS_PARA_CONSIDERARLA_OLVIDADA);
            throw new BusinessException("Esa jornada llevaba abierta más de "
                    + IncompleteTimeEntryCloser.HORAS_PARA_CONSIDERARLA_OLVIDADA
                    + " horas y se ha cerrado. Ficha la entrada de nuevo y pide que te corrijan la anterior.");
        }
        // En pausa no: el tramo que se cierra no sabría cuánto de esa pausa es suyo.
        if (registro.isEnPausa()) {
            throw new BusinessException("Reanuda la jornada antes de cambiar de proyecto.");
        }
        Project nuevo = projectAllocationService.proyectosParaFichar(user, LocalDate.now(user.zona())).stream()
                .filter(p -> p.getId() == proyectoId)
                .findFirst()
                .orElseThrow(() -> new BusinessException("No tienes asignado ese proyecto hoy.", HttpStatus.FORBIDDEN));
        Project anterior = projectAllocationService.proyectoEnCurso(registro).orElse(null);
        if (anterior != null && anterior.getId() == nuevo.getId()) {
            throw new BusinessException("Ya estás trabajando en " + nuevo.getCodigo() + ".");
        }

        String antes = toJson(registro);
        projectAllocationService.cambiarDeProyecto(registro, nuevo, clock.instant());
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
                projectAllocationService.proyectosParaFichar(user, LocalDate.now(user.zona())).stream()
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
    private void avisarSiNoEsLaborable(User user, LocalDate hoy) {
        nonWorkingDayService.motivo(user, hoy).ifPresent(motivo -> {
            List<User> destinatarios = Destinatarios.conAuthority(
                    userRepository, user.getEmpresa(), "ausencia:aprobar", user);
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
        return nonWorkingDayService.motivo(user, LocalDate.now(user.zona()));
    }

    @Override
    public Optional<TimeEntry> getActiveTimeEntry(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user);
    }

    @Override
    public Page<TimeEntry> getHistory(String userEmail, Pageable pagina) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return timeEntryRepository.findHistoryByUsuario(user, pagina);
    }

    @Override
    public Page<TimeEntry> getHistory(String userEmail, LocalDate desde, LocalDate hasta, Pageable pagina) {
        if (desde.isAfter(hasta)) {
            throw new BusinessException("La fecha de inicio no puede ser posterior a la de fin.", HttpStatus.BAD_REQUEST);
        }
        // Un año como mucho: aunque ya va por páginas, un rango abierto haría
        // contar la vida laboral entera de alguien en cada petición.
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS_HISTORIAL) {
            throw new BusinessException("El periodo no puede pasar de un año.", HttpStatus.BAD_REQUEST);
        }
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        // Días de la empresa: en Madrid, un fichaje a las 23:30 UTC del día 31 es del día 1.
        return timeEntryRepository.findHistoryByUsuarioEntre(
                user,
                desde.atStartOfDay(user.zona()).toInstant(),
                hasta.plusDays(1).atStartOfDay(user.zona()).toInstant(),
                pagina);
    }

    @Override
    public Page<TeamTimeEntryDTO> getTeamHistory(String managerEmail, Long usuarioId, Pageable pagina) {
        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Gestor no encontrado con email: " + managerEmail));
        Page<TimeEntry> fichajes = usuarioId == null
                ? timeEntryRepository.findTeamHistory(manager.getEmpresa(), pagina)
                : timeEntryRepository.findTeamHistoryDeUsuario(manager.getEmpresa(), usuarioId, pagina);
        return fichajes.map(timeEntryMapper::toTeamDTO);
    }

    /**
     * Quita la marca de una jornada a la que llegó un fichaje sin conexión
     * pasadas 72 h (ADR 054): alguien que aprueba correcciones ha mirado las
     * horas y le valen. No cambia nada más; si no le valen, lo que toca es una
     * corrección. Como con las correcciones, no la da por buena su dueño,
     * salvo que nadie más en la empresa pueda.
     */
    @Override
    @Transactional
    public TimeEntry darPorBueno(String actorEmail, long registroId) {
        User actor = userRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        TimeEntry registro = timeEntryRepository.findById(registroId)
                .orElseThrow(() -> new ResourceNotFoundException("Fichaje no encontrado."));
        if (registro.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes revisar fichajes de otra empresa.");
        }
        if (registro.isAnulado() || !registro.isRevisarPorLlegarTarde()) {
            throw new BusinessException("Esa jornada no tiene nada pendiente de revisar.");
        }
        if (registro.getHoraSalida() == null) {
            throw new BusinessException("La jornada sigue abierta: revísala cuando se cierre.");
        }
        if (registro.getUsuario().getId() == actor.getId()
                && !Destinatarios.conAuthority(userRepository, actor.getEmpresa(), "correccion:aprobar", actor)
                        .isEmpty()) {
            throw new BusinessException("Tu propia jornada la tiene que revisar otra persona.", HttpStatus.FORBIDDEN);
        }

        String antes = toJson(registro);
        registro.setRevisarPorLlegarTarde(false);
        TimeEntry revisado = timeEntryRepository.save(registro);
        eventPublisher.publishEvent(new TimeEntryAuditEvent(TimeEntryAudit.builder()
                .registro(revisado)
                .usuario(revisado.getUsuario())
                .modificadoPor(actor)
                .accion(AuditAction.DADO_POR_BUENO)
                .valorAnterior(antes)
                .valorNuevo(toJson(revisado))
                .motivo("Revisada: llegó sin conexión pasadas "
                        + RecepcionDeFichajesSinConexion.HORAS_PARA_ACEPTARLO_SOLO + " horas.")
                .build()));
        log.info("Usuario {} da por buena la jornada {} (llegó sin conexión tarde).", actor.getId(), registroId);
        return revisado;
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
        // Hacia atrás hasta el original. Los dos recorridos llevan un registro
        // de por dónde han pasado (Fase A6): sin él, un ciclo en los datos
        // --A corrige a B y B corrige a A-- deja este bucle girando PARA
        // SIEMPRE y con él el hilo que atiende la petición. No hay nada en la
        // base que impida ese ciclo, así que la defensa tiene que estar aquí.
        Set<Long> vistos = new LinkedHashSet<>();
        TimeEntry raiz = entry;
        while (raiz.getRegistroOriginal() != null) {
            if (!vistos.add(raiz.getId())) {
                throw new IllegalStateException(
                        "La cadena de correcciones del fichaje " + entry.getId() + " tiene un ciclo.");
            }
            raiz = raiz.getRegistroOriginal();
        }

        List<Long> ids = new ArrayList<>();
        vistos.clear();
        for (TimeEntry actual = raiz; actual != null;
                actual = timeEntryRepository.findByRegistroOriginal_Id(actual.getId()).orElse(null)) {
            if (!vistos.add(actual.getId())) {
                throw new IllegalStateException(
                        "La cadena de correcciones del fichaje " + entry.getId() + " tiene un ciclo.");
            }
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
