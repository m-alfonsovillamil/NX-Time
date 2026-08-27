package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de AbsenceController: rutas, authorities y
 * validación. Las transiciones de estado en sí las cubre {@link
 * com.nxtime.nxtime.service.impl.AbsenceServiceImplTest}.
 */
@NxTimeWebMvcTest(AbsenceController.class)
@Import(WebMvcTestSecurityConfig.class)
class AbsenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AbsenceService absenceService;

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "ausencia:escribir")
    @DisplayName("POST /ausencias con cuerpo válido y authority correcta devuelve 200")
    void requestAbsence_conAuthorityYCuerpoValido_devuelve200() throws Exception {
        when(absenceService.createRequest(eq("empleado@nxtime.test"), any()))
                .thenReturn(new AbsenceResponse(1L, null, null, null, null, AbsenceStatus.PENDIENTE, null));

        mockMvc.perform(post("/api/v1/ausencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-06-01\",\"fechaFin\":\"2026-06-05\",\"tipo\":\"VACACIONES\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "ausencia:leer")
    @DisplayName("POST /ausencias sin la authority 'ausencia:escribir' devuelve 403")
    void requestAbsence_sinAuthorityDeEscritura_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/ausencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-06-01\",\"fechaFin\":\"2026-06-05\",\"tipo\":\"VACACIONES\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "ausencia:escribir")
    @DisplayName("POST /ausencias sin 'tipo' devuelve 400 (Bean Validation)")
    void requestAbsence_sinTipo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/ausencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaInicio\":\"2026-06-01\",\"fechaFin\":\"2026-06-05\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "ausencia:leer")
    @DisplayName("GET /ausencias/mis-peticiones con authority correcta devuelve 200")
    void getMyRequests_conAuthority_devuelve200() throws Exception {
        when(absenceService.getMyRequests("empleado@nxtime.test")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/ausencias/mis-peticiones")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "ausencia:leer")
    @DisplayName("GET /ausencias/gestor/pendientes sin 'ausencia:aprobar' devuelve 403 (un EMPLEADO no aprueba)")
    void getPendingRequests_sinAuthorityDeAprobar_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/ausencias/gestor/pendientes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "ausencia:aprobar")
    @DisplayName("GET /ausencias/gestor/pendientes con 'ausencia:aprobar' devuelve 200")
    void getPendingRequests_conAuthorityDeAprobar_devuelve200() throws Exception {
        when(absenceService.getPendingRequests("gestor@nxtime.test")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/ausencias/gestor/pendientes")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "ausencia:aprobar")
    @DisplayName("POST /ausencias/gestor/aprobar/{id} llama al servicio con estado APROBADA")
    void approveRequest_llamaAlServicioConEstadoAprobada() throws Exception {
        when(absenceService.changeRequestStatus("gestor@nxtime.test", 5L, AbsenceStatus.APROBADA))
                .thenReturn(new AbsenceResponse(5L, null, null, null, null, AbsenceStatus.APROBADA, null));

        mockMvc.perform(post("/api/v1/ausencias/gestor/aprobar/5")).andExpect(status().isOk());
        verify(absenceService).changeRequestStatus("gestor@nxtime.test", 5L, AbsenceStatus.APROBADA);
    }

    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "ausencia:aprobar")
    @DisplayName("POST /ausencias/gestor/rechazar/{id} llama al servicio con estado RECHAZADA")
    void rejectRequest_llamaAlServicioConEstadoRechazada() throws Exception {
        when(absenceService.changeRequestStatus("gestor@nxtime.test", 5L, AbsenceStatus.RECHAZADA))
                .thenReturn(new AbsenceResponse(5L, null, null, null, null, AbsenceStatus.RECHAZADA, null));

        mockMvc.perform(post("/api/v1/ausencias/gestor/rechazar/5")).andExpect(status().isOk());
        verify(absenceService).changeRequestStatus("gestor@nxtime.test", 5L, AbsenceStatus.RECHAZADA);
    }
}
