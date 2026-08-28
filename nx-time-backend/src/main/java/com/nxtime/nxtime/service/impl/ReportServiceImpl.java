package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.report.ReportRow;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.ReportService;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prepara los datos de los informes mensuales (Fase 10).
 *
 * Los dos formatos (Excel y PDF) salen del MISMO {@link MonthlyReport}:
 * los generadores solo dan formato, no consultan ni calculan nada. Así
 * el Excel y el PDF de un mismo mes no pueden discrepar, que en un
 * documento con valor ante una inspección sería el peor defecto posible.
 */
@Service
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;

    public ReportServiceImpl(TimeEntryRepository timeEntryRepository, UserRepository userRepository) {
        this.timeEntryRepository = timeEntryRepository;
        this.userRepository = userRepository;
    }

    @Override
    public MonthlyReport informeDeEmpresa(String solicitanteEmail, YearMonth mes) {
        User solicitante = getUsuario(solicitanteEmail);

        List<TimeEntry> fichajes = timeEntryRepository.findParaInforme(
                solicitante.getEmpresa(), inicioDelMes(mes), inicioDelMesSiguiente(mes));

        return new MonthlyReport(
                solicitante.getEmpresa().getNombre(),
                "Todos los empleados",
                mes,
                fichajes.stream().map(this::aFila).toList());
    }

    @Override
    public MonthlyReport informeDeEmpleado(String solicitanteEmail, long empleadoId, YearMonth mes) {
        User solicitante = getUsuario(solicitanteEmail);
        User empleado = userRepository.findById(empleadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado."));

        // Mismo control de empresa que en el resto de operaciones entre
        // usuarios (ver AuthServiceImpl.setEmployeeActive): tener la
        // authority no da acceso a los datos de OTRA empresa.
        if (empleado.getEmpresa().getId() != solicitante.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes generar informes de empleados de otra empresa.");
        }

        List<TimeEntry> fichajes = timeEntryRepository.findParaInformeDeEmpleado(
                empleado, inicioDelMes(mes), inicioDelMesSiguiente(mes));

        return new MonthlyReport(
                solicitante.getEmpresa().getNombre(),
                empleado.getNombre(),
                mes,
                fichajes.stream().map(this::aFila).toList());
    }

    private User getUsuario(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    /**
     * Convierte un fichaje a línea de informe, proyectando los instantes
     * a hora española: el informe lo lee una persona en España, no un
     * sistema en UTC.
     */
    private ReportRow aFila(TimeEntry fichaje) {
        ZonedDateTime entrada = fichaje.getHoraEntrada().atZone(MADRID_ZONE);
        ZonedDateTime salida = fichaje.getHoraSalida().atZone(MADRID_ZONE);

        long segundosBrutos = Duration.between(fichaje.getHoraEntrada(), fichaje.getHoraSalida()).getSeconds();
        long segundosNetos = segundosBrutos - fichaje.getSegundosPausaAcumulados();

        return new ReportRow(
                fichaje.getUsuario().getNombre(),
                entrada.toLocalDate(),
                entrada.toLocalTime().withSecond(0).withNano(0),
                salida.toLocalTime().withSecond(0).withNano(0),
                fichaje.getSegundosPausaAcumulados() / 60,
                segundosNetos / 60,
                fichaje.isJornadaIncompleta());
    }

    private Instant inicioDelMes(YearMonth mes) {
        return mes.atDay(1).atStartOfDay(MADRID_ZONE).toInstant();
    }

    private Instant inicioDelMesSiguiente(YearMonth mes) {
        return mes.plusMonths(1).atDay(1).atStartOfDay(MADRID_ZONE).toInstant();
    }
}
