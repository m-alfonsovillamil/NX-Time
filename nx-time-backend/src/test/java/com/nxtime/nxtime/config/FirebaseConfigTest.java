package com.nxtime.nxtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cómo se lee la credencial de Firebase de la variable de entorno (Fase B5). */
class FirebaseConfigTest {

    private static final String JSON = "{\"type\":\"service_account\",\"project_id\":\"nx-time\"}";

    @Test
    @DisplayName("En base64, que es lo esperado en Render")
    void base64() {
        String codificado = Base64.getEncoder().encodeToString(JSON.getBytes(StandardCharsets.UTF_8));

        assertThat(new String(FirebaseConfig.json(codificado), StandardCharsets.UTF_8)).isEqualTo(JSON);
    }

    @Test
    @DisplayName("En base64 partido en líneas, como lo deja 'base64' sin -w0")
    void base64ConSaltos() {
        String codificado = Base64.getMimeEncoder().encodeToString((JSON + JSON + JSON).getBytes(StandardCharsets.UTF_8));
        assertThat(codificado).contains("\r\n");

        assertThat(new String(FirebaseConfig.json(codificado), StandardCharsets.UTF_8)).isEqualTo(JSON + JSON + JSON);
    }

    @Test
    @DisplayName("El JSON tal cual también vale, con espacios alrededor")
    void jsonTalCual() {
        assertThat(new String(FirebaseConfig.json("  " + JSON + "\n"), StandardCharsets.UTF_8)).isEqualTo(JSON);
    }

    @Test
    @DisplayName("Una credencial mal copiada no tumba el arranque: se sigue sin push")
    void credencialMalaNoTumbaElArranque() {
        FirebaseConfig config = new FirebaseConfig();

        assertThat(config.pushGateway("esto-no-es-base64-!!").enviar(java.util.List.of("t"), java.util.Map.of()))
                .isEmpty();
        assertThat(config.pushGateway("{\"type\":\"service_account\"}").enviar(java.util.List.of("t"), java.util.Map.of()))
                .isEmpty();
    }
}
