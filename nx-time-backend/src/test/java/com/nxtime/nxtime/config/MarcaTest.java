package com.nxtime.nxtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los colores de {@link Marca} siguen siendo los del tema de Android, que es la
 * fuente. Si alguien cambia el primario en `Color.kt` y no aquí, los correos y
 * los PDF vuelven a tener otro color que la app.
 */
class MarcaTest {

    private static final Path COLOR_KT =
            Path.of("../nx-time-frontend-android/src/main/java/com/nxtime/app/ui/theme/Color.kt");

    @Test
    @DisplayName("Cada color de Marca es el mismo que el de Color.kt con ese nombre")
    void coincidenConElTemaDeAndroid() throws IOException {
        // En una imagen o un checkout solo del backend no está: entonces no hay
        // nada con lo que comparar, y no es un fallo.
        assumeTrue(Files.exists(COLOR_KT), "sin el módulo de Android al lado");
        String tema = Files.readString(COLOR_KT);

        Map<String, String> esperados = Map.of(
                "LightPrimary", Marca.PRIMARIO,
                "LightOnPrimary", Marca.SOBRE_PRIMARIO,
                "LightPrimaryContainer", Marca.CONTENEDOR_PRIMARIO,
                "LightOnPrimaryContainer", Marca.SOBRE_CONTENEDOR_PRIMARIO,
                "LightOnSurface", Marca.TEXTO,
                "LightOnSurfaceVariant", Marca.TEXTO_SECUNDARIO,
                "LightOutlineVariant", Marca.BORDE,
                "LightFondoArriba", Marca.FONDO,
                "LightError", Marca.ERROR);

        esperados.forEach((nombre, hex) -> {
            Matcher m = Pattern.compile("val " + nombre + " = Color\\(0xFF([0-9A-Fa-f]{6})\\)").matcher(tema);
            assertThat(m.find()).as(nombre + " en Color.kt").isTrue();
            assertThat("#" + m.group(1).toUpperCase()).as(nombre).isEqualTo(hex);
        });
        Matcher verde = Pattern.compile("ColoresJornadaClaro = ColoresJornada\\(\\s*trabajando = Color\\(0xFF([0-9A-Fa-f]{6})\\)")
                .matcher(tema);
        assertThat(verde.find()).as("ColoresJornadaClaro.trabajando en Color.kt").isTrue();
        assertThat("#" + verde.group(1).toUpperCase()).as("EXITO").isEqualTo(Marca.EXITO);
    }

    @Test
    @DisplayName("Los correos solo usan colores de la marca: el azul de antes no vuelve")
    void losCorreosSoloUsanLaMarca() throws IOException {
        java.util.Set<String> permitidos = java.util.Set.of(Marca.PRIMARIO, Marca.SOBRE_PRIMARIO,
                Marca.CONTENEDOR_PRIMARIO, Marca.SOBRE_CONTENEDOR_PRIMARIO, Marca.TEXTO, Marca.TEXTO_SECUNDARIO,
                Marca.BORDE, Marca.FONDO, Marca.ERROR, Marca.EXITO);
        Pattern hex = Pattern.compile("#[0-9A-Fa-f]{6}\\b|#[0-9A-Fa-f]{3}\\b");
        try (var plantillas = Files.list(Path.of("src/main/resources/templates/email"))) {
            for (Path plantilla : plantillas.toList()) {
                Matcher m = hex.matcher(Files.readString(plantilla));
                while (m.find()) {
                    assertThat(permitidos).as(plantilla.getFileName() + " usa " + m.group())
                            .contains(m.group().toUpperCase());
                }
            }
        }
    }

    @Test
    @DisplayName("Los conversores dan el mismo color")
    void conversores() {
        assertThat(Marca.awt(Marca.PRIMARIO).getRGB() & 0xFFFFFF).isEqualTo(0x0E7C86);
        assertThat(Marca.rgb(Marca.PRIMARIO)).containsExactly((byte) 0x0E, (byte) 0x7C, (byte) 0x86);
    }
}
