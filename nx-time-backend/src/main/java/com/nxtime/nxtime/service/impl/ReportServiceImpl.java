package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.DatoConsultado;
import com.nxtime.nxtime.domain.MonthlySignatureStatus;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.report.ReportRow;
import com.nxtime.nxtime.domain.OrigenDelFichaje;
import com.nxtime.nxtime.repository.FichajeRecibidoRepository;
import com.nxtime.nxtime.repository.MonthlySignatureRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.ReportService;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final RegistroDeAccesos accesos;
    private final FichajeRecibidoRepository recibidos;

    /** Cuántos ids van en cada consulta: un informe anual de una empresa grande pasaría del tope de parámetros de PostgreSQL. */
    static final int IDS_POR_CONSULTA = 1000;

    public ReportServiceImpl(
            TimeEntryRepository timeEntryRepository,
            UserRepository userRepository,
            MonthlySignatureRepository signatureRepository,
            RegistroDeAccesos accesos,
            FichajeRecibidoRepository recibidos) {
        this.timeEntryRepository = timeEntryRepository;
        this.userRepository = userRepository;
        this.signatureRepository = signatureRepository;
        this.accesos = accesos;
        this.recibidos = recibidos;
    }

    @Override
    public MonthlyReport informeDeEmpresa(String solicitanteEmail, YearMonth mes) {
        User solicitante = getUsuario(solicitanteEmail);
        ZoneId zona = solicitante.zona();

        List<TimeEntry> fichajes = timeEntryRepository.findParaInforme(
                solicitante.getEmpresa(), inicioDelMes(mes, zona), inicioDelMes(mes.plusMonths(1), zona));
        accesos.anotar(solicitante, DatoConsultado.INFORME_EMPRESA, null, "Excel de horas de " + mes);

        Set<Long> sinConexion = conFichajeSinConexion(fichajes);
        return new MonthlyReport(
                solicitante.getEmpresa().getNombre(),
                "Todos los empleados",
                mes,
                fichajes.stream().map(fichaje -> aFila(fichaje, zona, sinConexion)).toList(),
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
        accesos.anotar(solicitante, DatoConsultado.INFORME_MENSUAL, empleado.getId(), "Registro de " + mes);
        return construir(solicitante, empleado, mes);
    }

    /**
     * El registro mensual de cada persona que fichó ese mes, para la
     * exportación de la Inspección (plan J, fase J7).
     *
     * <b>No anota cada uno en el registro de accesos</b>: lo hace una vez quien
     * los pide ({@code ExportacionParaLaInspeccion}), que es el acceso de
     * verdad. Si no, una exportación de un año dejaría cientos de filas.
     */
    @Override
    public List<MonthlyReport> informesDeLaPlantilla(String solicitanteEmail, YearMonth mes) {
        User solicitante = getUsuario(solicitanteEmail);
        ZoneId zona = solicitante.zona();
        Map<Long, User> personas = new LinkedHashMap<>();
        for (TimeEntry fichaje : timeEntryRepository.findParaInforme(
                solicitante.getEmpresa(), inicioDelMes(mes, zona), inicioDelMes(mes.plusMonths(1), zona))) {
            personas.putIfAbsent(fichaje.getUsuario().getId(), fichaje.getUsuario());
        }
        return personas.values().stream().map(persona -> construir(solicitante, persona, mes)).toList();
    }

    private MonthlyReport construir(User solicitante, User empleado, YearMonth mes) {
        ZoneId zona = solicitante.zona();
        List<TimeEntry> fichajes = timeEntryRepository.findParaInformeDeEmpleado(
                empleado, inicioDelMes(mes, zona), inicioDelMes(mes.plusMonths(1), zona));

        // La firma vigente del mes, si la hay (Fase B3). Solo la vigente: una
        // invalidada ya no dice nada del registro de hoy.
        MonthlyReport.FirmaDelInforme firma = signatureRepository
                .findByUsuario_IdAndAnioAndMesAndEstado(
                        empleado.getId(), mes.getYear(), mes.getMonthValue(), MonthlySignatureStatus.VIGENTE)
                .map(vigente -> new MonthlyReport.FirmaDelInforme(
                        empleado.nombreCompleto(),
                        vigente.getFirmadaEn(),
                        vigente.getHash(),
                        vigente.getVisadaPor() == null ? null : vigente.getVisadaPor().nombreCompleto(),
                        vigente.getVisadaEn()))
                .orElse(null);

        Set<Long> sinConexion = conFichajeSinConexion(fichajes);
        return new MonthlyReport(
                solicitante.getEmpresa().getNombre(),
                empleado.nombreCompleto(),
                mes,
                fichajes.stream().map(fichaje -> aFila(fichaje, zona, sinConexion)).toList(),
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
    /**
     * Las jornadas con algún fichaje sin conexión (ADR 054): las que se
     * abrieron así, y las que recibieron alguno después. Una corrección copia
     * el origen, así que la jornada nueva sigue marcada si se abrió sin conexión.
     */
    private Set<Long> conFichajeSinConexion(List<TimeEntry> fichajes) {
        Set<Long> marcadas = new HashSet<>();
        List<Long> ids = new java.util.ArrayList<>(fichajes.size());
        for (TimeEntry fichaje : fichajes) {
            if (fichaje.getOrigen() == OrigenDelFichaje.APP_SIN_CONEXION) {
                marcadas.add(fichaje.getId());
            } else {
                ids.add(fichaje.getId());
            }
        }
        for (int desde = 0; desde < ids.size(); desde += IDS_POR_CONSULTA) {
            marcadas.addAll(recibidos.conFichajeSinConexion(
                    ids.subList(desde, Math.min(ids.size(), desde + IDS_POR_CONSULTA))));
        }
        return marcadas;
    }

    private ReportRow aFila(TimeEntry fichaje, ZoneId zona, Set<Long> sinConexion) {
        ZonedDateTime entrada = fichaje.getHoraEntrada().atZone(zona);
        ZonedDateTime salida = fichaje.getHoraSalida().atZone(zona);

        long segundosBrutos = Duration.between(fichaje.getHoraEntrada(), fichaje.getHoraSalida()).getSeconds();
        long segundosNetos = segundosBrutos - fichaje.getSegundosPausaAcumulados();

        return new ReportRow(
                fichaje.getUsuario().nombreCompleto(),
                entrada.toLocalDate(),
                entrada.toLocalTime().withSecond(0).withNano(0),
                salida.toLocalTime().withSecond(0).withNano(0),
                fichaje.getSegundosPausaAcumulados(),
                // En SEGUNDOS, sin truncar: el total del informe se
                // agrega a partir de aquí (ver ReportRow).
                segundosNetos,
                fichaje.isJornadaIncompleta(),
                fichaje.getUsuario().getCodigoEmpleado(),
                fichaje.getModalidad(),
                sinConexion.contains(fichaje.getId()),
                fichaje.isRevisarPorLlegarTarde());
    }

    private static Instant inicioDelMes(YearMonth mes, ZoneId zona) {
        return mes.atDay(1).atStartOfDay(zona).toInstant();
    }
}
