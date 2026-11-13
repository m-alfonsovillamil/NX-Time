package com.nxtime.nxtime.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Modalidad;
import com.nxtime.nxtime.domain.OrigenDelFichaje;
import com.nxtime.nxtime.domain.PuntoDeFichaje;
import com.nxtime.nxtime.dto.PersonalDataExport;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que va dentro de las celdas del PDF de «Mis datos»: el extractor de texto
 * del IT no las lee.
 */
class PersonalDataPdfGeneratorTest {

    private static PersonalDataExport.Fichaje fichaje(boolean anulado, boolean incompleta, Modalidad modalidad,
            OrigenDelFichaje origen) {
        Instant entrada = Instant.parse("2026-11-10T08:00:00Z");
        return new PersonalDataExport.Fichaje(1L, entrada, entrada.plusSeconds(8 * 3600), 0, anulado, incompleta,
                null, origen, modalidad, null, null);
    }

    @Test
    @DisplayName("La ubicación: coordenadas con su precisión, o por qué no hay")
    void ubicacion() {
        assertThat(PersonalDataPdfGenerator.ubicacion(PuntoDeFichaje.capturada(43.3623456, -8.4115678, 25)))
                .isEqualTo("43.3623, -8.4116 (± 25 m)");
        assertThat(PersonalDataPdfGenerator.ubicacion(PuntoDeFichaje.capturada(43.36, -8.41, null)))
                .isEqualTo("43.3600, -8.4100");
        assertThat(PersonalDataPdfGenerator.ubicacion(PuntoDeFichaje.sin(PuntoDeFichaje.Estado.DENEGADA)))
                .isEqualTo("Sin permiso");
        assertThat(PersonalDataPdfGenerator.ubicacion(PuntoDeFichaje.sin(PuntoDeFichaje.Estado.NO_DISPONIBLE)))
                .isEqualTo("No disponible");
        // A los 12 meses se borran las coordenadas y queda el estado.
        assertThat(PersonalDataPdfGenerator.ubicacion(
                new PuntoDeFichaje(PuntoDeFichaje.Estado.CAPTURADA, null, null, null)))
                .isEqualTo("Tomada (ya borrada a los 12 meses)");
        assertThat(PersonalDataPdfGenerator.ubicacion(null)).isEqualTo("—");
    }

    @Test
    @DisplayName("Las observaciones suman la modalidad y si se fichó sin conexión")
    void observaciones() {
        assertThat(PersonalDataPdfGenerator.observaciones(fichaje(false, true, Modalidad.A_DISTANCIA,
                OrigenDelFichaje.APP_SIN_CONEXION))).isEqualTo("Cerrada por el sistema · A distancia · Sin conexión");
        assertThat(PersonalDataPdfGenerator.observaciones(fichaje(true, false, Modalidad.PRESENCIAL,
                OrigenDelFichaje.APP))).isEqualTo("Sustituido por una corrección · Presencial");
        assertThat(PersonalDataPdfGenerator.observaciones(fichaje(false, false, null, null))).isEmpty();
    }
}
