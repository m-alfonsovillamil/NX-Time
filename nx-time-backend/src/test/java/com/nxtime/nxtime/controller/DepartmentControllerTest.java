package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.DepartmentResponse;
import com.nxtime.nxtime.service.DepartmentService;
import com.nxtime.nxtime.service.EmployeeProfileService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de DepartmentController (Fase B).
 *
 * Lo que se fija aquí es que **leer y gestionar son authorities
 * distintas**: un GESTOR necesita el listado para mirar un perfil, pero
 * organizar la empresa es de RRHH.
 */
@NxTimeWebMvcTest(DepartmentController.class)
@Import(WebMvcTestSecurityConfig.class)
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private EmployeeProfileService employeeProfileService;

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("GET /departamentos con 'empleado:leer' (GESTOR) devuelve 200 con el recuento")
    void listar_comoGestor_devuelve200() throws Exception {
        when(departmentService.listar(any()))
                .thenReturn(List.of(new DepartmentResponse(1L, "Operaciones", 4L)));

        mockMvc.perform(get("/api/v1/departamentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Operaciones"))
                .andExpect(jsonPath("$[0].empleados").value(4));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /departamentos como EMPLEADO devuelve 403")
    void listar_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/departamentos")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("POST /departamentos como GESTOR devuelve 403: leerlos sí, crearlos no")
    void crear_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/departamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ventas\"}"))
                .andExpect(status().isForbidden());

        verify(departmentService, never()).crear(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("POST /departamentos con 'departamento:gestionar' devuelve 200")
    void crear_comoRRHH_devuelve200() throws Exception {
        when(departmentService.crear(any(), any()))
                .thenReturn(new DepartmentResponse(1L, "Ventas", 0L));

        mockMvc.perform(post("/api/v1/departamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ventas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empleados").value(0));
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("POST /departamentos sin nombre devuelve 400")
    void crear_sinNombre_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/departamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("PATCH /departamentos/{id} renombra")
    void renombrar_devuelve200() throws Exception {
        when(departmentService.renombrar(eq(1L), any(), any()))
                .thenReturn(new DepartmentResponse(1L, "Ventas", 0L));

        mockMvc.perform(patch("/api/v1/departamentos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ventas\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("DELETE /departamentos/{id} devuelve 204 sin cuerpo")
    void borrar_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/departamentos/1")).andExpect(status().isNoContent());

        verify(departmentService).borrar(eq(1L), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("PATCH .../empleados/{id} con departamentoId null saca al empleado del suyo")
    void asignar_conNull_llegaComoNull() throws Exception {
        mockMvc.perform(patch("/api/v1/departamentos/empleados/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departamentoId\":null}"))
                .andExpect(status().isOk());

        // Aquí null SÍ significa algo -- sacarle del departamento --, al
        // revés que en el resto de los PATCH del proyecto.
        ArgumentCaptor<Long> id = ArgumentCaptor.captor();
        verify(employeeProfileService).assignDepartment(eq(10L), id.capture(), any());
        org.assertj.core.api.Assertions.assertThat(id.getValue()).isNull();
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PATCH .../empleados/{id} como GESTOR devuelve 403: a qué departamento va alguien es de RRHH")
    void asignar_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/v1/departamentos/empleados/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departamentoId\":1}"))
                .andExpect(status().isForbidden());
    }
}
