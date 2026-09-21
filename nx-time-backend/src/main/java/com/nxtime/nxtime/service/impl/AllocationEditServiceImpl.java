package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AllocationsResponse;
import com.nxtime.nxtime.dto.ClockProjectsResponse;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.SetAllocationsRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.CorrectionRequestRepository;
import com.nxtime.nxtime.repository.ProjectAllocationRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.service.AllocationEditService;
import com.nxtime.nxtime.service.CorrectionService;
import com.nxtime.nxtime.service.ProjectAllocationService;
import com.nxtime.nxtime.service.ValidadorDeReparto;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.time.temporal.TemporalAdjusters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ver {@link AllocationEditService}. */
@Service
@Transactional(readOnly = true)
public class AllocationEditServiceImpl implements AllocationEditService {

    private static final Logger log = LoggerFactory.getLogger(AllocationEditServiceImpl.class);

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final String VER_EQUIPO = "fichaje:leer:equipo";

    private final TimeEntryRepository timeEntryRepository;
    private final ProjectAllocationRepository allocationRepository;
    private final ProjectAllocationService projectAllocationService;
    private final CorrectionRequestRepository correctionRepository;
    private final CorrectionService correctionService;
    private final TimeEntrySnapshotSerializer snapshotSerializer;
    private final ApplicationEventPublisher eventPublisher;
    private final ValidadorDeReparto validadorDeReparto;
    private final Clock clock;

    @Autowired
    public AllocationEditServiceImpl(
            TimeEntryRepository timeEntryRepository,
            ProjectAllocationRepository allocationRepository,
            ProjectAllocationService projectAllocationService,
            CorrectionRequestRepository correctionRepository,
            CorrectionService correctionService,
            TimeEntrySnapshotSerializer snapshotSerializer,
            ApplicationEventPublisher eventPublisher,
            ValidadorDeReparto validadorDeReparto) {
        this(timeEntryRepository, allocationRepository, projectAllocationService, correctionRepository,
                correctionService, snapshotSerializer, eventPublisher, validadorDeReparto, Clock.systemUTC());
    }

    AllocationEditServiceImpl(
            TimeEntryRepository timeEntryRepository,
            ProjectAllocationRepository allocationRepository,
            ProjectAllocationService projectAllocationService,
            CorrectionRequestRepository correctionRepository,
            CorrectionService correctionService,
            TimeEntrySnapshotSerializer snapshotSerializer,
            ApplicationEventPublisher eventPublisher,
            ValidadorDeReparto validadorDeReparto,
            Clock clock) {
        this.timeEntryRepository = timeEntryRepository;
        this.allocationRepository = allocationRepository;
        this.projectAllocationService = projectAllocationService;
        this.correctionRepository = correctionRepository;
        this.correctionService = correctionService;
        this.snapshotSerializer = snapshotSerializer;
        this.eventPublisher = eventPublisher;
        this.validadorDeReparto = validadorDeReparto;
        this.clock = clock;
    }

    @Override
    public AllocationsResponse deLaJornada(long fichajeId, User actor) {
        TimeEntry fichaje = cargar(fichajeId, actor);
        boolean esMio = fichaje.getUsuario().getId() == actor.getId();
        if (!esMio && !RoleAuthorities.tiene(actor, VER_EQUIPO)) {
            throw new TenantAccessException("No puedes ver el reparto del fichaje de otra persona.");
        }
        return respuesta(fichaje);
    }

    @Override
    @Transactional
    public Resultado repartir(long fichajeId, SetAllocationsRequest peticion, User actor) {
        TimeEntry fichaje = cargar(fichajeId, actor);
        // Repartir el fichaje de OTRA persona no existe por esta vía, igual que
        // con las pausas (ADR 015): para eso está pedir una corrección.
        if (fichaje.getUsuario().getId() != actor.getId()) {
            throw new TenantAccessException(
                    "Solo puedes repartir las horas de tus propios fichajes.");
        }
        if (fichaje.isAnulado()) {
            throw new BusinessException("Este fichaje ya fue corregido; reparte su versión nueva.");
        }
        if (fichaje.getHoraSalida() == null) {
            throw new BusinessException(
                    "La jornada sigue abierta. Usa \"Cambiar de proyecto\" mientras trabajas.");
        }
        correctionRepository.findVivaDelRegistro(fichajeId).ifPresent(viva -> {
            throw new BusinessException("Ese fichaje tiene una corrección sin resolver. Espera a que se resuelva.");
        });

        Map<Project, Long> reparto = validarYResolver(fichaje, peticion);
        long segundos = reparto.values().stream().mapToLong(Long::longValue).sum();
        long neto = ProjectAllocationServiceImpl.neto(fichaje);

        if (segundos < neto) {
            throw new BusinessException(
                    "El reparto suma menos de lo trabajado. Quitar horas es corregir el fichaje: pide una corrección.",
                    HttpStatus.BAD_REQUEST);
        }
        if (segundos == neto && esDeLaSemanaEnCurso(fichaje)) {
            String antes = snapshotSerializer.toJson(fichaje);
            projectAllocationService.aplicarReparto(fichaje, reparto);
            anotar(fichaje, actor, antes, descripcion(reparto));
            log.info("{} reparte las horas del fichaje {}: {}", actor.getEmail(), fichajeId, descripcion(reparto));
            return new Resultado(respuesta(fichaje), null);
        }

        // Fuera de plazo, o pidiendo más horas de las fichadas: lo aprueba un
        // gestor por el circuito de correcciones, con el reparto dentro.
        String motivo = peticion.motivo() != null ? peticion.motivo().trim() : "";
        if (motivo.isBlank()) {
            throw new BusinessException(
                    segundos > neto
                            ? "El reparto suma más de lo fichado: explica por qué para que lo apruebe un gestor."
                            : "Esa jornada ya no es de esta semana: explica por qué cambia el reparto.",
                    HttpStatus.BAD_REQUEST);
        }
        // Si se piden más horas, la salida se amplía lo justo para que quepan.
        Instant salida = segundos == neto
                ? fichaje.getHoraSalida()
                : fichaje.getHoraEntrada().plusSeconds(segundos + fichaje.getSegundosPausaAcumulados());
        var solicitud = correctionService.solicitar(fichajeId, new CorrectionRequestDTO(
                fichaje.getHoraEntrada(), salida, motivo, null, null, lineas(reparto)), actor);
        return new Resultado(null, solicitud);
    }

