package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.VacationBalance;
import com.nxtime.nxtime.dto.SimpleEmployeeDTO;
import com.nxtime.nxtime.dto.UpdateEmployeeProfileRequest;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.repository.VacationBalanceRepository;
import com.nxtime.nxtime.service.EmployeeProfileService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeProfileServiceImpl implements EmployeeProfileService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeProfileServiceImpl.class);

    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    private final UserRepository userRepository;
    private final VacationBalanceRepository vacationBalanceRepository;

    public EmployeeProfileServiceImpl(UserRepository userRepository,
                                      VacationBalanceRepository vacationBalanceRepository) {
        this.userRepository = userRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
    }

    @Override
    public List<SimpleEmployeeDTO> getMyEmployees(User manager) {
        List<User> empleados = userRepository.findByEmpresaAndRol(manager.getEmpresa(), Role.EMPLEADO);
        if (empleados.isEmpty()) {
            return List.of();
        }

        // Una consulta para todos los saldos, no una por empleado: en
        // una empresa de cincuenta personas la alternativa son cincuenta
        // SELECT cada vez que se abre el panel.
        Map<Long, Integer> saldosPorUsuario = saldosDelAnio(empleados, anioActual());

        return empleados.stream()
                .map(empleado -> toDto(empleado, saldosPorUsuario))
                .toList();
    }

    @Override
    @Transactional
    public SimpleEmployeeDTO updateProfile(long employeeId, UpdateEmployeeProfileRequest request, User actor) {
        User empleado = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        // No hay filtro multi-tenant automático (ADR 006): cada endpoint
        // que recibe un id ajeno compara la empresa a mano.
        if (empleado.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes configurar empleados de otra empresa.");
        }

        int anio = anioActual();

        if (request.horasSemanales() != null) {
            empleado.setHorasSemanales(request.horasSemanales());
            userRepository.save(empleado);
        }

        if (request.diasVacaciones() != null) {
            // Upsert. Hasta la Fase A nadie escribía nunca en
            // "saldo_vacaciones", así que para casi todo el mundo este
            // es el INSERT de la primera fila; a partir de ahí, UPDATE.
            VacationBalance saldo = vacationBalanceRepository.findByUsuarioAndAnio(empleado, anio)
                    .orElseGet(() -> VacationBalance.builder()
                            .usuario(empleado)
                            .anio(anio)
                            .build());
            saldo.setDiasTotales(request.diasVacaciones());
            vacationBalanceRepository.save(saldo);
        }

        log.info("{} ha configurado la ficha de {} (horas={}, dias={}, anio={})",
                actor.getEmail(), empleado.getEmail(),
                request.horasSemanales(), request.diasVacaciones(), anio);

        return toDto(empleado, saldosDelAnio(List.of(empleado), anio));
    }

    /** El año en curso en Madrid, que es la zona en la que opera la aplicación. */
    private int anioActual() {
        return LocalDate.now(MADRID_ZONE).getYear();
    }

    private Map<Long, Integer> saldosDelAnio(List<User> empleados, int anio) {
        Map<Long, Integer> saldos = new HashMap<>();
        vacationBalanceRepository.findByAnioAndUsuarioIn(anio, empleados).forEach(
                saldo -> saldos.put(saldo.getUsuario().getId(), saldo.getDiasTotales()));
        return saldos;
    }

    private SimpleEmployeeDTO toDto(User empleado, Map<Long, Integer> saldosPorUsuario) {
        return new SimpleEmployeeDTO(
                empleado.getId(),
                empleado.getNombre(),
                empleado.getEmail(),
                empleado.isActivo(),
                empleado.getHorasSemanales(),
                // Días EFECTIVOS: quien no tiene fila hereda el mínimo
                // legal, igual que hace VacationBalanceService al leer.
                saldosPorUsuario.getOrDefault(
                        empleado.getId(), VacationBalanceServiceImpl.DIAS_POR_DEFECTO));
    }
}
