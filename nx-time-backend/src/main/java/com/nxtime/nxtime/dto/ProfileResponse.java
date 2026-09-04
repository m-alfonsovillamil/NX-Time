package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Role;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * La ficha completa de una persona: lo que ella misma ve en su perfil, y
 * lo que ve un gestor al consultar el de otro.
 *
 * Junta los datos personales (que edita la propia persona) con los
 * laborales (que solo puede tocar RRHH) porque quien mira un perfil los
 * quiere juntos; **quién puede cambiar cada cosa lo decide el endpoint,
 * no este DTO**: {@code PATCH /perfil} solo escribe los personales.
 *
 * @param nombreCompleto nombre y apellidos ya unidos, para que ningún
 *   cliente tenga que concatenarlos a su manera. Cuando no hay
 *   apellidos es solo el nombre, sin espacio suelto detrás.
 * @param iniciales dos letras para el avatar cuando no hay foto. Se
 *   calculan aquí y no en cada cliente: con un solo nombre es una letra,
 *   y ese "si no hay apellidos, coge la primera del nombre" es
 *   exactamente el tipo de regla que se implementa distinta en cada
 *   sitio.
 * @param diasVacaciones los EFECTIVOS del año en curso, con el valor por
 *   defecto ya aplicado (ver SimpleEmployeeDTO).
 */
public record ProfileResponse(
        long id,
        String email,
        String nombre,
        String apellidos,
        String nombreCompleto,
        String iniciales,
        LocalDate fechaNacimiento,
        String puesto,
        Long departamentoId,
        String departamentoNombre,
        Role rol,
        boolean activo,
        BigDecimal horasSemanales,
        int diasVacaciones
) {
}
