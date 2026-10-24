package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * El resultado de importar una plantilla (ADR 052). Primero se simula: se lee
 * y se comprueba todo sin crear nada. Después se importa de verdad, y entonces
 * o entran todas las filas o ninguna.
 *
 * @param invitaciones cómo quedan los correos de alta tras crear a estas
 *   personas: los que salen ya y los que esperan a que haya cupo
 */
@Schema(description = "Lo que ha salido de leer (y, si no es una simulación, importar) un fichero de plantilla")
public record ImportacionPlantillaResponse(
        @Schema(description = "true si no se ha creado nada: solo se ha comprobado") boolean simulada,
        @Schema(description = "Personas en el fichero") int filas,
        @Schema(description = "Filas con algún error") int conErrores,
        @Schema(description = "Personas creadas (0 al simular)") int creadas,
        @Schema(description = "Columnas del fichero que no se han reconocido y se han ignorado")
        List<String> columnasIgnoradas,
        List<FilaDeImportacion> detalle,
        @Schema(description = "Tras importar: los correos de alta. Null al simular") EstadoInvitacionesResponse invitaciones) {
}
