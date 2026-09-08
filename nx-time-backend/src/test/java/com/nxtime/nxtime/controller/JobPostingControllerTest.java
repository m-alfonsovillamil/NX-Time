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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.ApplicationStatus;
import com.nxtime.nxtime.domain.JobPostingStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.dto.JobApplicationResponse;
import com.nxtime.nxtime.dto.JobPostingResponse;
import com.nxtime.nxtime.service.JobPostingService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de ofertas internas (Fase H).
 *
 * El reparto de permisos aquí es asimétrico y es lo que hay que fijar:
 * <b>leer y optar lo puede toda la plantilla</b> —un tablón de vacantes
 * al que no llega la gente no es un tablón—, mientras que publicar y
 * valorar empiezan en GESTOR.
 *
 * Lo que este test NO cubre, a propósito, es que nadie valore su propia
 * candidatura: eso no lo puede parar un {@code @PreAuthorize} porque
 * quien valora tiene la authority. Vive en el servicio y se prueba en
 * {@code JobPostingServiceImplTest}.
 */
@NxTimeWebMvcTest(JobPostingController.class)
@Import(WebMvcTestSecurityConfig.class)
class JobPostingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobPostingService jobPostingService;

    private JobPostingResponse oferta() {
        return new JobPostingResponse(
                5L, "Backend sénior", "Java 21 y Spring Boot.", "Desarrollador/a",
                "Ingeniería", "Marta", JobPostingStatus.ABIERTA,
                Instant.parse("2026-09-01T08:00:00Z"), LocalDate.of(2026, 9, 30),
                true, false, false, null);
    }

    private JobApplicationResponse candidatura() {
        return new JobApplicationResponse(
                30L, 5L, "Backend sénior", 10L, "Ana", "Me presento.",
                ApplicationStatus.RECIBIDA, null, null, null,
                Instant.parse("2026-09-02T08:00:00Z"), 77L, "cv-ana.pdf", false);
    }

    // ------------------------------------------------------------------
    // Ver y optar: toda la plantilla
    // ------------------------------------------------------------------

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /ofertas: un EMPLEADO ve el tablón sin permisos de gestión")
    void publicadas_comoEmpleado_devuelve200() throws Exception {
        when(jobPostingService.publicadas(any())).thenReturn(List.of(oferta()));

        mockMvc.perform(get("/api/v1/ofertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Backend sénior"))
                .andExpect(jsonPath("$[0].admiteCandidaturas").value(true))
                // El contador de candidaturas no viaja a quien no las
                // valora: cuántos compañeros han optado no es su dato.
                .andExpect(jsonPath("$[0].candidaturas").isEmpty());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /ofertas/{id}/candidaturas: un EMPLEADO se presenta y recibe 201")
    void presentar_comoEmpleado_devuelve201() throws Exception {
        when(jobPostingService.presentarCandidatura(eq(5L), any(), any()))
                .thenReturn(candidatura());

        mockMvc.perform(post("/api/v1/ofertas/5/candidaturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carta\":\"Me presento.\"}"))
                .andExpect(status().isCreated())
                // El id del adjunto CONGELADO: es lo que hay que
                // descargar para leer el CV tal como se presentó.
                .andExpect(jsonPath("$.cvAdjuntoId").value(77))
                .andExpect(jsonPath("$.estado").value("RECIBIDA"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("El cuerpo de la candidatura NO admite elegir CV: solo lleva carta")
    void presentar_noSeEligeElCv() throws Exception {
        when(jobPostingService.presentarCandidatura(eq(5L), any(), any()))
                .thenReturn(candidatura());

        // Un cliente que intente colar un adjunto ajeno no consigue
        // nada: el campo no existe en el DTO y Jackson lo descarta. El
        // CV lo pone el servidor, y es el vigente de quien se presenta.
        mockMvc.perform(post("/api/v1/ofertas/5/candidaturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carta\":\"Hola\",\"cvAdjuntoId\":999}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /candidaturas/mias devuelve las propias sin permisos de gestión")
    void misCandidaturas_comoEmpleado_devuelve200() throws Exception {
        when(jobPostingService.misCandidaturas(any())).thenReturn(List.of(candidatura()));

        mockMvc.perform(get("/api/v1/candidaturas/mias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ofertaTitulo").value("Backend sénior"))
                .andExpect(jsonPath("$[0].puedoValorar").value(false));
    }

    // ------------------------------------------------------------------
    // Publicar y valorar: desde GESTOR
    // ------------------------------------------------------------------

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("POST /ofertas como EMPLEADO devuelve 403")
    void crear_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Backend\",\"descripcion\":\"Java\"}"))
                .andExpect(status().isForbidden());
        verify(jobPostingService, never()).crear(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("POST /ofertas con 'oferta:publicar' devuelve 201")
    void crear_comoGestor_devuelve201() throws Exception {
        when(jobPostingService.crear(any(), any())).thenReturn(oferta());

        mockMvc.perform(post("/api/v1/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Backend sénior\",\"descripcion\":\"Java 21.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("POST /ofertas sin título devuelve 400")
    void crear_sinTitulo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"  \",\"descripcion\":\"Java 21.\"}"))
                .andExpect(status().isBadRequest());
        verify(jobPostingService, never()).crear(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PUT /ofertas/{id} no lleva estado: publicar es un endpoint aparte")
    void editar_noTocaElEstado() throws Exception {
        when(jobPostingService.editar(eq(5L), any(), any())).thenReturn(oferta());

        // Un "estado" en el cuerpo de la edición se descarta: con él
        // dentro, guardar un cambio de redacción publicaría la oferta.
        mockMvc.perform(put("/api/v1/ofertas/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Backend\",\"descripcion\":\"Java\","
                                + "\"estado\":\"ABIERTA\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /ofertas/gestion como EMPLEADO devuelve 403 y no choca con /{id}")
    void gestion_comoEmpleado_devuelve403() throws Exception {
        // "/gestion" es una ruta literal y gana a la plantilla "/{id}":
        // si no lo hiciera, Spring intentaría convertirla a long.
        mockMvc.perform(get("/api/v1/ofertas/gestion")).andExpect(status().isForbidden());
        verify(jobPostingService, never()).detalle(anyLong(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /ofertas/{id}/candidaturas como EMPLEADO devuelve 403")
    void candidaturasDeOferta_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/ofertas/5/candidaturas")).andExpect(status().isForbidden());
        verify(jobPostingService, never()).candidaturasDeOferta(anyLong(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("GET /ofertas/{id}/candidaturas con la authority devuelve 200")
    void candidaturasDeOferta_comoGestor_devuelve200() throws Exception {
        when(jobPostingService.candidaturasDeOferta(eq(5L), any()))
                .thenReturn(List.of(candidatura()));

        mockMvc.perform(get("/api/v1/ofertas/5/candidaturas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].candidato").value("Ana"))
                .andExpect(jsonPath("$[0].cvNombre").value("cv-ana.pdf"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("PATCH /candidaturas/{id}/estado como EMPLEADO devuelve 403")
    void valorar_comoEmpleado_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/v1/candidaturas/30/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"SELECCIONADA\"}"))
                .andExpect(status().isForbidden());
        verify(jobPostingService, never()).valorar(anyLong(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PATCH /candidaturas/{id}/estado sin estado devuelve 400")
    void valorar_sinEstado_devuelve400() throws Exception {
        mockMvc.perform(patch("/api/v1/candidaturas/30/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comentario\":\"Algo\"}"))
                .andExpect(status().isBadRequest());
        verify(jobPostingService, never()).valorar(anyLong(), any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PATCH /ofertas/{id}/estado con la authority llega al servicio")
    void cambiarEstado_comoGestor_devuelve200() throws Exception {
        when(jobPostingService.cambiarEstado(eq(5L), any(), any())).thenReturn(oferta());

        mockMvc.perform(patch("/api/v1/ofertas/5/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ABIERTA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABIERTA"));
    }
}
