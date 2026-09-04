package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.ProfileResponse;
import com.nxtime.nxtime.service.EmployeeProfileService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de ProfileController (Fase B).
 *
 * El caso que más importa aquí no es el camino feliz sino el de abajo:
 * mandar campos que no están en {@code UpdateProfileRequest} -- rol,
 * jornada, vacaciones -- no debe cambiarlos. Es lo que impide que
 * cualquiera se ascienda con un PATCH sobre su propio perfil.
 */
@NxTimeWebMvcTest(ProfileController.class)
@Import(WebMvcTestSecurityConfig.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeProfileService employeeProfileService;

    private static ProfileResponse perfil() {
        return new ProfileResponse(
                10L, "ana@nxtime.test", "Ana", "Fernández", "Ana Fernández", "AF",
                LocalDate.of(1995, 3, 14), "Analista", 3L, "Operaciones",
                Role.EMPLEADO, true, new BigDecimal("40.0"), 22);
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /perfil como EMPLEADO devuelve 200: el perfil propio no pide authority")
    void getMyProfile_comoEmpleado_devuelve200() throws Exception {
        when(employeeProfileService.getMyProfile(any())).thenReturn(perfil());

        mockMvc.perform(get("/api/v1/perfil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Ana Fernández"))
                .andExpect(jsonPath("$.iniciales").value("AF"))
                .andExpect(jsonPath("$.departamentoNombre").value("Operaciones"));
    }

    @Test
    @DisplayName("GET /perfil sin autenticar se rechaza y no llega al servicio")
    void getMyProfile_sinAutenticar_seRechaza() throws Exception {
        // Código exacto no: la cadena mínima del test no monta el punto
        // de entrada JWT. El 401 real lo fija ApiContractTest.
        mockMvc.perform(get("/api/v1/perfil")).andExpect(status().is4xxClientError());

        verify(employeeProfileService, never()).getMyProfile(any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("PATCH /perfil acepta los campos personales")
    void updateMyProfile_devuelve200() throws Exception {
        when(employeeProfileService.updateMyProfile(any(), any())).thenReturn(perfil());

        mockMvc.perform(patch("/api/v1/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apellidos\":\"Fernández\",\"puesto\":\"Analista\"}"))
                .andExpect(status().isOk());
        verify(employeeProfileService).updateMyProfile(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("Los campos que NO son personales se ignoran: no hay forma de ascenderse con un PATCH")
    void updateMyProfile_camposDeMas_seIgnoran() throws Exception {
        when(employeeProfileService.updateMyProfile(any(), any())).thenReturn(perfil());

        // Ni "rol" ni "horasSemanales" ni "diasVacaciones" existen en el
        // record, así que Jackson los descarta y nunca llegan al
        // servicio. Devolver 200 y no tocarlos es lo correcto.
        mockMvc.perform(patch("/api/v1/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"puesto\":\"Analista\",\"rol\":\"ADMIN\","
                                + "\"horasSemanales\":10,\"diasVacaciones\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("EMPLEADO"))
                .andExpect(jsonPath("$.diasVacaciones").value(22));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("PATCH /perfil con fecha de nacimiento futura devuelve 400")
    void updateMyProfile_fechaFutura_devuelve400() throws Exception {
        mockMvc.perform(patch("/api/v1/perfil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaNacimiento\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest());

        verify(employeeProfileService, never()).updateMyProfile(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("GET /perfil/{id} con 'empleado:leer' devuelve 200")
    void getProfile_conAuthority_devuelve200() throws Exception {
        when(employeeProfileService.getProfile(eq(10L), any())).thenReturn(perfil());

        mockMvc.perform(get("/api/v1/perfil/10")).andExpect(status().isOk());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /perfil/{id} como EMPLEADO devuelve 403: el de los demás no es asunto suyo")
    void getProfile_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/perfil/10")).andExpect(status().isForbidden());
    }
}
