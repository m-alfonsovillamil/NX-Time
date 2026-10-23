package com.nxtime.nxtime.security.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los códigos de recuperación del segundo factor (ADR 051). */
class CodigosDeRecuperacionTest {

    @Test
    @DisplayName("Son diez, distintos, con la forma abcde-23456 y sin caracteres que se confundan")
    void forma() {
        List<String> codigos = CodigosDeRecuperacion.nuevos();
        assertThat(codigos).hasSize(10);
        assertThat(new HashSet<>(codigos)).hasSize(10);
        assertThat(codigos).allMatch(codigo -> codigo.matches("[a-hjkmnp-z2-9]{5}-[a-hjkmnp-z2-9]{5}"));
    }

    @Test
    @DisplayName("Valen copiados de cualquier manera: con mayúsculas, sin guion o con espacios")
    void normalizados() {
        String hash = CodigosDeRecuperacion.hash("abcde-23456");
        assertThat(CodigosDeRecuperacion.hash("ABCDE-23456")).isEqualTo(hash);
        assertThat(CodigosDeRecuperacion.hash("abcde23456")).isEqualTo(hash);
        assertThat(CodigosDeRecuperacion.hash(" abcde 23456 ")).isEqualTo(hash);
        assertThat(hash).hasSize(64).isNotEqualTo(CodigosDeRecuperacion.hash("abcde-23457"));
    }

    @Test
    @DisplayName("Un código de seis cifras no se toma por uno de recuperación, ni al revés")
    void distinguibles() {
        assertThat(CodigosDeRecuperacion.parece("123456")).isFalse();
        assertThat(CodigosDeRecuperacion.parece("123 456")).isFalse();
        assertThat(CodigosDeRecuperacion.parece("abcde-23456")).isTrue();
        assertThat(CodigosDeRecuperacion.parece("ABCDE23456")).isTrue();
    }
}
