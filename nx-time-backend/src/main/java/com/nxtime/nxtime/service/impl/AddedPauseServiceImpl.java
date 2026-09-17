package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AddedPause;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AddPauseRequest;
import com.nxtime.nxtime.dto.AddedPauseDTO;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.SimpleUserDTO;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AddedPauseRepository;
import com.nxtime.nxtime.repository.CorrectionRequestRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.service.AddedPauseService;
import com.nxtime.nxtime.service.CorrectionService;
import com.nxtime.nxtime.service.ReglasDePausa;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link AddedPauseService} y ADR 015.
 */
@Service
@Transactional(readOnly = true)
public class AddedPauseServiceImpl implements AddedPauseService {

    private static final Logger log = LoggerFactory.getLogger(AddedPauseServiceImpl.class);

    /**
     * "Hoy" es el hoy de Madrid, que es donde vive la jornada laboral. Es la
     * misma zona con la que los agregados parten los días.
     */
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final String VER_EQUIPO = "fichaje:leer:equipo";

    private final TimeEntryRepository timeEntryRepository;
    private final AddedPauseRepository addedPauseRepository;
    private final CorrectionRequestRepository correctionRepository;
    private final CorrectionService correctionService;
    private final TimeEntrySnapshotSerializer snapshotSerializer;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public AddedPauseServiceImpl(
            TimeEntryRepository timeEntryRepository,
            AddedPauseRepository addedPauseRepository,
            CorrectionRequestRepository correctionRepository,
            CorrectionService correctionService,
            TimeEntrySnapshotSerializer snapshotSerializer,
            ApplicationEventPublisher eventPublisher) {
        this(timeEntryRepository, addedPauseRepository, correctionRepository, correctionService,
                snapshotSerializer, eventPublisher, Clock.systemUTC());
    }

