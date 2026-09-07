package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.DisputeRequest;
import com.nxtime.nxtime.dto.ResolveCorrectionRequest;
import com.nxtime.nxtime.dto.SimpleUserDTO;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.CorrectionRequestRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.CorrectionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solicitudes de corrección (Fase E).
 *
 * <b>La regla que ordena toda la clase: quién resuelve depende de quién
 * pidió.</b> Está en {@link #puedeResolver} y en ningún sitio más — se
 * consulta desde crear, resolver, disputar y listar, y tenerla repetida
 * es como una de las cuatro copias se queda atrás.
 *
 * <ul>
 *   <li>La pide el dueño → resuelve alguien con {@code correccion:aprobar}.</li>
 *   <li>La pide otro → resuelve <b>el dueño</b>, a quien le cambian sus
 *       horas. Si no está de acuerdo, DISPUTA (no rechaza).</li>
 *   <li>EN_DISPUTA → resuelve {@code correccion:disputa:resolver} (RRHH),
 *       que está por encima de las dos partes.</li>
 *   <li>La pide el dueño teniendo {@code correccion:aprobar} → se
 *       auto-aprueba, y la traza lo dice.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CorrectionServiceImpl implements CorrectionService {

    private static final Logger log = LoggerFactory.getLogger(CorrectionServiceImpl.class);

    private static final String APROBAR = "correccion:aprobar";
    private static final String RESOLVER_DISPUTAS = "correccion:disputa:resolver";
    private static final String CORREGIR_AJENO = "fichaje:corregir";

    private final CorrectionRequestRepository correctionRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;
    private final TimeEntrySnapshotSerializer snapshotSerializer;
    private final ApplicationEventPublisher eventPublisher;

    public CorrectionServiceImpl(
            CorrectionRequestRepository correctionRepository,
            TimeEntryRepository timeEntryRepository,
            UserRepository userRepository,
            TimeEntrySnapshotSerializer snapshotSerializer,
            ApplicationEventPublisher eventPublisher) {
        this.correctionRepository = correctionRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.userRepository = userRepository;
        this.snapshotSerializer = snapshotSerializer;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------
    // Pedir
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public CorrectionResponse solicitar(long fichajeId, CorrectionRequestDTO request, User actor) {
        TimeEntry fichaje = timeEntryRepository.findById(fichajeId)
                .orElseThrow(() -> new ResourceNotFoundException("Fichaje no encontrado."));

        if (fichaje.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes corregir fichajes de otra empresa.");
        }
        // Pedir una corrección sobre el fichaje de otra persona es una
        // operación distinta de pedirla sobre el propio, y por eso exige
        // su authority: es lo que antes hacía RRHH directamente.
        boolean esMio = fichaje.getUsuario().getId() == actor.getId();
        if (!esMio && !tiene(actor, CORREGIR_AJENO)) {
            throw new TenantAccessException("Solo puedes pedir correcciones de tus propios fichajes.");
        }
        if (fichaje.isAnulado()) {
            throw new BusinessException(
                    "Este fichaje ya fue corregido antes; corrige la nueva versión, no la original.");
        }
        if (fichaje.getHoraSalida() == null) {
            throw new BusinessException("No se puede corregir una jornada activa; ciérrala primero.");
        }
        if (!request.horaSalida().isAfter(request.horaEntrada())) {
            throw new BusinessException(
                    "La hora de salida corregida debe ser posterior a la de entrada.",
                    HttpStatus.BAD_REQUEST);
        }

        // Se mira antes para dar un 409 que se entienda; quien lo impide
        // de verdad es el índice único parcial de la base, que además
        // cubre dos peticiones simultáneas.
        correctionRepository.findVivaDelRegistro(fichajeId).ifPresent(viva -> {
            throw new BusinessException(
                    "Ese fichaje ya tiene una solicitud de corrección sin resolver.");
        });

        CorrectionRequest solicitud = CorrectionRequest.builder()
                .empresa(actor.getEmpresa())
                .registro(fichaje)
                .solicitante(actor)
                .horaEntradaPropuesta(request.horaEntrada())
                .horaSalidaPropuesta(request.horaSalida())
                .motivo(request.motivo().trim())
                .estado(CorrectionStatus.PENDIENTE)
                .creadoEn(Instant.now())
                .build();

        solicitud = guardarControlandoLaCarrera(solicitud);

        anotarEnLaTraza(solicitud, AuditAction.SOLICITUD_CORRECCION, actor, request.motivo());

        /*
         * Auto-aprobación: quien pide es el dueño Y puede aprobar.
         *
         * No es un atajo por comodidad. Sin ella, un ADMIN —que no tiene
         * a nadie por encima— no podría corregir jamás su propio fichaje:
         * la solicitud se quedaría esperando a alguien que no existe. Lo
         * que la hace aceptable es que queda escrita como tal en la
         * traza, con su motivo, igual que cualquier otra.
         */
        if (esMio && tiene(actor, APROBAR)) {
            log.info("{} se auto-aprueba la corrección del fichaje {}", actor.getEmail(), fichajeId);
            return aplicar(solicitud, actor, "Auto-aprobada por el propio empleado.");
        }

        eventPublisher.publishEvent(new NotificationEvents.CorrectionRequested(
                solicitud, destinatariosDeLaSolicitud(solicitud)));
        return toResponse(solicitud, actor);
    }

    // ------------------------------------------------------------------
    // Resolver
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public CorrectionResponse resolver(long correccionId, ResolveCorrectionRequest request, User actor) {
        CorrectionRequest solicitud = deLaMismaEmpresa(correccionId, actor);

        if (!solicitud.getEstado().estaViva()) {
            throw new BusinessException("Esa solicitud ya está resuelta.");
        }
        if (!puedeResolver(solicitud, actor)) {
            throw new TenantAccessException("No te toca a ti resolver esa corrección.");
        }
        if (Boolean.FALSE.equals(request.aprobada())
                && (request.comentario() == null || request.comentario().isBlank())) {
            // Misma regla que al rechazar una ausencia: decir que no sin
            // decir por qué deja a la otra persona sin nada que hacer.
            throw new BusinessException(
                    "Al rechazar una corrección hay que explicar por qué.", HttpStatus.BAD_REQUEST);
        }

        if (Boolean.TRUE.equals(request.aprobada())) {
            return aplicar(solicitud, actor, request.comentario());
        }

        solicitud.setEstado(CorrectionStatus.RECHAZADA);
        solicitud.setAprobador(actor);
        solicitud.setFechaResolucion(Instant.now());
        solicitud.setComentarioResolucion(request.comentario().trim());
        correctionRepository.save(solicitud);

        // El rechazo se anota aunque el fichaje NO cambie, y ahí está su
        // valor: sin esto, un intento de corrección rechazado no dejaría
        // ningún rastro y la traza solo contaría los cambios que salieron
        // adelante.
        anotarEnLaTraza(solicitud, AuditAction.RECHAZO_CORRECCION, actor, request.comentario());

        eventPublisher.publishEvent(new NotificationEvents.CorrectionResolved(
                solicitud, List.of(solicitud.getSolicitante())));
        log.info("{} ha rechazado la corrección {}", actor.getEmail(), correccionId);
        return toResponse(solicitud, actor);
    }

    // ------------------------------------------------------------------
    // Disputar
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public CorrectionResponse disputar(long correccionId, DisputeRequest request, User actor) {
        CorrectionRequest solicitud = deLaMismaEmpresa(correccionId, actor);

        if (solicitud.getEstado() != CorrectionStatus.PENDIENTE) {
            throw new BusinessException("Solo se puede disputar una solicitud pendiente.");
        }
        // Disputar es "no acepto que me cambien MIS horas", así que solo
        // tiene sentido para el dueño del fichaje.
        if (solicitud.getDuenoDelFichaje().getId() != actor.getId()) {
            throw new TenantAccessException("Solo el dueño del fichaje puede disputar la corrección.");
        }
        // Y solo sobre lo que ha pedido OTRO: disputar la corrección que
        // has pedido tú no significa nada -- para eso está no pedirla.
        if (solicitud.laPidioElDueno()) {
            throw new BusinessException(
                    "Esta corrección la pediste tú: no hay nada que disputar.", HttpStatus.BAD_REQUEST);
        }

        solicitud.setEstado(CorrectionStatus.EN_DISPUTA);
        solicitud.setMotivoDisputa(request.motivo().trim());
        correctionRepository.save(solicitud);

        anotarEnLaTraza(solicitud, AuditAction.DISPUTA, actor, request.motivo());

        eventPublisher.publishEvent(new NotificationEvents.CorrectionDisputed(
                solicitud, quienResuelveDisputas(actor)));
        log.info("{} ha disputado la corrección {}", actor.getEmail(), correccionId);
        return toResponse(solicitud, actor);
    }

    // ------------------------------------------------------------------
    // Listar
    // ------------------------------------------------------------------

    @Override
    public List<CorrectionResponse> pendientesParaMi(User actor) {
        // Se traen todas las vivas de la empresa y se filtran por la
        // misma regla que decide si se puede resolver. Filtrarlo en SQL
        // exigiría repetir esa regla en JPQL -- dos copias de lo más
        // delicado de la fase. El volumen lo permite: son las que están
        // sin resolver, no el histórico.
        return correctionRepository.findVivasDeEmpresa(actor.getEmpresa().getId()).stream()
                .filter(solicitud -> puedeResolver(solicitud, actor))
                .map(solicitud -> toResponse(solicitud, actor))
                .toList();
    }

    @Override
    public List<CorrectionResponse> mias(User actor) {
        return correctionRepository.findMias(actor.getId()).stream()
                .map(solicitud -> toResponse(solicitud, actor))
                .toList();
    }

    // ------------------------------------------------------------------
    // El corazón: quién resuelve qué
    // ------------------------------------------------------------------

    /**
     * Si a {@code actor} le toca resolver esta solicitud.
     *
     * Es la única definición de la regla en todo el proyecto. El DTO la
     * expone como {@code puedoResolver} para que la app no tenga que
     * reimplementarla.
     */
    private boolean puedeResolver(CorrectionRequest solicitud, User actor) {
        if (solicitud.getEmpresa().getId() != actor.getEmpresa().getId()) {
            return false;
        }
        if (solicitud.getEstado() == CorrectionStatus.EN_DISPUTA) {
            // La discrepancia es entre el empleado y quien lleva su
            // equipo: la resuelve alguien por encima de los dos.
            return tiene(actor, RESOLVER_DISPUTAS);
        }
        if (solicitud.laPidioElDueno()) {
            // Nadie se aprueba a sí mismo por esta vía: si el dueño
            // pudiera aprobar, ya se auto-aprobó al pedirla.
            return tiene(actor, APROBAR) && solicitud.getSolicitante().getId() != actor.getId();
        }
        // Se la piden a él: decide el dueño del fichaje.
        return solicitud.getDuenoDelFichaje().getId() == actor.getId();
    }

    /** Solo el dueño, y solo cuando la pidió otro (ver {@link #disputar}). */
    private boolean puedeDisputar(CorrectionRequest solicitud, User actor) {
        return solicitud.getEstado() == CorrectionStatus.PENDIENTE
                && !solicitud.laPidioElDueno()
                && solicitud.getDuenoDelFichaje().getId() == actor.getId();
    }

    // ------------------------------------------------------------------

    /**
     * Aplica la corrección: crea el fichaje corregido y anula el
     * original.
     *
     * Es lo que antes hacía {@code TimeEntryServiceImpl.correctTimeEntry}
     * directamente desde el PATCH. Ahora solo se llega aquí con una
     * aprobación detrás, que es el cambio de fondo de la fase.
     */
    private CorrectionResponse aplicar(CorrectionRequest solicitud, User actor, String comentario) {
        TimeEntry original = solicitud.getRegistro();

        // Puede haber pasado tiempo desde que se pidió: si mientras tanto
        // otra corrección anuló el fichaje, aplicar esta crearía una
        // segunda versión "buena" del mismo día.
        if (original.isAnulado()) {
            throw new BusinessException(
                    "El fichaje ya se corrigió por otra vía mientras esta solicitud esperaba.");
        }

        String antes = snapshotSerializer.toJson(original);

        TimeEntry corregido = timeEntryRepository.save(TimeEntry.builder()
                .usuario(original.getUsuario())
                .empresa(original.getEmpresa())
                .horaEntrada(solicitud.getHoraEntradaPropuesta())
                .horaSalida(solicitud.getHoraSalidaPropuesta())
                .registroOriginal(original)
                .build());

        original.setAnulado(true);
        timeEntryRepository.save(original);

        solicitud.setEstado(CorrectionStatus.APROBADA);
        solicitud.setAprobador(actor);
        solicitud.setFechaResolucion(Instant.now());
        solicitud.setComentarioResolucion(
                comentario != null && !comentario.isBlank() ? comentario.trim() : null);
        correctionRepository.save(solicitud);

        eventPublisher.publishEvent(new TimeEntryAuditEvent(TimeEntryAudit.builder()
                .registro(original)
                .usuario(original.getUsuario())
                .modificadoPor(actor)
                .accion(AuditAction.CORRECCION)
                .valorAnterior(antes)
                .valorNuevo(snapshotSerializer.toJson(corregido))
                .motivo(solicitud.getMotivo())
                .build()));

        eventPublisher.publishEvent(new NotificationEvents.CorrectionResolved(
                solicitud, List.of(solicitud.getSolicitante())));

        log.info("Corrección {} aplicada por {}: fichaje {} -> {}",
                solicitud.getId(), actor.getEmail(), original.getId(), corregido.getId());
        return toResponse(solicitud, actor);
    }

    /**
     * Guarda la solicitud traduciendo el choque del índice único parcial.
     *
     * Dos peticiones simultáneas sobre el mismo fichaje pasan las dos la
     * comprobación previa; la segunda la para la base. Sin esto llegaría
     * al cliente como un 500.
     */
    private CorrectionRequest guardarControlandoLaCarrera(CorrectionRequest solicitud) {
        try {
            return correctionRepository.saveAndFlush(solicitud);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    "Ese fichaje ya tiene una solicitud de corrección sin resolver.");
        }
    }

    /**
     * Deja constancia en la traza del FICHAJE, no de la solicitud.
     *
     * Va ahí a propósito: quien audite un fichaje tiene que ver todo lo
     * que se intentó hacer con él, salga o no adelante. La cadena de
     * hashes la cierra {@code TimeEntryAuditListener}, como siempre.
     */
    private void anotarEnLaTraza(
            CorrectionRequest solicitud, AuditAction accion, User actor, String motivo) {
        eventPublisher.publishEvent(new TimeEntryAuditEvent(TimeEntryAudit.builder()
                .registro(solicitud.getRegistro())
                .usuario(solicitud.getDuenoDelFichaje())
                .modificadoPor(actor)
                .accion(accion)
                .valorAnterior(snapshotSerializer.toJson(solicitud.getRegistro()))
                .valorNuevo(propuestaComoJson(solicitud))
                .motivo(motivo)
                .build()));
    }

    /** Lo que se PROPONE, con la misma forma que un fichaje real. */
    private String propuestaComoJson(CorrectionRequest solicitud) {
        return snapshotSerializer.toJson(TimeEntry.builder()
                .usuario(solicitud.getDuenoDelFichaje())
                .empresa(solicitud.getEmpresa())
                .horaEntrada(solicitud.getHoraEntradaPropuesta())
                .horaSalida(solicitud.getHoraSalidaPropuesta())
                .build());
    }

    /** A quién avisar de una solicitud nueva: a quien pueda resolverla. */
    private List<User> destinatariosDeLaSolicitud(CorrectionRequest solicitud) {
        if (!solicitud.laPidioElDueno()) {
            // Se la piden al dueño: es él quien decide.
            return List.of(solicitud.getDuenoDelFichaje());
        }
        return conAuthority(solicitud.getEmpresa(), APROBAR, solicitud.getSolicitante());
    }

    private List<User> quienResuelveDisputas(User actor) {
        return conAuthority(actor.getEmpresa(), RESOLVER_DISPUTAS, null);
    }

    /**
     * La gente de la empresa que tiene una authority, excluyendo a quien
     * se indique (normalmente, quien acaba de hacer la acción: avisarse a
     * uno mismo de lo que acaba de hacer solo genera ruido).
     */
    private List<User> conAuthority(Company empresa, String authority, User excluido) {
        List<User> destinatarios = new ArrayList<>();
        for (User candidato : userRepository.findByEmpresa(empresa)) {
            // Una cuenta de baja no tiene que recibir avisos de algo que
            // ya no puede resolver.
            if (!candidato.isActivo()) {
                continue;
            }
            if (excluido != null && candidato.getId() == excluido.getId()) {
                continue;
            }
            if (tiene(candidato, authority)) {
                destinatarios.add(candidato);
            }
        }
        return destinatarios;
    }

    private boolean tiene(User usuario, String authority) {
        // Se pregunta a RoleAuthorities y no al SecurityContext: es la
        // misma fuente que alimenta los @PreAuthorize, así que no puede
        // decir una cosa distinta. Y sirve para usuarios que NO son quien
        // hace la petición (los destinatarios de un aviso).
        return RoleAuthorities.forRole(usuario.getRol()).contains(authority);
    }

    private CorrectionRequest deLaMismaEmpresa(long id, User actor) {
        CorrectionRequest solicitud = correctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de corrección no encontrada."));
        if (solicitud.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Esa solicitud es de otra empresa.");
        }
        return solicitud;
    }

    private CorrectionResponse toResponse(CorrectionRequest solicitud, User actor) {
        TimeEntry fichaje = solicitud.getRegistro();
        return new CorrectionResponse(
                solicitud.getId(),
                fichaje.getId(),
                new SimpleUserDTO(solicitud.getDuenoDelFichaje().getNombre()),
                new SimpleUserDTO(solicitud.getSolicitante().getNombre()),
                fichaje.getHoraEntrada(),
                fichaje.getHoraSalida(),
                solicitud.getHoraEntradaPropuesta(),
                solicitud.getHoraSalidaPropuesta(),
                solicitud.getMotivo(),
                solicitud.getEstado(),
                solicitud.getAprobador() != null
                        ? new SimpleUserDTO(solicitud.getAprobador().getNombre()) : null,
                solicitud.getFechaResolucion(),
                solicitud.getComentarioResolucion(),
                solicitud.getMotivoDisputa(),
                solicitud.getCreadoEn(),
                puedeResolver(solicitud, actor),
                puedeDisputar(solicitud, actor));
    }
}
