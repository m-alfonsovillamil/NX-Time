package com.nxtime.nxtime.dto.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.dto.PasswordResetRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CabeEnBcryptTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("72 caracteres con ñ no caben: son 144 bytes, y lo que pasa de 72 BCrypt no lo distingue")
    void cuentaBytesYNoCaracteres() {
        String setentaYDosEnes = "ñ".repeat(72);
        String treintaYSeisEnes = "ñ".repeat(36);

        assertThat(validador.validate(new ChangePasswordRequest("vieja-123", setentaYDosEnes))).isNotEmpty();
        assertThat(validador.validate(new ChangePasswordRequest("vieja-123", treintaYSeisEnes))).isEmpty();
        assertThat(validador.validate(new ChangePasswordRequest("vieja-123", "a".repeat(72)))).isEmpty();
        assertThat(validador.validate(new ChangePasswordRequest("vieja-123", "a".repeat(73)))).isNotEmpty();
    }

    @Test
    @DisplayName("Las tres puertas por las que entra una contraseña nueva la comprueban")
    void lasTresPuertas() {
        String larga = "é".repeat(40);

        assertThat(validador.validate(new ChangePasswordRequest("vieja-123", larga))).isNotEmpty();
        assertThat(validador.validate(new PasswordResetRequest("ana@nxtime.test", "123456", larga))).isNotEmpty();
        assertThat(validador.validate(new RegisterManagerRequest("Empresa", "Ana", "Pruebas", "ana@nxtime.test", larga, null)))
                .isNotEmpty();
    }

    /**
     * Por qué hace falta, con la versión de Spring Security que hay: deja
     * escrito qué hace BCrypt con lo que pasa de 72 bytes. Si un día cambia,
     * este test lo dice.
     */
    @Test
    @DisplayName("BCrypt no admite más de 72 bytes: o lo rechaza o no lo distingue")
    void loQueHaceBcrypt() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        String setentaYDos = "a".repeat(72);
        String hash = bcrypt.encode(setentaYDos);

        boolean rechaza;
        boolean distingue = false;
        try {
            distingue = !bcrypt.matches(setentaYDos + "b", hash);
            rechaza = false;
        } catch (IllegalArgumentException e) {
            rechaza = true;
        }
        assertThat(rechaza || !distingue).isTrue();
    }
}
