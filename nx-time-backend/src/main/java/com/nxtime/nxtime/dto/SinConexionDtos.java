package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.FichajeRecibido;
import com.nxtime.nxtime.domain.TimeEntryAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lo que manda la app al volver la red con los fichajes que hizo sin conexión,
 * y lo que se le contesta (ADR 054).
 */
public final class SinConexionDtos {

    private SinConexionDtos() {
    }

    /** Los fichajes pendientes, en el orden en que se hicieron. */
    // Los nombres del contrato van con «SinConexion»: «Fichaje» y «Resultado» a
    // secas ya los usan otros esquemas (el de «Mis datos»), y springdoc pisaría
    // uno con otro.
    @Schema(name = "EnvioSinConexionRequest")
    public record EnvioRequest(
            @NotNull
            @Size(min = 1, max = 50, message = "Se mandan entre 1 y 50 fichajes cada vez.")
            List<@Valid @NotNull Fichaje> fichajes
    ) {
    }

    /**
     * Un fichaje hecho sin conexión.
     *
     * @param idCliente          lo genera el móvil al fichar; si se vuelve a mandar, se reconoce
     * @param horaDelDispositivo la hora del reloj del móvil cuando se fichó
     * @param msDesdeElFichaje   cuánto ha pasado desde que se fichó, medido con el
     *                           reloj del móvil que no se puede cambiar a mano
     *                           ({@code elapsedRealtime}). Null si el móvil se
     *                           ha reiniciado desde entonces: esa cuenta empieza
     *                           de cero al arrancar
     */
    @Schema(name = "FichajeSinConexion")
    public record Fichaje(
            @NotNull UUID idCliente,
            @NotNull TimeEntryAction tipo,
            Long proyectoId,
            @NotNull Instant horaDelDispositivo,
            @PositiveOrZero Long msDesdeElFichaje
    ) {
    }

    /**
     * Qué se hizo con cada uno, en el mismo orden.
     *
     * @param yaRecibido ya había llegado antes: esto es lo que se contestó entonces
     * @param motivo     por qué se rechazó, o qué hay que revisar
     * @param hora       la hora con la que se aplicó, o null si se rechazó
     */
    @Schema(name = "ResultadoSinConexion")
    public record Resultado(
            UUID idCliente,
            @Schema(description = "APLICADO, A_REVISAR (llegó pasadas 72 h: lo tiene que dar por bueno un gestor) "
                    + "o RECHAZADO")
            FichajeRecibido.Resultado resultado,
            boolean yaRecibido,
            String motivo,
            Long registroId,
            Instant hora
    ) {
    }

    /**
     * @param jornada la jornada abierta después de aplicarlos, o null si no
     *                queda ninguna: con esto la app pone la pantalla al día
     */
    @Schema(name = "EnvioSinConexionResponse")
    public record EnvioResponse(List<Resultado> resultados, TimeEntryResponse jornada) {
    }
}
