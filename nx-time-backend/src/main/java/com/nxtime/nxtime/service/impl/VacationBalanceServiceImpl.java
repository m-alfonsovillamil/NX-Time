package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.VacationBalance;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.VacationBalanceRepository;
import com.nxtime.nxtime.service.VacationBalanceService;
import com.nxtime.nxtime.service.WorkingDayService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saldo de vacaciones (Fase 9).
 *
 * Los días CONSUMIDOS no se guardan en ninguna columna: se calculan
 * sumando los días hábiles de las peticiones de vacaciones APROBADAS
 * del año (ver {@link VacationBalance} para el porqué). Los días
 * TOTALES sí se guardan, porque son un dato de negocio que alguien
 * decide (convenio, antigüedad...), no algo derivable.
 */
@Service
@Transactional(readOnly = true)
public class VacationBalanceServiceImpl implements VacationBalanceService {

    /**
     * Derecho anual por defecto cuando el empleado todavía no tiene una
     * fila propia en "saldo_vacaciones": 22 días hábiles es el mínimo
     * legal en España (art. 38 ET, "treinta días naturales"). Así el
     * saldo funciona desde el primer día sin tener que dar de alta a
     * mano a cada empleado; quien quiera otro valor, crea la fila.
     */
    public static final int DIAS_POR_DEFECTO = 22;

    private final VacationBalanceRepository vacationBalanceRepository;
    private final AbsenceRequestRepository absenceRequestRepository;
    private final WorkingDayService workingDayService;

    public VacationBalanceServiceImpl(
            VacationBalanceRepository vacationBalanceRepository,
            AbsenceRequestRepository absenceRequestRepository,
            WorkingDayService workingDayService
    ) {
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.absenceRequestRepository = absenceRequestRepository;
        this.workingDayService = workingDayService;
    }

    @Override
    public VacationBalanceResponse getBalance(User usuario, int anio) {
        int diasTotales = vacationBalanceRepository.findByUsuarioAndAnio(usuario, anio)
                .map(VacationBalance::getDiasTotales)
                .orElse(DIAS_POR_DEFECTO);

        int diasConsumidos = contarDiasConsumidos(usuario, anio);
        return new VacationBalanceResponse(anio, diasTotales, diasConsumidos, diasTotales - diasConsumidos);
    }

    private int contarDiasConsumidos(User usuario, int anio) {
        LocalDate inicioDeAnio = LocalDate.of(anio, 1, 1);
        LocalDate finDeAnio = LocalDate.of(anio, 12, 31);

        List<AbsenceRequest> aprobadas =
                absenceRequestRepository.findVacacionesAprobadasDelAnio(usuario, inicioDeAnio, finDeAnio);

        int total = 0;
        for (AbsenceRequest peticion : aprobadas) {
            // Una petición a caballo entre dos años (del 28/12 al 3/1)
            // solo consume del año que se está calculando los días que
            // caen dentro de él -- de ahí el recorte del rango.
            LocalDate desde = peticion.getFechaInicio().isBefore(inicioDeAnio) ? inicioDeAnio : peticion.getFechaInicio();
            LocalDate hasta = peticion.getFechaFin().isAfter(finDeAnio) ? finDeAnio : peticion.getFechaFin();
            total += workingDayService.contarDiasHabiles(usuario.getEmpresa(), desde, hasta);
        }
        return total;
    }
}
