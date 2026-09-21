package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.DeletionRequest;
import com.nxtime.nxtime.domain.DeletionStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DeletionCandidate;
import com.nxtime.nxtime.dto.DeletionRequestDTO;
import com.nxtime.nxtime.dto.DeletionResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.Destinatarios;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.DeletionRequestRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.DataDeletionService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link DataDeletionService} y el ADR 016. Lo que borra de verdad está
 * en {@link PersonalDataEraser}; aquí están las reglas de cuándo se puede.
 */
@Service
@Transactional(readOnly = true)
public class DataDeletionServiceImpl implements DataDeletionService {

    private static final Logger log = LoggerFactory.getLogger(DataDeletionServiceImpl.class);

    /** Quien ejecuta o rechaza: RRHH y ADMIN, los mismos que dan de baja. */
    static final String AUTHORITY_EJECUTAR = "empleado:gestionar";

    /** RD-ley 8/2019: el registro horario se conserva cuatro años. */
    static final int ANIOS_DE_CONSERVACION = 4;

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final DeletionRequestRepository repository;
    private final UserRepository userRepository;
    private final PersonalDataEraser eraser;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public DataDeletionServiceImpl(
            DeletionRequestRepository repository,
            UserRepository userRepository,
            PersonalDataEraser eraser,
            ApplicationEventPublisher eventPublisher) {
        this(repository, userRepository, eraser, eventPublisher, Clock.systemUTC());
    }

