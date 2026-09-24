package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.ScheduleAssignment;
import com.nxtime.nxtime.domain.ScheduleIncident;
import com.nxtime.nxtime.domain.ScheduleIncidentStatus;
import com.nxtime.nxtime.domain.ScheduleIncidentType;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ScheduleIncidentResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.Destinatarios;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.ScheduleAssignmentRepository;
import com.nxtime.nxtime.repository.ScheduleIncidentRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.JornadaTeoricaService.DiaTeorico;
import com.nxtime.nxtime.service.ReglasDeCuadrante;
import com.nxtime.nxtime.service.ReglasDeCuadrante.FichajeDelDia;
import com.nxtime.nxtime.service.ReglasDeCuadrante.IncidenciaDetectada;
import com.nxtime.nxtime.service.ScheduleIncidentService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ver {@link ScheduleIncidentService}. */
@Service
@Transactional(readOnly = true)
public class ScheduleIncidentServiceImpl implements ScheduleIncidentService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleIncidentServiceImpl.class);

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    static final String REVISAR = "cuadrante:incidencias:revisar";

    /**
     * Tope de la bandeja. Sin paginar todavía (llega con la Fase A7, junto con
     * las otras listas), pero tampoco sin límite: una empresa que asigna
     * cuadrantes y no revisa nunca acumularía miles.
     */
    static final int TOPE_BANDEJA = 200;

    private final ScheduleIncidentRepository incidentRepository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;
    private final JornadaTeoricaService jornadaTeoricaService;
    private final ApplicationEventPublisher eventPublisher;

    public ScheduleIncidentServiceImpl(
            ScheduleIncidentRepository incidentRepository,
            ScheduleAssignmentRepository assignmentRepository,
            TimeEntryRepository timeEntryRepository,
            UserRepository userRepository,
            JornadaTeoricaService jornadaTeoricaService,
            ApplicationEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.assignmentRepository = assignmentRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.userRepository = userRepository;
        this.jornadaTeoricaService = jornadaTeoricaService;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------
    // El barrido
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int detectar(LocalDate desde, LocalDate hasta) {
        Map<Company, Set<User>> nuevasPorEmpresa = new LinkedHashMap<>();
        Map<Company, Integer> cuantasPorEmpresa = new LinkedHashMap<>();
        Map<User, List<NotificationEvents.IncidenciaNueva>> nuevasPorPersona = new LinkedHashMap<>();
        int nuevas = 0;

        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            for (ScheduleIncident creada : detectarElDia(dia)) {
                nuevas++;
                User persona = creada.getUsuario();
                nuevasPorEmpresa.computeIfAbsent(persona.getEmpresa(), e -> new LinkedHashSet<>()).add(persona);
                cuantasPorEmpresa.merge(persona.getEmpresa(), 1, Integer::sum);
                nuevasPorPersona.computeIfAbsent(persona, p -> new ArrayList<>()).add(
                        new NotificationEvents.IncidenciaNueva(creada.getTipo(), creada.getFecha(),
                                creada.getMinutos(), ReglasDeCuadrante.hora(creada.getHoraPrevista())));
            }
        }

        // A quien las tiene, un aviso por barrido y no uno por incidencia:
        // quien llega tarde y sale pronto el mismo día no recibe dos correos,
        // ni veinte la noche que una corrección mueve dos semanas.
        nuevasPorPersona.forEach((persona, suyas) -> eventPublisher.publishEvent(
                new NotificationEvents.ScheduleIncidentDetected(persona.getEmpresa().getId(), suyas, List.of(persona))));
        avisarAQuienRevisa(nuevasPorEmpresa, cuantasPorEmpresa);
        log.info("Barrido de incidencias {} .. {}: {} nuevas.", desde, hasta, nuevas);
        return nuevas;
    }

    /**
     * Un día, en lotes: quién tenía cuadrante, su horario teórico, lo que
     * fichó y lo que ya había detectado. Un puñado de consultas por día,
     * sean diez personas o mil. Devuelve las incidencias CREADAS.
     */
    private List<ScheduleIncident> detectarElDia(LocalDate dia) {
        Map<String, ScheduleIncident> existentes = new HashMap<>();
        for (ScheduleIncident incidencia : incidentRepository.findDelDia(dia)) {
            existentes.put(clave(incidencia.getUsuario().getId(), incidencia.getTipo()), incidencia);
        }

        List<User> personas = assignmentRepository.findVigentesEl(dia).stream()
                .map(ScheduleAssignment::getUsuario)
                .filter(User::isActivo)
                .distinct()
                .toList();

        Set<String> encontradas = new HashSet<>();
        List<ScheduleIncident> creadas = new ArrayList<>();

        if (!personas.isEmpty()) {
            Map<Long, DiaTeorico> teoricos = jornadaTeoricaService.diaDeVarios(personas, dia);
            Map<Long, List<FichajeDelDia>> fichajes = fichajesDelDia(personas, dia);

            for (User persona : personas) {
                DiaTeorico teorico = teoricos.get(persona.getId());
                if (teorico == null || !teorico.tieneCuadrante()) {
                    continue;
                }
                List<IncidenciaDetectada> delDia = ReglasDeCuadrante.incidencias(
                        dia, MADRID, teorico.tramos(), teorico.minutos(),
                        fichajes.getOrDefault(persona.getId(), List.of()));

                for (IncidenciaDetectada detectada : delDia) {
                    String clave = clave(persona.getId(), detectada.tipo());
                    encontradas.add(clave);
                    ScheduleIncident existente = existentes.get(clave);
                    if (existente == null) {
                        creadas.add(incidentRepository.save(nueva(persona, dia, detectada)));
                    } else if (existente.getEstado() == ScheduleIncidentStatus.PENDIENTE) {
                        // Una corrección puede haber movido la hora: la
                        // pendiente se pone al día. Una ya explicada o
                        // decidida no se toca: sobre ella ya ha hablado
                        // alguien, y reescribirla le cambiaría lo que leyó.
                        existente.setMinutos(detectada.minutos());
                        existente.setHoraPrevista(detectada.horaPrevista());
                        existente.setHoraReal(detectada.horaReal());
                        existente.setRegistro(detectada.registroId() == null
                                ? null : timeEntryRepository.getReferenceById(detectada.registroId()));
                    }
                }
            }
        }

        // Las pendientes que ya no proceden se retiran: la corrección que
        // arregló el fichaje, la baja aprobada después. Solo las PENDIENTES:
        // una que alguien ya explicó o decidió sigue su curso.
        List<ScheduleIncident> caducadas = existentes.entrySet().stream()
                .filter(entrada -> !encontradas.contains(entrada.getKey()))
                .map(Map.Entry::getValue)
                .filter(incidencia -> incidencia.getEstado() == ScheduleIncidentStatus.PENDIENTE)
                .toList();
        if (!caducadas.isEmpty()) {
            incidentRepository.deleteAll(caducadas);
            log.info("Retiradas {} incidencias del {} que ya no proceden.", caducadas.size(), dia);
        }
        return creadas;
    }

    /** Los fichajes que empiezan ese día (en hora de España), de todos a la vez. */
    private Map<Long, List<FichajeDelDia>> fichajesDelDia(List<User> personas, LocalDate dia) {
        Instant desde = dia.atStartOfDay(MADRID).toInstant();
        Instant hasta = dia.plusDays(1).atStartOfDay(MADRID).toInstant();
        List<Long> ids = personas.stream().map(User::getId).toList();
        return timeEntryRepository.findVivosDeUsuariosQueEmpiezanEntre(ids, desde, hasta).stream()
                .collect(Collectors.groupingBy(
                        registro -> registro.getUsuario().getId(),
                        Collectors.mapping(ScheduleIncidentServiceImpl::aFichaje, Collectors.toList())));
    }

    private static FichajeDelDia aFichaje(TimeEntry registro) {
        return new FichajeDelDia(registro.getId(), registro.getHoraEntrada(), registro.getHoraSalida(),
                registro.isJornadaIncompleta());
    }

    private ScheduleIncident nueva(User persona, LocalDate dia, IncidenciaDetectada detectada) {
        return ScheduleIncident.builder()
                .empresa(persona.getEmpresa())
                .usuario(persona)
                .fecha(dia)
                .tipo(detectada.tipo())
                .minutos(detectada.minutos())
                .horaPrevista(detectada.horaPrevista())
                .horaReal(detectada.horaReal())
                .registro(detectada.registroId() == null
                        ? null : timeEntryRepository.getReferenceById(detectada.registroId()))
                .build();
    }

    /**
     * El resumen de la noche para quien revisa: como mucho uno por empresa, y
     * ninguno si no hay nada nuevo. Mismo criterio que el de horas extra: un
     * correo por incidencia a cada gestor es spam por diseño, pero no avisar
     * de nada deja incidencias semanas sin mirar. Cuenta las CREADAS, no las
     * tocadas.
     */
    private void avisarAQuienRevisa(Map<Company, Set<User>> nuevasPorEmpresa, Map<Company, Integer> cuantas) {
        for (Map.Entry<Company, Set<User>> entrada : nuevasPorEmpresa.entrySet()) {
            Company empresa = entrada.getKey();
            List<User> revisores = Destinatarios.conAuthority(userRepository, empresa, REVISAR, null);
            if (revisores.isEmpty()) {
                continue;
            }
            eventPublisher.publishEvent(new NotificationEvents.ScheduleIncidentSummary(
                    empresa.getId(), cuantas.getOrDefault(empresa, 0), entrada.getValue().size(), revisores));
        }
    }

    private static String clave(long usuarioId, ScheduleIncidentType tipo) {
        return usuarioId + "|" + tipo;
    }

    // ------------------------------------------------------------------
    // Consultas y decisiones
    // ------------------------------------------------------------------

    @Override
    public List<ScheduleIncidentResponse> mias(User actor, int anio) {
        return incidentRepository.findDeUsuarioEnRango(actor.getId(), LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31))
                .stream()
                .map(ScheduleIncidentServiceImpl::toResponse)
                .toList();
    }

    @Override
    public List<ScheduleIncidentResponse> bandeja(User actor, boolean resueltas) {
        Set<ScheduleIncidentStatus> estados = resueltas
                ? EnumSet.of(ScheduleIncidentStatus.ACEPTADA, ScheduleIncidentStatus.RECHAZADA)
                : EnumSet.of(ScheduleIncidentStatus.PENDIENTE, ScheduleIncidentStatus.JUSTIFICADA);
        return incidentRepository
                .findBandeja(actor.getEmpresa().getId(), actor.getId(), estados, PageRequest.of(0, TOPE_BANDEJA))
                .stream()
                .map(ScheduleIncidentServiceImpl::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ScheduleIncidentResponse justificar(long incidenciaId, String texto, User actor) {
        ScheduleIncident incidencia = deLaEmpresa(incidenciaId, actor);
        // Solo quien la tiene explica la suya. Explicar la de otro sería
        // hablar por él.
        if (incidencia.getUsuario().getId() != actor.getId()) {
            throw new TenantAccessException("Solo puedes explicar tus propias incidencias.");
        }
        if (incidencia.getEstado().resuelta()) {
            throw new BusinessException("Esa incidencia ya está decidida.");
        }
        incidencia.setJustificacion(texto.trim());
        incidencia.setJustificadaEn(Instant.now());
        incidencia.setEstado(ScheduleIncidentStatus.JUSTIFICADA);
        return toResponse(incidentRepository.save(incidencia));
    }

    @Override
    @Transactional
    public ScheduleIncidentResponse resolver(long incidenciaId, boolean aceptar, String comentario, User actor) {
        ScheduleIncident incidencia = deLaEmpresa(incidenciaId, actor);
        if (!RoleAuthorities.tiene(actor, REVISAR)) {
            throw new TenantAccessException("No puedes revisar incidencias de cuadrante.");
        }
        // Nadie decide sobre lo suyo, tenga la authority que tenga: el mismo
        // conflicto de interés que en las correcciones y las horas extra.
        if (incidencia.getUsuario().getId() == actor.getId()) {
            throw new TenantAccessException("No puedes revisar tus propias incidencias.");
        }
        if (incidencia.getEstado().resuelta()) {
            throw new BusinessException("Esa incidencia ya está decidida.");
        }
        String motivo = comentario == null ? null : comentario.trim();
        // Aceptar no pide motivo: no hay nada que objetar. Decir que una
        // explicación no vale, sí: es la decisión que alguien querría ver
        // motivada.
        if (!aceptar && (motivo == null || motivo.isBlank())) {
            throw new BusinessException("Hay que explicar por qué la incidencia no queda justificada.",
                    HttpStatus.BAD_REQUEST);
        }
        incidencia.setEstado(aceptar ? ScheduleIncidentStatus.ACEPTADA : ScheduleIncidentStatus.RECHAZADA);
        incidencia.setComentarioResolucion(motivo == null || motivo.isBlank() ? null : motivo);
        incidencia.setResueltaPor(actor);
        incidencia.setResueltaEn(Instant.now());
        return toResponse(incidentRepository.save(incidencia));
    }

    private ScheduleIncident deLaEmpresa(long incidenciaId, User actor) {
        ScheduleIncident incidencia = incidentRepository.findById(incidenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Incidencia no encontrada."));
        if (incidencia.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Esa incidencia es de otra empresa.");
        }
        return incidencia;
    }

    static ScheduleIncidentResponse toResponse(ScheduleIncident incidencia) {
        User persona = incidencia.getUsuario();
        String nombre = persona.getApellidos() == null || persona.getApellidos().isBlank()
                ? persona.getNombre()
                : persona.getNombre() + " " + persona.getApellidos();
        return new ScheduleIncidentResponse(
                incidencia.getId(),
                persona.getId(),
                nombre,
                incidencia.getFecha(),
                incidencia.getTipo(),
                incidencia.getMinutos(),
                ReglasDeCuadrante.hora(incidencia.getHoraPrevista()),
                incidencia.getHoraReal(),
                incidencia.getEstado(),
                incidencia.getJustificacion(),
                incidencia.getJustificadaEn(),
                incidencia.getComentarioResolucion(),
                incidencia.getResueltaPor() != null ? incidencia.getResueltaPor().getNombre() : null,
                incidencia.getResueltaEn());
    }
}
