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
 *
 * Y lo PEDIDO pero sin resolver también descuenta. Antes no: se podían
 * pedir dos tramos que por separado cabían en el saldo y juntos no, y un
 * gestor aprobaba los dos sin que nada avisara -- el saldo se quedaba en
 * negativo. Quien tiene quince días pendientes no tiene esos quince días
 * libres para pedir otra cosa, aunque todavía no se los hayan concedido.
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
        return getBalance(usuario, anio, LocalDate.now(usuario.zona()));
    }

    @Override
    public VacationBalanceResponse getBalance(User usuario, int anio, LocalDate referencia) {
        int derecho = derecho(usuario, anio);
        int diasConsumidos = contarDiasAprobados(usuario, anio);
        int diasPendientes = contarDiasPendientes(usuario, anio);
        Arrastre arrastre = arrastre(usuario, anio, referencia);
        int diasTotales = derecho + arrastre.dias();
        return new VacationBalanceResponse(anio, diasTotales, diasConsumidos, diasPendientes,
                diasTotales - diasConsumidos - diasPendientes, arrastre.dias(), arrastre.caducaEl());
    }

    private int derecho(User usuario, int anio) {
        return vacationBalanceRepository.findByUsuarioAndAnio(usuario, anio)
                .map(VacationBalance::getDiasTotales)
                .orElse(DIAS_POR_DEFECTO);
    }

    /** Lo arrastrado del año anterior que cuenta, y hasta cuándo (null si no hay o ya caducó). */
    record Arrastre(int dias, LocalDate caducaEl) {
        static final Arrastre NINGUNO = new Arrastre(0, null);
    }

    /**
     * Lo que pasa del año anterior (ADR 060), calculado al leer.
     *
     * <ul>
     *   <li>Lo que sobró: el derecho del año anterior menos lo aprobado en él.
     *       Sin encadenar: lo arrastrado a aquel año no vuelve a pasar a este.</li>
     *   <li>Hasta la fecha tope cuenta entero. Pasada, solo lo que se gastó en
     *       vacaciones que EMPEZABAN antes del tope (aprobadas o pedidas): el
     *       resto caduca. La misma regla al pedir y al aprobar, con la fecha
     *       de inicio de la ausencia: unas vacaciones de junio no pueden gastar
     *       días que caducan en marzo.</li>
     * </ul>
     */
    private Arrastre arrastre(User usuario, int anio, LocalDate referencia) {
        com.nxtime.nxtime.domain.Company empresa = usuario.getEmpresa();
        if (empresa == null || !empresa.isArrastreVacaciones()) {
            return Arrastre.NINGUNO;
        }
        int sobrante = Math.max(0, derecho(usuario, anio - 1) - contarDiasAprobados(usuario, anio - 1));
        if (sobrante == 0) {
            return Arrastre.NINGUNO;
        }
        LocalDate tope = empresa.topeDelArrastre(anio);
        if (!referencia.isAfter(tope)) {
            return new Arrastre(sobrante, tope);
        }
        LocalDate inicioDeAnio = LocalDate.of(anio, 1, 1);
        LocalDate finDeAnio = LocalDate.of(anio, 12, 31);
        List<AbsenceRequest> antesDelTope = new java.util.ArrayList<>();
        absenceRequestRepository.findVacacionesAprobadasDelAnio(usuario, inicioDeAnio, finDeAnio).stream()
                .filter(p -> !p.getFechaInicio().isAfter(tope)).forEach(antesDelTope::add);
        absenceRequestRepository.findVacacionesPendientesDelAnio(usuario, inicioDeAnio, finDeAnio).stream()
                .filter(p -> !p.getFechaInicio().isAfter(tope)).forEach(antesDelTope::add);
        int usado = contarDiasHabilesDe(antesDelTope, usuario, inicioDeAnio, finDeAnio);
        return new Arrastre(Math.min(sobrante, usado), null);
    }

    @Override
    public int contarDiasAprobados(User usuario, int anio) {
        LocalDate inicioDeAnio = LocalDate.of(anio, 1, 1);
        LocalDate finDeAnio = LocalDate.of(anio, 12, 31);
        return contarDiasHabilesDe(
                absenceRequestRepository.findVacacionesAprobadasDelAnio(usuario, inicioDeAnio, finDeAnio),
                usuario, inicioDeAnio, finDeAnio);
    }

    private int contarDiasPendientes(User usuario, int anio) {
        LocalDate inicioDeAnio = LocalDate.of(anio, 1, 1);
        LocalDate finDeAnio = LocalDate.of(anio, 12, 31);
        return contarDiasHabilesDe(
                absenceRequestRepository.findVacacionesPendientesDelAnio(usuario, inicioDeAnio, finDeAnio),
                usuario, inicioDeAnio, finDeAnio);
    }

    private int contarDiasHabilesDe(
            List<AbsenceRequest> peticiones, User usuario, LocalDate inicioDeAnio, LocalDate finDeAnio) {
        int total = 0;
        for (AbsenceRequest peticion : peticiones) {
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
