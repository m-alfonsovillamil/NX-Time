package com.nxtime.nxtime.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.ScheduleExceptionType;
import com.nxtime.nxtime.dto.ScheduleAssignmentResponse;
import com.nxtime.nxtime.dto.ScheduleExceptionResponse;
import com.nxtime.nxtime.dto.ScheduleTemplateResponse;
import com.nxtime.nxtime.dto.TeamScheduleEntryResponse;
import com.nxtime.nxtime.dto.TheoreticalDayResponse;
import com.nxtime.nxtime.service.JornadaTeoricaService.Origen;
import com.nxtime.nxtime.service.ScheduleService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import com.nxtime.nxtime.web.support.WithMockSecurityUser;
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
 * {@code @WebMvcTest} de ScheduleController (Fase B1).
 *
 * Lo que se fija aquí es el reparto de permisos: <b>ver el horario propio lo
 * puede todo el mundo</b> —saber a qué hora te toca entrar es parte de tu
 * jornada—, y todo lo demás empieza en GESTOR. Que las reglas de fechas y de
 * empresa se cumplan lo prueba {@code CuadranteIT} contra la base.
 */
@NxTimeWebMvcTest(ScheduleController.class)
@Import(WebMvcTestSecurityConfig.class)
class ScheduleControllerTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleService scheduleService;

    private static TheoreticalDayResponse dia() {
        return new TheoreticalDayResponse(DIA, Origen.CUADRANTE, 480, "09:00",
                List.of(new ScheduleTemplateResponse.Tramo(1, 540, 1020, "09:00", "17:00", 480, false)),
                null, "Oficina");
    }

    private static ScheduleTemplateResponse plantilla() {
        return new ScheduleTemplateResponse(3L, "Oficina", null, 2400, true, true, List.of());
    }

    private static ScheduleAssignmentResponse asignacion(String aviso) {
        return new ScheduleAssignmentResponse(9L, 10L, "Ana Prueba", 3L, "Oficina", DIA, null, false, aviso);
    }

    // ------------------------------------------------------------------
    // El horario propio: todo el mundo
    // ------------------------------------------------------------------

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /cuadrantes/mio: un EMPLEADO ve su horario, con las horas ya formateadas")
    void mio_comoEmpleado() throws Exception {
        when(scheduleService.mio(eq(DIA), eq(DIA.plusDays(6)), any())).thenReturn(List.of(dia()));

        mockMvc.perform(get("/api/v1/cuadrantes/mio")
                        .param("desde", DIA.toString()).param("hasta", DIA.plusDays(6).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origen").value("CUADRANTE"))
                .andExpect(jsonPath("$[0].entrada").value("09:00"))
                .andExpect(jsonPath("$[0].tramos[0].horaFin").value("17:00"));
    }

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("GET /cuadrantes/mio sin fechas es un 400, no un 500")
    void mio_sinFechas() throws Exception {
        mockMvc.perform(get("/api/v1/cuadrantes/mio")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Gestión: desde GESTOR
    // ------------------------------------------------------------------

    @Test
    @WithMockSecurityUser(rol = Role.EMPLEADO)
    @DisplayName("Un EMPLEADO no gestiona cuadrantes: ni plantillas, ni asignaciones, ni excepciones, ni el horario de otros")
    void empleado_noGestiona() throws Exception {
        mockMvc.perform(get("/api/v1/cuadrantes/plantillas")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/cuadrantes/plantillas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"X\",\"tramos\":[]}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/cuadrantes/asignaciones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":1,\"plantillaId\":1,\"fechaInicio\":\"2026-10-05\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/cuadrantes/excepciones").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":1,\"fecha\":\"2026-10-05\",\"tipo\":\"LIBRE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/cuadrantes/usuarios/2")
                .param("desde", DIA.toString()).param("hasta", DIA.toString())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/cuadrantes/equipo").param("fecha", DIA.toString()))
                .andExpect(status().isForbidden());

        verify(scheduleService, never()).plantillas(any());
        verify(scheduleService, never()).asignar(any(), any());
        verify(scheduleService, never()).crearExcepcion(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("POST /cuadrantes/plantillas como GESTOR crea la plantilla")
    void crearPlantilla_comoGestor() throws Exception {
        when(scheduleService.crearPlantilla(any(), any())).thenReturn(plantilla());

        mockMvc.perform(post("/api/v1/cuadrantes/plantillas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Oficina\",\"tramos\":[{\"diaSemana\":1,\"inicio\":540,\"fin\":1020}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutosSemanales").value(2400));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Un día de la semana fuera de 1..7 se rechaza antes de llegar al servicio")
    void crearPlantilla_diaInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/cuadrantes/plantillas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Oficina\",\"tramos\":[{\"diaSemana\":8,\"inicio\":540,\"fin\":1020}]}"))
                .andExpect(status().isBadRequest());
        verify(scheduleService, never()).crearPlantilla(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("PUT, GET y DELETE de plantillas como GESTOR")
    void restoDePlantillas_comoGestor() throws Exception {
        when(scheduleService.plantillas(any())).thenReturn(List.of(plantilla()));
        when(scheduleService.editarPlantilla(eq(3L), any(), any())).thenReturn(plantilla());

        mockMvc.perform(get("/api/v1/cuadrantes/plantillas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Oficina"));
        mockMvc.perform(put("/api/v1/cuadrantes/plantillas/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Oficina\",\"tramos\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/cuadrantes/plantillas/3")).andExpect(status().isNoContent());

        verify(scheduleService).borrarPlantilla(eq(3L), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("POST /cuadrantes/asignaciones devuelve el aviso si el cuadrante no suma la jornada")
    void asignar_conAviso() throws Exception {
        when(scheduleService.asignar(any(), any())).thenReturn(asignacion("La plantilla suma 30 h..."));

        mockMvc.perform(post("/api/v1/cuadrantes/asignaciones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":10,\"plantillaId\":3,\"fechaInicio\":\"2026-10-05\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").value("La plantilla suma 30 h..."));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Una asignación sin fecha de inicio es un 400")
    void asignar_sinFecha() throws Exception {
        mockMvc.perform(post("/api/v1/cuadrantes/asignaciones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":10,\"plantillaId\":3}"))
                .andExpect(status().isBadRequest());
        verify(scheduleService, never()).asignar(any(), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Cerrar, borrar y listar asignaciones como GESTOR")
    void restoDeAsignaciones_comoGestor() throws Exception {
        when(scheduleService.cerrarAsignacion(eq(9L), eq(DIA), any())).thenReturn(asignacion(null));
        when(scheduleService.asignacionesDe(eq(10L), any())).thenReturn(List.of(asignacion(null)));

        mockMvc.perform(patch("/api/v1/cuadrantes/asignaciones/9").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fechaFin\":\"2026-10-05\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/cuadrantes/asignaciones/9")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/cuadrantes/usuarios/10/asignaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plantillaNombre").value("Oficina"));

        verify(scheduleService).borrarAsignacion(eq(9L), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Excepciones y horario de otra persona como GESTOR")
    void excepcionesYHorarioDeOtro_comoGestor() throws Exception {
        ScheduleExceptionResponse libre = new ScheduleExceptionResponse(
                5L, 10L, DIA, ScheduleExceptionType.LIBRE, null, null, null, null, "Cambio de día");
        when(scheduleService.crearExcepcion(any(), any())).thenReturn(List.of(libre));
        when(scheduleService.excepcionesDe(eq(10L), any())).thenReturn(List.of(libre));
        when(scheduleService.deUnaPersona(eq(10L), eq(DIA), eq(DIA), any())).thenReturn(List.of(dia()));

        mockMvc.perform(post("/api/v1/cuadrantes/excepciones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":10,\"fecha\":\"2026-10-05\",\"tipo\":\"LIBRE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("LIBRE"));
        mockMvc.perform(get("/api/v1/cuadrantes/usuarios/10/excepciones")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/cuadrantes/excepciones/5")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/cuadrantes/usuarios/10")
                        .param("desde", DIA.toString()).param("hasta", DIA.toString()))
                .andExpect(status().isOk());

        verify(scheduleService).borrarExcepcion(eq(5L), any());
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("GET /cuadrantes/equipo con 'fichaje:leer:equipo' (GESTOR)")
    void equipo_comoGestor() throws Exception {
        when(scheduleService.equipo(eq(DIA), any()))
                .thenReturn(List.of(new TeamScheduleEntryResponse(10L, "Ana Prueba", dia())));

        mockMvc.perform(get("/api/v1/cuadrantes/equipo").param("fecha", DIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Ana Prueba"))
                .andExpect(jsonPath("$[0].dia.minutos").value(480));
    }

    @Test
    @WithMockSecurityUser(rol = Role.GESTOR)
    @DisplayName("Un id que no es número es un 400, no un 500")
    void idNoNumerico() throws Exception {
        mockMvc.perform(delete("/api/v1/cuadrantes/plantillas/abc")).andExpect(status().isBadRequest());
        verify(scheduleService, never()).borrarPlantilla(anyLong(), any());
    }
}
