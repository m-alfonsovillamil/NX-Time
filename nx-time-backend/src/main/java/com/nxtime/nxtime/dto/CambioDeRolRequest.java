package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** El rol nuevo de una persona de la empresa (ADR 052). */
@Schema(description = "El rol nuevo")
public record CambioDeRolRequest(
        @Schema(description = "EMPLEADO, GESTOR, RRHH o ADMIN")
        @NotNull(message = "Elige el rol.")
        Role rol) {
}
