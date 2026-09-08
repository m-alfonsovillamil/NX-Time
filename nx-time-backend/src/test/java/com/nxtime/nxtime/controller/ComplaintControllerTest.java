package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.ComplaintCategory;
import com.nxtime.nxtime.domain.ComplaintStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.ComplaintCreatedResponse;
import com.nxtime.nxtime.dto.ComplaintResponse;
import com.nxtime.nxtime.dto.ComplaintSummaryResponse;
import com.nxtime.nxtime.dto.CreateComplaintRequest;
import com.nxtime.nxtime.service.ComplaintService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} del canal de denuncias (Fase G).
 *
 * Lo que se fija aquí es el corte entre las <b>dos puertas</b> del
 * canal, que es lo que un cambio despistado rompería sin que ningún
 * test de servicio se enterase:
 *
 * <ul>
 *   <li>Denunciar y seguir por código: cualquiera con
 *       {@code denuncia:crear}, o sea todo el mundo.</li>
 *   <li>Instruir: solo {@code denuncia:instruir}, que <b>únicamente
 *       tiene ADMIN</b>. Se comprueba expresamente que un GESTOR y un
 *       RRHH reciben 403 — la denuncia puede ser sobre ellos, y que la
 *       jerarquía de roles no se la conceda es el requisito, no un
 *       efecto colateral.</li>
 * </ul>
 */
