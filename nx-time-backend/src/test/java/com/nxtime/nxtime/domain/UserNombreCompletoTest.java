package com.nxtime.nxtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El nombre que va en los documentos: el registro de jornada, el Excel, el ZIP para la Inspección. */
class UserNombreCompletoTest {

    private static User persona(String nombre, String apellidos) {
        return User.builder().nombre(nombre).apellidos(apellidos).build();
    }

    @Test
    @DisplayName("Con apellidos, nombre y apellidos")
    void conApellidos() {
        assertThat(persona("Ana", "Fernández Gil").nombreCompleto()).isEqualTo("Ana Fernández Gil");
    }

    @Test
    @DisplayName("Sin apellidos, o en blanco, solo el nombre y sin espacio al final")
    void sinApellidos() {
        assertThat(persona("Ana", null).nombreCompleto()).isEqualTo("Ana");
        assertThat(persona("Ana", "  ").nombreCompleto()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Los espacios sobrantes de los apellidos no se cuelan en el documento")
    void sinEspaciosDeMas() {
        assertThat(persona("Ana", " Gil ").nombreCompleto()).isEqualTo("Ana Gil");
    }
}
