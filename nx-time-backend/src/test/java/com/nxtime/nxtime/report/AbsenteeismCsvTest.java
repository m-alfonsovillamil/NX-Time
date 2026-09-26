package com.nxtime.nxtime.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbsenteeismCsvTest {

    @Test
    @DisplayName("Un nombre que empieza como una fórmula sale como texto (inyección de CSV)")
    void neutralizaFormulas() {
        assertThat(AbsenteeismCsv.celda("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(AbsenteeismCsv.celda("+34 600")).isEqualTo("'+34 600");
        assertThat(AbsenteeismCsv.celda("-1")).isEqualTo("'-1");
        assertThat(AbsenteeismCsv.celda("@SUMA")).isEqualTo("'@SUMA");
        assertThat(AbsenteeismCsv.celda("Ana Pérez")).isEqualTo("Ana Pérez");
    }

    @Test
    @DisplayName("El separador, las comillas y los saltos de línea van entre comillas")
    void entrecomilla() {
        assertThat(AbsenteeismCsv.celda("I+D; Calidad")).isEqualTo("\"I+D; Calidad\"");
        assertThat(AbsenteeismCsv.celda("Dpto \"B\"")).isEqualTo("\"Dpto \"\"B\"\"\"");
        assertThat(AbsenteeismCsv.celda("dos\nlíneas")).isEqualTo("\"dos\nlíneas\"");
        assertThat(AbsenteeismCsv.celda(null)).isEmpty();
    }
}