    DataDeletionServiceImpl(
            DeletionRequestRepository repository,
            UserRepository userRepository,
            PersonalDataEraser eraser,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.eraser = eraser;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DeletionResponse solicitar(User persona, DeletionRequestDTO peticion) {
        // Se comprueba para dar un 409 que se entienda; quien lo garantiza
        // de verdad, también ante dos toques seguidos, es el índice único
        // parcial uq_borrados_una_pendiente_por_usuario.
        if (repository.findByUsuarioAndEstado(persona, DeletionStatus.PENDIENTE).isPresent()) {
            throw new BusinessException("Ya tienes una solicitud de borrado pendiente.");
        }

        DeletionRequest solicitud = repository.save(DeletionRequest.builder()
                .empresa(persona.getEmpresa())
                .usuario(persona)
                .motivo(vacioComoNulo(peticion != null ? peticion.motivo() : null))
                .creadaEn(Instant.now(clock))
                .build());

        // Dentro de la transacción: el listener corre @Async y sin sesión.
        // Se excluye a la persona: un ADMIN que pide lo suyo no tiene que
        // recibir el aviso de que "alguien" lo pide.
        List<User> destinatarios = Destinatarios.conAuthority(
                userRepository, persona.getEmpresa(), AUTHORITY_EJECUTAR, persona);
        if (destinatarios.isEmpty()) {
            // Nadie más con la authority: nadie podrá ejecutarla. No se
            // impide pedirla -- el derecho existe igual --, pero tiene que
            // quedar visto.
            log.warn("Solicitud de borrado {} en la empresa {} sin nadie que pueda ejecutarla.",
                    solicitud.getId(), persona.getEmpresa().getId());
        }
        eventPublisher.publishEvent(new NotificationEvents.DeletionRequested(solicitud, destinatarios));

        return aRespuesta(solicitud, List.of());
    }

    @Override
    @Transactional
    public DeletionResponse registrar(User actor, long usuarioId, String comoLlego) {
        User persona = userRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        if (persona.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Esa persona no pertenece a tu empresa.");
        }
        if (persona.getId() == actor.getId()) {
            // El CHECK de V21 también lo impide; aquí se explica.
            throw new BusinessException("Para pedir el borrado de tus propios datos, hazlo desde Ajustes.");
        }
        String texto = vacioComoNulo(comoLlego);
        if (texto == null) {
            throw new BusinessException("Hay que indicar cómo llegó la solicitud.", HttpStatus.BAD_REQUEST);
        }
        if (repository.existsByUsuarioAndEstado(persona, DeletionStatus.EJECUTADA)) {
            throw new BusinessException("Los datos de esta persona ya se borraron.");
        }
        if (repository.existsByUsuarioAndEstado(persona, DeletionStatus.PENDIENTE)) {
            throw new BusinessException("Esta persona ya tiene una solicitud de borrado pendiente.");
        }

        DeletionRequest solicitud = repository.save(DeletionRequest.builder()
                .empresa(persona.getEmpresa())
                .usuario(persona)
                .registradaPor(actor)
                .motivo(texto)
                .creadaEn(Instant.now(clock))
                .build());

        // A los demás que pueden ejecutarla, para que haya otro par de ojos.
        // Ni a quien la registra (ya lo sabe) ni a la persona.
        List<User> destinatarios = Destinatarios.conAuthority(
                        userRepository, persona.getEmpresa(), AUTHORITY_EJECUTAR, actor).stream()
                .filter(u -> u.getId() != persona.getId())
                .toList();
        eventPublisher.publishEvent(new NotificationEvents.DeletionRequested(solicitud, destinatarios));
        eventPublisher.publishEvent(new NotificationEvents.DeletionRegistered(
                persona.getEmail(), persona.getNombre(), persona.getEmpresa().getNombre()));

        log.info("Solicitud de borrado {} registrada para el usuario {} por el usuario {}.",
                solicitud.getId(), persona.getId(), actor.getId());
        return aRespuesta(solicitud, List.of());
    }

    @Override
    public List<DeletionCandidate> candidatos(User actor) {
        return repository.findCandidatos(actor.getEmpresa().getId(), actor.getId()).stream()
                .map(u -> new DeletionCandidate(u.getId(), nombreCompleto(u), u.getEmail(), u.isActivo()))
                .toList();
    }

    @Override
    public Optional<DeletionResponse> miUltimaSolicitud(User persona) {
        return repository.findFirstByUsuarioOrderByCreadaEnDesc(persona)
                .map(solicitud -> aRespuesta(solicitud, List.of()));
    }

    @Override
    @Transactional
    public DeletionResponse cancelar(User persona) {
        DeletionRequest solicitud = repository.findByUsuarioAndEstado(persona, DeletionStatus.PENDIENTE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No tienes ninguna solicitud de borrado pendiente."));
        solicitud.setEstado(DeletionStatus.CANCELADA);
        // El CHECK exige que en una CANCELADA la resuelva la propia persona.
        solicitud.setResueltaPor(solicitud.getUsuario());
        solicitud.setResueltaEn(Instant.now(clock));
        return aRespuesta(repository.save(solicitud), List.of());
    }

    @Override
    public List<DeletionResponse> pendientes(User actor) {
        return repository.findByEmpresa_IdAndEstadoOrderByCreadaEnAsc(
                        actor.getEmpresa().getId(), DeletionStatus.PENDIENTE).stream()
                .map(solicitud -> aRespuesta(solicitud, bloqueos(solicitud, actor)))
                .toList();
    }

    @Override
    public int contarPendientes(User actor) {
        return (int) repository.countByEmpresa_IdAndEstado(actor.getEmpresa().getId(), DeletionStatus.PENDIENTE);
    }

    @Override
    @Transactional
    public DeletionResponse ejecutar(long solicitudId, User actor) {
        DeletionRequest solicitud = pendienteDeMiEmpresa(solicitudId, actor);

        List<String> bloqueos = bloqueos(solicitud, actor);
        if (!bloqueos.isEmpty()) {
            throw new BusinessException("Todavía no se puede ejecutar. " + String.join(" ", bloqueos));
        }

        User persona = solicitud.getUsuario();
        long usuarioId = persona.getId();
        // Se copian ANTES de purgar: es lo que necesita el correo, y después
        // la entidad cargada ya no coincide con lo que hay en la base.
        String email = persona.getEmail();
        String nombre = persona.getNombre();
        String empresa = persona.getEmpresa().getNombre();

        LocalDate anonimizarDesde = anonimizarDesde(usuarioId);
        Map<String, Integer> borrado = eraser.purgar(usuarioId);

        solicitud.setEstado(DeletionStatus.EJECUTADA);
        solicitud.setResueltaPor(actor);
        solicitud.setResueltaEn(Instant.now(clock));
        solicitud.setAnonimizarDesde(anonimizarDesde);
        repository.save(solicitud);

        // Sin datos de la persona en el log: con los ids basta para cruzarlo.
        log.info("Borrado {} ejecutado sobre el usuario {} por el usuario {}. Filas borradas: {}. "
                        + "Anonimizar desde {}.",
                solicitud.getId(), usuarioId, actor.getId(), borrado, anonimizarDesde);

        eventPublisher.publishEvent(
                new NotificationEvents.DeletionExecuted(email, nombre, empresa, anonimizarDesde));
        return aRespuesta(solicitud, List.of());
    }

    @Override
    @Transactional
    public DeletionResponse rechazar(long solicitudId, String comentario, User actor) {
        DeletionRequest solicitud = pendienteDeMiEmpresa(solicitudId, actor);
        if (solicitud.getUsuario().getId() == actor.getId()) {
            throw new BusinessException("No puedes resolver tu propia solicitud de borrado.");
        }
        String texto = vacioComoNulo(comentario);
        if (texto == null) {
            throw new BusinessException("Hay que explicar por qué no se ejecuta el borrado.",
                    HttpStatus.BAD_REQUEST);
        }

        solicitud.setEstado(DeletionStatus.RECHAZADA);
        solicitud.setResueltaPor(actor);
        solicitud.setResueltaEn(Instant.now(clock));
        solicitud.setComentarioResolucion(texto);
        repository.save(solicitud);

        eventPublisher.publishEvent(new NotificationEvents.DeletionRejected(solicitud));
        return aRespuesta(solicitud, List.of());
    }

    @Override
    @Transactional
    public int anonimizarVencidas(LocalDate hoy) {
        List<DeletionRequest> vencidas = repository.findPorAnonimizar(hoy);
        for (DeletionRequest solicitud : vencidas) {
            eraser.anonimizar(solicitud.getUsuario().getId());
            // El motivo lo escribió la persona: también se va.
            solicitud.setMotivo(null);
            solicitud.setAnonimizadaEn(Instant.now(clock));
            repository.save(solicitud);
            log.info("Usuario {} anonimizado (solicitud de borrado {}).",
                    solicitud.getUsuario().getId(), solicitud.getId());
        }
        return vencidas.size();
    }

    /**
     * Lo que impide ejecutar hoy, en frases para quien decide. Vacía si se
     * puede.
     *
     * Cada regla está porque ejecutar dejaría algo roto o a medias:
     * <ul>
     *   <li>La propia: la empresa tiene que comprobar, y quien comprueba no
     *       puede ser quien pide.</li>
     *   <li>Jornada abierta: la cuenta se desactiva y nadie la cerraría.</li>
     *   <li>Correcciones o ausencias sin resolver: esperarían la respuesta de
     *       alguien que ya no puede entrar a darla.</li>
     *   <li>Denuncias abiertas: la persona perdería el hilo del expediente, y
     *       quien instruye, a quién preguntar.</li>
     *   <li>El único ADMIN activo: la empresa se quedaría sin nadie que pueda
     *       gestionar roles. Antes hay que nombrar a otro.</li>
     * </ul>
     */
    List<String> bloqueos(DeletionRequest solicitud, User actor) {
        User persona = solicitud.getUsuario();
        long usuarioId = persona.getId();
        List<String> bloqueos = new ArrayList<>();

        if (usuarioId == actor.getId()) {
            bloqueos.add("Es tu propia solicitud: la tiene que ejecutar otra persona.");
        }
        if (repository.contarJornadasAbiertas(usuarioId) > 0) {
            bloqueos.add("Tiene una jornada abierta.");
        }
        long correcciones = repository.contarCorreccionesVivas(usuarioId);
        if (correcciones > 0) {
            bloqueos.add(correcciones == 1
                    ? "Tiene 1 corrección de fichaje sin resolver."
                    : "Tiene " + correcciones + " correcciones de fichaje sin resolver.");
        }
        long ausencias = repository.contarAusenciasPendientes(usuarioId);
        if (ausencias > 0) {
            bloqueos.add(ausencias == 1
                    ? "Tiene 1 petición de ausencia pendiente."
                    : "Tiene " + ausencias + " peticiones de ausencia pendientes.");
        }
        if (repository.contarDenunciasAbiertas(usuarioId) > 0) {
            bloqueos.add("Tiene una denuncia abierta en el canal interno.");
        }
        if (persona.getRol() == Role.ADMIN && persona.isActivo()
                && repository.contarAdminsActivos(persona.getEmpresa().getId()) <= 1) {
            bloqueos.add("Es el único administrador activo de la empresa: antes hay que nombrar a otro.");
        }
        return bloqueos;
    }

    /**
     * Cuatro años desde el día del último fichaje, en hora de España. Sin
     * fichajes, desde hoy: no hay registro que conservar, pero la solicitud
     * tiene que llevar fecha (el CHECK lo exige) y así la regla es una sola.
     */
    private LocalDate anonimizarDesde(long usuarioId) {
        LocalDate base = repository.ultimaEntrada(usuarioId)
                .map(instante -> instante.atZone(MADRID).toLocalDate())
                .orElseGet(() -> LocalDate.now(clock.withZone(MADRID)));
        return base.plusYears(ANIOS_DE_CONSERVACION);
    }

    private DeletionRequest pendienteDeMiEmpresa(long solicitudId, User actor) {
        DeletionRequest solicitud = repository.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de borrado no encontrada."));
        if (solicitud.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("La solicitud de borrado no pertenece a tu empresa.");
        }
        if (solicitud.getEstado() != DeletionStatus.PENDIENTE) {
            throw new BusinessException("La solicitud ya está resuelta.");
        }
        return solicitud;
    }

    private DeletionResponse aRespuesta(DeletionRequest solicitud, List<String> bloqueos) {
        User persona = solicitud.getUsuario();
        return new DeletionResponse(
                solicitud.getId(),
                persona.getId(),
                nombreCompleto(persona),
                persona.getEmail(),
                solicitud.getEstado().name(),
                solicitud.getMotivo(),
                solicitud.getRegistradaPor() != null ? solicitud.getRegistradaPor().getNombre() : null,
                solicitud.getCreadaEn(),
                solicitud.getResueltaPor() != null ? solicitud.getResueltaPor().getNombre() : null,
                solicitud.getResueltaEn(),
                solicitud.getComentarioResolucion(),
                solicitud.getAnonimizarDesde(),
                bloqueos);
    }

    private static String nombreCompleto(User persona) {
        return persona.getApellidos() == null || persona.getApellidos().isBlank()
                ? persona.getNombre()
                : persona.getNombre() + " " + persona.getApellidos();
    }

    private static String vacioComoNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
