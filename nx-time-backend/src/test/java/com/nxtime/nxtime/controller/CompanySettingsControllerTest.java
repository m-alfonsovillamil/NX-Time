package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.CompanySettingsResponse;
import com.nxtime.nxtime.service.CompanySettingsService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de CompanySettingsController (fase Z2): los ajustes de
 * la empresa son solo de ADMIN, ni siquiera de RRHH, porque la zona mueve los
 * días de todo el histórico (ADR 032).
 */
@NxTimeWebMvcTest(CompanySettingsController.class)
@Import(WebMvcTestSecurityConfig.class)
class CompanySettingsControllerTest {

    private static final String RUTA = "/api/v1/empresa/ajustes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanySettingsService service;

    @Test
    @WithMockSecurityUser(rol = Role.ADMIN)
    @DisplayName("GET como ADMIN devuelve nombre y zona")
    void leer_comoAdmin() throws Exception {
        when(service.leer(any())).thenReturn(new CompanySettingsResponse("Talleres Ana", "Atlantic/Canary", 0));

        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Talleres Ana"))
                .andExpect(jsonPath("$.zonaHoraria").value("Atlantic/Canary"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("RRHH no puede ni leerlos ni cambiarlos: 403")
    void comoRrhh_403() throws Exception {
        mockMvc.perform(get(RUTA)).andExpect(status().isForbidden());
        mockMvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"zonaHoraria\":\"Europe/Madrid\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).guardar(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.ADMIN)
    @DisplayName("PUT sin zona o con el nombre en blanco es un 400, sin llegar al servicio")
    void guardar_invalido_400() throws Exception {
        mockMvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \",\"zonaHoraria\":\"Europe/Madrid\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Talleres\"}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).guardar(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.ADMIN)
    @DisplayName("PUT válido devuelve lo guardado y cuántas firmas cayeron")
    void guardar_comoAdmin() throws Exception {
        when(service.guardar(any(), any())).thenReturn(new CompanySettingsResponse("Talleres", "Atlantic/Canary", 2));

        mockMvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Talleres\",\"zonaHoraria\":\"Atlantic/Canary\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmasInvalidadas").value(2));
    }
}
