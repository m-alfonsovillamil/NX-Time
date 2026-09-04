package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.SimpleEmployeeDTO;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.service.AuthService;
import com.nxtime.nxtime.service.EmployeeProfileService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import java.math.BigDecimal;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de ManagerController: authorities granulares por
 * rol (ver {@link com.nxtime.nxtime.domain.RoleAuthorities}), en
 * particular que "gestor:crear" solo la tenga ADMIN y "empleado:gestionar"
 * solo RRHH/ADMIN -- antes cualquier GESTOR podía crear otro GESTOR sin
 * límite (ver auditoría, defectos de diseño). Usa {@code
 * @WithMockSecurityUser} porque estos endpoints leen
 * {@code @AuthenticationPrincipal SecurityUser}, no solo el nombre de
 * usuario.
 */
@NxTimeWebMvcTest(ManagerController.class)
@Import(WebMvcTestSecurityConfig.class)
class ManagerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AbsenceService absenceService;

    @MockitoBean
    private EmployeeProfileService employeeProfileService;

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("POST /gestor/empleados con 'empleado:crear' (GESTOR) devuelve 200")
    void createEmployee_comoGestor_devuelve200() throws Exception {
        mockMvc.perform(post("/api/v1/gestor/empleados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo\",\"email\":\"nuevo@nxtime.test\",\"contrasena\":\"password1\"}"))
                .andExpect(status().isOk());
        verify(authService).createEmployee(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /gestor/empleados como EMPLEADO (sin 'empleado:crear') devuelve 403")
    void createEmployee_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/gestor/empleados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo\",\"email\":\"nuevo@nxtime.test\",\"contrasena\":\"password1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockSecurityUser(rol = Role.ADMIN)
    @DisplayName("POST /gestor/gestores con 'gestor:crear' (ADMIN) devuelve 200")
    void createManager_comoAdmin_devuelve200() throws Exception {
        mockMvc.perform(post("/api/v1/gestor/gestores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo Gestor\",\"email\":\"gestor2@nxtime.test\",\"contrasena\":\"password1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("POST /gestor/gestores como GESTOR (sin 'gestor:crear') devuelve 403 -- solo ADMIN lo tiene")
    void createManager_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/gestor/gestores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo Gestor\",\"email\":\"gestor2@nxtime.test\",\"contrasena\":\"password1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("GET /gestor/mis-empleados con 'empleado:leer' devuelve 200")
    void getMyEmployees_conAuthority_devuelve200() throws Exception {
        when(employeeProfileService.getMyEmployees(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/gestor/mis-empleados")).andExpect(status().isOk());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("GET /gestor/ausencias-historial con 'ausencia:leer:equipo' devuelve 200")
    void getAbsenceHistory_conAuthority_devuelve200() throws Exception {
        when(absenceService.getHistory(eq("test@nxtime.test"))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/gestor/ausencias-historial")).andExpect(status().isOk());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("PATCH /gestor/empleados/{id}/estado con 'empleado:gestionar' (RRHH) devuelve 200")
    void updateEmployeeStatus_comoRRHH_devuelve200() throws Exception {
        mockMvc.perform(patch("/api/v1/gestor/empleados/2/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk());
        verify(authService).setEmployeeActive(eq(2L), eq(false), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PATCH /gestor/empleados/{id}/estado como GESTOR (sin 'empleado:gestionar') devuelve 403")
    void updateEmployeeStatus_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/v1/gestor/empleados/2/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("PATCH /gestor/empleados/{id}/estado sin el campo 'activo' devuelve 400")
    void updateEmployeeStatus_sinCampoActivo_devuelve400() throws Exception {
        mockMvc.perform(patch("/api/v1/gestor/empleados/2/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Fase A: la ficha de empleado
    // ------------------------------------------------------------------

    private static SimpleEmployeeDTO fichaDeEjemplo() {
        return new SimpleEmployeeDTO(
                2L, "Empleado", "empleado@nxtime.test", true, new BigDecimal("37.5"), 25, 3L, "Operaciones");
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("PATCH /gestor/empleados/{id}/ficha con 'empleado:configurar' (RRHH) devuelve 200")
    void updateEmployeeProfile_comoRRHH_devuelve200() throws Exception {
        when(employeeProfileService.updateProfile(eq(2L), any(), any())).thenReturn(fichaDeEjemplo());

        mockMvc.perform(patch("/api/v1/gestor/empleados/2/ficha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasSemanales\":37.5,\"diasVacaciones\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horasSemanales").value(37.5))
                .andExpect(jsonPath("$.diasVacaciones").value(25));
        verify(employeeProfileService).updateProfile(eq(2L), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PATCH /gestor/empleados/{id}/ficha como GESTOR devuelve 403: sin 'empleado:configurar'")
    void updateEmployeeProfile_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/v1/gestor/empleados/2/ficha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasSemanales\":37.5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("PATCH /gestor/empleados/{id}/ficha como EMPLEADO devuelve 403")
    void updateEmployeeProfile_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/v1/gestor/empleados/2/ficha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasSemanales\":37.5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("PATCH /gestor/empleados/{id}/ficha con 61 horas devuelve 400, no un 500 de la base")
    void updateEmployeeProfile_jornadaFueraDeRango_devuelve400() throws Exception {
        mockMvc.perform(patch("/api/v1/gestor/empleados/2/ficha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horasSemanales\":61.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("PATCH /gestor/empleados/{id}/ficha con el cuerpo vacío devuelve 200: es un PATCH, null es 'no tocar'")
    void updateEmployeeProfile_cuerpoVacio_devuelve200() throws Exception {
        when(employeeProfileService.updateProfile(eq(2L), any(), any())).thenReturn(fichaDeEjemplo());

        mockMvc.perform(patch("/api/v1/gestor/empleados/2/ficha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
