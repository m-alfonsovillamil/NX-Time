package com.nxtime.nxtime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.AnalyticsGrouping;
import com.nxtime.nxtime.domain.AnalyticsPeriod;
import com.nxtime.nxtime.domain.AnalyticsScope;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.AbsenceReasonDays;
import com.nxtime.nxtime.dto.AbsenteeismResponse;
import com.nxtime.nxtime.dto.AbsenteeismRow;
import com.nxtime.nxtime.dto.AnalyticsSummaryResponse;
import com.nxtime.nxtime.dto.AnalyticsWindow;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.service.AnalyticsService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de AnalyticsController (Fase B4).
 *
 * Fija el reparto de permisos: la analítica empieza en GESTOR y el CSV pide
 * además exportar informes. Qué parte de la empresa ve cada uno lo decide el
 * servicio (AnalyticsServiceImplTest y AnaliticaIT).
 */
@NxTimeWebMvcTest(AnalyticsController.class)
@Import(WebMvcTestSecurityConfig.class)
class AnalyticsControllerTest {

    private static final AnalyticsWindow VENTANA = new AnalyticsWindow(AnalyticsPeriod.MES,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 25),
            AnalyticsScope.EMPRESA, null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("Un EMPLEADO no ve la analítica")
    void empleado_noVe() throws Exception {
        mockMvc.perform(get("/api/v1/analitica/resumen")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analitica/absentismo")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/analitica/puntualidad")).andExpect(status().isForbidden());
        verify(analyticsService, never()).resumen(any(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("GET /resumen: por defecto el mes de hoy, con los porcentajes con decimal")
    void resumen_porDefecto() throws Exception {
        when(analyticsService.resumen(any(), eq(AnalyticsPeriod.MES), eq(LocalDate.now(ZoneId.of("Europe/Madrid")))))
                .thenReturn(new AnalyticsSummaryResponse(VENTANA, 12, new BigDecimal("4.2"), new BigDecimal("0.8"),
                        new BigDecimal("93.5"), 18.5, new BigDecimal("2.0"), 452L));

        mockMvc.perform(get("/api/v1/analitica/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.absentismo").value(4.2))
                .andExpect(jsonPath("$.puntualidad").value(93.5))
                .andExpect(jsonPath("$.ventana.evaluadoHasta").value("2026-09-25"))
                .andExpect(jsonPath("$.ventana.alcance").value("EMPRESA"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Un periodo que no existe es un 400")
    void periodoInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/analitica/resumen").param("periodo", "SEMANA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Un gestor sin departamento recibe un 409 que explica por qué")
    void gestorSinDepartamento() throws Exception {
        when(analyticsService.absentismo(any(), any(), any(), any()))
                .thenThrow(new BusinessException("No tienes departamento asignado"));

        mockMvc.perform(get("/api/v1/analitica/absentismo"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Un GESTOR no exporta el CSV: exportar es de RRHH")
    void csv_gestorNo() throws Exception {
        mockMvc.perform(get("/api/v1/analitica/absentismo.csv")).andExpect(status().isForbidden());
        verify(analyticsService, never()).absentismo(any(), any(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("RRHH exporta el CSV: con BOM, punto y coma, coma decimal y nombre con el periodo")
    void csv_rrhh() throws Exception {
        AbsenteeismRow ventas = new AbsenteeismRow(3L, "Ventas", 4, 80, 76, 3, 1, 5,
                new BigDecimal("5.0"), new BigDecimal("1.3"),
                List.of(new AbsenceReasonDays("MEDICO", "Consulta médica", 3)));
        AbsenteeismRow total = new AbsenteeismRow(null, "Total", 4, 80, 76, 3, 1, 5,
                new BigDecimal("5.0"), new BigDecimal("1.3"), List.of());
        when(analyticsService.absentismo(any(), eq(AnalyticsPeriod.MES), any(), eq(AnalyticsGrouping.DEPARTAMENTO)))
                .thenReturn(new AbsenteeismResponse(VENTANA, AnalyticsGrouping.DEPARTAMENTO, total, List.of(ventas)));

        byte[] cuerpo = mockMvc.perform(get("/api/v1/analitica/absentismo.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"absentismo-2026-09-01-a-2026-09-30.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(cuerpo[0]).isEqualTo((byte) 0xEF);
        String texto = new String(cuerpo, 3, cuerpo.length - 3, StandardCharsets.UTF_8);
        assertThat(texto.split("\r\n"))
                .contains("Ventas;4;80;76;3;1;5;5,0;1,3;Consulta médica: 3", "Total;4;80;76;3;1;5;5,0;1,3;");
    }
}
