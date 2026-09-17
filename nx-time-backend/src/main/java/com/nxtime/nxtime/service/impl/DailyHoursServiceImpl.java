package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DailyHoursResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.DailyHoursService;
import com.nxtime.nxtime.service.OvertimeCalculator;
import com.nxtime.nxtime.service.WorkingDayService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link DailyHoursService}.
 *
 * No inventa ninguna regla: qué día es hábil lo decide {@link
 * WorkingDayService} y cuánto se espera de un día, {@link
 * OvertimeCalculator#objetivoSemanal} con un solo día. Son las mismas cuentas
 * que el detector de horas extra, así que el gráfico no puede decir que un
 * día sobra cuando el detector dice que no.
 */
@Service
@Transactional(readOnly = true)
public class DailyHoursServiceImpl implements DailyHoursService {

    /** Un mes largo con margen. El gráfico más grande es el de un mes. */
    static final int MAXIMO_DIAS = 62;

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final UserRepository userRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final AbsenceRequestRepository absenceRequestRepository;
    private final HolidayRepository holidayRepository;
    private final WorkingDayService workingDayService;

    public DailyHoursServiceImpl(
            UserRepository userRepository,
            TimeEntryRepository timeEntryRepository,
            AbsenceRequestRepository absenceRequestRepository,
            HolidayRepository holidayRepository,
            WorkingDayService workingDayService) {
        this.userRepository = userRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.absenceRequestRepository = absenceRequestRepository;
        this.holidayRepository = holidayRepository;
        this.workingDayService = workingDayService;
    }

    @Override
    public List<DailyHoursResponse> horasPorDia(String email, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new BusinessException("La fecha de inicio no puede ser posterior a la de fin.", HttpStatus.BAD_REQUEST);
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new BusinessException("El periodo no puede pasar de " + MAXIMO_DIAS + " días.", HttpStatus.BAD_REQUEST);
        }
        User usuario = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        Map<LocalDate, Long> segundos = new HashMap<>();
        timeEntryRepository.sumarSegundosPorDiaDeUsuario(
                        usuario.getId(),
                        desde.atStartOfDay(MADRID).toInstant(),
                        hasta.plusDays(1).atStartOfDay(MADRID).toInstant())
                .forEach(fila -> segundos.put(fila.getDia(), fila.getSegundos()));

        Set<LocalDate> habiles = workingDayService.diasHabiles(usuario.getEmpresa(), desde, hasta);
        Map<LocalDate, String> festivos = festivos(usuario.getEmpresa().getId(), desde, hasta);
        Map<LocalDate, String> ausencias = ausencias(usuario, desde, hasta);
        long esperadoPorDia = OvertimeCalculator.objetivoSemanal(usuario.getHorasSemanales(), 1);

        List<DailyHoursResponse> dias = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            boolean seEspera = habiles.contains(dia) && !ausencias.containsKey(dia);
            dias.add(new DailyHoursResponse(
                    dia,
                    segundos.getOrDefault(dia, 0L) / 60,
                    seEspera ? esperadoPorDia : 0,
                    festivos.get(dia),
                    ausencias.get(dia)));
        }
        return dias;
    }

    /** Con nombre: el calendario cacheado solo guarda las fechas. Como mucho dos años. */
    private Map<LocalDate, String> festivos(long empresaId, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, String> porFecha = new HashMap<>();
        IntStream.rangeClosed(desde.getYear(), hasta.getYear())
                .forEach(anio -> holidayRepository.findByEmpresaYAnio(empresaId, anio).stream()
                        .filter(f -> !f.getFecha().isBefore(desde) && !f.getFecha().isAfter(hasta))
                        .forEach(f -> porFecha.putIfAbsent(f.getFecha(), descripcion(f))));
        return porFecha;
    }

    private static String descripcion(Holiday festivo) {
        return festivo.getDescripcion() != null ? festivo.getDescripcion() : "Festivo";
    }

    /** Solo las APROBADAS: una pendiente no cambia lo que se espera de ese día. */
    private Map<LocalDate, String> ausencias(User usuario, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, String> porFecha = new HashMap<>();
        for (AbsenceRequest ausencia : absenceRequestRepository.findByUsuario(usuario)) {
            if (ausencia.getEstado() != AbsenceStatus.APROBADA) {
                continue;
            }
            LocalDate inicio = ausencia.getFechaInicio().isBefore(desde) ? desde : ausencia.getFechaInicio();
            LocalDate fin = ausencia.getFechaFin().isAfter(hasta) ? hasta : ausencia.getFechaFin();
            for (LocalDate dia = inicio; !dia.isAfter(fin); dia = dia.plusDays(1)) {
                porFecha.putIfAbsent(dia, ausencia.getTipo().getEtiqueta());
            }
        }
        return porFecha;
    }
}
