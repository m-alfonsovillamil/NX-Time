package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.HolidayScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Alta y edición de un festivo de la empresa (Fase C).
 *
 * No lleva {@code empresaId}: es siempre la de quien hace la petición.
 * Aceptarlo del cliente sería dar a elegir a qué empresa meterle un
 * festivo, exactamente el agujero multi-tenant que el ADR 006 obliga a
 * cerrar a mano en cada endpoint.
 *
 * {@code ambito} no admite {@link HolidayScope#NACIONAL}, y por eso no
 * se valida con una anotación: lo comprueba el servicio, que es quien
 * puede explicar por qué en el 400 ("los nacionales los pone el
 * sistema"). Una restricción declarativa daría el mismo rechazo con un
 * mensaje genérico sobre un valor no permitido.
 *
 * Que la fecha no esté en el pasado <b>no se valida</b>, a propósito: un
 * gestor tiene que poder rellenar el calendario del año en curso en
 * julio, y los festivos de enero ya pasaron. El pasado también se
 * consulta -- un informe de marzo necesita saber qué días fueron
 * festivos en marzo.
 */
public record HolidayRequest(

        @NotNull(message = "La fecha del festivo es obligatoria.")
        LocalDate fecha,

        @NotBlank(message = "La descripción del festivo es obligatoria.")
        @Size(max = 200, message = "La descripción no puede pasar de 200 caracteres.")
        String descripcion,

        @NotNull(message = "El ámbito del festivo es obligatorio.")
        HolidayScope ambito
) {
}
