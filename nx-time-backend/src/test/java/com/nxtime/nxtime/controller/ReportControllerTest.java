package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.report.ExcelReportGenerator;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.report.PdfReportGenerator;
import com.nxtime.nxtime.service.ReportService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de ReportController: rutas, authorities,
 * validación de parámetros y cabeceras de descarga. El contenido de los
 * ficheros se prueba en {@code ReportGeneratorTest}.
 */
@NxTimeWebMvcTest(ReportController.class)
@Import(WebMvcTestSecurityConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private ExcelReportGenerator excelGenerator;
    @MockitoBean
    private PdfReportGenerator pdfGenerator;

    private MonthlyReport informeVacio() {
        return new MonthlyReport("Empresa", "Empleado", YearMonth.of(2026, 6), List.of());
    }

    @Test
    @WithMockUser(username = "rrhh@nxtime.test", authorities = "informe:exportar")
    @DisplayName("GET /informes/horas devuelve 200 con las cabeceras de descarga de un .xlsx")
    void exportarExcel_conAuthority_devuelve200ConCabecerasDeDescarga() throws Exception {
        when(reportService.informeDeEmpresa(eq("rrhh@nxtime.test"), any())).thenReturn(informeVacio());

        mockMvc.perform(get("/api/v1/informes/horas").param("anio", "2026").param("mes", "6"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"horas-2026-06.xlsx\""));

        verify(reportService).informeDeEmpresa("rrhh@nxtime.test", YearMonth.of(2026, 6));
    }

    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "fichaje:leer:equipo")
    @DisplayName("GET /informes/horas sin 'informe:exportar' devuelve 403 (un GESTOR no exporta)")
    void exportarExcel_sinAuthority_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/informes/horas").param("anio", "2026").param("mes", "6"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "rrhh@nxtime.test", authorities = "informe:exportar")
    @DisplayName("GET /informes/mensual/{id} devuelve 200 con content-type de PDF")
    void exportarPdf_conAuthority_devuelve200ConPdf() throws Exception {
        when(reportService.informeDeEmpleado(eq("rrhh@nxtime.test"), anyLong(), any()))
                .thenReturn(informeVacio());

        mockMvc.perform(get("/api/v1/informes/mensual/7").param("anio", "2026").param("mes", "6"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"registro-7-2026-06.pdf\""));

        verify(reportService).informeDeEmpleado("rrhh@nxtime.test", 7L, YearMonth.of(2026, 6));
    }

    @Test
    @WithMockUser(username = "rrhh@nxtime.test", authorities = "informe:exportar")
    @DisplayName("Un mes fuera de rango (13) se rechaza con 400, no genera un informe absurdo")
    void exportar_mesFueraDeRango_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/informes/horas").param("anio", "2026").param("mes", "13"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "rrhh@nxtime.test", authorities = "informe:exportar")
    @DisplayName("Un año fuera de rango se rechaza con 400")
    void exportar_anioFueraDeRango_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/informes/horas").param("anio", "1800").param("mes", "6"))
                .andExpect(status().isBadRequest());
    }
}
