package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.service.AuthService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de AuthController: rutas públicas (/auth/** no
 * exige autenticación, igual que en producción) y validación de entrada.
 * El flujo real de login/registro/refresh/logout contra la app completa
 * ya lo cubre {@code ApiContractTest}.
 */
@NxTimeWebMvcTest(AuthController.class)
@Import(WebMvcTestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /auth/register-manager con datos válidos (sin autenticar) devuelve 200")
    void registerManager_datosValidos_devuelve200() throws Exception {
        when(authService.registerManager(any()))
                .thenReturn(new AuthenticationResponse("token", "refresh", "Ada", Role.ADMIN));

        mockMvc.perform(post("/auth/register-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreEmpresa\":\"Empresa SL\",\"nombreGestor\":\"Ada\","
                                + "\"email\":\"ada@nxtime.test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/register-manager con email inválido devuelve 400")
    void registerManager_emailInvalido_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/register-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreEmpresa\":\"Empresa SL\",\"nombreGestor\":\"Ada\","
                                + "\"email\":\"no-es-un-email\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register-manager con contraseña demasiado corta devuelve 400")
    void registerManager_contrasenaCorta_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/register-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreEmpresa\":\"Empresa SL\",\"nombreGestor\":\"Ada\","
                                + "\"email\":\"ada@nxtime.test\",\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/login con datos válidos devuelve 200")
    void login_datosValidos_devuelve200() throws Exception {
        when(authService.login(any()))
                .thenReturn(new AuthenticationResponse("token", "refresh", "Ada", Role.GESTOR));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@nxtime.test\",\"contrasena\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/login con email en blanco devuelve 400")
    void login_emailEnBlanco_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"contrasena\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/refresh con un refreshToken en blanco devuelve 400")
    void refresh_refreshTokenEnBlanco_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/logout con un refreshToken válido devuelve 200")
    void logout_refreshTokenValido_devuelve200() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"algun-token\"}"))
                .andExpect(status().isOk());
    }
}
