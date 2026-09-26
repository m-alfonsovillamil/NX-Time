package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.PushPlatform;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.service.PushDeviceService;
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

/** {@code @WebMvcTest} de PushDeviceController (Fase B5). */
@NxTimeWebMvcTest(PushDeviceController.class)
@Import(WebMvcTestSecurityConfig.class)
class PushDeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushDeviceService pushDeviceService;

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("Cualquiera con sesión registra su móvil: recibir tus avisos no es un privilegio")
    void registrar() throws Exception {
        mockMvc.perform(post("/api/v1/dispositivos-push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"plataforma\":\"ANDROID\"}"))
                .andExpect(status().isNoContent());

        verify(pushDeviceService).registrar(any(), eq("abc"), eq(PushPlatform.ANDROID));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("Sin token o con una plataforma que no existe es un 400 y no llega al servicio")
    void registrarMal() throws Exception {
        mockMvc.perform(post("/api/v1/dispositivos-push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"  \",\"plataforma\":\"ANDROID\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/dispositivos-push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"plataforma\":\"SYMBIAN\"}"))
                .andExpect(status().isBadRequest());

        verify(pushDeviceService, never()).registrar(any(), anyString(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("La baja lleva el token en el cuerpo, no en la URL")
    void baja() throws Exception {
        mockMvc.perform(post("/api/v1/dispositivos-push/baja")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\"}"))
                .andExpect(status().isNoContent());

        verify(pushDeviceService).darDeBaja(any(), eq("abc"));
    }

    @Test
    @DisplayName("Sin sesión se rechaza y no llega al servicio")
    void sinSesion() throws Exception {
        mockMvc.perform(post("/api/v1/dispositivos-push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"plataforma\":\"ANDROID\"}"))
                .andExpect(status().is4xxClientError());

        verify(pushDeviceService, never()).registrar(any(), anyString(), any());
    }
}
