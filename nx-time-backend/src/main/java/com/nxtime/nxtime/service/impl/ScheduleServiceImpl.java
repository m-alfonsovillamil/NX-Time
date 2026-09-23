package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.ScheduleAssignment;
import com.nxtime.nxtime.domain.ScheduleException;
import com.nxtime.nxtime.domain.ScheduleExceptionType;
import com.nxtime.nxtime.domain.ScheduleSlot;
import com.nxtime.nxtime.domain.ScheduleTemplate;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ScheduleAssignmentRequest;
import com.nxtime.nxtime.dto.ScheduleAssignmentResponse;
import com.nxtime.nxtime.dto.ScheduleExceptionRequest;
import com.nxtime.nxtime.dto.ScheduleExceptionResponse;
import com.nxtime.nxtime.dto.ScheduleTemplateRequest;
import com.nxtime.nxtime.dto.ScheduleTemplateResponse;
import com.nxtime.nxtime.dto.TeamScheduleEntryResponse;
import com.nxtime.nxtime.dto.TheoreticalDayResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.Destinatarios;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.ScheduleAssignmentRepository;
import com.nxtime.nxtime.repository.ScheduleExceptionRepository;
import com.nxtime.nxtime.repository.ScheduleSlotRepository;
import com.nxtime.nxtime.repository.ScheduleTemplateRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.OvertimeCalculator;
import com.nxtime.nxtime.service.ReglasDeCuadrante;
import com.nxtime.nxtime.service.ReglasDeCuadrante.TramoSemanal;
import com.nxtime.nxtime.service.ScheduleService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ver {@link ScheduleService}. */
@Service
@Transactional(readOnly = true)
public class ScheduleServiceImpl implements ScheduleService {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** El mismo tope que el detalle de horas por día: dos meses. */
    static final int MAXIMO_DIAS = 62;

    /** Quién lleva la jornada contratada, que es a quien se avisa si el cuadrante no la suma. */
    private static final String QUIEN_FIJA_LA_JORNADA = "empleado:configurar";

    private final ScheduleTemplateRepository templateRepository;
    private final ScheduleSlotRepository slotRepository;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final ScheduleExceptionRepository exceptionRepository;
    private final UserRepository userRepository;
    private final JornadaTeoricaService jornadaTeoricaService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public ScheduleServiceImpl(
            ScheduleTemplateRepository templateRepository,
            ScheduleSlotRepository slotRepository,
            ScheduleAssignmentRepository assignmentRepository,
            ScheduleExceptionRepository exceptionRepository,
            UserRepository userRepository,
            JornadaTeoricaService jornadaTeoricaService,
            ApplicationEventPublisher eventPublisher) {
        this(templateRepository, slotRepository, assignmentRepository, exceptionRepository,
                userRepository, jornadaTeoricaService, eventPublisher, Clock.systemUTC());
    }

