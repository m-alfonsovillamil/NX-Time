package com.nxtime.nxtime.kiosco;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Kiosk;
import com.nxtime.nxtime.service.impl.KioskProfileServiceImplPruebas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La tarjeta QR del kiosco y las reglas pequeñas que la rodean (ADR 033). */
class TarjetaDeKioscoTest {

    private final TarjetaDeKiosco tarjeta = new TarjetaDeKiosco("secreto-de-prueba");

    @Test
    @DisplayName("El código se lee de vuelta con su dueño y su versión")
    void idaYVuelta() {
        String codigo = tarjeta.codigo(42, 3);

        assertThat(codigo).startsWith("NXK1.42.3.");
        assertThat(tarjeta.leer(codigo)).contains(new TarjetaDeKiosco.Lectura(42, 3));
    }

    @Test
    @DisplayName("Cambiar el id o la versión sin la clave no da una tarjeta válida")
    void falsificar() {
        String firma = tarjeta.codigo(42, 3).substring("NXK1.42.3.".length());

        assertThat(tarjeta.leer("NXK1.43.3." + firma)).isEmpty();
        assertThat(tarjeta.leer("NXK1.42.4." + firma)).isEmpty();
        assertThat(tarjeta.leer("basura")).isEmpty();
        assertThat(tarjeta.leer(null)).isEmpty();
        // Con otra clave (otro JWT_SECRET), las tarjetas de antes dejan de valer.
        assertThat(new TarjetaDeKiosco("otro-secreto").leer(tarjeta.codigo(42, 3))).isEmpty();
    }

    @Test
    @DisplayName("El SVG es un QR con fondo blanco, sin nada que cargar de fuera")
    void svg() {
        String svg = tarjeta.svg(tarjeta.codigo(42, 3));

        assertThat(svg).startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"")
                .contains("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>")
                .contains("<path fill=\"#000\" d=\"M")
                .doesNotContain("href");
    }

    @Test
    @DisplayName("Los PIN que se prueban primero no se admiten")
    void pinDemasiadoFacil() {
        assertThat(KioskProfileServiceImplPruebas.demasiadoFacil("1111")).isTrue();
        assertThat(KioskProfileServiceImplPruebas.demasiadoFacil("1234")).isTrue();
        assertThat(KioskProfileServiceImplPruebas.demasiadoFacil("987654")).isTrue();
        assertThat(KioskProfileServiceImplPruebas.demasiadoFacil("4827")).isFalse();
        assertThat(KioskProfileServiceImplPruebas.demasiadoFacil("1123")).isFalse();
    }

    @Test
    @DisplayName("El motivo de la auditoría dice desde qué kiosco, junto al proyecto si lo hay")
    void motivoConKiosco() {
        Kiosk kiosco = Kiosk.builder().id(7).nombre("Entrada almacén").build();

        assertThat(KioskProfileServiceImplPruebas.conKiosco(null, null)).isNull();
        assertThat(KioskProfileServiceImplPruebas.conKiosco("Proyecto: P-1", null)).isEqualTo("Proyecto: P-1");
        assertThat(KioskProfileServiceImplPruebas.conKiosco(null, kiosco))
                .isEqualTo("Desde el kiosco «Entrada almacén» (id 7)");
        assertThat(KioskProfileServiceImplPruebas.conKiosco("Proyecto: P-1", kiosco))
                .isEqualTo("Proyecto: P-1 · Desde el kiosco «Entrada almacén» (id 7)");
    }
}
