package com.nxtime.nxtime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.UpdateAbsenceStatusRequest;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        when(absenceService.createRequest(eq("empleado@nxtime.test"), any())).thenReturn(respuesta(AbsenceStatus.PENDIENTE));

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

    // Fase 9: un único PATCH /{id}/estado en lugar de los dos POST
    // (/gestor/aprobar/{id} y /gestor/rechazar/{id}).
    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "ausencia:aprobar")
    @DisplayName("PATCH /ausencias/{id}/estado con APROBADA llama al servicio y devuelve 200")
    void updateRequestStatus_aprobar_devuelve200() throws Exception {
        when(absenceService.changeRequestStatus(eq("gestor@nxtime.test"), eq(5L), any()))
                .thenReturn(respuesta(AbsenceStatus.APROBADA));

        mockMvc.perform(patch("/api/v1/ausencias/5/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"APROBADA\",\"comentario\":\"Adelante.\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateAbsenceStatusRequest> captor =
                ArgumentCaptor.forClass(UpdateAbsenceStatusRequest.class);
        verify(absenceService).changeRequestStatus(eq("gestor@nxtime.test"), eq(5L), captor.capture());
        assertThat(captor.getValue().estado()).isEqualTo(AbsenceStatus.APROBADA);
        assertThat(captor.getValue().comentario()).isEqualTo("Adelante.");
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "ausencia:escribir")
    @DisplayName("PATCH /ausencias/{id}/estado sin 'ausencia:aprobar' devuelve 403")
    void updateRequestStatus_sinAuthority_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/v1/ausencias/5/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"APROBADA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "ausencia:aprobar")
    @DisplayName("PATCH /ausencias/{id}/estado sin 'estado' devuelve 400 (Bean Validation)")
    void updateRequestStatus_sinEstado_devuelve400() throws Exception {
        mockMvc.perform(patch("/api/v1/ausencias/5/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comentario\":\"Sin estado\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "ausencia:leer")
    @DisplayName("GET /ausencias/saldo-vacaciones sin parámetro 'anio' usa el año actual")
    void getMyVacationBalance_sinAnio_usaElAnioActual() throws Exception {
        int anioActual = Year.now(ZoneId.of("Europe/Madrid")).getValue();
        when(absenceService.getMyVacationBalance("empleado@nxtime.test", anioActual))
                .thenReturn(new VacationBalanceResponse(anioActual, 22, 5, 17));

        mockMvc.perform(get("/api/v1/ausencias/saldo-vacaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDisponibles").value(17));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "ausencia:leer")
    @DisplayName("GET /ausencias/saldo-vacaciones?anio=2025 consulta ese año")
    void getMyVacationBalance_conAnio_consultaEseAnio() throws Exception {
        when(absenceService.getMyVacationBalance("empleado@nxtime.test", 2025))
                .thenReturn(new VacationBalanceResponse(2025, 22, 22, 0));

        mockMvc.perform(get("/api/v1/ausencias/saldo-vacaciones").param("anio", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anio").value(2025));
        verify(absenceService).getMyVacationBalance("empleado@nxtime.test", 2025);
    }

    private static AbsenceResponse respuesta(AbsenceStatus estado) {
        return new AbsenceResponse(5L, null, null, null, null, estado, null, null, null, null, 3);
    }
}
