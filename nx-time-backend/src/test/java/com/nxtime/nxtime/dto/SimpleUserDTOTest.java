package com.nxtime.nxtime.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los listados de equipo dicen quién es alguien con nombre y apellidos
 * (revisión del 15/11/2026): con solo el nombre de pila, dos «Javier» de la
 * misma empresa no se distinguían al aprobarles algo.
 */
class SimpleUserDTOTest {

    @Test
    @DisplayName("lleva nombre y apellidos")
    void nombreYApellidos() {
        User javier = User.builder().nombre("Javier").apellidos("López Serna").build();

        assertThat(SimpleUserDTO.de(javier).nombre()).isEqualTo("Javier López Serna");
    }

    @Test
    @DisplayName("sin apellidos, solo el nombre y sin espacio detrás")
    void sinApellidos() {
        User raul = User.builder().nombre("Raúl").apellidos("  ").build();

        assertThat(SimpleUserDTO.de(raul).nombre()).isEqualTo("Raúl");
    }

    @Test
    @DisplayName("sin persona (un aprobador que aún no existe), null")
    void sinPersona() {
        assertThat(SimpleUserDTO.de(null)).isNull();
    }
}
