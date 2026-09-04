package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios de la ficha de empleado (Fase A).
 *
 * Hasta ahora {@code usuarios.horas_semanales} y {@code saldo_vacaciones}
 * solo se leían: nadie llamaba jamás a
 * {@code vacationBalanceRepository.save()}. Aquí se comprueba que
 * escribir funciona, que un PATCH parcial no toca lo que no le
 * corresponde, y que el aislamiento entre empresas sigue en pie.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeProfileServiceImplTest {

    private static final int ANIO_ACTUAL = LocalDate.now(ZoneId.of("Europe/Madrid")).getYear();

    @Mock
    private UserRepository userRepository;

    @Mock
    private VacationBalanceRepository vacationBalanceRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    private EmployeeProfileServiceImpl service;

    private Company empresa;
    private Company otraEmpresa;
    private User rrhh;
    private User empleado;

    @BeforeEach
    void setUp() {
        service = new EmployeeProfileServiceImpl(
                userRepository, vacationBalanceRepository, departmentRepository);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        otraEmpresa = Company.builder().id(2L).nombre("Otra Empresa").build();
        rrhh = User.builder().id(5L).email("rrhh@nxtime.test").nombre("Elena")
                .rol(Role.RRHH).empresa(empresa).build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").nombre("Ana")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true)
                .horasSemanales(new BigDecimal("40.0")).build();
    }

    // ------------------------------------------------------------------
    // Listado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Quien no tiene fila de saldo hereda el mínimo legal, no un null")
    void getMyEmployees_sinFilaDeSaldo_usaElValorPorDefecto() {
        when(userRepository.findByEmpresaAndRol(empresa, Role.EMPLEADO)).thenReturn(List.of(empleado));
        when(vacationBalanceRepository.findByAnioAndUsuarioIn(anyInt(), anyList())).thenReturn(List.of());

        List<SimpleEmployeeDTO> empleados = service.getMyEmployees(rrhh);

        assertThat(empleados).hasSize(1);
        assertThat(empleados.get(0).diasVacaciones()).isEqualTo(VacationBalanceServiceImpl.DIAS_POR_DEFECTO);
        assertThat(empleados.get(0).horasSemanales()).isEqualByComparingTo("40.0");
    }

    @Test
    @DisplayName("Quien sí la tiene ve sus días, y todos los saldos se piden en UNA consulta")
    void getMyEmployees_conFilaDeSaldo_yUnaSolaConsulta() {
        User otro = User.builder().id(11L).email("otro@nxtime.test").nombre("Luis")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true)
                .horasSemanales(new BigDecimal("37.5")).build();
        when(userRepository.findByEmpresaAndRol(empresa, Role.EMPLEADO)).thenReturn(List.of(empleado, otro));
        when(vacationBalanceRepository.findByAnioAndUsuarioIn(anyInt(), anyList())).thenReturn(List.of(
                VacationBalance.builder().id(1L).usuario(otro).anio(ANIO_ACTUAL).diasTotales(25).build()));

        List<SimpleEmployeeDTO> empleados = service.getMyEmployees(rrhh);

        assertThat(empleados).extracting(SimpleEmployeeDTO::diasVacaciones)
                .containsExactly(VacationBalanceServiceImpl.DIAS_POR_DEFECTO, 25);
        // Nada de un SELECT por empleado: el panel de una empresa de
        // cincuenta personas dispararía cincuenta consultas.
        verify(vacationBalanceRepository, never()).findByUsuarioAndAnio(any(), anyInt());
    }

    @Test
    @DisplayName("Sin empleados no se consulta ningún saldo")
    void getMyEmployees_sinEmpleados_niSiquieraPreguntaPorLosSaldos() {
        when(userRepository.findByEmpresaAndRol(empresa, Role.EMPLEADO)).thenReturn(List.of());

        assertThat(service.getMyEmployees(rrhh)).isEmpty();
        verify(vacationBalanceRepository, never()).findByAnioAndUsuarioIn(anyInt(), anyList());
    }

    // ------------------------------------------------------------------
    // PATCH de la ficha
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Fijar los días de vacaciones CREA la fila si el empleado no tenía saldo")
    void updateProfile_sinFila_laCrea() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        when(vacationBalanceRepository.findByUsuarioAndAnio(empleado, ANIO_ACTUAL)).thenReturn(Optional.empty());
        lenient().when(vacationBalanceRepository.findByAnioAndUsuarioIn(anyInt(), anyList())).thenReturn(List.of());

        service.updateProfile(10L, new UpdateEmployeeProfileRequest(null, 25), rrhh);

        ArgumentCaptor<VacationBalance> guardado = ArgumentCaptor.captor();
        verify(vacationBalanceRepository).save(guardado.capture());
        assertThat(guardado.getValue().getId()).isZero();
        assertThat(guardado.getValue().getUsuario()).isEqualTo(empleado);
        assertThat(guardado.getValue().getAnio()).isEqualTo(ANIO_ACTUAL);
        assertThat(guardado.getValue().getDiasTotales()).isEqualTo(25);
    }

    @Test
    @DisplayName("Si ya tenía saldo se actualiza esa fila, no se duplica")
    void updateProfile_conFila_laActualiza() {
        VacationBalance existente = VacationBalance.builder()
                .id(7L).usuario(empleado).anio(ANIO_ACTUAL).diasTotales(22).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        when(vacationBalanceRepository.findByUsuarioAndAnio(empleado, ANIO_ACTUAL))
                .thenReturn(Optional.of(existente));
        lenient().when(vacationBalanceRepository.findByAnioAndUsuarioIn(anyInt(), anyList())).thenReturn(List.of());

        service.updateProfile(10L, new UpdateEmployeeProfileRequest(null, 30), rrhh);

        ArgumentCaptor<VacationBalance> guardado = ArgumentCaptor.captor();
        verify(vacationBalanceRepository).save(guardado.capture());
        assertThat(guardado.getValue().getId()).isEqualTo(7L);
        assertThat(guardado.getValue().getDiasTotales()).isEqualTo(30);
    }

    @Test
    @DisplayName("Cambiar solo la jornada NO toca el saldo de vacaciones")
    void updateProfile_soloHoras_noTocaElSaldo() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        lenient().when(vacationBalanceRepository.findByAnioAndUsuarioIn(anyInt(), anyList())).thenReturn(List.of());

        SimpleEmployeeDTO ficha = service.updateProfile(
                10L, new UpdateEmployeeProfileRequest(new BigDecimal("37.5"), null), rrhh);

        assertThat(empleado.getHorasSemanales()).isEqualByComparingTo("37.5");
        assertThat(ficha.horasSemanales()).isEqualByComparingTo("37.5");
        verify(userRepository).save(empleado);
        // Es un PATCH: lo que no viene, no se toca.
        verify(vacationBalanceRepository, never()).save(any());
        verify(vacationBalanceRepository, never()).findByUsuarioAndAnio(any(), anyInt());
    }

    @Test
    @DisplayName("Un cuerpo vacío no escribe nada en ninguna de las dos tablas")
    void updateProfile_todoNull_noEscribeNada() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        lenient().when(vacationBalanceRepository.findByAnioAndUsuarioIn(anyInt(), anyList())).thenReturn(List.of());

        service.updateProfile(10L, new UpdateEmployeeProfileRequest(null, null), rrhh);

        verify(userRepository, never()).save(any());
        verify(vacationBalanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("La ficha se guarda contra el año en curso de Madrid")
    void updateProfile_usaElAnioEnCursoDeMadrid() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        when(vacationBalanceRepository.findByUsuarioAndAnio(empleado, ANIO_ACTUAL)).thenReturn(Optional.empty());
        lenient().when(vacationBalanceRepository.findByAnioAndUsuarioIn(anyInt(), anyList())).thenReturn(List.of());

        service.updateProfile(10L, new UpdateEmployeeProfileRequest(null, 25), rrhh);

        // El saldo de vacaciones es un derecho ANUAL: sin año no existe
        // "los días de vacaciones" a secas.
        verify(vacationBalanceRepository).findByUsuarioAndAnio(empleado, ANIO_ACTUAL);
    }

    @Test
    @DisplayName("No se puede configurar a un empleado de otra empresa")
    void updateProfile_otraEmpresa_lanzaTenantAccess() {
        User ajeno = User.builder().id(99L).email("ajeno@otra.test").nombre("Ajeno")
                .rol(Role.EMPLEADO).empresa(otraEmpresa).build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.updateProfile(
                99L, new UpdateEmployeeProfileRequest(new BigDecimal("35.0"), null), rrhh))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("otra empresa");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Configurar a alguien que no existe da 404")
    void updateProfile_empleadoInexistente_lanzaResourceNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(
                404L, new UpdateEmployeeProfileRequest(null, 25), rrhh))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Perfil propio (Fase B)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El nombre completo une nombre y apellidos, y sin apellidos no deja un espacio suelto")
    void perfil_nombreCompleto() {
        lenient().when(vacationBalanceRepository.findByUsuarioAndAnio(any(), anyInt())).thenReturn(Optional.empty());

        empleado.setApellidos("Fernández Ruiz");
        assertThat(service.getMyProfile(empleado).nombreCompleto()).isEqualTo("Ana Fernández Ruiz");

        empleado.setApellidos(null);
        assertThat(service.getMyProfile(empleado).nombreCompleto()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Las iniciales son dos letras también cuando no hay apellidos")
    void perfil_iniciales() {
        lenient().when(vacationBalanceRepository.findByUsuarioAndAnio(any(), anyInt())).thenReturn(Optional.empty());

        empleado.setApellidos("Fernández");
        assertThat(service.getMyProfile(empleado).iniciales()).isEqualTo("AF");

        // Sin apellidos NO es una letra sola flotando en el círculo:
        // son las dos primeras del nombre.
        empleado.setApellidos(null);
        assertThat(service.getMyProfile(empleado).iniciales()).isEqualTo("AN");

        // Y un nombre de una sola letra no revienta el substring.
        empleado.setNombre("Z");
        assertThat(service.getMyProfile(empleado).iniciales()).isEqualTo("Z");
    }

    @Test
    @DisplayName("Actualizar el perfil cambia solo lo que viene, y recorta los espacios")
    void updateMyProfile_soloLoQueViene() {
        empleado.setPuesto("Analista");
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        lenient().when(vacationBalanceRepository.findByUsuarioAndAnio(any(), anyInt())).thenReturn(Optional.empty());

        service.updateMyProfile(
                new UpdateProfileRequest(null, "  Fernández  ", LocalDate.of(1995, 3, 14), null), empleado);

        assertThat(empleado.getApellidos()).isEqualTo("Fernández");
        assertThat(empleado.getFechaNacimiento()).isEqualTo(LocalDate.of(1995, 3, 14));
        // El puesto no venía en la petición: se queda como estaba.
        assertThat(empleado.getPuesto()).isEqualTo("Analista");
        assertThat(empleado.getNombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Una cadena vacía BORRA el dato, no guarda una cadena vacía")
    void updateMyProfile_cadenaVaciaBorra() {
        empleado.setPuesto("Analista");
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        lenient().when(vacationBalanceRepository.findByUsuarioAndAnio(any(), anyInt())).thenReturn(Optional.empty());

        service.updateMyProfile(new UpdateProfileRequest(null, null, null, "   "), empleado);

        // Null y no "": si no, la pantalla enseñaría un hueco en vez de
        // no enseñar el campo.
        assertThat(empleado.getPuesto()).isNull();
    }

    @Test
    @DisplayName("El nombre sí se puede cambiar, pero no vaciar")
    void updateMyProfile_nombreVacio_lanza400() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));

        assertThatThrownBy(() -> service.updateMyProfile(
                new UpdateProfileRequest("   ", null, null, null), empleado))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nombre");

        assertThat(empleado.getNombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Ver el perfil de alguien de otra empresa está prohibido")
    void getProfile_otraEmpresa_lanzaTenantAccess() {
        User ajeno = User.builder().id(99L).email("ajeno@otra.test").nombre("Ajeno")
                .rol(Role.EMPLEADO).empresa(otraEmpresa).build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.getProfile(99L, rrhh))
                .isInstanceOf(TenantAccessException.class);
    }

    // ------------------------------------------------------------------
    // Asignación de departamento
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Asignar un departamento de la misma empresa funciona")
    void assignDepartment_mismaEmpresa() {
        Department departamento = Department.builder().id(3L).empresa(empresa).nombre("Operaciones").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        when(departmentRepository.findById(3L)).thenReturn(Optional.of(departamento));
        lenient().when(vacationBalanceRepository.findByUsuarioAndAnio(any(), anyInt())).thenReturn(Optional.empty());

        ProfileResponse perfil = service.assignDepartment(10L, 3L, rrhh);

        assertThat(empleado.getDepartamento()).isEqualTo(departamento);
        assertThat(perfil.departamentoNombre()).isEqualTo("Operaciones");
    }

    @Test
    @DisplayName("Un departamento de OTRA empresa no se puede asignar aunque el empleado sea mío")
    void assignDepartment_departamentoDeOtraEmpresa_lanzaTenantAccess() {
        // El empleado es de mi empresa y el id del departamento existe:
        // sin esta comprobación, un dato de otro tenant acabaría dentro
        // de una ficha propia.
        Department ajeno = Department.builder().id(4L).empresa(otraEmpresa).nombre("Ajeno").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        when(departmentRepository.findById(4L)).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> service.assignDepartment(10L, 4L, rrhh))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("departamento");

        assertThat(empleado.getDepartamento()).isNull();
    }

    @Test
    @DisplayName("Con null se saca al empleado del departamento que tuviera")
    void assignDepartment_null_loSaca() {
        empleado.setDepartamento(Department.builder().id(3L).empresa(empresa).nombre("Operaciones").build());
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        lenient().when(vacationBalanceRepository.findByUsuarioAndAnio(any(), anyInt())).thenReturn(Optional.empty());

        ProfileResponse perfil = service.assignDepartment(10L, null, rrhh);

        assertThat(empleado.getDepartamento()).isNull();
        assertThat(perfil.departamentoId()).isNull();
        verify(departmentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("La respuesta trae ya los valores nuevos, para que el cliente no tenga que recargar")
    void updateProfile_devuelveLaFichaActualizada() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(empleado));
        when(vacationBalanceRepository.findByUsuarioAndAnio(empleado, ANIO_ACTUAL)).thenReturn(Optional.empty());
        when(vacationBalanceRepository.findByAnioAndUsuarioIn(anyInt(), anyList())).thenReturn(List.of(
                VacationBalance.builder().id(1L).usuario(empleado).anio(ANIO_ACTUAL).diasTotales(25).build()));

        SimpleEmployeeDTO ficha = service.updateProfile(
                10L, new UpdateEmployeeProfileRequest(new BigDecimal("37.5"), 25), rrhh);

        assertThat(ficha.id()).isEqualTo(10L);
        assertThat(ficha.email()).isEqualTo("empleado@nxtime.test");
        assertThat(ficha.horasSemanales()).isEqualByComparingTo("37.5");
        assertThat(ficha.diasVacaciones()).isEqualTo(25);
    }
}
