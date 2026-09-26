package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.config.CacheConfig;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.AnalyticsGrouping;
import com.nxtime.nxtime.domain.AnalyticsPeriod;
import com.nxtime.nxtime.domain.AnalyticsScope;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceReasonDays;
import com.nxtime.nxtime.dto.AbsenteeismResponse;
import com.nxtime.nxtime.dto.AbsenteeismRow;
import com.nxtime.nxtime.dto.AnalyticsSummaryResponse;
import com.nxtime.nxtime.dto.AnalyticsWindow;
import com.nxtime.nxtime.dto.PunctualityResponse;
import com.nxtime.nxtime.dto.PunctualityRow;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.AnalyticsRepository;
import com.nxtime.nxtime.repository.AnalyticsRepository.JornadasProjection;
import com.nxtime.nxtime.repository.AnalyticsRepository.PersonaProjection;
import com.nxtime.nxtime.repository.AnalyticsRepository.RetrasosProjection;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AnalyticsService;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.JornadaTeoricaService.DiaTeorico;
import com.nxtime.nxtime.service.ReglasDeAbsentismo;
import com.nxtime.nxtime.service.ReglasDeAbsentismo.Clase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link AnalyticsService} y el ADR 026.
 *
 * <p>Dos piezas:
 * <ul>
 *   <li><b>El censo</b>: persona a persona y día a día, qué fue cada día
 *       ({@link ReglasDeAbsentismo}). Necesita el horario planificado, que
 *       vive en {@link JornadaTeoricaService} y no en SQL (plantillas,
 *       vigencias y excepciones con su precedencia), así que el cruce se hace
 *       aquí. Pero <b>ninguna consulta dentro del bucle</b>: los hechos se
 *       traen antes, en lote, con las consultas nativas de
 *       {@link AnalyticsRepository}, y el número de consultas no depende de
 *       cuántas personas ni de cuántos días.</li>
 *   <li><b>Los retrasos</b>: agregados en la base, a los tres niveles en una
 *       consulta. El censo solo pone el denominador: cuántas entradas con
 *       horario hubo.</li>
 * </ul>
 *
 * Todo se cachea media hora ({@link CacheConfig#ANALITICA}) con una clave que
 * incluye quién mira --el alcance sale de ahí-- y el día de hoy, porque la
 * cuenta llega hasta ayer.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Quien la tiene ve la empresa entera; quien no, su departamento. */
    static final String VE_LA_EMPRESA = "empleado:gestionar";

    static final String TOTAL = "Total";
    static final String SIN_DEPARTAMENTO = "Sin departamento";

    private final AnalyticsRepository analyticsRepository;
    private final UserRepository userRepository;
    private final JornadaTeoricaService jornadaTeoricaService;
    private final Clock clock;

    @Autowired
    public AnalyticsServiceImpl(
            AnalyticsRepository analyticsRepository,
            UserRepository userRepository,
            JornadaTeoricaService jornadaTeoricaService) {
        this(analyticsRepository, userRepository, jornadaTeoricaService, Clock.system(MADRID));
    }

    AnalyticsServiceImpl(
            AnalyticsRepository analyticsRepository,
            UserRepository userRepository,
            JornadaTeoricaService jornadaTeoricaService,
            Clock clock) {
        this.analyticsRepository = analyticsRepository;
        this.userRepository = userRepository;
        this.jornadaTeoricaService = jornadaTeoricaService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Lo que se publica
    // ------------------------------------------------------------------

    @Override
    @Cacheable(cacheNames = CacheConfig.ANALITICA,
            key = "'resumen:' + #actor.id + ':' + #periodo + ':' + #fecha + ':' + T(java.time.LocalDate).now()")
    public AnalyticsSummaryResponse resumen(User actor, AnalyticsPeriod periodo, LocalDate fecha) {
        Ventana ventana = ventana(actor, periodo, fecha);
        Censo censo = censar(ventana);
        Acumulado total = censo.total();
        PunctualityRow puntualidad = filaDePuntualidad(null, TOTAL, total, censo.retrasosTotales());

        BigDecimal incompletas = null;
        Long minutosMedios = null;
        if (!censo.vacio()) {
            JornadasProjection jornadas = analyticsRepository.jornadas(
                    censo.ids(), inicioDelDia(ventana.desde()), inicioDelDia(ventana.evaluadoHasta().plusDays(1)));
            incompletas = ReglasDeAbsentismo.porcentaje(numero(jornadas.getIncompletas()), numero(jornadas.getJornadas()));
            long diasConJornada = censo.diasConJornada();
            if (diasConJornada > 0 && jornadas.getSegundos() != null) {
                // Truncado, como en el panel: 89 segundos son 1 minuto.
                minutosMedios = (long) (jornadas.getSegundos() / 60) / diasConJornada;
            }
        }

        return new AnalyticsSummaryResponse(
                ventana.dto(),
                censo.personas().size(),
                ReglasDeAbsentismo.porcentaje(total.perdidos(), total.laborables),
                ReglasDeAbsentismo.porcentaje(total.sinFichaje, total.laborables),
                puntualidad.puntualidad(),
                puntualidad.retrasoMedioMinutos(),
                incompletas,
                minutosMedios);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.ANALITICA,
            key = "'absentismo:' + #actor.id + ':' + #periodo + ':' + #fecha + ':' + #agrupar + ':' "
                    + "+ T(java.time.LocalDate).now()")
    public AbsenteeismResponse absentismo(
            User actor, AnalyticsPeriod periodo, LocalDate fecha, AnalyticsGrouping agrupar) {
        Ventana ventana = ventana(actor, periodo, fecha);
        Censo censo = censar(ventana);
        List<AbsenteeismRow> filas = grupos(censo, agrupar).stream()
                .map(grupo -> filaDeAbsentismo(grupo.id(), grupo.nombre(), grupo.acumulado()))
                .toList();
        return new AbsenteeismResponse(
                ventana.dto(), agrupar, filaDeAbsentismo(null, TOTAL, censo.total()), filas);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.ANALITICA,
            key = "'puntualidad:' + #actor.id + ':' + #periodo + ':' + #fecha + ':' + #agrupar + ':' "
                    + "+ T(java.time.LocalDate).now()")
    public PunctualityResponse puntualidad(
            User actor, AnalyticsPeriod periodo, LocalDate fecha, AnalyticsGrouping agrupar) {
        Ventana ventana = ventana(actor, periodo, fecha);
        Censo censo = censar(ventana);
        List<PunctualityRow> filas = grupos(censo, agrupar).stream()
                .map(grupo -> filaDePuntualidad(grupo.id(), grupo.nombre(), grupo.acumulado(),
                        censo.retrasosDe(agrupar, grupo.id())))
                .toList();
        return new PunctualityResponse(
                ventana.dto(), agrupar,
                filaDePuntualidad(null, TOTAL, censo.total(), censo.retrasosTotales()), filas);
    }

    // ------------------------------------------------------------------
    // El periodo y el alcance
    // ------------------------------------------------------------------

    /**
     * El periodo que contiene {@code fecha}, contado hasta ayer, y lo que ve
     * quien pregunta.
     */
    Ventana ventana(User actor, AnalyticsPeriod periodo, LocalDate fecha) {
        LocalDate desde = periodo.inicio(fecha);
        LocalDate hasta = periodo.fin(fecha);
        LocalDate ayer = LocalDate.now(clock).minusDays(1);
        LocalDate evaluadoHasta = desde.isAfter(ayer) ? null : (hasta.isBefore(ayer) ? hasta : ayer);

        long empresaId = actor.getEmpresa().getId();
        if (RoleAuthorities.tiene(actor, VE_LA_EMPRESA)) {
            return new Ventana(
                    new AnalyticsWindow(periodo, desde, hasta, evaluadoHasta, AnalyticsScope.EMPRESA, null),
                    empresaId, null);
        }
        Department departamento = actor.getDepartamento();
        if (departamento == null) {
            // Ni la empresa entera (no le corresponde) ni un cero (afirmaría
            // que su equipo no falta nunca): que se sepa por qué no hay cifras.
            throw new BusinessException("No tienes departamento asignado, y la analítica de un gestor abarca "
                    + "solo el suyo. Pide a RRHH que te asigne uno.");
        }
        return new Ventana(
                new AnalyticsWindow(periodo, desde, hasta, evaluadoHasta,
                        AnalyticsScope.DEPARTAMENTO, departamento.getNombre()),
                empresaId, departamento.getId());
    }

    record Ventana(AnalyticsWindow dto, long empresaId, Long departamentoId) {

        LocalDate desde() {
            return dto.desde();
        }

        LocalDate evaluadoHasta() {
            return dto.evaluadoHasta();
        }
    }

    // ------------------------------------------------------------------
    // El censo
    // ------------------------------------------------------------------

    Censo censar(Ventana ventana) {
        if (ventana.evaluadoHasta() == null) {
            return Censo.VACIO;
        }
        LocalDate desde = ventana.desde();
        LocalDate hasta = ventana.evaluadoHasta();

        List<PersonaProjection> personas =
                analyticsRepository.personas(ventana.empresaId(), ventana.departamentoId(), desde, hasta);
        if (personas.isEmpty()) {
            return Censo.VACIO;
        }
        List<Long> ids = personas.stream().map(PersonaProjection::getUsuarioId).toList();

        // Los hechos, en lote: cinco consultas se mire a diez personas o a mil.
        Map<Long, List<DiaTeorico>> planificado =
                jornadaTeoricaService.planificadoDeVarios(userRepository.findAllById(ids), desde, hasta);
        Map<Long, Set<LocalDate>> fichados = analyticsRepository
                .diasConJornada(ids, inicioDelDia(desde), inicioDelDia(hasta.plusDays(1))).stream()
                .collect(Collectors.groupingBy(AnalyticsRepository.UsuarioDiaProjection::getUsuarioId,
                        Collectors.mapping(AnalyticsRepository.UsuarioDiaProjection::getDia, Collectors.toSet())));
        Map<Long, Map<LocalDate, AbsenceType>> ausencias = new HashMap<>();
        analyticsRepository.diasDeAusencia(ids, desde, hasta).forEach(fila -> ausencias
                .computeIfAbsent(fila.getUsuarioId(), id -> new HashMap<>())
                .put(fila.getDia(), AbsenceType.valueOf(fila.getTipo())));
        Map<Long, Set<LocalDate>> aceptadas = analyticsRepository.ausenciasAceptadas(ids, desde, hasta).stream()
                .collect(Collectors.groupingBy(AnalyticsRepository.UsuarioDiaProjection::getUsuarioId,
                        Collectors.mapping(AnalyticsRepository.UsuarioDiaProjection::getDia, Collectors.toSet())));

        Map<Long, Acumulado> porPersona = new LinkedHashMap<>();
        for (PersonaProjection persona : personas) {
            long id = persona.getUsuarioId();
            // Desde que empezó a fichar y hasta que se fue: antes y después
            // no se le debía nada a esta empresa.
            LocalDate primero = max(desde, persona.getPrimerDia());
            LocalDate ultimo = persona.getDiaDeBaja() == null ? hasta : min(hasta, persona.getDiaDeBaja());

            Acumulado acumulado = new Acumulado();
            acumulado.personas.add(id);
            Set<LocalDate> susFichajes = fichados.getOrDefault(id, Set.of());
            Map<LocalDate, AbsenceType> susAusencias = ausencias.getOrDefault(id, Map.of());
            Set<LocalDate> susAceptadas = aceptadas.getOrDefault(id, Set.of());

            for (DiaTeorico dia : planificado.getOrDefault(id, List.of())) {
                LocalDate fecha = dia.fecha();
                if (fecha.isBefore(primero) || fecha.isAfter(ultimo)) {
                    continue;
                }
                boolean fichado = susFichajes.contains(fecha);
                ReglasDeAbsentismo.Dia clasificado = ReglasDeAbsentismo.clasificar(
                        dia, susAusencias.get(fecha), fichado, susAceptadas.contains(fecha));
                acumulado.sumar(clasificado, fichado, dia.tieneCuadrante());
            }
            porPersona.put(id, acumulado);
        }

        return new Censo(personas, ids, porPersona, analyticsRepository.retrasos(ids, desde, hasta));
    }

    record Censo(
            List<PersonaProjection> personas,
            List<Long> ids,
            Map<Long, Acumulado> porPersona,
            List<RetrasosProjection> retrasos) {

        static final Censo VACIO = new Censo(List.of(), List.of(), Map.of(), List.of());

        boolean vacio() {
            return personas.isEmpty();
        }

        Acumulado total() {
            Acumulado total = new Acumulado();
            porPersona.values().forEach(total::sumar);
            return total;
        }

        long diasConJornada() {
            return porPersona.values().stream().mapToLong(a -> a.fichados).sum();
        }

        RetrasosProjection retrasosTotales() {
            return retrasos.stream().filter(r -> "EMPRESA".equals(r.getNivel())).findFirst().orElse(null);
        }

        /** Los retrasos de un grupo; en DEPARTAMENTO, un id null es "sin departamento". */
        RetrasosProjection retrasosDe(AnalyticsGrouping agrupar, Long id) {
            return switch (agrupar) {
                case EMPRESA -> retrasosTotales();
                case DEPARTAMENTO -> retrasos.stream()
                        .filter(r -> "DEPARTAMENTO".equals(r.getNivel()))
                        .filter(r -> Objects.equals(r.getDepartamentoId(), id))
                        .findFirst().orElse(null);
                case EMPLEADO -> retrasos.stream()
                        .filter(r -> "EMPLEADO".equals(r.getNivel()))
                        .filter(r -> Objects.equals(r.getUsuarioId(), id))
                        .findFirst().orElse(null);
            };
        }
    }

    /** Lo que se va sumando de un grupo de personas. */
    static final class Acumulado {
        final Set<Long> personas = new HashSet<>();
        int laborables;
        int trabajados;
        int justificados;
        int sinFichaje;
        int vacaciones;
        /** Días con horario teórico en que se fichó: el denominador de la puntualidad. */
        int conHorario;
        /** Días con alguna jornada, para la media de minutos por día. */
        int fichados;
        final Map<String, Integer> motivos = new HashMap<>();

        void sumar(ReglasDeAbsentismo.Dia dia, boolean fichado, boolean conCuadrante) {
            Clase clase = dia.clase();
            // Cuenta también el sábado trabajado: la media de minutos por día
            // es de los días con jornada, fueran o no laborables.
            if (fichado) {
                fichados++;
            }
            if (clase == Clase.VACACIONES) {
                vacaciones++;
            }
            if (!clase.cuenta()) {
                return;
            }
            laborables++;
            switch (clase) {
                case TRABAJADO -> trabajados++;
                case AUSENCIA_JUSTIFICADA -> {
                    justificados++;
                    motivos.merge(dia.motivo(), 1, Integer::sum);
                }
                case SIN_FICHAJE -> sinFichaje++;
                default -> {
                    // NO_LABORABLE y VACACIONES ya han salido arriba.
                }
            }
            if (fichado && conCuadrante && clase == Clase.TRABAJADO) {
                conHorario++;
            }
        }

        void sumar(Acumulado otro) {
            personas.addAll(otro.personas);
            laborables += otro.laborables;
            trabajados += otro.trabajados;
            justificados += otro.justificados;
            sinFichaje += otro.sinFichaje;
            vacaciones += otro.vacaciones;
            conHorario += otro.conHorario;
            fichados += otro.fichados;
            otro.motivos.forEach((motivo, dias) -> motivos.merge(motivo, dias, Integer::sum));
        }

        int perdidos() {
            return justificados + sinFichaje;
        }
    }

    // ------------------------------------------------------------------
    // Los grupos y las filas
    // ------------------------------------------------------------------

    record Grupo(Long id, String nombre, Acumulado acumulado) {
    }

    /**
     * Los grupos de una agrupación, ya sumados. Los departamentos por nombre
     * con "Sin departamento" al final; las personas, en el orden de la
     * consulta (por nombre).
     */
    private static List<Grupo> grupos(Censo censo, AnalyticsGrouping agrupar) {
        return switch (agrupar) {
            case EMPRESA -> List.of();
            case EMPLEADO -> censo.personas().stream()
                    .map(p -> new Grupo(p.getUsuarioId(), p.getNombre(), censo.porPersona().get(p.getUsuarioId())))
                    .toList();
            case DEPARTAMENTO -> {
                Map<Long, String> nombres = new HashMap<>();
                Map<Long, Acumulado> porDepartamento = new HashMap<>();
                for (PersonaProjection persona : censo.personas()) {
                    // HashMap admite la clave null: es "sin departamento".
                    nombres.put(persona.getDepartamentoId(), persona.getDepartamento());
                    porDepartamento.computeIfAbsent(persona.getDepartamentoId(), id -> new Acumulado())
                            .sumar(censo.porPersona().get(persona.getUsuarioId()));
                }
                yield porDepartamento.entrySet().stream()
                        .map(e -> new Grupo(e.getKey(),
                                e.getKey() == null ? SIN_DEPARTAMENTO : nombres.get(e.getKey()), e.getValue()))
                        .sorted(Comparator.comparing((Grupo g) -> g.id() == null)
                                .thenComparing(Grupo::nombre, String.CASE_INSENSITIVE_ORDER))
                        .toList();
            }
        };
    }

    private static AbsenteeismRow filaDeAbsentismo(Long id, String nombre, Acumulado a) {
        List<AbsenceReasonDays> motivos = a.motivos.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new AbsenceReasonDays(e.getKey(), ReglasDeAbsentismo.etiqueta(e.getKey()), e.getValue()))
                .toList();
        return new AbsenteeismRow(
                id, nombre, a.personas.size(),
                a.laborables, a.trabajados, a.justificados, a.sinFichaje, a.vacaciones,
                ReglasDeAbsentismo.porcentaje(a.perdidos(), a.laborables),
                ReglasDeAbsentismo.porcentaje(a.sinFichaje, a.laborables),
                motivos);
    }

    private static PunctualityRow filaDePuntualidad(Long id, String nombre, Acumulado a, RetrasosProjection r) {
        int retrasos = r == null ? 0 : (int) numero(r.getRetrasos());
        // Un retraso siempre cae en un día con horario y fichaje, así que no
        // debería superar a las entradas; el max es por si una incidencia ya
        // decidida sobrevive a una corrección que le quitó el fichaje.
        int puntuales = Math.max(0, a.conHorario - retrasos);
        return new PunctualityRow(
                id, nombre,
                a.conHorario, puntuales, retrasos,
                ReglasDeAbsentismo.porcentaje(puntuales, (long) puntuales + retrasos),
                retrasos == 0 ? null : redondear(r.getMedia()),
                retrasos == 0 ? null : redondear(r.getMediana()),
                r == null ? 0 : (int) numero(r.getHasta30()),
                r == null ? 0 : (int) numero(r.getMasDe30()));
    }

    // ------------------------------------------------------------------

    private static Instant inicioDelDia(LocalDate dia) {
        return dia.atStartOfDay(MADRID).toInstant();
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static long numero(Long valor) {
        return valor == null ? 0 : valor;
    }

    private static Double redondear(Double valor) {
        return valor == null ? null : Math.round(valor * 10) / 10.0;
    }
}
