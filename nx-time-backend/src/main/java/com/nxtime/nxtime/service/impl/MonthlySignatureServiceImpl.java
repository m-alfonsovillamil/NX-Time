package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.HuellaDelMes;
import com.nxtime.nxtime.domain.MonthlySignature;
import com.nxtime.nxtime.domain.MonthlySignatureStatus;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.MonthlySignatureResponse;
import com.nxtime.nxtime.dto.SignableMonthResponse;
import com.nxtime.nxtime.dto.SignatureVerificationResponse;
import com.nxtime.nxtime.dto.TeamSignatureResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.MonthlySignatureRepository;
import com.nxtime.nxtime.repository.NoticeRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.MonthlySignatureService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MonthlySignatureServiceImpl implements MonthlySignatureService {

    private static final Logger log = LoggerFactory.getLogger(MonthlySignatureServiceImpl.class);

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    static final String VISAR = "firma:visar";

    /** Cuántos meses terminados enseña "mis meses": medio año basta para ponerse al día. */
    static final int MESES_A_LA_VISTA = 6;

    private final MonthlySignatureRepository signatureRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;
    private final NoticeRepository noticeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public MonthlySignatureServiceImpl(
            MonthlySignatureRepository signatureRepository,
            TimeEntryRepository timeEntryRepository,
            UserRepository userRepository,
            NoticeRepository noticeRepository,
            ApplicationEventPublisher eventPublisher) {
        this(signatureRepository, timeEntryRepository, userRepository, noticeRepository, eventPublisher,
                Clock.systemUTC());
    }

    /** Con el reloj inyectable, para los tests: "el mes ha terminado" depende de hoy. */
    MonthlySignatureServiceImpl(
            MonthlySignatureRepository signatureRepository,
            TimeEntryRepository timeEntryRepository,
            UserRepository userRepository,
            NoticeRepository noticeRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.signatureRepository = signatureRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.userRepository = userRepository;
        this.noticeRepository = noticeRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Lo propio
    // ------------------------------------------------------------------

    @Override
    public List<SignableMonthResponse> misMeses(User actor) {
        YearMonth actual = YearMonth.now(clock.withZone(MADRID));
        YearMonth primero = actual.minusMonths(MESES_A_LA_VISTA);

        Map<YearMonth, List<TimeEntry>> porMes = timeEntryRepository
                .findVivosDeUsuariosQueEmpiezanEntre(List.of(actor.getId()), inicio(primero), inicio(actual))
                .stream()
                .collect(Collectors.groupingBy(fichaje -> YearMonth.from(fichaje.getHoraEntrada().atZone(MADRID))));

        // La vigente de cada mes o, si no la hay, la última que se cayó.
        Map<YearMonth, MonthlySignature> firmaDelMes = new HashMap<>();
        for (MonthlySignature firma : signatureRepository.findByUsuario_IdOrderByAnioDescMesDescFirmadaEnDesc(actor.getId())) {
            firmaDelMes.merge(firma.periodo(), firma,
                    (ya, otra) -> ya.getEstado() == MonthlySignatureStatus.VIGENTE ? ya
                            : otra.getEstado() == MonthlySignatureStatus.VIGENTE ? otra : ya);
        }

        List<SignableMonthResponse> meses = new ArrayList<>();
        for (YearMonth mes = actual.minusMonths(1); !mes.isBefore(primero); mes = mes.minusMonths(1)) {
            List<TimeEntry> fichajes = porMes.getOrDefault(mes, List.of());
            MonthlySignature firma = firmaDelMes.get(mes);
            if (fichajes.isEmpty() && firma == null) {
                continue;
            }
            boolean firmado = firma != null && firma.getEstado() == MonthlySignatureStatus.VIGENTE;
            String bloqueo = firmado ? null : bloqueo(fichajes);
            long netos = HuellaDelMes.resumen(actor.getId(), actor.getEmpresa().getId(), mes, fichajes).segundosNetos();
            meses.add(new SignableMonthResponse(mes.getYear(), mes.getMonthValue(), fichajes.size(), netos,
                    !firmado && bloqueo == null, bloqueo, firma == null ? null : toResponse(firma)));
        }
        return meses;
    }

    @Override
    @Transactional
    public MonthlySignatureResponse firmar(User actor, YearMonth mes, String ip) {
        YearMonth actual = YearMonth.now(clock.withZone(MADRID));
        if (!mes.isBefore(actual)) {
            throw new BusinessException("Ese mes todavía no ha terminado.", HttpStatus.BAD_REQUEST);
        }
        if (signatureRepository.findByUsuario_IdAndAnioAndMesAndEstado(
                actor.getId(), mes.getYear(), mes.getMonthValue(), MonthlySignatureStatus.VIGENTE).isPresent()) {
            throw new BusinessException("Ese mes ya está firmado.");
        }

        List<TimeEntry> fichajes = fichajesDelMes(actor.getId(), mes);
        String bloqueo = bloqueo(fichajes);
        if (bloqueo != null) {
            throw new BusinessException(bloqueo, HttpStatus.UNPROCESSABLE_ENTITY);
        }

        HuellaDelMes.Resumen resumen = HuellaDelMes.resumen(actor.getId(), actor.getEmpresa().getId(), mes, fichajes);
        MonthlySignature firma = signatureRepository.save(MonthlySignature.builder()
                .empresa(actor.getEmpresa())
                .usuario(actor)
                .anio(mes.getYear())
                .mes(mes.getMonthValue())
                .hash(HuellaDelMes.hash(resumen))
                .versionHuella(HuellaDelMes.VERSION)
                .jornadas(resumen.jornadas())
                .segundosNetos(resumen.segundosNetos())
                .firmadaEn(Instant.now(clock))
                .ip(ip)
                .build());
        log.info("Firma mensual: usuario={}, mes={}, huella={}", actor.getId(), mes, firma.getHash());
        return toResponse(firma);
    }

    /**
     * Por qué no se puede firmar un mes, o null si se puede.
     *
     * Una jornada que cerró el sistema lleva una salida que nadie fichó:
     * firmarla sería firmar un dato que el propio sistema marca como
     * inventado. Hay que corregirla antes.
     */
    private static String bloqueo(List<TimeEntry> fichajes) {
        if (fichajes.isEmpty()) {
            return "No hay jornadas que firmar en ese mes.";
        }
        long abiertas = fichajes.stream().filter(fichaje -> fichaje.getHoraSalida() == null).count();
        if (abiertas > 0) {
            return abiertas == 1 ? "Queda una jornada sin cerrar en ese mes."
                    : "Quedan " + abiertas + " jornadas sin cerrar en ese mes.";
        }
        long delSistema = fichajes.stream().filter(TimeEntry::isJornadaIncompleta).count();
        if (delSistema > 0) {
            return (delSistema == 1 ? "Una jornada la cerró" : delSistema + " jornadas las cerró")
                    + " el sistema porque faltaba la salida. Pide que se corrijan antes de firmar.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Comprobar y visar
    // ------------------------------------------------------------------

    @Override
    public SignatureVerificationResponse verificar(long firmaId, User actor) {
        MonthlySignature firma = deLaEmpresa(firmaId, actor);
        if (firma.getUsuario().getId() != actor.getId() && !RoleAuthorities.tiene(actor, VISAR)) {
            throw new TenantAccessException("Solo puedes comprobar tus propias firmas.");
        }
        String actual = huellaDeHoy(firma);
        return new SignatureVerificationResponse(firma.getId(), firma.getEstado(), actual.equals(firma.getHash()),
                firma.getHash(), actual);
    }

    @Override
    public List<TeamSignatureResponse> equipo(User actor, YearMonth mes) {
        Map<Long, MonthlySignature> porPersona = new HashMap<>();
        for (MonthlySignature firma : signatureRepository.findByEmpresa_IdAndAnioAndMes(
                actor.getEmpresa().getId(), mes.getYear(), mes.getMonthValue())) {
            porPersona.merge(firma.getUsuario().getId(), firma, MonthlySignatureServiceImpl::laQueCuenta);
        }
        return userRepository.findByEmpresaAndActivoTrue(actor.getEmpresa()).stream()
                .sorted(Comparator.comparing(MonthlySignatureServiceImpl::nombreCompleto))
                .map(persona -> {
                    MonthlySignature firma = porPersona.get(persona.getId());
                    String estado = firma == null ? "SIN_FIRMAR" : firma.getEstado().name();
                    return new TeamSignatureResponse(persona.getId(), nombreCompleto(persona), estado,
                            firma == null ? null : toResponse(firma));
                })
                .toList();
    }

    @Override
    @Transactional
    public MonthlySignatureResponse visar(long firmaId, User actor) {
        MonthlySignature firma = deLaEmpresa(firmaId, actor);
        if (!RoleAuthorities.tiene(actor, VISAR)) {
            throw new TenantAccessException("No puedes visar firmas.");
        }
        // Dar el visto bueno de la empresa a lo propio no es un visto bueno.
        if (firma.getUsuario().getId() == actor.getId()) {
            throw new TenantAccessException("No puedes visar tu propia firma.");
        }
        if (firma.getEstado() != MonthlySignatureStatus.VIGENTE) {
            throw new BusinessException("Esa firma ya no está vigente: una corrección la dejó sin efecto.");
        }
        if (firma.getVisadaEn() != null) {
            throw new BusinessException("Esa firma ya está visada.");
        }
        firma.setVisadaPor(actor);
        firma.setVisadaEn(Instant.now(clock));
        return toResponse(signatureRepository.save(firma));
    }

    // ------------------------------------------------------------------
    // La invalidación y el recordatorio
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void revisarTrasCambio(TimeEntry registro, Instant entradaAnterior, String motivo) {
        Set<Integer> meses = new LinkedHashSet<>();
        meses.add(clave(YearMonth.from(registro.getHoraEntrada().atZone(MADRID))));
        if (entradaAnterior != null) {
            meses.add(clave(YearMonth.from(entradaAnterior.atZone(MADRID))));
        }
        // Casi siempre vacía: el mes en curso nunca está firmado, y fichar es
        // lo que más pasa por aquí.
        for (MonthlySignature firma : signatureRepository.findVigentesDeUsuarioEnMeses(
                registro.getUsuario().getId(), meses)) {
            // Se recalcula en vez de invalidar a ciegas: cambiar el proyecto
            // de una jornada, o anularla y volverla a crear igual, no cambia
            // lo que se firmó, y tumbar la firma por eso sería ruido.
            if (huellaDeHoy(firma).equals(firma.getHash())) {
                continue;
            }
            firma.setEstado(MonthlySignatureStatus.INVALIDADA);
            firma.setInvalidadaEn(Instant.now(clock));
            firma.setMotivoInvalidacion(motivo.length() > 300 ? motivo.substring(0, 300) : motivo);
            signatureRepository.save(firma);
            log.info("Firma {} ({} de {}) invalidada: {}", firma.getId(), firma.periodo(),
                    firma.getUsuario().getId(), motivo);
            eventPublisher.publishEvent(new NotificationEvents.SignatureInvalidated(
                    firma.getEmpresa().getId(), firma.getAnio(), firma.getMes(), motivo, List.of(firma.getUsuario())));
        }
    }

    @Override
    @Transactional
    public int recordar(LocalDate hoy) {
        YearMonth mes = YearMonth.from(hoy).minusMonths(1);
        Set<Long> firmados = new HashSet<>(
                signatureRepository.findUsuariosConFirmaVigente(mes.getYear(), mes.getMonthValue()));
        // Un recordatorio por persona y mes: la tarea corre a diario para
        // ponerse al día si un día no corrió, no para insistir.
        Set<Long> yaRecordados = new HashSet<>(noticeRepository.findDestinatariosDeTipoDesde(
                NoticeType.RECORDATORIO_FIRMA, hoy.withDayOfMonth(1).atStartOfDay(MADRID).toInstant()));

        List<User> pendientes = timeEntryRepository.findUsuariosActivosQueFicharonEntre(inicio(mes), inicio(mes.plusMonths(1)))
                .stream()
                .filter(persona -> !firmados.contains(persona.getId()) && !yaRecordados.contains(persona.getId()))
                .toList();
        if (!pendientes.isEmpty()) {
            eventPublisher.publishEvent(new NotificationEvents.SignatureReminder(mes, pendientes));
        }
        return pendientes.size();
    }

    // ------------------------------------------------------------------

    private String huellaDeHoy(MonthlySignature firma) {
        return HuellaDelMes.hash(HuellaDelMes.resumen(firma.getUsuario().getId(), firma.getEmpresa().getId(),
                firma.periodo(), fichajesDelMes(firma.getUsuario().getId(), firma.periodo())));
    }

    private List<TimeEntry> fichajesDelMes(long usuarioId, YearMonth mes) {
        return timeEntryRepository.findVivosDeUsuariosQueEmpiezanEntre(
                List.of(usuarioId), inicio(mes), inicio(mes.plusMonths(1)));
    }

    private MonthlySignature deLaEmpresa(long firmaId, User actor) {
        MonthlySignature firma = signatureRepository.findById(firmaId)
                .orElseThrow(() -> new ResourceNotFoundException("Firma no encontrada."));
        if (firma.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Esa firma es de otra empresa.");
        }
        return firma;
    }

    private static MonthlySignature laQueCuenta(MonthlySignature una, MonthlySignature otra) {
        if (una.getEstado() == MonthlySignatureStatus.VIGENTE) {
            return una;
        }
        if (otra.getEstado() == MonthlySignatureStatus.VIGENTE) {
            return otra;
        }
        return una.getFirmadaEn().isAfter(otra.getFirmadaEn()) ? una : otra;
    }

    private static int clave(YearMonth mes) {
        return mes.getYear() * 12 + mes.getMonthValue();
    }

    private static Instant inicio(YearMonth mes) {
        return mes.atDay(1).atStartOfDay(MADRID).toInstant();
    }

    private static String nombreCompleto(User persona) {
        return persona.getApellidos() == null || persona.getApellidos().isBlank()
                ? persona.getNombre()
                : persona.getNombre() + " " + persona.getApellidos();
    }

    static MonthlySignatureResponse toResponse(MonthlySignature firma) {
        return new MonthlySignatureResponse(
                firma.getId(),
                firma.getUsuario().getId(),
                nombreCompleto(firma.getUsuario()),
                firma.getAnio(),
                firma.getMes(),
                firma.getEstado(),
                firma.getHash(),
                firma.getJornadas(),
                firma.getSegundosNetos(),
                firma.getFirmadaEn(),
                firma.getInvalidadaEn(),
                firma.getMotivoInvalidacion(),
                firma.getVisadaPor() == null ? null : nombreCompleto(firma.getVisadaPor()),
                firma.getVisadaEn());
    }
}
