package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.ScheduleAssignment;
import com.nxtime.nxtime.domain.ScheduleException;
import com.nxtime.nxtime.domain.ScheduleExceptionType;
import com.nxtime.nxtime.domain.ScheduleSlot;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.ScheduleAssignmentRepository;
import com.nxtime.nxtime.repository.ScheduleExceptionRepository;
import com.nxtime.nxtime.repository.ScheduleSlotRepository;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.NonWorkingDayService;
import com.nxtime.nxtime.service.OvertimeCalculator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ver {@link JornadaTeoricaService}. */
@Service
@Transactional(readOnly = true)
public class JornadaTeoricaServiceImpl implements JornadaTeoricaService {

    private final NonWorkingDayService nonWorkingDayService;
    private final ScheduleAssignmentRepository assignmentRepository;
    private final ScheduleSlotRepository slotRepository;
    private final ScheduleExceptionRepository exceptionRepository;

    public JornadaTeoricaServiceImpl(
            NonWorkingDayService nonWorkingDayService,
            ScheduleAssignmentRepository assignmentRepository,
            ScheduleSlotRepository slotRepository,
            ScheduleExceptionRepository exceptionRepository) {
        this.nonWorkingDayService = nonWorkingDayService;
        this.assignmentRepository = assignmentRepository;
        this.slotRepository = slotRepository;
        this.exceptionRepository = exceptionRepository;
    }

    @Override
    public DiaTeorico dia(User persona, LocalDate fecha) {
        return dias(persona, fecha, fecha).get(0);
    }

    /**
     * Cuatro consultas para todo el rango, se pida un día o un mes: los
     * motivos de no laborable, las asignaciones, las excepciones y los tramos
     * de las plantillas que aparezcan. Nada dentro del bucle toca la base.
     */
    @Override
    public List<DiaTeorico> dias(User persona, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            return List.of();
        }

        Map<LocalDate, NonWorkingDayService.Motivo> noLaborables =
                nonWorkingDayService.motivosEnRango(persona, desde, hasta);
        List<ScheduleAssignment> asignaciones =
                assignmentRepository.findDeUsuarioEnRango(persona.getId(), desde, hasta);
        Map<LocalDate, List<ScheduleException>> excepciones = exceptionRepository
                .findByUsuario_IdAndFechaBetweenOrderByFechaAscInicioAsc(persona.getId(), desde, hasta)
                .stream()
                .collect(Collectors.groupingBy(ScheduleException::getFecha, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Map<DayOfWeek, List<Tramo>>> tramosPorPlantilla = tramosDe(asignaciones);

        List<DiaTeorico> dias = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            dias.add(diaTeorico(fecha, asignaciones, noLaborables, excepciones, tramosPorPlantilla));
        }
        return dias;
    }

