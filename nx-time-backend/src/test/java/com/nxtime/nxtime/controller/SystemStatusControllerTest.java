package com.nxtime.nxtime.controller;

import static com.nxtime.nxtime.domain.ScheduledTask.CIERRE_JORNADAS;
import static com.nxtime.nxtime.domain.ScheduledTask.HORAS_EXTRA;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.dto.SystemStatusResponse;
import com.nxtime.nxtime.dto.SystemStatusResponse.TaskStatus;
import com.nxtime.nxtime.service.TaskMonitorService;
import com.nxtime.nxtime.web.support.NxTimeWebMvcTest;
import com.nxtime.nxtime.web.support.WebMvcTestSecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} de SystemStatusController (paso 5 del piloto).
 *
 * Lo que se fija: que responde SIN token (lo consulta un workflow de
 * GitHub), que el código HTTP refleja el estado -- es lo único que mira
 * {@code curl --fail} -- y que no saca el detalle de las ejecuciones.
 */
@NxTimeWebMvcTest(SystemStatusController.class)
@Import(WebMvcTestSecurityConfig.class)
class SystemStatusControllerTest {

    private static final Instant ANOCHE = Instant.parse("2026-09-12T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskMonitorService taskMonitorService;

    private static TaskStatus tarea(ScheduledTask tarea, boolean ok) {
        return new TaskStatus(tarea, ANOCHE, ok, ANOCHE,
                ok ? ScheduledTaskResult.OK : ScheduledTaskResult.ERROR, ok ? ANOCHE : null);
    }

    @Test
    @DisplayName("GET /estado/tareas sin token y con todo en orden devuelve 200")
    void tareas_todoEnOrden_devuelve200SinToken() throws Exception {
        when(taskMonitorService.estado()).thenReturn(new SystemStatusResponse(true,
                List.of(tarea(CIERRE_JORNADAS, true), tarea(HORAS_EXTRA, true))));

        mockMvc.perform(get("/estado/tareas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.tareas[0].tarea").value("CIERRE_JORNADAS"))
                .andExpect(jsonPath("$.tareas[1].tarea").value("HORAS_EXTRA"));
    }

    @Test
    @DisplayName("GET /estado/tareas con una tarea fallida devuelve 503, que es lo que pone en rojo el workflow")
    void tareas_unaFallida_devuelve503() throws Exception {
        when(taskMonitorService.estado()).thenReturn(new SystemStatusResponse(false,
                List.of(tarea(CIERRE_JORNADAS, true), tarea(HORAS_EXTRA, false))));

        mockMvc.perform(get("/estado/tareas"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.tareas[1].ok").value(false))
                .andExpect(jsonPath("$.tareas[1].ultimoResultado").value("ERROR"));
    }

    @Test
    @DisplayName("GET /estado/tareas no expone el detalle de las ejecuciones: es público")
    void tareas_noExponeElDetalle() throws Exception {
        when(taskMonitorService.estado()).thenReturn(new SystemStatusResponse(true,
                List.of(tarea(CIERRE_JORNADAS, true), tarea(HORAS_EXTRA, true))));

        mockMvc.perform(get("/estado/tareas"))
                .andExpect(jsonPath("$.tareas[0].detalle").doesNotExist());
    }
}
