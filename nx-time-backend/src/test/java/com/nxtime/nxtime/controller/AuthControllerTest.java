package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.service.AccessCodeService;
import com.nxtime.nxtime.service.AuthService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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

    @MockitoBean
    private AccessCodeService accessCodeService;

    @Test
    @DisplayName("POST /auth/register-manager con datos válidos (sin autenticar) devuelve 200")
    void registerManager_datosValidos_devuelve200() throws Exception {
        when(authService.registerManager(any()))
                .thenReturn(new AuthenticationResponse("token", "refresh", "Ada", Role.ADMIN));

        mockMvc.perform(post("/auth/register-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreEmpresa\":\"Empresa SL\",\"nombre\":\"Ada\","
                                + "\"apellidos\":\"Lovelace\",\"email\":\"ada@nxtime.test\","
                                + "\"contrasena\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/register-manager con email inválido devuelve 400")
    void registerManager_emailInvalido_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/register-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreEmpresa\":\"Empresa SL\",\"nombre\":\"Ada\","
                                + "\"apellidos\":\"Lovelace\",\"email\":\"no-es-un-email\","
                                + "\"contrasena\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register-manager con contraseña demasiado corta devuelve 400")
    void registerManager_contrasenaCorta_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/register-manager")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreEmpresa\":\"Empresa SL\",\"nombre\":\"Ada\","
                                + "\"apellidos\":\"Lovelace\",\"email\":\"ada@nxtime.test\","
                                + "\"contrasena\":\"abc\"}"))
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

    // ---- Códigos de acceso (ADR 014) ----

    @Test
    @DisplayName("POST /auth/recuperar sin autenticar devuelve 202 y pide el código")
    void recuperar_correoValido_devuelve202() throws Exception {
        mockMvc.perform(post("/auth/recuperar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@nxtime.test\"}"))
                .andExpect(status().isAccepted());

        verify(accessCodeService).solicitarRecuperacion("ada@nxtime.test");
    }

    @Test
    @DisplayName("POST /auth/recuperar con un email mal formado devuelve 400 sin llegar al servicio")
    void recuperar_emailInvalido_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/recuperar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-un-email\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accessCodeService);
    }

    @Test
    @DisplayName("POST /auth/recuperar/confirmar con datos válidos devuelve 204")
    void confirmar_datosValidos_devuelve204() throws Exception {
        mockMvc.perform(post("/auth/recuperar/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@nxtime.test\",\"codigo\":\" 012345 \","
                                + "\"contrasenaNueva\":\"nuevaSegura123\"}"))
                .andExpect(status().isNoContent());

        verify(accessCodeService).confirmar("ada@nxtime.test", " 012345 ", "nuevaSegura123");
    }

    @Test
    @DisplayName("POST /auth/recuperar/confirmar con un código que no son 6 dígitos devuelve 400")
    void confirmar_codigoMalFormado_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/recuperar/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@nxtime.test\",\"codigo\":\"12ab\","
                                + "\"contrasenaNueva\":\"nuevaSegura123\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accessCodeService);
    }

    @Test
    @DisplayName("POST /auth/recuperar/confirmar con una contraseña de menos de 8 caracteres devuelve 400")
    void confirmar_contrasenaCorta_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/recuperar/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@nxtime.test\",\"codigo\":\"123456\","
                                + "\"contrasenaNueva\":\"corta\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/recuperar/confirmar con un código que no vale devuelve el 400 del servicio, con su mensaje")
    void confirmar_codigoNoValido_devuelve400ConMensaje() throws Exception {
        doThrow(new BusinessException("El código no es válido o ha caducado. Pide uno nuevo.", HttpStatus.BAD_REQUEST))
                .when(accessCodeService).confirmar(anyString(), anyString(), anyString());

        mockMvc.perform(post("/auth/recuperar/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@nxtime.test\",\"codigo\":\"999999\","
                                + "\"contrasenaNueva\":\"nuevaSegura123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El código no es válido o ha caducado. Pide uno nuevo."));
    }
}
