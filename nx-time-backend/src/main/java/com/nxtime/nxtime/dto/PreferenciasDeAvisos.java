package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.CategoriaDeAviso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Cómo le llegan los avisos a cada persona (ADR 059; plan N, paso N9). */
public final class PreferenciasDeAvisos {

    private PreferenciasDeAvisos() {
    }

    /**
     * Una categoría, con lo que elige esa persona. La campana de la aplicación
     * siempre la recibe; aquí solo se decide el correo y el push.
     *
     * @param configurable falso en las obligatorias, que llegan siempre por todo.
     */
    public record PreferenciaDeAviso(
            CategoriaDeAviso categoria,
            String etiqueta,
            String descripcion,
            boolean correo,
            boolean push,
            boolean configurable) {
    }

    public record CambioDePreferencia(
            @NotNull(message = "Falta la categoría.") CategoriaDeAviso categoria,
            boolean correo,
            boolean push) {
    }

    public record GuardarPreferenciasRequest(
            @Schema(description = "Las categorías que se cambian; las que no van, se quedan como estaban")
            @NotNull(message = "Faltan las preferencias.")
            @Size(max = 20, message = "Demasiadas preferencias.")
            List<@Valid CambioDePreferencia> preferencias) {
    }
}