    AddedPauseServiceImpl(
            TimeEntryRepository timeEntryRepository,
            AddedPauseRepository addedPauseRepository,
            CorrectionRequestRepository correctionRepository,
            CorrectionService correctionService,
            TimeEntrySnapshotSerializer snapshotSerializer,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.timeEntryRepository = timeEntryRepository;
        this.addedPauseRepository = addedPauseRepository;
        this.correctionRepository = correctionRepository;
        this.correctionService = correctionService;
        this.snapshotSerializer = snapshotSerializer;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Añadir
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Resultado anadir(long fichajeId, AddPauseRequest request, User actor) {
        TimeEntry fichaje = cargar(fichajeId, actor);

        // Añadir una pausa al fichaje de OTRA persona no existe por esta vía.
        // Es cambiarle sus horas, y eso ya tiene su camino con sus garantías:
        // una corrección, que exige 'fichaje:corregir' y que ella puede
        // disputar.
        if (fichaje.getUsuario().getId() != actor.getId()) {
            throw new TenantAccessException(
                    "Solo puedes añadir pausas a tus propios fichajes. "
                            + "Para el de otra persona, pide una corrección.");
        }
        if (fichaje.isAnulado()) {
            throw new BusinessException(
                    "Este fichaje ya fue corregido; añade la pausa a su versión nueva.");
        }

        if (!esViaDirecta(fichaje)) {
            // Día pasado: se pide como corrección, con las horas tal cual.
            // Todo lo demás —validar, aprobar, disputar, avisar, auto-aprobar
            // a quien puede— lo pone el mecanismo que ya existe.
            var solicitud = correctionService.solicitar(fichajeId, new CorrectionRequestDTO(
                    fichaje.getHoraEntrada(),
                    fichaje.getHoraSalida(),
                    request.motivo(),
                    request.inicio(),
                    request.fin()), actor);
            log.info("{} pide añadir una pausa al fichaje {} (día pasado): solicitud {}",
                    actor.getEmail(), fichajeId, solicitud.id());
            return new Resultado(null, solicitud);
        }

        // Directa, pero no si hay una corrección esperando: sumar una pausa
        // mientras hay unas horas propuestas pendientes dejaría esa propuesta
        // evaluada contra un fichaje que ya no es el que era.
        correctionRepository.findVivaDelRegistro(fichajeId).ifPresent(viva -> {
            throw new BusinessException(
                    "Ese fichaje tiene una corrección sin resolver. Espera a que se resuelva.");
        });

        Instant ahora = clock.instant();
        Instant limite = fichaje.getHoraSalida() != null ? fichaje.getHoraSalida() : ahora;
        List<AddedPause> vivas = addedPauseRepository.findByRegistroAndAnuladaFalseOrderByInicioAsc(fichaje);

        ReglasDePausa.exigirValida(
                request.inicio(), request.fin(),
                fichaje.getHoraEntrada(), limite,
                segundosDePausaConLaEnCurso(fichaje, ahora),
                vivas,
                fichaje.isEnPausa() ? fichaje.getInicioPausaActual() : null,
                ahora);

        String antes = snapshotSerializer.toJson(fichaje);

        AddedPause pausa = addedPauseRepository.save(AddedPause.builder()
                .empresa(fichaje.getEmpresa())
                .registro(fichaje)
                .inicio(request.inicio())
                .fin(request.fin())
                .motivo(request.motivo().trim())
                .creadaPor(actor)
                .creadaEn(ahora)
                .build());

        fichaje.setSegundosPausaAcumulados(fichaje.getSegundosPausaAcumulados() + pausa.getSegundos());
        TimeEntry guardado = timeEntryRepository.save(fichaje);

        anotar(guardado, actor, AuditAction.PAUSA_ANADIDA, antes, request.motivo().trim());
        log.info("{} añade una pausa de {} min al fichaje {}",
                actor.getEmail(), pausa.getSegundos() / 60, fichajeId);
        return new Resultado(guardado, null);
    }

    // ------------------------------------------------------------------
    // Consultar
    // ------------------------------------------------------------------

    @Override
    public List<AddedPauseDTO> deLaJornada(long fichajeId, User actor) {
        TimeEntry fichaje = cargar(fichajeId, actor);
        boolean esMio = fichaje.getUsuario().getId() == actor.getId();
        if (!esMio && !RoleAuthorities.forRole(actor.getRol()).contains(VER_EQUIPO)) {
            throw new TenantAccessException("No puedes ver las pausas del fichaje de otra persona.");
        }
        return addedPauseRepository.findByRegistroAndAnuladaFalseOrderByInicioAsc(fichaje).stream()
                .map(p -> new AddedPauseDTO(
                        p.getId(),
                        p.getInicio(),
                        p.getFin(),
                        p.getSegundos() / 60,
                        p.getMotivo(),
                        new SimpleUserDTO(p.getCreadaPor().getNombre()),
                        p.getCreadaEn(),
                        p.getSolicitud() != null))
                .toList();
    }

    // ------------------------------------------------------------------
    // Deshacer
    // ------------------------------------------------------------------

    /*
     * Solo sobre la jornada abierta, y la frontera es deliberada: una
     * jornada abierta todavía no ha entrado en ningún informe ni en el
     * cálculo de horas extra, así que deshacer no cambia nada que alguien
     * haya visto. Una vez cerrada, quitar una pausa SUBE el tiempo
     * trabajado, y eso ya no es autoservicio: se pide una corrección.
     */
    @Override
    @Transactional
    public TimeEntry anular(long fichajeId, long pausaId, User actor) {
        TimeEntry fichaje = cargar(fichajeId, actor);
        if (fichaje.getUsuario().getId() != actor.getId()) {
            throw new TenantAccessException("Solo puedes deshacer pausas de tus propios fichajes.");
        }
        if (fichaje.getHoraSalida() != null) {
            throw new BusinessException(
                    "La jornada ya está cerrada. Para quitar una pausa, pide una corrección.");
        }

        AddedPause pausa = addedPauseRepository.findById(pausaId)
                .filter(p -> p.getRegistro().getId() == fichajeId)
                .orElseThrow(() -> new ResourceNotFoundException("Esa pausa no es de este fichaje."));
        if (pausa.isAnulada()) {
            throw new BusinessException("Esa pausa ya estaba deshecha.");
        }

        String antes = snapshotSerializer.toJson(fichaje);
        Instant ahora = clock.instant();

        pausa.setAnulada(true);
        pausa.setAnuladaPor(actor);
        pausa.setAnuladaEn(ahora);
        addedPauseRepository.save(pausa);

        fichaje.setSegundosPausaAcumulados(
                Math.max(0, fichaje.getSegundosPausaAcumulados() - pausa.getSegundos()));
        TimeEntry guardado = timeEntryRepository.save(fichaje);

        anotar(guardado, actor, AuditAction.PAUSA_ANULADA, antes,
                "Deshace la pausa añadida: " + pausa.getMotivo());
        return guardado;
    }

    // ------------------------------------------------------------------
    // Apoyo
    // ------------------------------------------------------------------

    /**
     * Directa si la jornada sigue abierta, o si se cerró y empezó hoy.
     *
     * Por la hora de ENTRADA y no por la de salida, a propósito: los
     * agregados asignan cada jornada al día en que empezó, así que "la
     * jornada de hoy" es la que empezó hoy, termine cuando termine.
     */
    boolean esViaDirecta(TimeEntry fichaje) {
        if (fichaje.getHoraSalida() == null) {
            return true;
        }
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), MADRID);
        return LocalDate.ofInstant(fichaje.getHoraEntrada(), MADRID).equals(hoy);
    }

    private TimeEntry cargar(long fichajeId, User actor) {
        TimeEntry fichaje = timeEntryRepository.findById(fichajeId)
                .orElseThrow(() -> new ResourceNotFoundException("Fichaje no encontrado."));
        if (fichaje.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Ese fichaje es de otra empresa.");
        }
        return fichaje;
    }

    /** La pausa en curso todavía no está en el contador: se suma al vuelo. */
    private static long segundosDePausaConLaEnCurso(TimeEntry fichaje, Instant ahora) {
        long acumulado = fichaje.getSegundosPausaAcumulados();
        if (fichaje.isEnPausa() && fichaje.getInicioPausaActual() != null) {
            acumulado += Duration.between(fichaje.getInicioPausaActual(), ahora).getSeconds();
        }
        return acumulado;
    }

    private void anotar(TimeEntry fichaje, User actor, AuditAction accion, String antes, String motivo) {
        eventPublisher.publishEvent(new TimeEntryAuditEvent(TimeEntryAudit.builder()
                .registro(fichaje)
                .usuario(fichaje.getUsuario())
                .modificadoPor(actor)
                .accion(accion)
                .valorAnterior(antes)
                .valorNuevo(snapshotSerializer.toJson(fichaje))
                .motivo(motivo)
                .build()));
    }
}
