package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.ProjectAssignment;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ProjectAssignmentRequest;
import com.nxtime.nxtime.dto.ProjectAssignmentResponse;
import com.nxtime.nxtime.dto.ProjectDetailResponse;
import com.nxtime.nxtime.dto.ProjectHoursResponse;
import com.nxtime.nxtime.dto.ProjectRequest;
import com.nxtime.nxtime.dto.ProjectResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.ProjectAssignmentRepository;
import com.nxtime.nxtime.repository.ProjectRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.ProjectService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);

    /** El mes es el español, no el de UTC (ver TimeEntryMapper). */
    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    /**
     * Nombre de la restricción que impide que una persona esté en dos
     * proyectos el mismo día. Se busca en el mensaje de la excepción
     * para distinguir ese choque concreto de cualquier otra violación de
     * integridad, que no significaría lo mismo.
     */
    private static final String RESTRICCION_SOLAPE = "ex_asignaciones_sin_solape";

    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    public ProjectServiceImpl(
            ProjectRepository projectRepository,
            ProjectAssignmentRepository assignmentRepository,
            UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------
    // Proyectos
    // ------------------------------------------------------------------

    @Override
    public List<ProjectResponse> listar(User actor) {
        return projectRepository.findByEmpresaOrderByCodigoAsc(actor.getEmpresa()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ProjectDetailResponse detalle(long id, int anio, int mes, User actor) {
        Project proyecto = deLaMismaEmpresa(id, actor);
        YearMonth periodo = periodoValido(anio, mes);

        List<ProjectAssignmentResponse> asignaciones =
                assignmentRepository.findDelProyecto(id).stream()
                        .map(this::toResponse)
                        .toList();

        List<ProjectDetailResponse.EmployeeHoursItem> horas =
                assignmentRepository.sumarSegundosDelProyectoPorEmpleado(
                                id, inicioDelMes(periodo), finExclusivo(periodo)).stream()
                        .map(fila -> new ProjectDetailResponse.EmployeeHoursItem(
                                fila.getUsuarioId(), fila.getNombre(), aMinutos(fila.getSegundos())))
                        .toList();

        return new ProjectDetailResponse(
                toResponse(proyecto), asignaciones, periodo.getYear(), periodo.getMonthValue(), horas);
    }

    @Override
    @Transactional
    public ProjectResponse crear(ProjectRequest request, User actor) {
        String codigo = request.codigo().trim();
        fechasCoherentes(request.fechaInicio(), request.fechaFin());

        if (projectRepository.existsByEmpresaAndCodigoIgnoreCase(actor.getEmpresa(), codigo)) {
            throw new BusinessException("Ya existe un proyecto con el código " + codigo + ".");
        }

        Project proyecto = projectRepository.save(Project.builder()
                .empresa(actor.getEmpresa())
                .codigo(codigo)
                .nombre(request.nombre().trim())
                .descripcion(textoOpcional(request.descripcion()))
                .fechaInicio(request.fechaInicio())
                .fechaFin(request.fechaFin())
                .activo(true)
                .build());

        log.info("{} ha creado el proyecto {}", actor.getEmail(), codigo);
        return toResponse(proyecto);
    }

    @Override
    @Transactional
    public ProjectResponse editar(long id, ProjectRequest request, User actor) {
        Project proyecto = deLaMismaEmpresa(id, actor);
        String codigo = request.codigo().trim();
        fechasCoherentes(request.fechaInicio(), request.fechaFin());

        // Renombrarse a sí mismo no es un conflicto: sin este "no es él
        // mismo", cambiar solo las mayúsculas del código daría 409.
        if (!proyecto.getCodigo().equalsIgnoreCase(codigo)
                && projectRepository.existsByEmpresaAndCodigoIgnoreCase(actor.getEmpresa(), codigo)) {
            throw new BusinessException("Ya existe un proyecto con el código " + codigo + ".");
        }

        proyecto.setCodigo(codigo);
        proyecto.setNombre(request.nombre().trim());
        proyecto.setDescripcion(textoOpcional(request.descripcion()));
        proyecto.setFechaInicio(request.fechaInicio());
        proyecto.setFechaFin(request.fechaFin());
        projectRepository.save(proyecto);

        return toResponse(proyecto);
    }

    @Override
    @Transactional
    public ProjectResponse cambiarEstado(long id, boolean activo, User actor) {
        Project proyecto = deLaMismaEmpresa(id, actor);
        proyecto.setActivo(activo);
        projectRepository.save(proyecto);

        log.info("{} ha {} el proyecto {}",
                actor.getEmail(), activo ? "reabierto" : "cerrado", proyecto.getCodigo());
        return toResponse(proyecto);
    }

    @Override
    @Transactional
    public void borrar(long id, User actor) {
        Project proyecto = deLaMismaEmpresa(id, actor);

        long asignaciones = assignmentRepository.countByProyecto_Id(id);
        if (asignaciones > 0) {
            // Se comprueba antes para poder decir CUÁNTAS hay; si no, lo
            // que salta es la violación de fk_asignaciones_proyecto, que
            // llega al cliente como un 500 sin explicación (mismo caso
            // que borrar un departamento con gente dentro).
            //
            // Y el mensaje sugiere cerrarlo, que casi siempre es lo que
            // de verdad se quiere: borrar un proyecto con historial
            // borraría el reparto de horas que ya se informó.
            throw new BusinessException(
                    "No se puede borrar: el proyecto tiene " + asignaciones + " asignación(es). "
                            + "Si ya ha terminado, ciérralo en vez de borrarlo.");
        }

        projectRepository.delete(proyecto);
        log.info("{} ha borrado el proyecto {}", actor.getEmail(), proyecto.getCodigo());
    }

    // ------------------------------------------------------------------
    // Asignaciones
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public ProjectAssignmentResponse asignar(
            long proyectoId, ProjectAssignmentRequest request, User actor) {
        Project proyecto = deLaMismaEmpresa(proyectoId, actor);
        fechasCoherentes(request.fechaInicio(), request.fechaFin());

        User persona = userRepository.findById(request.usuarioId())
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado."));
        if (persona.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes asignar a alguien de otra empresa.");
        }

        // Se busca el choque ANTES de insertar solo para poder decir en
        // qué proyecto está ya. Quien lo impide de verdad es el EXCLUDE
        // de la base: entre esta lectura y el INSERT cabe otra petición,
        // y por eso el catch de abajo no sobra.
        assignmentRepository.findVigenteDe(persona.getId(), request.fechaInicio())
                .ifPresent(existente -> {
                    // "ya tiene una asignación" y no "ya está asignado":
                    // el mensaje concatena un nombre propio y el
                    // servidor no sabe el género de quien lo lleva, así
                    // que cualquier participio concordado se equivoca la
                    // mitad de las veces ("Ana ya está asignado").
                    throw new BusinessException(
                            persona.getNombre() + " ya tiene una asignación en "
                                    + existente.getProyecto().getCodigo() + " en esas fechas.");
                });

        ProjectAssignment asignacion = ProjectAssignment.builder()
                .empresa(actor.getEmpresa())
                .usuario(persona)
                .proyecto(proyecto)
                .fechaInicio(request.fechaInicio())
                .fechaFin(request.fechaFin())
                .build();

        return toResponse(guardarControlandoElSolape(asignacion, persona));
    }

    @Override
    @Transactional
    public ProjectAssignmentResponse finalizarAsignacion(
            long asignacionId, LocalDate fechaFin, User actor) {
        ProjectAssignment asignacion = assignmentRepository.findById(asignacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Asignación no encontrada."));
        if (asignacion.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes tocar asignaciones de otra empresa.");
        }
        if (fechaFin == null) {
            throw new BusinessException("Hay que indicar la fecha de fin.", HttpStatus.BAD_REQUEST);
        }
        fechasCoherentes(asignacion.getFechaInicio(), fechaFin);

        asignacion.setFechaFin(fechaFin);
        return toResponse(guardarControlandoElSolape(asignacion, asignacion.getUsuario()));
    }

    @Override
    public List<ProjectAssignmentResponse> asignacionesDe(long usuarioId, User actor) {
        User persona = userRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado."));
        if (persona.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes ver los proyectos de otra empresa.");
        }
        return assignmentRepository.findDeUsuario(usuarioId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------
    // Horas
    // ------------------------------------------------------------------

    @Override
    public ProjectHoursResponse horasDelMes(int anio, int mes, User actor) {
        YearMonth periodo = periodoValido(anio, mes);

        List<ProjectHoursResponse.ProjectHoursItem> items =
                assignmentRepository.sumarSegundosPorProyecto(
                                actor.getEmpresa().getId(),
                                inicioDelMes(periodo),
                                finExclusivo(periodo)).stream()
                        .map(fila -> new ProjectHoursResponse.ProjectHoursItem(
                                fila.getProyectoId(),
                                fila.getCodigo(),
                                fila.getNombre(),
                                aMinutos(fila.getSegundos())))
                        .toList();

        return new ProjectHoursResponse(periodo.getYear(), periodo.getMonthValue(), items);
    }

    // ------------------------------------------------------------------

    /**
     * Guarda una asignación traduciendo el rechazo del EXCLUDE a un 409.
     *
     * Sin esto, dos peticiones simultáneas —o cualquier solape que la
     * comprobación previa no vea, como asignar a alguien un rango que
     * ENGLOBA una asignación futura suya— llegarían al cliente como un
     * 500 "error inesperado". La base es la que manda; aquí solo se
     * traduce lo que dice.
     */
    private ProjectAssignment guardarControlandoElSolape(
            ProjectAssignment asignacion, User persona) {
        try {
            return assignmentRepository.saveAndFlush(asignacion);
        } catch (DataIntegrityViolationException e) {
            if (mensajeCompleto(e).contains(RESTRICCION_SOLAPE)) {
                // Sin participio concordado, por lo mismo que arriba.
                throw new BusinessException(
                        persona.getNombre() + " ya tiene una asignación en otro proyecto en alguna "
                                + "de esas fechas. Cierra la asignación anterior antes de crear la nueva.");
            }
            throw e;
        }
    }

    /**
     * El nombre de la restricción viene en la excepción de PostgreSQL,
     * que Spring envuelve un par de veces: mirar solo
     * {@code getMessage()} del nivel de arriba se lo perdería.
     */
    private String mensajeCompleto(Throwable e) {
        StringBuilder texto = new StringBuilder();
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            texto.append(causa.getMessage()).append(' ');
        }
        return texto.toString();
    }

    private Project deLaMismaEmpresa(long id, User actor) {
        Project proyecto = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado."));
        if (proyecto.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes gestionar proyectos de otra empresa.");
        }
        return proyecto;
    }

    private void fechasCoherentes(LocalDate inicio, LocalDate fin) {
        if (fin != null && fin.isBefore(inicio)) {
            throw new BusinessException(
                    "La fecha de fin no puede ser anterior a la de inicio.", HttpStatus.BAD_REQUEST);
        }
    }

    private YearMonth periodoValido(int anio, int mes) {
        if (mes < 1 || mes > 12) {
            throw new BusinessException("El mes tiene que estar entre 1 y 12.", HttpStatus.BAD_REQUEST);
        }
        return YearMonth.of(anio, mes);
    }

    private Instant inicioDelMes(YearMonth periodo) {
        return periodo.atDay(1).atStartOfDay(MADRID_ZONE).toInstant();
    }

    /** Fin exclusivo: el día 1 del mes siguiente a las 00:00. */
    private Instant finExclusivo(YearMonth periodo) {
        return periodo.plusMonths(1).atDay(1).atStartOfDay(MADRID_ZONE).toInstant();
    }

    /** Truncado, no redondeado: 89 segundos son 1 minuto (ver DashboardServiceImpl). */
    private long aMinutos(long segundos) {
        return segundos / 60;
    }

    private String textoOpcional(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private ProjectResponse toResponse(Project proyecto) {
        return new ProjectResponse(
                proyecto.getId(),
                proyecto.getCodigo(),
                proyecto.getNombre(),
                proyecto.getDescripcion(),
                proyecto.getFechaInicio(),
                proyecto.getFechaFin(),
                proyecto.isActivo(),
                assignmentRepository.countByProyecto_Id(proyecto.getId()));
    }

    private ProjectAssignmentResponse toResponse(ProjectAssignment asignacion) {
        Project proyecto = asignacion.getProyecto();
        User persona = asignacion.getUsuario();
        return new ProjectAssignmentResponse(
                asignacion.getId(),
                persona.getId(),
                persona.getNombre(),
                proyecto.getId(),
                proyecto.getCodigo(),
                proyecto.getNombre(),
                asignacion.getFechaInicio(),
                asignacion.getFechaFin(),
                asignacion.vigenteEl(LocalDate.now(MADRID_ZONE)));
    }
}
