package com.nxtime.nxtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiasTest {

    @Test
    @DisplayName("Los días se escriben como se leen: sin «,0» y con coma")
    void texto() {
        assertThat(Dias.texto(0)).isEqualTo("0");
        assertThat(Dias.texto(0.5)).isEqualTo("0,5");
        assertThat(Dias.texto(3)).isEqualTo("3");
        assertThat(Dias.texto(2.5)).isEqualTo("2,5");
        // stripTrailingZeros deja 20 como 2E+1: que no se cuele en un mensaje.
        assertThat(Dias.texto(20)).isEqualTo("20");
        assertThat(Dias.texto(-0.5)).isEqualTo("-0,5");
    }
}
