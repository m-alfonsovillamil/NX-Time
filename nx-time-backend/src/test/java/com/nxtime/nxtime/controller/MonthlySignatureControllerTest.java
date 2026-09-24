package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.MonthlySignatureStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.MonthlySignatureResponse;
import com.nxtime.nxtime.dto.SignableMonthResponse;
import com.nxtime.nxtime.dto.TeamSignatureResponse;
import com.nxtime.nxtime.service.MonthlySignatureService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de MonthlySignatureController (Fase B3).
 *
 * El reparto: firmar y ver lo propio lo puede todo el mundo; la vista de la
 * empresa y visar son de RRHH ({@code firma:visar}). Que nadie vise la suya lo
 * corta el servicio y lo prueba FirmaMensualIT.
 */
@NxTimeWebMvcTest(MonthlySignatureController.class)
@Import(WebMvcTestSecurityConfig.class)
class MonthlySignatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthlySignatureService signatureService;

    private static MonthlySignatureResponse firma() {
        return new MonthlySignatureResponse(3L, 10L, "Ana Prueba", 2026, 8, MonthlySignatureStatus.VIGENTE,
                "a".repeat(64), 21, 21 * 8 * 3600L, Instant.parse("2026-09-02T08:00:00Z"),
                null, null, null, null);
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /firmas: un EMPLEADO firma su mes, y se responde 201")
    void firmar_comoEmpleado() throws Exception {
        when(signatureService.firmar(any(), eq(YearMonth.of(2026, 8)), any())).thenReturn(firma());

        mockMvc.perform(post("/api/v1/firmas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anio\":2026,\"mes\":8}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.hash").value("a".repeat(64)));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("Un mes 13 es un 400 y no llega al servicio")
    void firmar_mesInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/firmas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anio\":2026,\"mes\":13}"))
                .andExpect(status().isBadRequest());
        verify(signatureService, never()).firmar(any(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /firmas/mias: los meses del EMPLEADO, con si se pueden firmar")
    void misMeses() throws Exception {
        when(signatureService.misMeses(any())).thenReturn(List.of(
                new SignableMonthResponse(2026, 8, 21, 21 * 8 * 3600L, true, null, null)));

        mockMvc.perform(get("/api/v1/firmas/mias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mes").value(8))
                .andExpect(jsonPath("$[0].puedeFirmar").value(true));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Un GESTOR no ve la empresa ni visa: eso es de RRHH")
    void gestor_noVisa() throws Exception {
        mockMvc.perform(get("/api/v1/firmas/equipo").param("anio", "2026").param("mes", "8"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/firmas/3/visado")).andExpect(status().isForbidden());
        verify(signatureService, never()).visar(anyLong(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("RRHH ve cómo está el mes en la empresa y visa")
    void rrhh_veYVisa() throws Exception {
        when(signatureService.equipo(any(), eq(YearMonth.of(2026, 8)))).thenReturn(List.of(
                new TeamSignatureResponse(10L, "Ana Prueba", "VIGENTE", firma())));
        when(signatureService.visar(eq(3L), any())).thenReturn(firma());

        mockMvc.perform(get("/api/v1/firmas/equipo").param("anio", "2026").param("mes", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("VIGENTE"));
        mockMvc.perform(post("/api/v1/firmas/3/visado")).andExpect(status().isOk());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("La vista de la empresa con un mes 0 es un 400")
    void equipo_mesInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/firmas/equipo").param("anio", "2026").param("mes", "0"))
                .andExpect(status().isBadRequest());
        verify(signatureService, never()).equipo(any(), any());
    }
}
