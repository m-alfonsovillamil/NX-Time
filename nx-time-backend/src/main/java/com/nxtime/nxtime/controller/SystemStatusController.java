package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.SystemStatusResponse;
import com.nxtime.nxtime.service.TaskMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado de las tareas nocturnas (paso 5 del piloto).
 *
 * <b>Público y fuera de {@code /api/v1}</b>: lo consulta un workflow de
 * GitHub que no tiene sesión, igual que Render consulta
 * {@code /actuator/health}.
 *
 * <b>Y no es un indicador del health de Actuator</b>, que habría sido lo
 * obvio: {@code /actuator/health} es el health check de Render, y una
 * tarea perdida lo pondría en 503 y Render reiniciaría un servicio que
 * está perfectamente. Ya pasó una vez con el servidor de correo (ver
 * {@code management.health.mail} en application.yml).
 */
@RestController
@RequestMapping("/estado")
@Tag(name = "Estado", description = "Si las tareas nocturnas han corrido. Público y sin datos de ninguna empresa.")
public class SystemStatusController {

    private final TaskMonitorService taskMonitorService;

    public SystemStatusController(TaskMonitorService taskMonitorService) {
        this.taskMonitorService = taskMonitorService;
    }

    @Operation(summary = "¿Han corrido las tareas nocturnas?",
            description = "200 si cada tarea programada terminó bien después de la última vez que le tocaba, "
                    + "503 si alguna no. Lo consulta a diario el workflow tareas-nocturnas.yml: un 503 lo pone "
                    + "en rojo y GitHub avisa por correo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Todas corrieron y terminaron bien",
                    content = @Content(schema = @Schema(implementation = SystemStatusResponse.class))),
            @ApiResponse(responseCode = "503", description = "Alguna no corrió, falló o se quedó a medias",
                    content = @Content(schema = @Schema(implementation = SystemStatusResponse.class)))
    })
    @GetMapping("/tareas")
    public ResponseEntity<SystemStatusResponse> tareas() {
        SystemStatusResponse estado = taskMonitorService.estado();
        return ResponseEntity.status(estado.ok() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(estado);
    }
}
