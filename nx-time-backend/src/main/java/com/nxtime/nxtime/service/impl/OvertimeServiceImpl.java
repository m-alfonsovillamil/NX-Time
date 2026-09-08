package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.OvertimeAlert;
import com.nxtime.nxtime.domain.OvertimeStatus;
import com.nxtime.nxtime.domain.OvertimeType;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.OvertimeAlertResponse;
import com.nxtime.nxtime.dto.OvertimeBalanceResponse;
import com.nxtime.nxtime.dto.ReviewOvertimeRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.OvertimeAlertRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.OvertimeCalculator;
import com.nxtime.nxtime.service.OvertimeService;
import com.nxtime.nxtime.service.WorkingDayService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detección y revisión de horas extra (Fase F).
 *
 * <b>Lo que detecta esto es un aviso, no una imputación.</b> El reloj
 * sabe que el martes duró once horas; no sabe si fue una jornada
 * intensiva pactada, un turno partido mal fichado o una guardia. Por eso
 * un aviso nace ABIERTO y solo cuenta para la bolsa anual del art. 35.2
 * ET cuando alguien lo ACEPTA.
 *
 * Los dos umbrales, y por qué son distintos:
 *
 * <ul>
 *   <li><b>Diario</b> ({@literal >} 9 h efectivas, art. 34.3 ET): un
 *       límite legal fijo. No depende de la jornada contratada — quien
 *       trabaja a media jornada se pasa de las nueve horas igual que
 *       quien la tiene completa.</li>
 *   <li><b>Semanal</b> ({@literal >} la jornada contratada
 *       <b>prorrateada por los días hábiles reales de esa semana</b>):
 *       aquí el listón se mueve. Una semana con un festivo entre medias
 *       no son 37,5 h sino 30, y medirla contra 37,5 daría un falso
 *       negativo permanente en cada puente; medirla contra un número
 *       fijo más bajo daría el error contrario. Se descuentan los
 *       festivos del calendario (Fase C) y las ausencias aprobadas.</li>
 * </ul>
 *
 * Y una regla que atraviesa las dos: <b>el proceso nocturno no deshace
 * decisiones humanas.</b> Recalcula y corrige los avisos ABIERTO — puede
 * subirles los minutos, bajárselos o retirarlos — pero uno JUSTIFICADO o
 * ACEPTADO no se toca nunca, aunque los fichajes de debajo hayan
 * cambiado. Si alguien miró ese martes y decidió, esa decisión manda.
 */
@Service
@Transactional(readOnly = true)
public class OvertimeServiceImpl implements OvertimeService {

    private static final Logger log = LoggerFactory.getLogger(OvertimeServiceImpl.class);

    private static final String REVISAR = "horasextra:revisar";

    /**
     * La zona en la que se agrupan los días. Los fichajes se guardan en
     * UTC (ADR 002) y "el martes" solo existe una vez proyectados aquí.
     */
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final OvertimeAlertRepository overtimeRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final AbsenceRequestRepository absenceRepository;
    private final UserRepository userRepository;
    private final WorkingDayService workingDayService;
    private final ApplicationEventPublisher eventPublisher;

