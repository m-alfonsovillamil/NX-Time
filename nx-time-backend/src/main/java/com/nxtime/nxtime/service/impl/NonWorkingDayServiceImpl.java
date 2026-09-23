package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.service.HolidayCalendar;
import com.nxtime.nxtime.service.NonWorkingDayService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ver {@link NonWorkingDayService}. */
@Service
@Transactional(readOnly = true)
public class NonWorkingDayServiceImpl implements NonWorkingDayService {

    private final HolidayCalendar holidayCalendar;
    private final HolidayRepository holidayRepository;
    private final AbsenceRequestRepository absenceRequestRepository;

    public NonWorkingDayServiceImpl(
            HolidayCalendar holidayCalendar,
            HolidayRepository holidayRepository,
            AbsenceRequestRepository absenceRequestRepository) {
        this.holidayCalendar = holidayCalendar;
        this.holidayRepository = holidayRepository;
        this.absenceRequestRepository = absenceRequestRepository;
    }

    @Override
    public Optional<Motivo> motivo(User persona, LocalDate dia) {
        long empresaId = persona.getEmpresa().getId();
        // Primero el calendario cacheado, que es lo que se consulta en cada
        // inicio de jornada; el nombre del festivo solo se busca si lo es.
        if (holidayCalendar.festivosDelAnio(empresaId, dia.getYear()).contains(dia)) {
            return Optional.of(deFestivo(holidayRepository.findByEmpresaYAnio(empresaId, dia.getYear()), dia));
        }
        return absenceRequestRepository.findAprobadasDeUsuarioEnFecha(persona.getId(), dia).stream()
                .findFirst()
                .map(NonWorkingDayServiceImpl::deAusencia);
    }

    @Override
    public Map<LocalDate, Motivo> motivosEnRango(User persona, LocalDate desde, LocalDate hasta) {
        Map<LocalDate, Motivo> motivos = new LinkedHashMap<>();
        if (desde.isAfter(hasta)) {
            return motivos;
        }
        long empresaId = persona.getEmpresa().getId();
        // Una consulta de ausencias para todo el rango, no una por día.
        List<AbsenceRequest> ausencias =
                absenceRequestRepository.findAprobadasDeUsuarioEnRango(persona, desde, hasta);
        // Los nombres de los festivos, solo si hay alguno y una vez por año.
        Map<Integer, List<Holiday>> festivosPorAnio = new HashMap<>();

        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            if (holidayCalendar.festivosDelAnio(empresaId, dia.getYear()).contains(dia)) {
                List<Holiday> delAnio = festivosPorAnio.computeIfAbsent(
                        dia.getYear(), anio -> holidayRepository.findByEmpresaYAnio(empresaId, anio));
                motivos.put(dia, deFestivo(delAnio, dia));
                continue;
            }
            LocalDate fecha = dia;
            ausencias.stream()
                    .filter(ausencia -> !fecha.isBefore(ausencia.getFechaInicio())
                            && !fecha.isAfter(ausencia.getFechaFin()))
                    .findFirst()
                    .ifPresent(ausencia -> motivos.put(fecha, deAusencia(ausencia)));
        }
        return motivos;
    }

    /*
     * Cómo se llama el motivo, en un sitio: las dos preguntas -- un día o un
     * rango -- tienen que contestar lo mismo, palabra por palabra.
     */

    private static Motivo deFestivo(List<Holiday> festivosDelAnio, LocalDate dia) {
        String nombre = festivosDelAnio.stream()
                .filter(festivo -> festivo.getFecha().equals(dia))
                .map(Holiday::getDescripcion)
                .filter(descripcion -> descripcion != null && !descripcion.isBlank())
                .findFirst()
                .orElse(null);
        return new Motivo(nombre != null ? "Festivo: " + nombre : "Festivo", false);
    }

    private static Motivo deAusencia(AbsenceRequest ausencia) {
        return new Motivo(
                ausencia.getTipo().getEtiqueta(),
                ausencia.getTipo() == AbsenceType.VACACIONES);
    }
}