    // ------------------------------------------------------------------

    /**
     * El reparto pedido, con los proyectos resueltos.
     *
     * Las reglas viven en {@link ValidadorDeReparto} y no aquí desde
     * septiembre de 2026: estaban solo en este camino, y el de las
     * correcciones --que también acepta un reparto-- se las saltaba entero.
     */
    private Map<Project, Long> validarYResolver(TimeEntry fichaje, SetAllocationsRequest peticion) {
        return validadorDeReparto.validarYResolver(fichaje, peticion.lineas().stream()
                .map(linea -> new ValidadorDeReparto.Linea(linea.proyectoId(), linea.minutos()))
                .toList());
    }

    /** Lunes a domingo, en hora de España: el plazo del reparto libre. */
    private boolean esDeLaSemanaEnCurso(TimeEntry fichaje) {
        LocalDate dia = fichaje.getHoraEntrada().atZone(MADRID).toLocalDate();
        LocalDate lunes = LocalDate.now(clock.withZone(MADRID))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return !dia.isBefore(lunes);
    }

    private AllocationsResponse respuesta(TimeEntry fichaje) {
        LocalDate dia = fichaje.getHoraEntrada().atZone(MADRID).toLocalDate();
        List<AllocationsResponse.Linea> lineas = allocationRepository.findByRegistroOrderByIdAsc(fichaje).stream()
                .map(i -> new AllocationsResponse.Linea(
                        i.getProyecto().getId(), i.getProyecto().getCodigo(), i.getProyecto().getNombre(),
                        i.getSegundos() / 60))
                .toList();
        List<ClockProjectsResponse.ProjectOption> disponibles =
                projectAllocationService.proyectosDelDia(fichaje.getUsuario(), dia).stream()
                        .map(p -> new ClockProjectsResponse.ProjectOption(p.getId(), p.getCodigo(), p.getNombre()))
                        .toList();
        Long solicitudViva = correctionRepository.findVivaDelRegistro(fichaje.getId())
                .map(viva -> viva.getId())
                .orElse(null);
        return new AllocationsResponse(
                fichaje.getId(),
                ProjectAllocationServiceImpl.neto(fichaje) / 60,
                esDeLaSemanaEnCurso(fichaje),
                lineas,
                disponibles,
                solicitudViva);
    }

    private static List<CorrectionRequestDTO.ProjectShare> lineas(Map<Project, Long> reparto) {
        return reparto.entrySet().stream()
                .map(e -> new CorrectionRequestDTO.ProjectShare(e.getKey().getId(), e.getValue() / 60))
                .toList();
    }

    private static String descripcion(Map<Project, Long> reparto) {
        return reparto.entrySet().stream()
                .map(e -> e.getKey().getCodigo() + " " + (e.getValue() / 60) + " min")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private void anotar(TimeEntry fichaje, User actor, String antes, String detalle) {
        eventPublisher.publishEvent(new TimeEntryAuditEvent(TimeEntryAudit.builder()
                .registro(fichaje)
                .usuario(fichaje.getUsuario())
                .modificadoPor(actor)
                .accion(AuditAction.REPARTO_PROYECTOS)
                .valorAnterior(antes)
                .valorNuevo(snapshotSerializer.toJson(fichaje))
                .motivo("Reparto por proyecto: " + detalle)
                .build()));
    }

    private TimeEntry cargar(long fichajeId, User actor) {
        TimeEntry fichaje = timeEntryRepository.findById(fichajeId)
                .orElseThrow(() -> new ResourceNotFoundException("Fichaje no encontrado."));
        if (fichaje.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Ese fichaje no es de tu empresa.");
        }
        return fichaje;
    }
}
