package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Una persona del fichero de plantilla, tal como se ha entendido (ADR 052), y
 * lo que le impide darse de alta. Sin errores, se crearía así.
 */
@Schema(description = "Una fila del fichero de plantilla")
public record FilaDeImportacion(
        @Schema(description = "Su número en el fichero (la de los títulos es la 1)") int fila,
        String nombre,
        String apellidos,
        String email,
        @Schema(description = "EMPLEADO si la columna no está o va vacía") Role rol,
        @Schema(description = "El departamento, ya con el nombre que tiene en la empresa") String departamento,
        BigDecimal horasSemanales,
        Integer diasVacaciones,
        @Schema(description = "Lo que impide darla de alta. Vacío: se puede") List<String> errores) {
}
