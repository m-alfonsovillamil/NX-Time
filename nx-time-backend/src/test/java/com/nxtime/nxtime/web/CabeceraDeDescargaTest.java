package com.nxtime.nxtime.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;

class CabeceraDeDescargaTest {

    @Test
    @DisplayName("Un nombre corriente sale igual en los dos parámetros")
    void nombreCorriente() {
        assertThat(CabeceraDeDescarga.de("attachment", "cv.pdf"))
                .isEqualTo("attachment; filename=\"cv.pdf\"; filename*=UTF-8''cv.pdf");
    }

    @Test
    @DisplayName("Unas comillas en el nombre no cierran el parámetro antes de tiempo")
    void comillasNoRompenLaCabecera() {
        String cabecera = CabeceraDeDescarga.de("attachment", "cv\"; filename=\"malo.exe");

        assertThat(cabecera).startsWith("attachment; filename=\"cv__ filename=_malo.exe\";");
        // Y quien lee la cabecera (Spring, como haría un navegador) ve el nombre de verdad.
        assertThat(ContentDisposition.parse(cabecera).getFilename()).isEqualTo("cv\"; filename=\"malo.exe");
    }

    @Test
    @DisplayName("Tildes y espacios llegan enteros por filename*, y el respaldo ASCII no lleva nada raro")
    void tildesYEspacios() {
        String cabecera = CabeceraDeDescarga.de("inline", "Currículum de José*.pdf");

        assertThat(cabecera).contains("filename=\"Curr_culum de Jos_*.pdf\"");
        assertThat(cabecera).contains("filename*=UTF-8''Curr%C3%ADculum%20de%20Jos%C3%A9%2A.pdf");
        assertThat(ContentDisposition.parse(cabecera).getFilename()).isEqualTo("Currículum de José*.pdf");
    }

    @Test
    @DisplayName("Un salto de línea no puede partir la cabecera en dos")
    void saltosDeLinea() {
        String cabecera = CabeceraDeDescarga.de("attachment", "a\r\nSet-Cookie: x=1");

        assertThat(cabecera).doesNotContain("\r").doesNotContain("\n");
    }
}