    public OvertimeServiceImpl(
            OvertimeAlertRepository overtimeRepository,
            TimeEntryRepository timeEntryRepository,
            AbsenceRequestRepository absenceRepository,
            UserRepository userRepository,
            WorkingDayService workingDayService,
            ApplicationEventPublisher eventPublisher) {
        this.overtimeRepository = overtimeRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.absenceRepository = absenceRepository;
        this.userRepository = userRepository;
        this.workingDayService = workingDayService;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Override
    public List<OvertimeAlertResponse> mios(User actor, int anio) {
        return overtimeRepository
                .findDeUsuarioEnRango(actor.getId(), primerDia(anio), ultimoDia(anio))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<OvertimeAlertResponse> delEquipo(User actor, int anio) {
        return overtimeRepository
                .findDeEmpresaEnRango(actor.getEmpresa().getId(), primerDia(anio), ultimoDia(anio))
                .stream()
                // Los suyos propios NO salen en la bandeja de revisión, y
                // eso lo decide aquí el servidor a propósito. La bandeja
                // es "lo que te toca decidir", y sobre lo tuyo no puedes
                // decidir nunca: dejarlos dentro pondría en pantalla dos
                // botones que el propio servicio va a rechazar con 403.
                //
                // Podría filtrarlo el cliente, y sería el mismo error que
                // la Fase E ya evitó con `puedoResolver`: una regla de
                // quién puede qué, duplicada fuera, acaba discrepando.
                // Sus avisos siguen estando en {@link #mios}, donde salen
                // sin botones.
                .filter(aviso -> aviso.getUsuario().getId() != actor.getId())
                .map(this::toResponse)
                .toList();
    }

    @Override
    public OvertimeBalanceResponse bolsa(User actor, Long usuarioId, int anio) {
        User dueno = actor;
        if (usuarioId != null && usuarioId != actor.getId()) {
            // Mirar la bolsa de otra persona es una operación de
            // revisión, aunque solo se lea: son sus horas.
            if (!tiene(actor, REVISAR)) {
                throw new TenantAccessException("No puedes ver la bolsa de horas extra de otra persona.");
            }
            dueno = userRepository.findById(usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado."));
            if (dueno.getEmpresa().getId() != actor.getEmpresa().getId()) {
                throw new TenantAccessException("Ese empleado es de otra empresa.");
            }
        }

        int consumidos = overtimeRepository
                .sumarMinutosAceptados(dueno.getId(), primerDia(anio), ultimoDia(anio));

        return new OvertimeBalanceResponse(
                anio,
                OvertimeCalculator.MINUTOS_BOLSA_ANUAL,
                consumidos,
                OvertimeCalculator.minutosRestantesDeLaBolsa(consumidos),
                overtimeRepository.countByUsuario_IdAndEstado(dueno.getId(), OvertimeStatus.ABIERTO),
                OvertimeCalculator.bolsaCercaDelLimite(consumidos));
    }

    // ------------------------------------------------------------------
    // Revisión
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public OvertimeAlertResponse revisar(long id, ReviewOvertimeRequest request, User actor) {
        OvertimeAlert aviso = overtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aviso de horas extra no encontrado."));

        if (aviso.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Ese aviso es de otra empresa.");
        }
        if (!tiene(actor, REVISAR)) {
            throw new TenantAccessException("No puedes revisar avisos de horas extra.");
        }
        // Nadie decide sobre sus propias horas extra, tenga la authority
        // que tenga. Es el mismo conflicto de interés que la Fase E ya
        // resolvió para las correcciones, y por la misma razón: aceptar
        // las horas extra que hiciste ayer es cobrarlas, y justificarlas
        // es hacerlas desaparecer.
        if (aviso.getUsuario().getId() == actor.getId()) {
            throw new TenantAccessException("No puedes revisar tus propias horas extra.");
        }
        if (aviso.getEstado() != OvertimeStatus.ABIERTO) {
            throw new BusinessException("Ese aviso ya está revisado.", HttpStatus.CONFLICT);
        }

        boolean aceptar = Boolean.TRUE.equals(request.aceptar());
        String justificacion = request.justificacion() == null ? null : request.justificacion().trim();

        // Aceptar no necesita explicación: el reloj ya ha medido. Decir
        // que once horas trabajadas NO son horas extra sí — es la
        // decisión que un inspector querría ver motivada.
        if (!aceptar && (justificacion == null || justificacion.isBlank())) {
            throw new BusinessException(
                    "Hay que explicar por qué ese exceso no cuenta como horas extra.",
                    HttpStatus.BAD_REQUEST);
        }

        int consumidosAntes = minutosConsumidosDelAnio(aviso.getUsuario().getId(), aviso.getFecha().getYear());

        aviso.setEstado(aceptar ? OvertimeStatus.ACEPTADO : OvertimeStatus.JUSTIFICADO);
        aviso.setJustificacion(justificacion);
        aviso.setRevisadoPor(actor);
        aviso.setFechaRevision(Instant.now());
        overtimeRepository.save(aviso);

        avisarSiLaBolsaAcabaDeLlegarAlLimite(aviso, consumidosAntes, aceptar);

        return toResponse(aviso);
    }

    /**
     * El aviso de bolsa se manda SOLO al cruzar el umbral, no cada vez
     * que se acepta algo estando ya por encima. Si no, quien lleve un año
     * apurado recibiría el mismo mensaje en cada revisión y dejaría de
     * leerlos — que es la forma habitual de que un aviso importante se
     * pierda.
     */
    private void avisarSiLaBolsaAcabaDeLlegarAlLimite(
            OvertimeAlert aviso, int consumidosAntes, boolean aceptado) {
        if (!aceptado) {
            return;
        }
        int consumidosAhora = consumidosAntes + aviso.getMinutosExtra();
        boolean cruzaAhora = !OvertimeCalculator.bolsaCercaDelLimite(consumidosAntes)
                && OvertimeCalculator.bolsaCercaDelLimite(consumidosAhora);
        if (!cruzaAhora) {
            return;
        }

        eventPublisher.publishEvent(new NotificationEvents.OvertimeBalanceNearLimit(
                aviso.getUsuario(),
                aviso.getFecha().getYear(),
                consumidosAhora,
                OvertimeCalculator.minutosRestantesDeLaBolsa(consumidosAhora),
                quienLoHizoYQuienRevisa(aviso)));
    }

    private int minutosConsumidosDelAnio(long usuarioId, int anio) {
        return overtimeRepository.sumarMinutosAceptados(usuarioId, primerDia(anio), ultimoDia(anio));
    }

    // ------------------------------------------------------------------
    // Detección (proceso nocturno)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int detectar(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            return 0;
        }

        List<TimeEntryRepository.DailyWorkProjection> jornadas =
                timeEntryRepository.sumarSegundosPorUsuarioYDia(
                        inicioDelDia(desde), inicioDelDia(hasta.plusDays(1)));

        Map<Long, List<TimeEntryRepository.DailyWorkProjection>> porUsuario = new HashMap<>();
        for (TimeEntryRepository.DailyWorkProjection jornada : jornadas) {
            porUsuario.computeIfAbsent(jornada.getUsuarioId(), k -> new ArrayList<>()).add(jornada);
        }

        Map<Long, Set<LocalDate>> ausencias = ausenciasPorUsuario(desde, hasta);

        // Lo que este barrido ha encontrado. Sirve para el paso final:
        // retirar los avisos ABIERTO que ya no proceden.
        Set<String> excesosEncontrados = new HashSet<>();
        int nuevos = 0;

        for (Map.Entry<Long, List<TimeEntryRepository.DailyWorkProjection>> entrada : porUsuario.entrySet()) {
            Optional<User> quizaUsuario = userRepository.findById(entrada.getKey());
            if (quizaUsuario.isEmpty()) {
                continue;
            }
            User usuario = quizaUsuario.get();
            List<TimeEntryRepository.DailyWorkProjection> dias = entrada.getValue();

            nuevos += revisarDiasDe(usuario, dias, excesosEncontrados);
            nuevos += revisarSemanasDe(
                    usuario, dias, ausencias.getOrDefault(usuario.getId(), Set.of()),
                    desde, hasta, excesosEncontrados);
        }

        retirarLosQueYaNoProceden(desde, hasta, excesosEncontrados);

        log.info("Barrido de horas extra {} .. {}: {} avisos nuevos o actualizados.", desde, hasta, nuevos);
        return nuevos;
    }

    /** Umbral diario: 9 h efectivas, igual para todo el mundo. */
    private int revisarDiasDe(
            User usuario,
            List<TimeEntryRepository.DailyWorkProjection> dias,
            Set<String> encontrados) {

        int tocados = 0;
        for (TimeEntryRepository.DailyWorkProjection dia : dias) {
            long minutos = dia.getSegundos() / 60;
            int exceso = OvertimeCalculator.excesoDiario(minutos);
            if (exceso == 0) {
                continue;
            }
            encontrados.add(clave(usuario.getId(), dia.getDia(), OvertimeType.DIARIA));
            TimeEntry registro = timeEntryRepository.getReferenceById(dia.getUltimoRegistroId());
            if (guardar(usuario, dia.getDia(), OvertimeType.DIARIA, exceso,
                    OvertimeCalculator.MINUTOS_MAXIMOS_POR_JORNADA, registro)) {
                tocados++;
            }
        }
        return tocados;
    }

    /**
     * Umbral semanal, con el listón prorrateado.
     *
     * Solo se miran semanas <b>completas y ya terminadas</b> que caigan
     * enteras dentro del rango barrido. Una semana a medias siempre
     * estaría por debajo de su objetivo, así que evaluarla no daría
     * falsos positivos — daría algo peor: un aviso que aparece el
     * miércoles con unos minutos y cambia cada noche hasta el domingo.
     */
    private int revisarSemanasDe(
            User usuario,
            List<TimeEntryRepository.DailyWorkProjection> dias,
            Set<LocalDate> diasDeAusencia,
            LocalDate desde,
            LocalDate hasta,
            Set<String> encontrados) {

        Map<LocalDate, Long> minutosPorSemana = new HashMap<>();
        for (TimeEntryRepository.DailyWorkProjection dia : dias) {
            LocalDate lunes = lunesDe(dia.getDia());
            minutosPorSemana.merge(lunes, dia.getSegundos() / 60, Long::sum);
        }

        LocalDate hoy = LocalDate.now(MADRID);
        int tocados = 0;

        for (Map.Entry<LocalDate, Long> semana : minutosPorSemana.entrySet()) {
            LocalDate lunes = semana.getKey();
            LocalDate domingo = lunes.plusDays(6);

            boolean terminada = domingo.isBefore(hoy);
            boolean dentroDelBarrido = !lunes.isBefore(desde) && !domingo.isAfter(hasta);
            if (!terminada || !dentroDelBarrido) {
                continue;
            }

            int habiles = diasHabilesDeLaSemana(usuario.getEmpresa(), lunes, domingo, diasDeAusencia);
            int exceso = OvertimeCalculator.excesoSemanal(
                    semana.getValue(), usuario.getHorasSemanales(), habiles);
            if (exceso == 0) {
                continue;
            }

            encontrados.add(clave(usuario.getId(), lunes, OvertimeType.SEMANAL));
            long esperados = OvertimeCalculator.objetivoSemanal(usuario.getHorasSemanales(), habiles);
            if (guardar(usuario, lunes, OvertimeType.SEMANAL, exceso, (int) esperados, null)) {
                tocados++;
            }
        }
        return tocados;
    }

    /**
     * Días hábiles de la semana menos los que la persona tenía cubiertos
     * por una ausencia aprobada.
     *
     * El descuento se hace sobre los hábiles y no sobre los siete días,
     * que es la diferencia entre restar bien y restar dos veces: unas
     * vacaciones de viernes a lunes cubren cuatro fechas pero solo dos
     * días de trabajo.
     */
    private int diasHabilesDeLaSemana(
            Company empresa, LocalDate lunes, LocalDate domingo, Set<LocalDate> diasDeAusencia) {
        Set<LocalDate> habiles = new LinkedHashSet<>(
                workingDayService.diasHabiles(empresa, lunes, domingo));
        habiles.removeAll(diasDeAusencia);
        return habiles.size();
    }

    /** Las fechas cubiertas por una ausencia aprobada, por persona. */
    private Map<Long, Set<LocalDate>> ausenciasPorUsuario(LocalDate desde, LocalDate hasta) {
        Map<Long, Set<LocalDate>> porUsuario = new HashMap<>();
        for (AbsenceRequest ausencia : absenceRepository.findAprobadasEnRango(desde, hasta)) {
            Set<LocalDate> fechas = porUsuario
                    .computeIfAbsent(ausencia.getUsuario().getId(), k -> new HashSet<>());
            // Se recorta al rango barrido: una baja de tres meses no
            // tiene por qué materializar noventa fechas para responder
            // sobre una semana.
            LocalDate inicio = ausencia.getFechaInicio().isBefore(desde) ? desde : ausencia.getFechaInicio();
            LocalDate fin = ausencia.getFechaFin().isAfter(hasta) ? hasta : ausencia.getFechaFin();
            for (LocalDate fecha = inicio; !fecha.isAfter(fin); fecha = fecha.plusDays(1)) {
                fechas.add(fecha);
            }
        }
        return porUsuario;
    }

    /**
     * Crea el aviso, o actualiza el que ya hubiera si sigue ABIERTO.
     *
     * @return true si algo cambió de verdad. Un aviso idéntico al que ya
     *     estaba no cuenta: el barrido corre cada noche sobre los mismos
     *     catorce días, y contarlos todos haría que el log dijera lo
     *     mismo siempre y no significara nada.
     */
    private boolean guardar(
            User usuario, LocalDate fecha, OvertimeType tipo,
            int minutosExtra, int minutosEsperados, TimeEntry registro) {

        Optional<OvertimeAlert> existente =
                overtimeRepository.findByUsuario_IdAndFechaAndTipo(usuario.getId(), fecha, tipo);

        if (existente.isPresent()) {
            OvertimeAlert aviso = existente.get();
            // Una decisión humana no se pisa. Ver el Javadoc de la clase.
            if (aviso.getEstado() != OvertimeStatus.ABIERTO) {
                return false;
            }
            if (aviso.getMinutosExtra() == minutosExtra
                    && aviso.getMinutosEsperados() == minutosEsperados) {
                return false;
            }
            aviso.setMinutosExtra(minutosExtra);
            aviso.setMinutosEsperados(minutosEsperados);
            aviso.setRegistro(registro);
            overtimeRepository.save(aviso);
            return true;
        }

        OvertimeAlert aviso = overtimeRepository.save(OvertimeAlert.builder()
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .registro(registro)
                .fecha(fecha)
                .minutosExtra(minutosExtra)
                .minutosEsperados(minutosEsperados)
                .tipo(tipo)
                .estado(OvertimeStatus.ABIERTO)
                .build());

        // Solo se notifica al crear. Un aviso que sube de 40 a 45 minutos
        // porque se corrigió el fichaje no merece un correo nuevo.
        //
        // Y no se notifica a quien está de baja o dado de alta en otra
        // parte: un ex-empleado no puede entrar a mirarlo, así que el
        // correo solo serviría para llegar a un buzón que ya no es suyo.
        if (usuario.isActivo()) {
            eventPublisher.publishEvent(new NotificationEvents.OvertimeDetected(
                    aviso, destinatariosDe(aviso)));
        }
        return true;
    }

    /**
     * Retira los avisos ABIERTO cuyo exceso ya no existe.
     *
     * Hace falta un paso aparte porque un día que se queda sin fichajes
     * válidos —lo que pasa cuando una corrección de la Fase E anula el
     * original— desaparece del agregado, así que el bucle de arriba no
     * vuelve a pasar por él y el aviso se quedaría ahí para siempre,
     * acusando de horas que ya no constan en ningún sitio.
     */
    private void retirarLosQueYaNoProceden(LocalDate desde, LocalDate hasta, Set<String> encontrados) {
        List<OvertimeAlert> abiertos = overtimeRepository
                .findByEstadoAndFechaBetween(OvertimeStatus.ABIERTO, desde, hasta);

        List<OvertimeAlert> caducados = abiertos.stream()
                .filter(a -> !encontrados.contains(
                        clave(a.getUsuario().getId(), a.getFecha(), a.getTipo())))
                .toList();

        if (caducados.isEmpty()) {
            return;
        }
        overtimeRepository.deleteAll(caducados);
        log.info("Retirados {} avisos de horas extra que ya no proceden.", caducados.size());
    }

    private static String clave(long usuarioId, LocalDate fecha, OvertimeType tipo) {
        return usuarioId + "|" + fecha + "|" + tipo;
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    /**
     * A quién avisa un exceso detectado: solo a quien lo hizo.
     *
     * No a los gestores, y no por descuido: una empresa mediana genera
     * decenas de excesos al mes, y un correo por cada uno a cada gestor
     * es spam por diseño. Quien revisa trabaja desde la cola
     * ({@code GET /api/v1/horas-extra/equipo} y el contador del panel),
     * que es como se lleva un trabajo recurrente. Ver el Javadoc de
     * {@code NotificationEvents.OvertimeDetected}.
     *
     * La bolsa al límite sí va a los dos lados: eso pasa como mucho una
     * vez al año por persona y afecta a lo que la empresa puede seguir
     * pidiendo.
     */
    private List<User> destinatariosDe(OvertimeAlert aviso) {
        return List.of(aviso.getUsuario());
    }

    /**
     * Para el aviso de bolsa al límite: el empleado y quien revisa.
     *
     * Aquí sí van los dos, porque no es lo mismo. Un exceso suelto pasa
     * cada semana; agotar el tope anual del art. 35.2 ET pasa como mucho
     * una vez al año y cambia lo que la empresa puede seguir pidiéndole a
     * esa persona el resto del ejercicio.
     */
    private List<User> quienLoHizoYQuienRevisa(OvertimeAlert aviso) {
        Set<User> destinatarios = new LinkedHashSet<>();
        if (aviso.getUsuario().isActivo()) {
            destinatarios.add(aviso.getUsuario());
        }
        destinatarios.addAll(revisoresDe(aviso.getEmpresa()));
        return List.copyOf(destinatarios);
    }

    /**
     * Se resuelven AQUÍ, dentro de la transacción, y no en el listener:
     * ese corre {@code @Async} y con la sesión de JPA ya cerrada, así que
     * no puede salir a buscar a nadie. Es la misma regla que siguen todos
     * los eventos de {@link NotificationEvents}.
     */
    private List<User> revisoresDe(Company empresa) {
        return userRepository.findByEmpresa(empresa).stream()
                .filter(User::isActivo)
                .filter(u -> tiene(u, REVISAR))
                .toList();
    }

    private boolean tiene(User usuario, String authority) {
        // A RoleAuthorities y no al SecurityContext: es la misma fuente
        // que alimenta los @PreAuthorize, y sirve para usuarios que no
        // son quien hace la petición.
        return RoleAuthorities.forRole(usuario.getRol()).contains(authority);
    }

    private static LocalDate lunesDe(LocalDate fecha) {
        return fecha.with(DayOfWeek.MONDAY);
    }

    private static LocalDate primerDia(int anio) {
        return LocalDate.of(anio, 1, 1);
    }

    private static LocalDate ultimoDia(int anio) {
        return LocalDate.of(anio, 12, 31);
    }

    private static Instant inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(MADRID).toInstant();
    }

    private OvertimeAlertResponse toResponse(OvertimeAlert aviso) {
        boolean semanal = aviso.getTipo() == OvertimeType.SEMANAL;
        return new OvertimeAlertResponse(
                aviso.getId(),
                aviso.getUsuario().getId(),
                aviso.getUsuario().getNombre(),
                aviso.getTipo().name(),
                aviso.getFecha(),
                semanal ? aviso.getFecha().plusDays(6) : aviso.getFecha(),
                aviso.getMinutosExtra(),
                aviso.getMinutosEsperados(),
                aviso.getRegistro() == null ? null : aviso.getRegistro().getId(),
                aviso.getEstado().name(),
                aviso.getJustificacion(),
                aviso.getRevisadoPor() == null ? null : aviso.getRevisadoPor().getNombre(),
                aviso.getFechaRevision());
    }
}
