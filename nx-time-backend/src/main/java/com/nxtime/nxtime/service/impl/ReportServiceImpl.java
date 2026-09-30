package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.MonthlySignatureStatus;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.report.ReportRow;
import com.nxtime.nxtime.repository.MonthlySignatureRepository;
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

    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;
    private final MonthlySignatureRepository signatureRepository;

    public ReportServiceImpl(
            TimeEntryRepository timeEntryRepository,
            UserRepository userRepository,
            MonthlySignatureRepository signatureRepository) {
        this.timeEntryRepository = timeEntryRepository;
        this.userRepository = userRepository;
        this.signatureRepository = signatureRepository;
    }

    @Override
    public MonthlyReport informeDeEmpresa(String solicitanteEmail, YearMonth mes) {
        User solicitante = getUsuario(solicitanteEmail);
        ZoneId zona = solicitante.zona();

        List<TimeEntry> fichajes = timeEntryRepository.findParaInforme(
                solicitante.getEmpresa(), inicioDelMes(mes, zona), inicioDelMes(mes.plusMonths(1), zona));

        return new MonthlyReport(
                solicitante.getEmpresa().getNombre(),
                "Todos los empleados",
                mes,
                fichajes.stream().map(fichaje -> aFila(fichaje, zona)).toList(),
                null,
                zona);
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

        ZoneId zona = solicitante.zona();
        List<TimeEntry> fichajes = timeEntryRepository.findParaInformeDeEmpleado(
                empleado, inicioDelMes(mes, zona), inicioDelMes(mes.plusMonths(1), zona));

        // La firma vigente del mes, si la hay (Fase B3). Solo la vigente: una
        // invalidada ya no dice nada del registro de hoy.
        MonthlyReport.FirmaDelInforme firma = signatureRepository
                .findByUsuario_IdAndAnioAndMesAndEstado(
                        empleado.getId(), mes.getYear(), mes.getMonthValue(), MonthlySignatureStatus.VIGENTE)
                .map(vigente -> new MonthlyReport.FirmaDelInforme(
                        empleado.getNombre(),
                        vigente.getFirmadaEn(),
                        vigente.getHash(),
                        vigente.getVisadaPor() == null ? null : vigente.getVisadaPor().getNombre(),
                        vigente.getVisadaEn()))
                .orElse(null);

        return new MonthlyReport(
                solicitante.getEmpresa().getNombre(),
                empleado.getNombre(),
                mes,
                fichajes.stream().map(fichaje -> aFila(fichaje, zona)).toList(),
                firma,
                zona);
    }

    private User getUsuario(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }

    /**
     * Convierte un fichaje a línea de informe, proyectando los instantes
     * a la hora de la empresa: el informe lo lee una persona allí, no un
     * sistema en UTC.
     */
    private ReportRow aFila(TimeEntry fichaje, ZoneId zona) {
        ZonedDateTime entrada = fichaje.getHoraEntrada().atZone(zona);
        ZonedDateTime salida = fichaje.getHoraSalida().atZone(zona);

        long segundosBrutos = Duration.between(fichaje.getHoraEntrada(), fichaje.getHoraSalida()).getSeconds();
        long segundosNetos = segundosBrutos - fichaje.getSegundosPausaAcumulados();

        return new ReportRow(
                fichaje.getUsuario().getNombre(),
                entrada.toLocalDate(),
                entrada.toLocalTime().withSecond(0).withNano(0),
                salida.toLocalTime().withSecond(0).withNano(0),
                fichaje.getSegundosPausaAcumulados() / 60,
                // En SEGUNDOS, sin truncar: el total del informe se
                // agrega a partir de aquí (ver ReportRow).
                segundosNetos,
                fichaje.isJornadaIncompleta());
    }

    private static Instant inicioDelMes(YearMonth mes, ZoneId zona) {
        return mes.atDay(1).atStartOfDay(zona).toInstant();
    }
}