    /**
     * Las mismas cuatro fuentes que {@link #dias}, pero de todos a la vez:
     * cinco consultas se pida el día de una persona o el de mil. Luego, la
     * misma función de precedencia, persona a persona, sobre lo ya cargado.
     */
    @Override
    public Map<Long, DiaTeorico> diaDeVarios(Collection<User> personas, LocalDate fecha) {
        if (personas.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = personas.stream().map(User::getId).toList();

        Map<Long, NonWorkingDayService.Motivo> noLaborables = nonWorkingDayService.motivosDelDia(personas, fecha);
        Map<Long, List<ScheduleAssignment>> asignaciones = assignmentRepository.findDeUsuariosEl(ids, fecha).stream()
                .collect(Collectors.groupingBy(asignacion -> asignacion.getUsuario().getId()));
        Map<Long, List<ScheduleException>> excepciones = exceptionRepository.findByUsuario_IdInAndFecha(ids, fecha)
                .stream()
                .collect(Collectors.groupingBy(excepcion -> excepcion.getUsuario().getId()));
        Map<Long, Map<DayOfWeek, List<Tramo>>> tramosPorPlantilla = tramosDe(
                asignaciones.values().stream().flatMap(List::stream).toList());

        Map<Long, DiaTeorico> porPersona = new HashMap<>();
        for (long id : ids) {
            NonWorkingDayService.Motivo motivo = noLaborables.get(id);
            List<ScheduleException> suyas = excepciones.getOrDefault(id, List.of());
            porPersona.put(id, diaTeorico(
                    fecha,
                    asignaciones.getOrDefault(id, List.of()),
                    motivo != null ? Map.of(fecha, motivo) : Map.of(),
                    suyas.isEmpty() ? Map.of() : Map.of(fecha, suyas),
                    tramosPorPlantilla));
        }
        return porPersona;
    }

    @Override
    public long minutosTeoricosSemana(User persona, LocalDate lunes) {
        if (lunes.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("La semana empieza en lunes, y " + lunes + " no lo es.");
        }
        List<DiaTeorico> semana = dias(persona, lunes, lunes.plusDays(6));

        long delCuadrante = semana.stream()
                .filter(DiaTeorico::tieneCuadrante)
                .mapToLong(DiaTeorico::minutos)
                .sum();

        // Los días sin cuadrante cuentan como siempre: su parte de la jornada
        // contratada. "Hábil" es lo mismo que en WorkingDayService (de lunes a
        // viernes y no festivo) menos las ausencias aprobadas; los festivos y
        // las ausencias ya vienen como NO_LABORABLE y no llegan aquí. Así, una
        // semana sin ningún día de cuadrante da el número de siempre.
        int habilesSinCuadrante = (int) semana.stream()
                .filter(dia -> dia.origen() == Origen.SIN_CUADRANTE)
                .filter(dia -> esEntreSemana(dia.fecha()))
                .count();

        return delCuadrante
                + OvertimeCalculator.objetivoSemanal(persona.getHorasSemanales(), habilesSinCuadrante);
    }

    // ------------------------------------------------------------------

    /**
     * La precedencia, en un solo sitio: no laborable, excepción, plantilla, y
     * si no hay nada, sin cuadrante.
     */
    private DiaTeorico diaTeorico(
            LocalDate fecha,
            List<ScheduleAssignment> asignaciones,
            Map<LocalDate, NonWorkingDayService.Motivo> noLaborables,
            Map<LocalDate, List<ScheduleException>> excepciones,
            Map<Long, Map<DayOfWeek, List<Tramo>>> tramosPorPlantilla) {

        ScheduleAssignment vigente = asignaciones.stream()
                .filter(asignacion -> asignacion.vigenteEl(fecha))
                .findFirst()
                .orElse(null);
        String plantilla = vigente != null ? vigente.getPlantilla().getNombre() : null;

        NonWorkingDayService.Motivo motivo = noLaborables.get(fecha);
        if (motivo != null) {
            return new DiaTeorico(fecha, Origen.NO_LABORABLE, 0, List.of(), motivo.texto(), plantilla);
        }

        List<ScheduleException> delDia = excepciones.get(fecha);
        if (delDia != null && !delDia.isEmpty()) {
            String explicacion = delDia.stream()
                    .map(ScheduleException::getMotivo)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            boolean libre = delDia.stream().anyMatch(e -> e.getTipo() == ScheduleExceptionType.LIBRE);
            if (libre) {
                return new DiaTeorico(fecha, Origen.EXCEPCION, 0, List.of(),
                        explicacion != null ? explicacion : "Día libre", plantilla);
            }
            List<Tramo> tramos = delDia.stream()
                    .map(e -> new Tramo(e.getInicio(), e.getFin()))
                    .sorted(Comparator.comparingInt(Tramo::inicio))
                    .toList();
            return new DiaTeorico(fecha, Origen.EXCEPCION, suma(tramos), tramos, explicacion, plantilla);
        }

        if (vigente != null) {
            List<Tramo> tramos = tramosPorPlantilla
                    .getOrDefault(vigente.getPlantilla().getId(), Map.of())
                    .getOrDefault(fecha.getDayOfWeek(), List.of());
            return new DiaTeorico(fecha, Origen.CUADRANTE, suma(tramos), tramos, null, plantilla);
        }

        return new DiaTeorico(fecha, Origen.SIN_CUADRANTE, 0, List.of(), null, null);
    }

    private Map<Long, Map<DayOfWeek, List<Tramo>>> tramosDe(List<ScheduleAssignment> asignaciones) {
        Set<Long> plantillas = asignaciones.stream()
                .map(asignacion -> asignacion.getPlantilla().getId())
                .collect(Collectors.toSet());
        if (plantillas.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<DayOfWeek, List<Tramo>>> porPlantilla = new HashMap<>();
        slotRepository.findByPlantilla_IdIn(plantillas).stream()
                .sorted(Comparator.comparingInt(ScheduleSlot::getInicio))
                .forEach(tramo -> porPlantilla
                        .computeIfAbsent(tramo.getPlantilla().getId(), id -> new HashMap<>())
                        .computeIfAbsent(tramo.dia(), dia -> new ArrayList<>())
                        .add(new Tramo(tramo.getInicio(), tramo.getFin())));
        return porPlantilla;
    }

    private static int suma(List<Tramo> tramos) {
        return tramos.stream().mapToInt(Tramo::minutos).sum();
    }

    private static boolean esEntreSemana(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY;
    }
}
