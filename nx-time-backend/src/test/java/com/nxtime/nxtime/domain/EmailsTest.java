package com.nxtime.nxtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ver {@link Emails}: el bug de las mayúsculas del 16/09/2026. */
class EmailsTest {

    @Test
    @DisplayName("Pasa a minúsculas y recorta los espacios")
    void normaliza() {
        assertThat(Emails.normalizar("  Juan.Perez@EMPRESA.com ")).isEqualTo("juan.perez@empresa.com");
    }

    @Test
    @DisplayName("Lo que ya está normalizado no cambia")
    void dejaIgualLoQueYaEstaBien() {
        assertThat(Emails.normalizar("juan@empresa.com")).isEqualTo("juan@empresa.com");
    }

    @Test
    @DisplayName("Tolera null, para que lo rechace @NotBlank y no un NullPointerException")
    void toleraNull() {
        assertThat(Emails.normalizar(null)).isNull();
    }

    /*
     * El motivo de que normalizar() fije Locale.ROOT. Con la configuración
     * regional turca, "I".toLowerCase() no da "i" sino "ı" (i sin punto), y
     * una dirección con I mayúscula dejaría de encontrarse en un servidor
     * con ese idioma. Aquí se comprueba que el resultado no depende de eso.
     */
    @Test
    @DisplayName("No depende del idioma del sistema: la I turca no rompe la dirección")
    void noDependeDelIdiomaDelSistema() {
        java.util.Locale anterior = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr"));
            assertThat(Emails.normalizar("IRENE@EMPRESA.com")).isEqualTo("irene@empresa.com");
        } finally {
            java.util.Locale.setDefault(anterior);
        }
    }
}