    /**
     * Con el reloj inyectable, para los tests: "hoy" decide qué se puede
     * tocar y qué ya es pasado, y un test que dependiera del día en que se
     * ejecuta pasaría o fallaría según el calendario.
     */
    ScheduleServiceImpl(
            ScheduleTemplateRepository templateRepository,
            ScheduleSlotRepository slotRepository,
            ScheduleAssignmentRepository assignmentRepository,
            ScheduleExceptionRepository exceptionRepository,
            UserRepository userRepository,
            JornadaTeoricaService jornadaTeoricaService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.templateRepository = templateRepository;
        this.slotRepository = slotRepository;
        this.assignmentRepository = assignmentRepository;
        this.exceptionRepository = exceptionRepository;
        this.userRepository = userRepository;
        this.jornadaTeoricaService = jornadaTeoricaService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Plantillas
    // ------------------------------------------------------------------

    @Override
    public List<ScheduleTemplateResponse> plantillas(User actor) {
        List<ScheduleTemplate> plantillas =
                templateRepository.findByEmpresa_IdOrderByNombreAsc(actor.getEmpresa().getId());
        if (plantillas.isEmpty()) {
            return List.of();
        }
        // Todos los tramos en una consulta, no una por plantilla. Lo que sí va
        // por plantilla son las dos comprobaciones de toResponse (si ya se
        // aplicó al pasado y si está asignada): dos COUNT por cada una, y una
        // empresa tiene tres o cuatro plantillas, no cientos.
        Map<Long, List<ScheduleSlot>> tramos = slotRepository
                .findByPlantilla_IdIn(plantillas.stream().map(ScheduleTemplate::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(tramo -> tramo.getPlantilla().getId()));
        return plantillas.stream()
                .map(plantilla -> toResponse(plantilla, tramos.getOrDefault(plantilla.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public ScheduleTemplateResponse crearPlantilla(ScheduleTemplateRequest request, User actor) {
        List<TramoSemanal> tramos = validados(request.tramos());
        ScheduleTemplate plantilla = templateRepository.saveAndFlush(ScheduleTemplate.builder()
                .empresa(actor.getEmpresa())
                .nombre(request.nombre().trim())
                .descripcion(textoOpcional(request.descripcion()))
                .build());
        return toResponse(plantilla, guardarTramos(plantilla, tramos));
    }

    @Override
    @Transactional
    public ScheduleTemplateResponse editarPlantilla(
            long plantillaId, ScheduleTemplateRequest request, User actor) {
        ScheduleTemplate plantilla = plantillaDeLaEmpresa(plantillaId, actor);
        List<TramoSemanal> nuevos = validados(request.tramos());
        List<ScheduleSlot> actuales = slotRepository.findByPlantilla_IdOrderByDiaSemanaAscInicioAsc(plantillaId);

        plantilla.setNombre(request.nombre().trim());
        plantilla.setDescripcion(textoOpcional(request.descripcion()));
        templateRepository.saveAndFlush(plantilla);

        // Renombrar siempre se puede. Cambiar los tramos, solo si no se ha
        // aplicado a ningún día pasado: si no, el horario teórico de esos días
        // cambiaría sin que nadie los tocara.
        if (mismosTramos(actuales, nuevos)) {
            return toResponse(plantilla, actuales);
        }
        if (assignmentRepository.haEstadoEnVigorAntesDe(plantillaId, hoy())) {
            throw new BusinessException("Esta plantilla ya se ha aplicado a días pasados, y cambiar sus "
                    + "tramos reescribiría su horario teórico. Crea una plantilla nueva y asígnala "
                    + "desde la fecha que quieras.");
        }
        // Con una sentencia DELETE, que se ejecuta en el acto, y no entidad a
        // entidad: Hibernate vuelca los INSERT antes que los DELETE, así que
        // borrando entidades los tramos nuevos chocarían con los viejos,
        // todavía sin borrar, contra el EXCLUDE de V31. Comprobado:
        // CuadranteIT.reemplazarTramosQueSePisan falla con deleteAll().
        slotRepository.borrarDeLaPlantilla(plantillaId);
        return toResponse(plantilla, guardarTramos(plantilla, nuevos));
    }

    @Override
    @Transactional
    public void borrarPlantilla(long plantillaId, User actor) {
        ScheduleTemplate plantilla = plantillaDeLaEmpresa(plantillaId, actor);
        // La base lo impediría igual (fk_asignaciones_horario_plantilla es
        // RESTRICT), pero con un mensaje que no explica nada.
        if (assignmentRepository.existsByPlantilla_Id(plantillaId)) {
            throw new BusinessException("No se puede borrar una plantilla que alguien tiene o ha tenido "
                    + "asignada: explica el horario teórico de esos días.");
        }
        slotRepository.borrarDeLaPlantilla(plantillaId);
        templateRepository.delete(plantilla);
    }

    // ------------------------------------------------------------------
    // Asignaciones
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public ScheduleAssignmentResponse asignar(ScheduleAssignmentRequest request, User actor) {
        User persona = personaDeLaEmpresa(request.usuarioId(), actor);
        ScheduleTemplate plantilla = plantillaDeLaEmpresa(request.plantillaId(), actor);

        if (request.fechaInicio().isBefore(hoy())) {
            throw new BusinessException("Un cuadrante no puede empezar en el pasado: reescribiría el "
                    + "horario teórico de días ya informados.", HttpStatus.BAD_REQUEST);
        }
        fechasCoherentes(request.fechaInicio(), request.fechaFin());

        // saveAndFlush para que el EXCLUDE salte AQUÍ, dentro de la
        // transacción, y lo traduzca el manejador global a un 409.
        ScheduleAssignment asignacion = assignmentRepository.saveAndFlush(ScheduleAssignment.builder()
                .empresa(actor.getEmpresa())
                .usuario(persona)
                .plantilla(plantilla)
                .fechaInicio(request.fechaInicio())
                .fechaFin(request.fechaFin())
                .build());

        return toResponse(asignacion, avisarSiNoSumaLaJornada(persona, plantilla, request.fechaInicio(), actor));
    }

    @Override
    @Transactional
    public ScheduleAssignmentResponse cerrarAsignacion(long asignacionId, LocalDate fechaFin, User actor) {
        ScheduleAssignment asignacion = asignacionDeLaEmpresa(asignacionId, actor);
        LocalDate ayer = hoy().minusDays(1);

        // Una que ya terminó antes de ayer ya no se toca: cambiarle el fin
        // añadiría o quitaría días pasados a su cuadrante.
        if (asignacion.getFechaFin() != null && asignacion.getFechaFin().isBefore(ayer)) {
            throw new BusinessException("Ese cuadrante ya terminó y no se puede cambiar: sus días ya "
                    + "están informados.");
        }
        if (fechaFin.isBefore(ayer)) {
            throw new BusinessException("Un cuadrante no se puede cerrar antes de ayer: dejaría sin "
                    + "horario teórico días ya informados.", HttpStatus.BAD_REQUEST);
        }
        fechasCoherentes(asignacion.getFechaInicio(), fechaFin);

        asignacion.setFechaFin(fechaFin);
        return toResponse(assignmentRepository.saveAndFlush(asignacion), null);
    }

    @Override
    @Transactional
    public void borrarAsignacion(long asignacionId, User actor) {
        ScheduleAssignment asignacion = asignacionDeLaEmpresa(asignacionId, actor);
        if (asignacion.getFechaInicio().isBefore(hoy())) {
            throw new BusinessException("Ese cuadrante ya ha estado en vigor y no se puede borrar. "
                    + "Ciérralo con una fecha de fin.");
        }
        assignmentRepository.delete(asignacion);
    }

    @Override
    public List<ScheduleAssignmentResponse> asignacionesDe(long usuarioId, User actor) {
        personaDeLaEmpresa(usuarioId, actor);
        return assignmentRepository.findDeUsuario(usuarioId).stream()
                .map(asignacion -> toResponse(asignacion, null))
                .toList();
    }

    // ------------------------------------------------------------------
    // Excepciones
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public List<ScheduleExceptionResponse> crearExcepcion(ScheduleExceptionRequest request, User actor) {
        User persona = personaDeLaEmpresa(request.usuarioId(), actor);
        if (request.fecha().isBefore(hoy())) {
            throw new BusinessException("Las excepciones son de hoy en adelante: la de un día pasado "
                    + "reescribiría su horario teórico.", HttpStatus.BAD_REQUEST);
        }
        // Sin cuadrante ese día no hay nada de lo que exceptuar. Un día libre
        // o un horario distinto de alguien sin horario no significa nada.
        boolean tieneCuadrante = assignmentRepository
                .findDeUsuarioEnRango(persona.getId(), request.fecha(), request.fecha()).stream()
                .anyMatch(asignacion -> asignacion.vigenteEl(request.fecha()));
        if (!tieneCuadrante) {
            throw new BusinessException(persona.getNombre() + " no tiene cuadrante ese día, así que no "
                    + "hay nada que cambiar. Asígnale uno primero.", HttpStatus.BAD_REQUEST);
        }

        List<ScheduleExceptionRequest.Tramo> tramos =
                request.tramos() == null ? List.of() : request.tramos();
        String motivo = textoOpcional(request.motivo());

        if (request.tipo() == ScheduleExceptionType.LIBRE) {
            if (!tramos.isEmpty()) {
                throw new BusinessException("Un día libre no lleva tramos.", HttpStatus.BAD_REQUEST);
            }
            ScheduleException libre = exceptionRepository.saveAndFlush(
                    excepcion(persona, request.fecha(), ScheduleExceptionType.LIBRE, null, null, motivo, actor));
            return List.of(toResponse(libre));
        }

        if (tramos.isEmpty()) {
            throw new BusinessException("Un día con horario distinto necesita al menos un tramo.",
                    HttpStatus.BAD_REQUEST);
        }
        for (ScheduleExceptionRequest.Tramo tramo : tramos) {
            ReglasDeCuadrante.problemaEnElTramo(tramo.inicio(), tramo.fin()).ifPresent(problema -> {
                throw new BusinessException(mayuscula(problema), HttpStatus.BAD_REQUEST);
            });
        }
        ReglasDeCuadrante.solapeEnUnDia(tramos.stream().map(t -> new int[] {t.inicio(), t.fin()}).toList())
                .ifPresent(problema -> {
                    throw new BusinessException(mayuscula(problema), HttpStatus.BAD_REQUEST);
                });

        List<ScheduleException> guardadas = exceptionRepository.saveAllAndFlush(tramos.stream()
                .map(tramo -> excepcion(persona, request.fecha(), ScheduleExceptionType.TRAMO,
                        tramo.inicio(), tramo.fin(), motivo, actor))
                .toList());
        return guardadas.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void borrarExcepcion(long excepcionId, User actor) {
        ScheduleException excepcion = exceptionRepository.findById(excepcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Excepción no encontrada."));
        mismaEmpresa(excepcion.getEmpresa().getId(), actor);
        if (excepcion.getFecha().isBefore(hoy())) {
            throw new BusinessException("Esa excepción es de un día pasado y ya no se puede quitar.");
        }
        exceptionRepository.delete(excepcion);
    }

    @Override
    public List<ScheduleExceptionResponse> excepcionesDe(long usuarioId, User actor) {
        personaDeLaEmpresa(usuarioId, actor);
        return exceptionRepository.findByUsuario_IdOrderByFechaDesc(usuarioId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Horario teórico
    // ------------------------------------------------------------------

    @Override
    public List<TheoreticalDayResponse> mio(LocalDate desde, LocalDate hasta, User actor) {
        rangoValido(desde, hasta);
        return jornadaTeoricaService.dias(actor, desde, hasta).stream().map(ScheduleServiceImpl::toResponse).toList();
    }

    @Override
    public List<TheoreticalDayResponse> deUnaPersona(long usuarioId, LocalDate desde, LocalDate hasta, User actor) {
        rangoValido(desde, hasta);
        User persona = personaDeLaEmpresa(usuarioId, actor);
        return jornadaTeoricaService.dias(persona, desde, hasta).stream()
                .map(ScheduleServiceImpl::toResponse)
                .toList();
    }

    /**
     * Una consulta por persona con cuadrante ese día, a través de
     * JornadaTeoricaService.
     *
     * Es deliberado y tiene precio: así la precedencia (no laborable,
     * excepción, plantilla) vive en un solo sitio. Para un equipo de decenas
     * de personas es asumible; el barrido nocturno de incidencias (Fase B2),
     * que recorre a toda la plantilla de todas las empresas, necesitará una
     * versión por lotes, y ese es su sitio.
     */
    @Override
    public List<TeamScheduleEntryResponse> equipo(LocalDate fecha, User actor) {
        return assignmentRepository.findVigentesDeEmpresaEl(actor.getEmpresa().getId(), fecha).stream()
                .map(ScheduleAssignment::getUsuario)
                .filter(User::isActivo)
                .sorted(Comparator.comparing(User::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(persona -> new TeamScheduleEntryResponse(
                        persona.getId(),
                        nombreCompleto(persona),
                        toResponse(jornadaTeoricaService.dia(persona, fecha))))
                .toList();
    }

    // ------------------------------------------------------------------

    /**
     * Si la plantilla no suma la jornada contratada, avisa a quien la fija y
     * devuelve el texto para quien está asignando. Si suma, null.
     */
    private String avisarSiNoSumaLaJornada(
            User persona, ScheduleTemplate plantilla, LocalDate desde, User actor) {
        long minutosPlantilla = ReglasDeCuadrante.minutosSemanales(
                slotRepository.findByPlantilla_IdOrderByDiaSemanaAscInicioAsc(plantilla.getId()).stream()
                        .map(tramo -> new TramoSemanal(tramo.dia(), tramo.getInicio(), tramo.getFin()))
                        .toList());
        if (!ReglasDeCuadrante.difiereDeLaJornada(minutosPlantilla, persona.getHorasSemanales())) {
            return null;
        }
        long minutosContrato = OvertimeCalculator.objetivoSemanal(persona.getHorasSemanales(), 5);

        eventPublisher.publishEvent(new NotificationEvents.ScheduleDiffersFromContract(
                actor.getEmpresa().getId(),
                nombreCompleto(persona),
                plantilla.getNombre(),
                minutosPlantilla,
                minutosContrato,
                desde,
                // Quien lleva los contratos, sin avisarse a sí mismo: si es
                // RRHH quien asigna, ya lo ve en la respuesta.
                Destinatarios.conAuthority(userRepository, actor.getEmpresa(), QUIEN_FIJA_LA_JORNADA, actor)));

        return "La plantilla suma " + ReglasDeCuadrante.duracion(minutosPlantilla) + " a la semana y la "
                + "jornada contratada de " + persona.getNombre() + " es de "
                + ReglasDeCuadrante.duracion(minutosContrato) + ". Se ha avisado a quien lleva los "
                + "contratos. Sus horas extra se calcularán contra el cuadrante.";
    }

    private List<TramoSemanal> validados(List<ScheduleTemplateRequest.Tramo> tramos) {
        List<TramoSemanal> semanales = tramos.stream()
                .map(tramo -> new TramoSemanal(DayOfWeek.of(tramo.diaSemana()), tramo.inicio(), tramo.fin()))
                .toList();
        Optional<String> problema = ReglasDeCuadrante.problemaEn(semanales);
        if (problema.isPresent()) {
            throw new BusinessException(mayuscula(problema.get()), HttpStatus.BAD_REQUEST);
        }
        return semanales;
    }

    private List<ScheduleSlot> guardarTramos(ScheduleTemplate plantilla, List<TramoSemanal> tramos) {
        return slotRepository.saveAllAndFlush(tramos.stream()
                .map(tramo -> ScheduleSlot.builder()
                        .plantilla(plantilla)
                        .diaSemana((short) tramo.dia().getValue())
                        .inicio(tramo.inicio())
                        .fin(tramo.fin())
                        .build())
                .toList());
    }

    private static boolean mismosTramos(List<ScheduleSlot> actuales, List<TramoSemanal> nuevos) {
        Set<List<Integer>> antes = new HashSet<>();
        for (ScheduleSlot tramo : actuales) {
            antes.add(List.of((int) tramo.getDiaSemana(), tramo.getInicio(), tramo.getFin()));
        }
        Set<List<Integer>> despues = new HashSet<>();
        for (TramoSemanal tramo : nuevos) {
            despues.add(List.of(tramo.dia().getValue(), tramo.inicio(), tramo.fin()));
        }
        return antes.equals(despues) && actuales.size() == nuevos.size();
    }

    private ScheduleException excepcion(User persona, LocalDate fecha, ScheduleExceptionType tipo,
            Integer inicio, Integer fin, String motivo, User actor) {
        return ScheduleException.builder()
                .empresa(persona.getEmpresa())
                .usuario(persona)
                .fecha(fecha)
                .tipo(tipo)
                .inicio(inicio)
                .fin(fin)
                .motivo(motivo)
                .creadaPor(actor)
                .build();
    }

    private LocalDate hoy() {
        return LocalDate.now(clock.withZone(MADRID));
    }

    private void rangoValido(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new BusinessException("La fecha final no puede ser anterior a la inicial.", HttpStatus.BAD_REQUEST);
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new BusinessException("Como mucho " + MAXIMO_DIAS + " días de una vez.", HttpStatus.BAD_REQUEST);
        }
    }

    private static void fechasCoherentes(LocalDate inicio, LocalDate fin) {
        if (fin != null && fin.isBefore(inicio)) {
            throw new BusinessException(
                    "La fecha de fin no puede ser anterior a la de inicio.", HttpStatus.BAD_REQUEST);
        }
    }

    private ScheduleTemplate plantillaDeLaEmpresa(long id, User actor) {
        ScheduleTemplate plantilla = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de horario no encontrada."));
        mismaEmpresa(plantilla.getEmpresa().getId(), actor);
        return plantilla;
    }

    private ScheduleAssignment asignacionDeLaEmpresa(long id, User actor) {
        ScheduleAssignment asignacion = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asignación de cuadrante no encontrada."));
        mismaEmpresa(asignacion.getEmpresa().getId(), actor);
        return asignacion;
    }

    private User personaDeLaEmpresa(long usuarioId, User actor) {
        User persona = userRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado."));
        mismaEmpresa(persona.getEmpresa().getId(), actor);
        return persona;
    }

    private static void mismaEmpresa(long empresaId, User actor) {
        if (empresaId != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes gestionar cuadrantes de otra empresa.");
        }
    }

    private static String textoOpcional(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static String mayuscula(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static String nombreCompleto(User persona) {
        String apellidos = persona.getApellidos();
        return apellidos == null || apellidos.isBlank()
                ? persona.getNombre()
                : persona.getNombre() + " " + apellidos;
    }

    // ------------------------------------------------------------------
    // A DTO
    // ------------------------------------------------------------------

    private ScheduleTemplateResponse toResponse(ScheduleTemplate plantilla, List<ScheduleSlot> tramos) {
        List<ScheduleTemplateResponse.Tramo> dto = tramos.stream()
                .sorted(Comparator.comparingInt(ScheduleSlot::getDiaSemana).thenComparingInt(ScheduleSlot::getInicio))
                .map(tramo -> tramo(tramo.getDiaSemana(), tramo.getInicio(), tramo.getFin()))
                .toList();
        long minutos = dto.stream().mapToLong(ScheduleTemplateResponse.Tramo::minutos).sum();
        boolean enVigorEnElPasado = assignmentRepository.haEstadoEnVigorAntesDe(plantilla.getId(), hoy());
        boolean asignada = assignmentRepository.existsByPlantilla_Id(plantilla.getId());
        return new ScheduleTemplateResponse(plantilla.getId(), plantilla.getNombre(), plantilla.getDescripcion(),
                minutos, !enVigorEnElPasado, !asignada, dto);
    }

    private ScheduleAssignmentResponse toResponse(ScheduleAssignment asignacion, String aviso) {
        return new ScheduleAssignmentResponse(
                asignacion.getId(),
                asignacion.getUsuario().getId(),
                nombreCompleto(asignacion.getUsuario()),
                asignacion.getPlantilla().getId(),
                asignacion.getPlantilla().getNombre(),
                asignacion.getFechaInicio(),
                asignacion.getFechaFin(),
                asignacion.vigenteEl(hoy()),
                aviso);
    }

    private ScheduleExceptionResponse toResponse(ScheduleException excepcion) {
        boolean conHoras = excepcion.getInicio() != null;
        return new ScheduleExceptionResponse(
                excepcion.getId(),
                excepcion.getUsuario().getId(),
                excepcion.getFecha(),
                excepcion.getTipo(),
                excepcion.getInicio(),
                excepcion.getFin(),
                conHoras ? ReglasDeCuadrante.hora(excepcion.getInicio()) : null,
                conHoras ? ReglasDeCuadrante.hora(excepcion.getFin()) : null,
                excepcion.getMotivo());
    }

    static TheoreticalDayResponse toResponse(JornadaTeoricaService.DiaTeorico dia) {
        return new TheoreticalDayResponse(
                dia.fecha(),
                dia.origen(),
                dia.minutos(),
                dia.tramos().isEmpty() ? null : ReglasDeCuadrante.hora(dia.tramos().get(0).inicio()),
                dia.tramos().stream()
                        .map(tramo -> tramo(dia.fecha().getDayOfWeek().getValue(), tramo.inicio(), tramo.fin()))
                        .toList(),
                dia.motivo(),
                dia.plantilla());
    }

    private static ScheduleTemplateResponse.Tramo tramo(int diaSemana, int inicio, int fin) {
        return new ScheduleTemplateResponse.Tramo(
                diaSemana,
                inicio,
                fin,
                ReglasDeCuadrante.hora(inicio),
                ReglasDeCuadrante.hora(fin),
                fin - inicio,
                fin > ReglasDeCuadrante.MINUTOS_DIA);
    }
}
