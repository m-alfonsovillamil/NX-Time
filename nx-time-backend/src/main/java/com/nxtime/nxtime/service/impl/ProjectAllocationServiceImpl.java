package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AddedPause;
import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.ProjectAllocation;
import com.nxtime.nxtime.domain.ProjectAllocation.Origen;
import com.nxtime.nxtime.domain.ProjectAssignment;
import com.nxtime.nxtime.domain.ProjectSegment;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.AddedPauseRepository;
import com.nxtime.nxtime.repository.ProjectAllocationRepository;
import com.nxtime.nxtime.repository.ProjectAssignmentRepository;
import com.nxtime.nxtime.repository.ProjectSegmentRepository;
import com.nxtime.nxtime.service.ProjectAllocationService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Ver {@link ProjectAllocationService}. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ProjectAllocationServiceImpl implements ProjectAllocationService {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ProjectAllocationRepository allocationRepository;
    private final ProjectSegmentRepository segmentRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final AddedPauseRepository addedPauseRepository;

    public ProjectAllocationServiceImpl(
            ProjectAllocationRepository allocationRepository,
            ProjectSegmentRepository segmentRepository,
            ProjectAssignmentRepository assignmentRepository,
            AddedPauseRepository addedPauseRepository) {
        this.allocationRepository = allocationRepository;
        this.segmentRepository = segmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.addedPauseRepository = addedPauseRepository;
    }

    @Override
    public void alCerrar(TimeEntry registro) {
        segmentRepository.findByRegistroAndFinIsNull(registro).ifPresent(tramo -> {
            tramo.setFin(registro.getHoraSalida());
            segmentRepository.save(tramo);
        });
        recalcular(registro);
    }

    @Override
    public void alCambiarPausas(TimeEntry registro) {
        // Con la jornada abierta no hay neto todavía: se calculará al cerrar.
        if (registro.getHoraSalida() != null) {
            recalcular(registro);
        }
    }

    @Override
    public void alCorregir(TimeEntry original, TimeEntry corregido) {
        List<ProjectSegment> tramos = segmentRepository.findByRegistroOrderByInicioAsc(original);
        tramos.forEach(tramo -> tramo.setRegistro(corregido));
        segmentRepository.saveAll(tramos);

        List<ProjectAllocation> imputaciones = allocationRepository.findByRegistroOrderByIdAsc(original);
        imputaciones.forEach(imputacion -> imputacion.setRegistro(corregido));
        allocationRepository.saveAll(imputaciones);

        if (imputaciones.isEmpty()) {
            recalcular(corregido);
            return;
        }
        // Las horas han cambiado y los tramos ya no describen la jornada nueva:
        // se conserva la proporción del reparto que había, y desde aquí es MANUAL.
        Map<Project, Long> reparto = new LinkedHashMap<>();
        imputaciones.forEach(i -> reparto.put(i.getProyecto(), i.getSegundos()));
        sustituir(corregido, reescalar(reparto, neto(corregido)), Origen.MANUAL);
    }

    // ------------------------------------------------------------------
    // Tramos
    // ------------------------------------------------------------------

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Project> proyectosParaFichar(User persona, LocalDate dia) {
        return assignmentRepository.findVigentesDe(persona.getId(), dia).stream()
                .map(ProjectAssignment::getProyecto)
                .filter(Project::isActivo)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<Project> proyectoEnCurso(TimeEntry registro) {
        return segmentRepository.findByRegistroAndFinIsNull(registro).map(ProjectSegment::getProyecto);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Project> proyectosDelDia(User persona, LocalDate dia) {
        return assignmentRepository.findVigentesDe(persona.getId(), dia).stream()
                .map(ProjectAssignment::getProyecto)
                .distinct()
                .toList();
    }

    @Override
    public void aplicarReparto(TimeEntry registro, Map<Project, Long> segundosPorProyecto) {
        sustituir(registro, new LinkedHashMap<>(segundosPorProyecto), Origen.MANUAL);
    }

    @Override
    public void abrirTramo(TimeEntry registro, Project proyecto) {
        segmentRepository.save(ProjectSegment.builder()
                .empresa(registro.getEmpresa())
                .registro(registro)
                .proyecto(proyecto)
                .inicio(registro.getHoraEntrada())
                .build());
    }

    @Override
    public void cambiarDeProyecto(TimeEntry registro, Project proyecto, Instant ahora) {
        var enCurso = segmentRepository.findByRegistroAndFinIsNull(registro);
        if (enCurso.isEmpty()) {
            // Sin tramos: lo fichado hasta ahora (y sus pausas) pasa a este
            // proyecto. Las añadidas a mano se descuentan por solape al
            // calcular, así que aquí solo van las fichadas.
            long anadidas = addedPauseRepository.findByRegistroAndAnuladaFalseOrderByInicioAsc(registro).stream()
                    .mapToLong(AddedPause::getSegundos).sum();
            segmentRepository.save(ProjectSegment.builder()
                    .empresa(registro.getEmpresa())
                    .registro(registro)
                    .proyecto(proyecto)
                    .inicio(registro.getHoraEntrada())
                    .segundosPausa(Math.max(0, registro.getSegundosPausaAcumulados() - anadidas))
                    .build());
            return;
        }
        ProjectSegment actual = enCurso.get();
        actual.setFin(ahora);
        // flush antes de abrir el siguiente: el índice único de "un tramo
        // abierto por jornada" se comprueba fila a fila, y el INSERT del nuevo
        // no puede llegar antes que el UPDATE que cierra este.
        segmentRepository.saveAndFlush(actual);
        segmentRepository.save(ProjectSegment.builder()
                .empresa(registro.getEmpresa())
                .registro(registro)
                .proyecto(proyecto)
                .inicio(ahora)
                .build());
    }

    @Override
    public void sumarPausaAlTramo(TimeEntry registro, long segundos) {
        segmentRepository.findByRegistroAndFinIsNull(registro).ifPresent(tramo -> {
            tramo.setSegundosPausa(tramo.getSegundosPausa() + Math.max(0, segundos));
            segmentRepository.save(tramo);
        });
    }

    // ------------------------------------------------------------------

    private void recalcular(TimeEntry registro) {
        long neto = neto(registro);
        List<ProjectAllocation> actuales = allocationRepository.findByRegistroOrderByIdAsc(registro);

        if (actuales.stream().anyMatch(i -> i.getOrigen() == Origen.MANUAL)) {
            Map<Project, Long> reparto = new LinkedHashMap<>();
            actuales.forEach(i -> reparto.put(i.getProyecto(), i.getSegundos()));
            sustituir(registro, reescalar(reparto, neto), Origen.MANUAL);
            return;
        }

        List<ProjectSegment> tramos = segmentRepository.findByRegistroOrderByInicioAsc(registro);
        if (!tramos.isEmpty()) {
            sustituir(registro, desdeTramos(registro, tramos, neto), Origen.TRAMOS);
            return;
        }

        if (!actuales.isEmpty()) {
            Map<Project, Long> reparto = new LinkedHashMap<>();
            actuales.forEach(i -> reparto.put(i.getProyecto(), i.getSegundos()));
            sustituir(registro, reescalar(reparto, neto), Origen.TRAMOS);
            return;
        }

        LocalDate dia = registro.getHoraEntrada().atZone(MADRID).toLocalDate();
        List<ProjectAssignment> vigentes = assignmentRepository.findVigentesDe(registro.getUsuario().getId(), dia);
        if (vigentes.size() == 1) {
            sustituir(registro, Map.of(vigentes.get(0).getProyecto(), neto), Origen.TRAMOS);
        }
    }

    /**
     * Cada tramo, menos sus pausas fichadas y menos lo que le solapen las
     * pausas añadidas a mano. Lo que sobre o falte por redondeos o por datos
     * incoherentes se ajusta al final para que la suma sea exactamente el neto.
     */
    private Map<Project, Long> desdeTramos(TimeEntry registro, List<ProjectSegment> tramos, long neto) {
        List<AddedPause> anadidas = addedPauseRepository.findByRegistroAndAnuladaFalseOrderByInicioAsc(registro);
        Map<Project, Long> reparto = new LinkedHashMap<>();
        for (ProjectSegment tramo : tramos) {
            Instant fin = tramo.getFin() != null ? tramo.getFin() : registro.getHoraSalida();
            long segundos = tramo.segundosBrutos(fin) - tramo.getSegundosPausa();
            for (AddedPause pausa : anadidas) {
                segundos -= solape(tramo.getInicio(), fin, pausa.getInicio(), pausa.getFin());
            }
            reparto.merge(tramo.getProyecto(), Math.max(0, segundos), Long::sum);
        }
        return cuadrar(reparto, neto);
    }

    /** Reparto proporcional al neto, con el resto al proyecto con más horas. */
    static Map<Project, Long> reescalar(Map<Project, Long> reparto, long neto) {
        long total = reparto.values().stream().mapToLong(Long::longValue).sum();
        Map<Project, Long> resultado = new LinkedHashMap<>();
        if (total == 0) {
            // Nada que proporcionar: todo al primero, para no perder el neto.
            reparto.keySet().forEach(p -> resultado.put(p, 0L));
            resultado.entrySet().stream().findFirst().ifPresent(e -> e.setValue(neto));
            return resultado;
        }
        reparto.forEach((proyecto, segundos) -> resultado.put(proyecto, segundos * neto / total));
        return cuadrar(resultado, neto);
    }

    /** Ajusta la línea mayor para que la suma sea exactamente {@code neto}. */
    static Map<Project, Long> cuadrar(Map<Project, Long> reparto, long neto) {
        long suma = reparto.values().stream().mapToLong(Long::longValue).sum();
        long diferencia = neto - suma;
        if (diferencia != 0 && !reparto.isEmpty()) {
            List<Map.Entry<Project, Long>> filas = new ArrayList<>(reparto.entrySet());
            filas.sort(Comparator.comparingLong((Map.Entry<Project, Long> e) -> e.getValue()).reversed());
            for (Map.Entry<Project, Long> fila : filas) {
                long nuevo = Math.max(0, fila.getValue() + diferencia);
                diferencia -= nuevo - fila.getValue();
                fila.setValue(nuevo);
                if (diferencia == 0) {
                    break;
                }
            }
        }
        return reparto;
    }

    /**
     * Deja las imputaciones de la jornada exactamente como {@code reparto}.
     *
     * Actualiza las filas que ya existen en vez de borrar y volver a insertar:
     * Hibernate ejecuta los INSERT antes que los DELETE al confirmar, y el
     * índice único (registro, proyecto) rechazaría la fila nueva.
     */
    private void sustituir(TimeEntry registro, Map<Project, Long> reparto, Origen origen) {
        Instant ahora = Instant.now();
        Map<Long, ProjectAllocation> existentes = new LinkedHashMap<>();
        allocationRepository.findByRegistroOrderByIdAsc(registro)
                .forEach(i -> existentes.put(i.getProyecto().getId(), i));

        List<ProjectAllocation> guardar = new ArrayList<>();
        reparto.forEach((proyecto, segundos) -> {
            ProjectAllocation fila = existentes.remove(proyecto.getId());
            if (fila == null) {
                fila = ProjectAllocation.builder()
                        .empresa(registro.getEmpresa())
                        .registro(registro)
                        .proyecto(proyecto)
                        .build();
            }
            fila.setSegundos(segundos);
            fila.setOrigen(origen);
            fila.setActualizadaEn(ahora);
            guardar.add(fila);
        });
        allocationRepository.deleteAll(existentes.values());
        allocationRepository.saveAll(guardar);
    }

    static long neto(TimeEntry registro) {
        if (registro.getHoraSalida() == null) {
            return 0;
        }
        long bruto = Duration.between(registro.getHoraEntrada(), registro.getHoraSalida()).getSeconds();
        return Math.max(0, bruto - registro.getSegundosPausaAcumulados());
    }

    private static long solape(Instant inicioA, Instant finA, Instant inicioB, Instant finB) {
        Instant inicio = inicioA.isAfter(inicioB) ? inicioA : inicioB;
        Instant fin = finA.isBefore(finB) ? finA : finB;
        return Math.max(0, Duration.between(inicio, fin).getSeconds());
    }
}
