package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.service.AuthService;
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

@NxTimeWebMvcTest(UserController.class)
@Import(WebMvcTestSecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @WithMockSecurityUser
    @DisplayName("POST /usuario/cambiar-contrasena autenticado y con cuerpo válido devuelve 200")
    void changePassword_autenticadoYConCuerpoValido_devuelve200() throws Exception {
        mockMvc.perform(post("/api/v1/usuario/cambiar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaAntigua\":\"viejo123\",\"contrasenaNueva\":\"nuevo123\"}"))
                .andExpect(status().isOk());
        verify(authService).changePassword(any(), any());
    }

    @Test
    @WithMockSecurityUser
    @DisplayName("POST /usuario/cambiar-contrasena con la contraseña nueva demasiado corta devuelve 400")
    void changePassword_contrasenaNuevaDemasiadoCorta_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/usuario/cambiar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaAntigua\":\"viejo123\",\"contrasenaNueva\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /usuario/cambiar-contrasena sin autenticar devuelve 401 o 403")
    void changePassword_sinAutenticar_esRechazado() throws Exception {
        mockMvc.perform(post("/api/v1/usuario/cambiar-contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaAntigua\":\"viejo123\",\"contrasenaNueva\":\"nuevo123\"}"))
                .andExpect(status().is4xxClientError());
    }
}
