package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.config.CacheConfig;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.WorkStatus;
import com.nxtime.nxtime.dto.CompanyDashboardResponse;
import com.nxtime.nxtime.dto.EmployeeHoursDTO;
import com.nxtime.nxtime.dto.PersonalDashboardResponse;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.DashboardService;
import com.nxtime.nxtime.service.VacationBalanceService;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Métricas agregadas del panel (Fase 10).
 *
 * Todas las sumas se hacen con GROUP BY en la base de datos (ver
 * {@link TimeEntryRepository}), no cargando entidades y sumando en
 * Java. Este servicio se limita a calcular los límites temporales y a
 * dar forma al resultado.
 *
 * "Hoy", "esta semana" y "este mes" se calculan en la zona horaria
 * ESPAÑOLA y luego se convierten a Instant, no en UTC: a las 00:30 de
 * un martes en Madrid, en UTC todavía es lunes, y el contador de "hoy"
 * mostraría las horas del día anterior. La semana empieza en lunes
 * (ISO-8601), no en domingo.
 *
 * Los resultados se cachean un minuto (ver {@link CacheConfig}): son
 * consultas sobre todo el histórico y nadie necesita el contador al
 * segundo. La clave incluye el día para que la caché no sobreviva al
 * cambio de fecha.
 */
@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    private final TimeEntryRepository timeEntryRepository;
    private final AbsenceRequestRepository absenceRequestRepository;
    private final UserRepository userRepository;
    private final VacationBalanceService vacationBalanceService;

    public DashboardServiceImpl(
            TimeEntryRepository timeEntryRepository,
            AbsenceRequestRepository absenceRequestRepository,
            UserRepository userRepository,
            VacationBalanceService vacationBalanceService
    ) {
        this.timeEntryRepository = timeEntryRepository;
        this.absenceRequestRepository = absenceRequestRepository;
        this.userRepository = userRepository;
        this.vacationBalanceService = vacationBalanceService;
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'personal:' + #email + ':' + T(java.time.LocalDate).now()")
    public PersonalDashboardResponse getPersonalDashboard(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));

        LocalDate hoy = LocalDate.now(MADRID_ZONE);
        Instant inicioDeHoy = inicioDelDia(hoy);
        Instant inicioDeSemana = inicioDelDia(hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        Instant inicioDeMes = inicioDelDia(hoy.withDayOfMonth(1));
        // Fin exclusivo: mañana a las 00:00. Cubre la jornada de hoy
        // entera sin depender de la hora a la que se consulte.
        Instant finExclusivo = inicioDelDia(hoy.plusDays(1));

        long segundosHoy = timeEntryRepository.sumarSegundosTrabajados(user.getId(), inicioDeHoy, finExclusivo);
        long segundosSemana = timeEntryRepository.sumarSegundosTrabajados(user.getId(), inicioDeSemana, finExclusivo);
        long segundosMes = timeEntryRepository.sumarSegundosTrabajados(user.getId(), inicioDeMes, finExclusivo);

        long ausenciasPendientes =
                absenceRequestRepository.countByUsuarioAndEstado(user, AbsenceStatus.PENDIENTE);

        return new PersonalDashboardResponse(
                estadoActual(user),
                aMinutos(segundosHoy),
                aMinutos(segundosSemana),
                aMinutos(segundosMes),
                ausenciasPendientes,
                vacationBalanceService.getBalance(user, hoy.getYear()));
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'empresa:' + #managerEmail + ':' + T(java.time.LocalDate).now()")
    public CompanyDashboardResponse getCompanyDashboard(String managerEmail) {
        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Gestor no encontrado con email: " + managerEmail));

        Company empresa = manager.getEmpresa();
        LocalDate hoy = LocalDate.now(MADRID_ZONE);
        Instant inicioDeMes = inicioDelDia(hoy.withDayOfMonth(1));
        Instant finExclusivo = inicioDelDia(hoy.plusDays(1));

        long segundosMes =
                timeEntryRepository.sumarSegundosTrabajadosEmpresa(empresa.getId(), inicioDeMes, finExclusivo);

        List<EmployeeHoursDTO> horasPorEmpleado =
                timeEntryRepository.sumarSegundosPorEmpleado(empresa.getId(), inicioDeMes, finExclusivo).stream()
                        .map(fila -> new EmployeeHoursDTO(
                                fila.getUsuarioId(), fila.getNombre(), aMinutos(fila.getSegundos())))
                        .toList();

        int empleadosActivos = (int) userRepository.findByEmpresaAndRol(empresa, Role.EMPLEADO).stream()
                .filter(User::isActivo)
                .count();

        return new CompanyDashboardResponse(
                empleadosActivos,
                aMinutos(segundosMes),
                absenceRequestRepository.countByEmpresa_IdAndEstado(empresa.getId(), AbsenceStatus.PENDIENTE),
                timeEntryRepository.contarIncidenciasAbiertas(empresa),
                horasPorEmpleado);
    }

    /**
     * El estado no se guarda en ninguna columna: se deriva de la jornada
     * abierta, si la hay (ver {@link WorkStatus}).
     */
    private WorkStatus estadoActual(User user) {
        Optional<TimeEntry> activa = timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(user);
        if (activa.isEmpty()) {
            return WorkStatus.SIN_JORNADA;
        }
        return activa.get().isEnPausa() ? WorkStatus.EN_PAUSA : WorkStatus.TRABAJANDO;
    }

    private Instant inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(MADRID_ZONE).toInstant();
    }

    /** Truncado, no redondeado: 89 segundos son 1 minuto trabajado, no 2. */
    private long aMinutos(long segundos) {
        return segundos / 60;
    }
}
