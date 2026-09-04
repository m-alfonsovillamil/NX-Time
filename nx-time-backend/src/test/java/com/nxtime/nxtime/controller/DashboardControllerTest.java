package com.nxtime.nxtime.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.WorkStatus;
import com.nxtime.nxtime.dto.CompanyDashboardResponse;
import com.nxtime.nxtime.dto.PersonalDashboardResponse;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.service.DashboardService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de DashboardController: rutas y authorities. Los
 * agregados en sí se prueban en {@code DashboardServiceIT} contra
 * PostgreSQL real.
 */
@NxTimeWebMvcTest(DashboardController.class)
@Import(WebMvcTestSecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /dashboard/resumen con 'fichaje:leer' devuelve 200")
    void getPersonalDashboard_conAuthority_devuelve200() throws Exception {
        when(dashboardService.getPersonalDashboard("empleado@nxtime.test")).thenReturn(
                new PersonalDashboardResponse(WorkStatus.TRABAJANDO, 450, 1800, 7200, 2,
                        new VacationBalanceResponse(2026, 22, 5, 17), 2400));

        mockMvc.perform(get("/api/v1/dashboard/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoActual").value("TRABAJANDO"))
                .andExpect(jsonPath("$.minutosHoy").value(450))
                .andExpect(jsonPath("$.saldoVacaciones.diasDisponibles").value(17));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "ausencia:leer")
    @DisplayName("GET /dashboard/resumen sin 'fichaje:leer' devuelve 403")
    void getPersonalDashboard_sinAuthority_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/resumen")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "fichaje:leer:equipo")
    @DisplayName("GET /dashboard/empresa con 'fichaje:leer:equipo' devuelve 200")
    void getCompanyDashboard_conAuthority_devuelve200() throws Exception {
        when(dashboardService.getCompanyDashboard("gestor@nxtime.test"))
                .thenReturn(new CompanyDashboardResponse(4, 12000, 3, 1, List.of()));

        mockMvc.perform(get("/api/v1/dashboard/empresa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empleadosActivos").value(4))
                .andExpect(jsonPath("$.incidenciasAbiertas").value(1));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /dashboard/empresa sin 'fichaje:leer:equipo' devuelve 403 (un EMPLEADO no ve la empresa)")
    void getCompanyDashboard_sinAuthorityDeEquipo_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/empresa")).andExpect(status().isForbidden());
    }
}