@NxTimeWebMvcTest(ComplaintController.class)
@Import(WebMvcTestSecurityConfig.class)
class ComplaintControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplaintService complaintService;

    private ComplaintResponse expediente() {
        return new ComplaintResponse(
                100L, ComplaintCategory.ACOSO, "Acoso laboral o sexual", "Los hechos.",
                ComplaintStatus.EN_INVESTIGACION, true, null,
                Instant.parse("2026-09-01T08:00:00Z"), Instant.parse("2026-09-02T08:00:00Z"),
                null, null, null, 82L, List.of());
    }

    private ComplaintSummaryResponse fila() {
        return new ComplaintSummaryResponse(
                100L, ComplaintCategory.SEGURIDAD, "Seguridad y salud en el trabajo",
                ComplaintStatus.RECIBIDA, true,
                Instant.parse("2026-09-01T08:00:00Z"), null, -2L, 82L, 0);
    }

    // ------------------------------------------------------------------
    // Denunciar
    // ------------------------------------------------------------------

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /denuncias: un EMPLEADO puede denunciar y recibe 201 con su código")
    void presentar_comoEmpleado_devuelve201ConCodigo() throws Exception {
        when(complaintService.presentar(any(), any())).thenReturn(new ComplaintCreatedResponse(
                "3f7c9d2a-0000-4000-8000-000000000000", true,
                Instant.parse("2026-09-08T08:00:00Z"), "Guarda este código."));

        mockMvc.perform(post("/api/v1/denuncias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"ACOSO\",\"descripcion\":\"Los hechos.\","
                                + "\"anonima\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoSeguimiento")
                        .value("3f7c9d2a-0000-4000-8000-000000000000"))
                .andExpect(jsonPath("$.anonima").value(true))
                .andExpect(jsonPath("$.avisoImportante").isNotEmpty());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /denuncias sin 'anonima' devuelve 400: no hay valor por defecto para eso")
    void presentar_sinDecirSiEsAnonima_devuelve400() throws Exception {
        // El campo es Boolean y @NotNull a propósito. Con un boolean
        // primitivo, un cliente que se olvidara del campo convertiría a
        // quien denuncia en delator sin que nadie lo hubiera decidido.
        mockMvc.perform(post("/api/v1/denuncias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"ACOSO\",\"descripcion\":\"Los hechos.\"}"))
                .andExpect(status().isBadRequest());
        verify(complaintService, never()).presentar(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /denuncias sin descripción devuelve 400")
    void presentar_sinDescripcion_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/denuncias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"ACOSO\",\"descripcion\":\"  \","
                                + "\"anonima\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /denuncias pasa al servicio la decisión de anonimato tal cual llegó")
    void presentar_pasaElAnonimatoTalCual() throws Exception {
        when(complaintService.presentar(any(), any())).thenReturn(new ComplaintCreatedResponse(
                "codigo", false, Instant.now(), "Guarda este código."));

        mockMvc.perform(post("/api/v1/denuncias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"FRAUDE\",\"descripcion\":\"Los hechos.\","
                                + "\"anonima\":false}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateComplaintRequest> captor =
                ArgumentCaptor.forClass(CreateComplaintRequest.class);
        verify(complaintService).presentar(captor.capture(), any());
        Assertions.assertThat(captor.getValue().anonima()).isFalse();
        Assertions.assertThat(captor.getValue().categoria())
                .isEqualTo(ComplaintCategory.FRAUDE);
    }

    // ------------------------------------------------------------------
    // Seguir con el código
    // ------------------------------------------------------------------

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /denuncias/seguimiento/{codigo}: basta el código, no se mira quién eres")
    void seguimiento_conCodigo_devuelve200() throws Exception {
        when(complaintService.seguimiento(eq("el-codigo"), any())).thenReturn(expediente());

        mockMvc.perform(get("/api/v1/denuncias/seguimiento/el-codigo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonima").value(true))
                .andExpect(jsonPath("$.denunciante").isEmpty())
                .andExpect(jsonPath("$.estado").value("EN_INVESTIGACION"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /denuncias/seguimiento/{codigo}/mensajes con texto vacío devuelve 400")
    void responder_sinTexto_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/denuncias/seguimiento/el-codigo/mensajes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(complaintService, never()).responder(any(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /denuncias/mias devuelve solo las identificadas y NO choca con /{id}")
    void mias_comoEmpleado_devuelve200() throws Exception {
        // "/mias" es una ruta literal y gana a la plantilla "/{id}": si
        // no lo hiciera, Spring intentaría convertir "mias" a long.
        when(complaintService.mias(any())).thenReturn(List.of(fila()));

        mockMvc.perform(get("/api/v1/denuncias/mias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100));
        verify(complaintService, never()).detalle(anyLong(), any());
    }

    // ------------------------------------------------------------------
    // Instruir: solo ADMIN
    // ------------------------------------------------------------------

    @Test
    @WithMockSecurityUser(rol = Role.ADMIN)
    @DisplayName("GET /denuncias como ADMIN devuelve la bandeja, con los plazos vencidos en negativo")
    void bandeja_comoAdmin_devuelve200() throws Exception {
        when(complaintService.bandeja(any())).thenReturn(List.of(fila()));

        mockMvc.perform(get("/api/v1/denuncias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].diasHastaAcuse").value(-2))
                .andExpect(jsonPath("$[0].anonima").value(true));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("GET /denuncias como GESTOR devuelve 403: la denuncia puede ser sobre él")
    void bandeja_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/denuncias")).andExpect(status().isForbidden());
        verify(complaintService, never()).bandeja(any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.RRHH)
    @DisplayName("GET /denuncias como RRHH también devuelve 403: la authority no baja de ADMIN")
    void bandeja_comoRrhh_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/denuncias")).andExpect(status().isForbidden());
        verify(complaintService, never()).bandeja(any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /denuncias/{id} como EMPLEADO devuelve 403 aunque sea suya")
    void detalle_comoEmpleado_devuelve403() throws Exception {
        // A lo suyo se llega por /seguimiento/{codigo}, no por id: si el
        // id valiera, un empleado podría probar números ajenos.
        mockMvc.perform(get("/api/v1/denuncias/100")).andExpect(status().isForbidden());
        verify(complaintService, never()).detalle(anyLong(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.ADMIN)
    @DisplayName("PATCH /denuncias/{id}/estado sin estado devuelve 400")
    void cambiarEstado_sinEstado_devuelve400() throws Exception {
        mockMvc.perform(patch("/api/v1/denuncias/100/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conclusion\":\"Algo\"}"))
                .andExpect(status().isBadRequest());
        verify(complaintService, never()).cambiarEstado(anyLong(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.ADMIN)
    @DisplayName("PATCH /denuncias/{id}/estado con la authority llega al servicio")
    void cambiarEstado_comoAdmin_devuelve200() throws Exception {
        when(complaintService.cambiarEstado(eq(100L), any(), any())).thenReturn(expediente());

        mockMvc.perform(patch("/api/v1/denuncias/100/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_INVESTIGACION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PATCH /denuncias/{id}/estado como GESTOR devuelve 403")
    void cambiarEstado_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/v1/denuncias/100/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ARCHIVADA\",\"conclusion\":\"No procede.\"}"))
                .andExpect(status().isForbidden());
        verify(complaintService, never()).cambiarEstado(anyLong(), any(), any());
    }
}
