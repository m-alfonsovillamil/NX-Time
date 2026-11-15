package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.EquipoAhoraResponse;
import com.nxtime.nxtime.dto.EquipoAhoraResponse.EstadoAhora;
import com.nxtime.nxtime.dto.EquipoAhoraResponse.PersonaAhora;
import com.nxtime.nxtime.dto.TeamScheduleEntryResponse;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.EquipoAhoraService;
import com.nxtime.nxtime.service.NonWorkingDayService;
import com.nxtime.nxtime.service.ScheduleService;
import java.text.Collator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quién está trabajando ahora y quién no ha fichado hoy (plan N, paso N7).
 *
 * Un número fijo de consultas, sea cual sea la plantilla: las personas, sus
 * jornadas de hoy, las que siguen abiertas desde antes (un turno de noche), los
 * motivos de día no laborable y los cuadrantes del día. Lo que más cuesta es lo
 * último, el mismo cálculo que ya hace «Cuadrantes del equipo».
 *
 * El retraso usa el mismo margen que las incidencias de cuadrante (ADR 024):
 * diez minutos. Aquí no se anota nada: es lo que se ve en este momento, y la
 * incidencia, si la hay, la detecta el proceso de la noche.
 */
@Service
@Transactional(readOnly = true)
public class EquipoAhoraServiceImpl implements EquipoAhoraService {

    /** El margen de las incidencias de cuadrante (ADR 024). */
    static final Duration MARGEN = Duration.ofMinutes(10);

    static final String MOTIVO_FIN_DE_SEMANA = "Fin de semana";
    static final String MOTIVO_LIBRE_EN_CUADRANTE = "Libre según su cuadrante";

    private final UserRepository userRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final NonWorkingDayService nonWorkingDayService;
    private final ScheduleService scheduleService;
    private final Clock clock;

    @Autowired
    public EquipoAhoraServiceImpl(UserRepository userRepository, TimeEntryRepository timeEntryRepository,
            NonWorkingDayService nonWorkingDayService, ScheduleService scheduleService) {
        this(userRepository, timeEntryRepository, nonWorkingDayService, scheduleService, Clock.systemUTC());
    }

    EquipoAhoraServiceImpl(UserRepository userRepository, TimeEntryRepository timeEntryRepository,
            NonWorkingDayService nonWorkingDayService, ScheduleService scheduleService, Clock clock) {
        this.userRepository = userRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.nonWorkingDayService = nonWorkingDayService;
        this.scheduleService = scheduleService;
        this.clock = clock;
    }

