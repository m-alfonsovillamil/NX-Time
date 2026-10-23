package com.nxtime.nxtime.dto.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.dto.PasswordResetRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Las contraseñas demasiado comunes (ADR 051). */
class NoEsComunTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();
    private final NoEsComun.Validador comun = new NoEsComun.Validador();

    @Test
    @DisplayName("La lista tiene unas diez mil, todas de 8 o más")
    void laLista() {
        assertThat(NoEsComun.Lista.cuantas()).isGreaterThanOrEqualTo(10_000);
    }

    @Test
    @DisplayName("Rechaza las de la lista, con mayúsculas o sin ellas, también las añadidas en español")
    void rechaza() {
        assertThat(comun.isValid("password123", null)).isFalse();
        assertThat(comun.isValid("Password123", null)).isFalse();
        assertThat(comun.isValid("QWERTYUIOP", null)).isFalse();
        assertThat(comun.isValid("Contraseña123", null)).isFalse();
        assertThat(comun.isValid("nxtime2026", null)).isFalse();
    }

    @Test
    @DisplayName("Deja pasar una frase cualquiera")
    void dejaPasar() {
        assertThat(comun.isValid("el gato de mi abuela come pipas", null)).isTrue();
        assertThat(comun.isValid("Tr3s-tristes-tigres", null)).isTrue();
        assertThat(comun.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("Está en las tres formas de elegir contraseña: registrar, cambiarla y con un código")
    void enLosTresSitios() {
        assertThat(validador.validate(new ChangePasswordRequest("la de antes", "iloveyou1")))
                .anyMatch(error -> error.getMessage().contains("de las más usadas"));
        assertThat(validador.validate(new PasswordResetRequest("ana@empresa.test", "123456", "iloveyou1")))
                .anyMatch(error -> error.getMessage().contains("de las más usadas"));
        assertThat(validador.validate(new RegisterManagerRequest("Empresa SL", "Ana", "López", "ana@empresa.test",
                "iloveyou1", null, true)))
                .anyMatch(error -> error.getMessage().contains("de las más usadas"));
    }
}
