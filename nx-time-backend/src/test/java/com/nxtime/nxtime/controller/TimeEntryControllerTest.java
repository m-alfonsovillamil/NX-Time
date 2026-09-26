package com.nxtime.nxtime.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.SimpleUserDTO;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryResponse;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.service.AddedPauseService;
import com.nxtime.nxtime.service.CorrectionService;
import com.nxtime.nxtime.service.TimeEntryService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de TimeEntryController: solo la capa web (rutas,
 * authorities de {@code @PreAuthorize}, validación de {@code @Valid} y el
 * mapeo a ProblemDetail vía GlobalExceptionHandler). La lógica de negocio
 * real (la máquina de estados) la cubre {@link
 * com.nxtime.nxtime.service.impl.TimeEntryServiceImplTest}.
 */
@NxTimeWebMvcTest(TimeEntryController.class)
@Import(WebMvcTestSecurityConfig.class)
class TimeEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TimeEntryService timeEntryService;

    @MockitoBean
    private CorrectionService correctionService;

    @MockitoBean
    private AddedPauseService addedPauseService;

    @MockitoBean
    private TimeEntryMapper timeEntryMapper;

    @MockitoBean
    private com.nxtime.nxtime.service.AllocationEditService allocationEditService;

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:escribir")
    @DisplayName("POST /fichaje con la authority correcta y cuerpo válido devuelve 200")
    void registerTimeEntry_conAuthorityYCuerpoValido_devuelve200() throws Exception {
        TimeEntry entry = TimeEntry.builder().id(1L).build();
        when(timeEntryService.registerTimeEntry(eq("empleado@nxtime.test"), any())).thenReturn(entry);
        when(timeEntryMapper.toResponse(entry))
                .thenReturn(new TimeEntryResponse(1L, Instant.now(), null, false, 0, 0));

        mockMvc.perform(post("/api/v1/fichaje")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INICIO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("POST /fichaje sin la authority 'fichaje:escribir' devuelve 403")
    void registerTimeEntry_sinAuthorityDeEscritura_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/fichaje")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INICIO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:escribir")
    @DisplayName("POST /fichaje con 'tipo' ausente devuelve 400 (Bean Validation)")
    void registerTimeEntry_sinTipo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/fichaje")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/activo sin jornada abierta devuelve 204")
    void getActiveTimeEntry_sinJornadaAbierta_devuelve204() throws Exception {
        when(timeEntryService.getActiveTimeEntry("empleado@nxtime.test")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/fichaje/activo")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/historial devuelve una página: primera, de 50, con los totales")
    void getHistory_conAuthority_devuelveUnaPagina() throws Exception {
        TimeEntry fichaje = TimeEntry.builder().id(9L).build();
        when(timeEntryService.getHistory(eq("empleado@nxtime.test"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fichaje), PageRequest.of(0, 50), 51));
        when(timeEntryMapper.toResponse(fichaje)).thenReturn(new TimeEntryResponse(9L, Instant.now(), null, false, 0, 0));

        mockMvc.perform(get("/api/v1/fichaje/historial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(9))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(50))
                .andExpect(jsonPath("$.totalElementos").value(51))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.hayMas").value(true));
        verify(timeEntryService).getHistory("empleado@nxtime.test", PageRequest.of(0, 50));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/historial pasa al servicio la página y el tamaño pedidos")
    void getHistory_paginaPedida() throws Exception {
        when(timeEntryService.getHistory(eq("empleado@nxtime.test"), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(3, 20)));

        mockMvc.perform(get("/api/v1/fichaje/historial").param("pagina", "3").param("tamano", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayMas").value(false));
        verify(timeEntryService).getHistory("empleado@nxtime.test", PageRequest.of(3, 20));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/historial con página negativa o tamaño fuera de 1..200 es un 400, no un recorte")
    void getHistory_paginaInvalida_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/fichaje/historial").param("pagina", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/fichaje/historial").param("tamano", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/fichaje/historial").param("tamano", "201")).andExpect(status().isBadRequest());
        verify(timeEntryService, org.mockito.Mockito.never()).getHistory(any(), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:escribir")
    @DisplayName("POST /fichaje con proyectoId lo pasa al servicio; sin él, también vale")
    void registerTimeEntry_conProyecto() throws Exception {
        TimeEntry entry = TimeEntry.builder().id(1L).build();
        when(timeEntryService.registerTimeEntry(eq("empleado@nxtime.test"), any())).thenReturn(entry);
        when(timeEntryMapper.toResponse(entry)).thenReturn(new TimeEntryResponse(1L, Instant.now(), null, false, 0, 0));

        mockMvc.perform(post("/api/v1/fichaje").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INICIO\",\"proyectoId\":7}"))
                .andExpect(status().isOk());

        verify(timeEntryService).registerTimeEntry("empleado@nxtime.test",
                new com.nxtime.nxtime.dto.TimeEntryRequest(com.nxtime.nxtime.domain.TimeEntryAction.INICIO, 7L));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("POST /fichaje/{id}/proyecto sin 'fichaje:escribir' devuelve 403; GET /fichaje/proyectos con 'fichaje:leer' 200")
    void proyectos_permisos() throws Exception {
        when(timeEntryService.proyectosParaFichar("empleado@nxtime.test"))
                .thenReturn(new com.nxtime.nxtime.dto.ClockProjectsResponse(List.of(), null));
        mockMvc.perform(get("/api/v1/fichaje/proyectos")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/fichaje/1/proyecto").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proyectoId\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:escribir")
    @DisplayName("POST /fichaje/{id}/proyecto llega al servicio; sin proyectoId válido, 400")
    void cambiarProyecto() throws Exception {
        when(timeEntryService.cambiarProyecto("empleado@nxtime.test", 1L, 7L))
                .thenReturn(new com.nxtime.nxtime.dto.ClockProjectsResponse(List.of(), null));
        mockMvc.perform(post("/api/v1/fichaje/1/proyecto").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proyectoId\":7}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/fichaje/1/proyecto").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proyectoId\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/hoy dice si es laborable y, si no, por qué")
    void getHoy() throws Exception {
        when(timeEntryService.motivoNoLaborableHoy("empleado@nxtime.test")).thenReturn(
                Optional.of(new com.nxtime.nxtime.service.NonWorkingDayService.Motivo("Festivo: Navidad", false)));
        mockMvc.perform(get("/api/v1/fichaje/hoy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laborable").value(false))
                .andExpect(jsonPath("$.motivo").value("Festivo: Navidad"));

        when(timeEntryService.motivoNoLaborableHoy("empleado@nxtime.test")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/fichaje/hoy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laborable").value(true));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/historial con las dos fechas filtra por periodo")
    void getHistory_conFechas_usaElPeriodo() throws Exception {
        when(timeEntryService.getHistory(eq("empleado@nxtime.test"),
                eq(java.time.LocalDate.of(2026, 9, 1)), eq(java.time.LocalDate.of(2026, 9, 30)), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 50)));

        mockMvc.perform(get("/api/v1/fichaje/historial").param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(timeEntryService).getHistory("empleado@nxtime.test",
                java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 30), PageRequest.of(0, 50));
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/historial con una sola fecha, o mal escrita, devuelve 400")
    void getHistory_fechasIncompletasOMalEscritas_devuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/fichaje/historial").param("desde", "2026-09-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/fichaje/historial").param("desde", "01/09/2026").param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "empleado@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("GET /fichaje/gestor/historial sin 'fichaje:leer:equipo' devuelve 403 (un EMPLEADO no ve al equipo)")
    void getTeamHistory_sinAuthorityDeEquipo_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/fichaje/gestor/historial")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gestor@nxtime.test", authorities = "fichaje:leer:equipo")
    @DisplayName("GET /fichaje/gestor/historial con la authority de equipo devuelve 200")
    void getTeamHistory_conAuthorityDeEquipo_devuelve200() throws Exception {
        when(timeEntryService.getTeamHistory(eq("gestor@nxtime.test"), any(Pageable.class)))
                .thenReturn(Page.<TeamTimeEntryDTO>empty(PageRequest.of(1, 30)));

        mockMvc.perform(get("/api/v1/fichaje/gestor/historial").param("pagina", "1").param("tamano", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray());
        verify(timeEntryService).getTeamHistory("gestor@nxtime.test", PageRequest.of(1, 30));
    }

    // ------------------------------------------------------------------
    // Pedir una corrección (Fase E)
    // ------------------------------------------------------------------
    // El PATCH /fichaje/{id} que corregía en el acto ya no existe: lo
    // sustituye este POST, que solo PIDE la corrección. El código de
    // respuesta es lo que distingue los dos desenlaces.

    @Test
    @WithMockSecurityUser(email = "empleado@nxtime.test", rol = Role.EMPLEADO)
    @DisplayName("POST /fichaje/{id}/correcciones devuelve 202: pedida, pero el fichaje NO ha cambiado")
    void solicitarCorreccion_quedaPendiente_devuelve202() throws Exception {
        when(correctionService.solicitar(eq(5L), any(), any()))
                .thenReturn(respuesta(CorrectionStatus.PENDIENTE));

        mockMvc.perform(post("/api/v1/fichaje/5/correcciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaEntrada\":\"2026-01-01T08:00:00Z\","
                                + "\"horaSalida\":\"2026-01-01T17:00:00Z\",\"motivo\":\"Fichaje olvidado\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @WithMockSecurityUser(email = "admin@nxtime.test", rol = Role.ADMIN)
    @DisplayName("Si se auto-aprueba devuelve 200, porque el fichaje SÍ ha cambiado")
    void solicitarCorreccion_autoAprobada_devuelve200() throws Exception {
        when(correctionService.solicitar(eq(5L), any(), any()))
                .thenReturn(respuesta(CorrectionStatus.APROBADA));

        // 200 y 202 no son un detalle: con 202 el fichaje sigue como
        // estaba y con 200 ya se ha corregido. Es lo único que distingue
        // los dos casos para quien llama.
        mockMvc.perform(post("/api/v1/fichaje/5/correcciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaEntrada\":\"2026-01-01T08:00:00Z\","
                                + "\"horaSalida\":\"2026-01-01T17:00:00Z\",\"motivo\":\"Fichaje olvidado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
    }

    @Test
    @WithMockUser(username = "nadie@nxtime.test", authorities = "fichaje:leer")
    @DisplayName("Sin 'correccion:solicitar' devuelve 403")
    void solicitarCorreccion_sinAuthority_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/fichaje/5/correcciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaEntrada\":\"2026-01-01T08:00:00Z\","
                                + "\"horaSalida\":\"2026-01-01T17:00:00Z\",\"motivo\":\"Da igual\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockSecurityUser(email = "empleado@nxtime.test", rol = Role.EMPLEADO)
    @DisplayName("Sin motivo devuelve 400: es lo que lee quien tiene que aprobar")
    void solicitarCorreccion_sinMotivo_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/fichaje/5/correcciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaEntrada\":\"2026-01-01T08:00:00Z\",\"horaSalida\":\"2026-01-01T17:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    private CorrectionResponse respuesta(CorrectionStatus estado) {
        return new CorrectionResponse(
                1L, 5L,
                new SimpleUserDTO("Empleado"), new SimpleUserDTO("Empleado"),
                Instant.now(), Instant.now(), Instant.now(), Instant.now(),
                null, null,
                "Fichaje olvidado", estado,
                null, null, null, null, Instant.now(),
                false, false, java.util.List.of());
    }
}