    @Override
    public EquipoAhoraResponse ahora(User actor) {
        ZoneId zona = actor.zona();
        Instant ahora = clock.instant();
        LocalDate hoy = LocalDate.ofInstant(ahora, zona);
        Instant inicioDeHoy = hoy.atStartOfDay(zona).toInstant();

        List<User> plantilla = userRepository.findByEmpresaAndActivoTrue(actor.getEmpresa());
        List<Long> ids = plantilla.stream().map(User::getId).toList();

        Map<Long, List<TimeEntry>> deHoy = ids.isEmpty() ? Map.of()
                : timeEntryRepository.findVivosDeUsuariosQueEmpiezanEntre(ids, inicioDeHoy, ahora.plusSeconds(1))
                        .stream().collect(Collectors.groupingBy(t -> t.getUsuario().getId()));
        // Las abiertas desde antes de hoy: un turno de noche que sigue en marcha.
        Map<Long, TimeEntry> abiertas = new HashMap<>();
        for (TimeEntry abierta : timeEntryRepository.findAbiertasDeEmpresa(actor.getEmpresa())) {
            abiertas.put(abierta.getUsuario().getId(), abierta);
        }
        Map<Long, NonWorkingDayService.Motivo> noLaborables = nonWorkingDayService.motivosDelDia(plantilla, hoy);
        // Quien tiene cuadrante hoy: su entrada, o null si hoy le toca libre.
        Map<Long, String> conCuadrante = new HashMap<>();
        for (TeamScheduleEntryResponse cuadrante : scheduleService.equipo(hoy, actor)) {
            if (cuadrante.dia() != null) {
                conCuadrante.put(cuadrante.usuarioId(),
                        cuadrante.dia().minutos() > 0 ? cuadrante.dia().entrada() : null);
            }
        }
        boolean finDeSemana = hoy.getDayOfWeek().getValue() >= 6;
        LocalTime horaAhora = LocalTime.ofInstant(ahora, zona);

        List<PersonaAhora> personas = new ArrayList<>(plantilla.size());
        for (User persona : plantilla) {
            List<TimeEntry> suyas = deHoy.getOrDefault(persona.getId(), List.of());
            TimeEntry abierta = abiertas.get(persona.getId());
            NonWorkingDayService.Motivo festivoOAusencia = noLaborables.get(persona.getId());
            String entradaPrevista = festivoOAusencia == null ? conCuadrante.get(persona.getId()) : null;
            // Por qué hoy no le toca, si no le toca. Los festivos y las ausencias
            // los dice NonWorkingDayService. Además, con cuadrante manda el
            // cuadrante (un día sin tramos es libre, aunque sea martes); sin él,
            // el fin de semana. Se vio ejecutándolo un domingo: salía toda la
            // plantilla «sin fichar».
            String motivo = festivoOAusencia != null ? festivoOAusencia.texto()
                    : conCuadrante.containsKey(persona.getId())
                            ? (entradaPrevista == null ? MOTIVO_LIBRE_EN_CUADRANTE : null)
                            : finDeSemana ? MOTIVO_FIN_DE_SEMANA : null;

            EstadoAhora estado;
            Instant desde = null;
            if (abierta != null) {
                estado = abierta.isEnPausa() ? EstadoAhora.EN_PAUSA : EstadoAhora.TRABAJANDO;
                desde = abierta.getHoraEntrada();
            } else if (!suyas.isEmpty()) {
                estado = EstadoAhora.TERMINADA;
                desde = suyas.stream().map(TimeEntry::getHoraEntrada).min(Comparator.naturalOrder()).orElse(null);
            } else if (motivo != null) {
                estado = EstadoAhora.NO_LABORABLE;
            } else {
                estado = EstadoAhora.SIN_FICHAR;
            }
            boolean conRetraso = estado == EstadoAhora.SIN_FICHAR && entradaPrevista != null
                    && horaAhora.isAfter(LocalTime.parse(entradaPrevista).plus(MARGEN));

            personas.add(new PersonaAhora(
                    persona.getId(),
                    persona.nombreCompleto(),
                    estado,
                    desde,
                    minutosDeHoy(suyas, abierta, ahora),
                    motivo,
                    entradaPrevista,
                    conRetraso,
                    abierta != null ? abierta.getModalidad() : null));
        }

        // Primero lo que pide atención: quien llega tarde y quien no ha
        // fichado; luego quien trabaja; al final, quien hoy no tenía que venir.
        Collator orden = Collator.getInstance(Locale.of("es", "ES"));
        personas.sort(Comparator.comparingInt(EquipoAhoraServiceImpl::prioridad)
                .thenComparing(PersonaAhora::nombre, orden));

        return new EquipoAhoraResponse(
                hoy,
                contar(personas, EstadoAhora.TRABAJANDO),
                contar(personas, EstadoAhora.EN_PAUSA),
                contar(personas, EstadoAhora.TERMINADA),
                contar(personas, EstadoAhora.SIN_FICHAR),
                (int) personas.stream().filter(PersonaAhora::conRetraso).count(),
                contar(personas, EstadoAhora.NO_LABORABLE),
                personas);
    }

    /**
     * Tiempo efectivo hasta ahora, en segundos exactos y pasado a minutos al
     * final: las de hoy ya cerradas, más la abierta (con su pausa en curso
     * descontada). La abierta se cuenta entera aunque empezara ayer.
     */
    static long minutosDeHoy(List<TimeEntry> deHoy, TimeEntry abierta, Instant ahora) {
        long segundos = 0;
        for (TimeEntry jornada : deHoy) {
            if (jornada.getHoraSalida() != null) {
                segundos += Duration.between(jornada.getHoraEntrada(), jornada.getHoraSalida()).getSeconds()
                        - jornada.getSegundosPausaAcumulados();
            }
        }
        if (abierta != null) {
            long enCurso = Duration.between(abierta.getHoraEntrada(), ahora).getSeconds()
                    - abierta.getSegundosPausaAcumulados();
            if (abierta.isEnPausa() && abierta.getInicioPausaActual() != null) {
                enCurso -= Duration.between(abierta.getInicioPausaActual(), ahora).getSeconds();
            }
            segundos += Math.max(0, enCurso);
        }
        return Math.max(0, segundos) / 60;
    }

    private static int prioridad(PersonaAhora persona) {
        if (persona.conRetraso()) {
            return 0;
        }
        return switch (persona.estado()) {
            case SIN_FICHAR -> 1;
            case TRABAJANDO -> 2;
            case EN_PAUSA -> 3;
            case TERMINADA -> 4;
            case NO_LABORABLE -> 5;
        };
    }

    private static int contar(List<PersonaAhora> personas, EstadoAhora estado) {
        return (int) personas.stream().filter(p -> p.estado() == estado).count();
    }
}
