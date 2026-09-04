package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.VacationBalance;
import com.nxtime.nxtime.dto.ProfileResponse;
import com.nxtime.nxtime.dto.SimpleEmployeeDTO;
import com.nxtime.nxtime.dto.UpdateEmployeeProfileRequest;
import com.nxtime.nxtime.dto.UpdateProfileRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.repository.VacationBalanceRepository;
import com.nxtime.nxtime.service.EmployeeProfileService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeProfileServiceImpl implements EmployeeProfileService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeProfileServiceImpl.class);

    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    /** Para que las iniciales pasen a mayúscula con reglas de aquí. */
    private static final Locale SPAIN = Locale.forLanguageTag("es-ES");

    private final UserRepository userRepository;
    private final VacationBalanceRepository vacationBalanceRepository;
    private final DepartmentRepository departmentRepository;

    public EmployeeProfileServiceImpl(UserRepository userRepository,
                                      VacationBalanceRepository vacationBalanceRepository,
                                      DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.departmentRepository = departmentRepository;
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

    // ------------------------------------------------------------------
    // Perfil (Fase B)
    // ------------------------------------------------------------------

    @Override
    public ProfileResponse getMyProfile(User actor) {
        return toProfile(actor);
    }

    @Override
    @Transactional
    public ProfileResponse updateMyProfile(UpdateProfileRequest request, User actor) {
        User usuario = userRepository.findById(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        // Cadena vacía SÍ es un cambio -- es cómo se borra un puesto que
        // ya no aplica --, así que se normaliza a null en vez de
        // guardar "" y que la pantalla enseñe un hueco.
        if (request.nombre() != null) {
            String nombre = request.nombre().trim();
            if (nombre.isEmpty()) {
                throw new BusinessException("El nombre no puede quedarse vacío.", HttpStatus.BAD_REQUEST);
            }
            usuario.setNombre(nombre);
        }
        if (request.apellidos() != null) {
            usuario.setApellidos(vacioComoNulo(request.apellidos()));
        }
        if (request.fechaNacimiento() != null) {
            usuario.setFechaNacimiento(request.fechaNacimiento());
        }
        if (request.puesto() != null) {
            usuario.setPuesto(vacioComoNulo(request.puesto()));
        }

        userRepository.save(usuario);
        log.info("{} ha actualizado su perfil", usuario.getEmail());
        return toProfile(usuario);
    }

    @Override
    public ProfileResponse getProfile(long usuarioId, User actor) {
        return toProfile(deLaMismaEmpresa(usuarioId, actor, "No puedes ver el perfil de otra empresa."));
    }

    @Override
    @Transactional
    public ProfileResponse assignDepartment(long usuarioId, Long departamentoId, User actor) {
        User usuario = deLaMismaEmpresa(usuarioId, actor, "No puedes gestionar empleados de otra empresa.");

        if (departamentoId == null) {
            usuario.setDepartamento(null);
        } else {
            Department departamento = departmentRepository.findById(departamentoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Departamento no encontrado."));
            // Un departamento de otra empresa sería un dato de otro
            // tenant colado en una ficha propia.
            if (departamento.getEmpresa().getId() != actor.getEmpresa().getId()) {
                throw new TenantAccessException("Ese departamento es de otra empresa.");
            }
            usuario.setDepartamento(departamento);
        }

        userRepository.save(usuario);
        log.info("{} ha puesto a {} en el departamento {}",
                actor.getEmail(), usuario.getEmail(), departamentoId);
        return toProfile(usuario);
    }

    private User deLaMismaEmpresa(long usuarioId, User actor, String mensaje) {
        User usuario = userRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
        if (usuario.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException(mensaje);
        }
        return usuario;
    }

    private static String vacioComoNulo(String valor) {
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private ProfileResponse toProfile(User usuario) {
        int anio = anioActual();
        int dias = vacationBalanceRepository.findByUsuarioAndAnio(usuario, anio)
                .map(VacationBalance::getDiasTotales)
                .orElse(VacationBalanceServiceImpl.DIAS_POR_DEFECTO);

        return new ProfileResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getApellidos(),
                nombreCompleto(usuario),
                iniciales(usuario),
                usuario.getFechaNacimiento(),
                usuario.getPuesto(),
                usuario.getDepartamento() != null ? usuario.getDepartamento().getId() : null,
                usuario.getDepartamento() != null ? usuario.getDepartamento().getNombre() : null,
                usuario.getRol(),
                usuario.isActivo(),
                usuario.getHorasSemanales(),
                dias);
    }

    private static String nombreCompleto(User usuario) {
        String apellidos = usuario.getApellidos();
        return apellidos == null || apellidos.isBlank()
                ? usuario.getNombre()
                : usuario.getNombre() + " " + apellidos;
    }

    /**
     * Las dos letras del avatar cuando no hay foto.
     *
     * Se calculan aquí y no en cada cliente porque la regla tiene un
     * caso raro que se implementaría distinto en cada sitio: con
     * apellidos son inicial de nombre + inicial de apellido, pero sin
     * ellos son las DOS primeras letras del nombre, no una sola letra
     * flotando en un círculo.
     */
    private static String iniciales(User usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellidos = usuario.getApellidos() == null ? "" : usuario.getApellidos().trim();

        if (nombre.isEmpty()) {
            return "?";
        }
        if (!apellidos.isEmpty()) {
            return (nombre.charAt(0) + "" + apellidos.charAt(0)).toUpperCase(SPAIN);
        }
        return nombre.substring(0, Math.min(2, nombre.length())).toUpperCase(SPAIN);
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
                        empleado.getId(), VacationBalanceServiceImpl.DIAS_POR_DEFECTO),
                empleado.getDepartamento() != null ? empleado.getDepartamento().getId() : null,
                empleado.getDepartamento() != null ? empleado.getDepartamento().getNombre() : null);
    }
}
