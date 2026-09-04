package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DepartmentRequest;
import com.nxtime.nxtime.dto.DepartmentResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.UserRepository;
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
 * Departamentos (Fase B).
 *
 * Los dos casos que de verdad importan aquí son el borrado con gente
 * dentro -- que tiene que dar un 409 explicando cuánta, no la violación
 * de clave ajena -- y el renombrado a sí mismo, que no es un conflicto.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserRepository userRepository;

    private DepartmentServiceImpl service;

    private Company empresa;
    private Company otraEmpresa;
    private User rrhh;

    @BeforeEach
    void setUp() {
        service = new DepartmentServiceImpl(departmentRepository, userRepository);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        otraEmpresa = Company.builder().id(2L).nombre("Otra Empresa").build();
        rrhh = User.builder().id(5L).email("rrhh@nxtime.test").nombre("Elena")
                .rol(Role.RRHH).empresa(empresa).build();
    }

    private Department departamento(long id, Company deQuien, String nombre) {
        return Department.builder().id(id).empresa(deQuien).nombre(nombre).build();
    }

    @Test
    @DisplayName("El listado dice cuánta gente tiene cada departamento")
    void listar_incluyeElRecuento() {
        when(departmentRepository.findByEmpresaOrderByNombreAsc(empresa))
                .thenReturn(List.of(departamento(1L, empresa, "Operaciones")));
        when(userRepository.countByDepartamento_Id(1L)).thenReturn(4L);

        List<DepartmentResponse> lista = service.listar(rrhh);

        assertThat(lista).singleElement()
                .extracting(DepartmentResponse::nombre, DepartmentResponse::empleados)
                .containsExactly("Operaciones", 4L);
    }

    @Test
    @DisplayName("Crear recorta los espacios del nombre")
    void crear_recortaElNombre() {
        when(departmentRepository.existsByEmpresaAndNombreIgnoreCase(empresa, "Operaciones")).thenReturn(false);
        when(departmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(userRepository.countByDepartamento_Id(anyLong())).thenReturn(0L);

        service.crear(new DepartmentRequest("  Operaciones  "), rrhh);

        ArgumentCaptor<Department> guardado = ArgumentCaptor.captor();
        verify(departmentRepository).save(guardado.capture());
        assertThat(guardado.getValue().getNombre()).isEqualTo("Operaciones");
        assertThat(guardado.getValue().getEmpresa()).isEqualTo(empresa);
    }

    @Test
    @DisplayName("Crear uno repetido da 409 con mensaje, no la violación del UNIQUE")
    void crear_repetido_lanza409() {
        when(departmentRepository.existsByEmpresaAndNombreIgnoreCase(empresa, "Operaciones")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(new DepartmentRequest("Operaciones"), rrhh))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe");

        verify(departmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Renombrar a lo que ya se llama (cambiando mayúsculas) NO es un conflicto")
    void renombrar_aSiMismo_noEsConflicto() {
        Department departamento = departamento(1L, empresa, "operaciones");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(departamento));
        lenient().when(userRepository.countByDepartamento_Id(1L)).thenReturn(0L);

        service.renombrar(1L, new DepartmentRequest("Operaciones"), rrhh);

        assertThat(departamento.getNombre()).isEqualTo("Operaciones");
        // Sin el "no es él mismo" del servicio, esto habría dado 409 por
        // chocar consigo mismo.
        verify(departmentRepository, never()).existsByEmpresaAndNombreIgnoreCase(any(), anyString());
    }

    @Test
    @DisplayName("Renombrar al nombre de OTRO departamento sí da 409")
    void renombrar_aOtroExistente_lanza409() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(departamento(1L, empresa, "Operaciones")));
        when(departmentRepository.existsByEmpresaAndNombreIgnoreCase(empresa, "Ventas")).thenReturn(true);

        assertThatThrownBy(() -> service.renombrar(1L, new DepartmentRequest("Ventas"), rrhh))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Borrar uno con gente dentro dice CUÁNTA hay, y no borra")
    void borrar_conEmpleados_lanza409ConElRecuento() {
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(departamento(1L, empresa, "Operaciones")));
        when(userRepository.countByDepartamento_Id(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.borrar(1L, rrhh))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("3");

        verify(departmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Borrar uno vacío sí lo borra")
    void borrar_vacio_loBorra() {
        Department departamento = departamento(1L, empresa, "Operaciones");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(departamento));
        when(userRepository.countByDepartamento_Id(1L)).thenReturn(0L);

        service.borrar(1L, rrhh);

        verify(departmentRepository).delete(departamento);
    }

    @Test
    @DisplayName("No se puede tocar un departamento de otra empresa")
    void otraEmpresa_lanzaTenantAccess() {
        when(departmentRepository.findById(9L)).thenReturn(Optional.of(departamento(9L, otraEmpresa, "Ajeno")));

        assertThatThrownBy(() -> service.borrar(9L, rrhh))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("otra empresa");
    }

    @Test
    @DisplayName("Un departamento que no existe da 404")
    void inexistente_lanzaResourceNotFound() {
        when(departmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renombrar(404L, new DepartmentRequest("X"), rrhh))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
