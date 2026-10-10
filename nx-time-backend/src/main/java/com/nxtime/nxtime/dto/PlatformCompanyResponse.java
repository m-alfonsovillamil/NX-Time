package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Una empresa en la lista del panel de plataforma (ADR 040): quién es y si se
 * usa. Solo cifras.
 */
@Schema(description = "Una empresa de la instalación, con sus cifras de uso")
public record PlatformCompanyResponse(
        long id,
        String nombre,
        @Schema(description = "Nombre IANA de su zona horaria", example = "Europe/Madrid")
        String zonaHoraria,
        @Schema(description = "Cuándo se dio de alta. Null en las anteriores a octubre de 2026 de las que "
                + "no quedaba rastro; en las demás anteriores a esa fecha es una estimación")
        Instant creadaEn,
        long empleadosActivos,
        long empleadosDeBaja,
        @Schema(description = "Quien la registró todavía no ha confirmado su correo: nadie ha podido entrar")
        boolean registroSinConfirmar,
        @Schema(description = "La entrada del fichaje más reciente. Null si no ha fichado nadie nunca")
        Instant ultimoFichaje,
        long fichajesEn7Dias,
        long fichajesEn30Dias,
        @Schema(description = "Personas distintas que han fichado en los últimos 30 días")
        long personasQueFichan,
        @Schema(description = "La última vez que alguien suyo usó la web o la app. Null si nadie ha entrado")
        Instant ultimaSesion
) {
}
