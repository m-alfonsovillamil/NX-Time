package com.nxtime.nxtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.FirebaseApp;
import com.nxtime.nxtime.notification.FirebasePushGateway;
import com.nxtime.nxtime.notification.PushGateway;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cómo se lee la credencial de Firebase de la variable de entorno (Fase B5). */
class FirebaseConfigTest {

    private static final String JSON = "{\"type\":\"service_account\",\"project_id\":\"nx-time\"}";

    @AfterEach
    void cerrarFirebase() {
        // FirebaseApp es un registro estático de la JVM: sin esto, el test que
        // inicializa de verdad dejaría la app puesta para los siguientes.
        FirebaseApp.getApps().stream()
                .filter(app -> app.getName().equals(FirebaseConfig.NOMBRE_APP))
                .forEach(FirebaseApp::delete);
    }

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

        assertThat(config.pushGateway("esto-no-es-base64-!!").enviar(List.of("t"), Map.of())).isEmpty();
        assertThat(config.pushGateway("{\"type\":\"service_account\"}").enviar(List.of("t"), Map.of())).isEmpty();
    }

    /*
     * El que faltaba, y el que habría evitado la caída del despliegue del
     * 26/09/2026: con una credencial BIEN formada, Firebase se inicializa de
     * verdad. Los otros tests nunca llegaban a FirebaseOptions, porque la
     * credencial fallaba antes, así que nadie ejercía el camino feliz; y ahí
     * faltaba una clase (JacksonFactory) que se había ido al excluir
     * google-cloud-storage. En producción, con la credencial real, el arranque
     * se cayó con ClassNotFoundException.
     *
     * La clave es de mentira, generada aquí: inicializar Firebase no llama a
     * Google, así que no hace falta una de verdad.
     */
    @Test
    @DisplayName("Con una credencial bien formada, Firebase se inicializa de verdad")
    void credencialBuenaInicializaFirebase() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(generador.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String cuenta = """
                {"type": "service_account",
                 "project_id": "nx-time-test",
                 "private_key_id": "abc123",
                 "private_key": "%s",
                 "client_email": "firebase-adminsdk@nx-time-test.iam.gserviceaccount.com",
                 "client_id": "123",
                 "token_uri": "https://oauth2.googleapis.com/token"}
                """.formatted(pem.replace("\n", "\\n"));

        PushGateway gateway = new FirebaseConfig().pushGateway(
                Base64.getEncoder().encodeToString(cuenta.getBytes(StandardCharsets.UTF_8)));

        assertThat(gateway).isInstanceOf(FirebasePushGateway.class);
        assertThat(FirebaseApp.getInstance(FirebaseConfig.NOMBRE_APP).getOptions().getProjectId())
                .isEqualTo("nx-time-test");
    }
}
