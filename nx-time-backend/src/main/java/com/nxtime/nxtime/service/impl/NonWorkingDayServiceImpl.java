package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.service.HolidayCalendar;
import com.nxtime.nxtime.service.NonWorkingDayService;
import java.time.LocalDate;
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
            String nombre = holidayRepository.findByEmpresaYAnio(empresaId, dia.getYear()).stream()
                    .filter(festivo -> festivo.getFecha().equals(dia))
                    .map(festivo -> festivo.getDescripcion())
                    .filter(descripcion -> descripcion != null && !descripcion.isBlank())
                    .findFirst()
                    .orElse(null);
            return Optional.of(new Motivo(nombre != null ? "Festivo: " + nombre : "Festivo", false));
        }
        return absenceRequestRepository.findAprobadasDeUsuarioEnFecha(persona.getId(), dia).stream()
                .findFirst()
                .map(ausencia -> new Motivo(
                        ausencia.getTipo().getEtiqueta(),
                        ausencia.getTipo() == AbsenceType.VACACIONES));
    }
}
