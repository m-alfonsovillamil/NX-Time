package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Una empresa vista desde el panel de plataforma (ADR 040).
 *
 * <b>Cifras, y el contacto de sus ADMIN.</b> Ni un fichaje ni un dato de
 * ningún empleado: quien presta el servicio necesita saber si una empresa lo
 * usa y a quién escribir, no qué hace su plantilla.
 */
@Schema(description = "El detalle de una empresa de la instalación: cifras de uso y el contacto de sus administradores")
public record PlatformCompanyDetailResponse(
        long id,
        String nombre,
        @Schema(description = "Nombre IANA de su zona horaria", example = "Europe/Madrid")
        String zonaHoraria,
        @Schema(description = "Cuándo se dio de alta. Null en las anteriores a octubre de 2026 de las que "
                + "no quedaba rastro; en las demás anteriores a esa fecha es una estimación")
        Instant creadaEn,
        long empleadosActivos,
        long empleadosDeBaja,
        @Schema(description = "La plantilla rol por rol; siempre los cuatro, aunque no haya nadie")
        List<Plantilla> plantilla,
        @Schema(description = "A quién escribir: sus ADMIN en activo")
        List<Administrador> administradores,
        Fichajes fichajes,
        Sesiones sesiones,
        long cuentasConGoogle,
        long cuentasConMicrosoft,
        @Schema(description = "Kioscos sin revocar")
        long kioscos,
        @Schema(description = "Móviles y navegadores con los avisos push activados")
        long dispositivosPush,
        long departamentos,
        @Schema(description = "Proyectos en activo")
        long proyectos,
        @Schema(description = "Lo que ocupan sus adjuntos (CV y fotos), en bytes")
        long bytesDeAdjuntos,
        @Schema(description = "Solicitudes de borrado de datos que aún no se han resuelto")
        long borradosPendientes,
        @Schema(description = "Filas suyas en la traza de auditoría de fichajes")
        long movimientosDeAuditoria
) {

    public record Plantilla(Role rol, long activos, long deBaja) {
    }

    /**
     * @param correoSinConfirmar registró la empresa y aún no ha canjeado el código: no ha podido entrar
     */
    public record Administrador(String nombre, String email, boolean correoSinConfirmar) {
    }

    /**
     * Sin contar los anulados.
     *
     * @param ultimo null si no ha fichado nadie nunca
     */
    public record Fichajes(Instant ultimo, long total, long en7Dias, long en30Dias, long personasEn30Dias) {
    }

    /**
     * @param ultima      null si nadie ha entrado nunca
     * @param webEn30Dias sesiones abiertas desde la web en los últimos 30 días
     * @param appEn30Dias y desde la app
     */
    public record Sesiones(Instant ultima, long webEn30Dias, long appEn30Dias) {
    }
}
