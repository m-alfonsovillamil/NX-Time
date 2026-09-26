package com.nxtime.nxtime.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.nxtime.nxtime.notification.FirebasePushGateway;
import com.nxtime.nxtime.notification.PushGateway;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Firebase, solo si hay credenciales (Fase B5, ADR 028).
 *
 * Sin la variable {@code FIREBASE_CREDENTIALS_JSON} no se crea nada: la
 * aplicación arranca igual y {@code PushSender} simplemente no envía. Es lo
 * que permite que el proyecto se clone y funcione —en local, en CI, en los
 * tests— sin acceso al proyecto de Firebase, que es de quien despliega.
 *
 * <b>Y con la credencial MAL copiada, tampoco se cae.</b> Un secreto pegado a
 * medias en el panel de Render no puede tumbar el backend entero: se registra
 * el error (que llega a Sentry) y se arranca sin push. El push es un extra; el
 * registro horario no puede depender de él.
 *
 * <b>La credencial es un secreto</b>: la clave privada de una cuenta de
 * servicio que puede mandar push a todos los usuarios. Va en el panel de
 * Render ({@code sync: false} en render.yaml), nunca en el repositorio. Se
 * espera en base64 porque Render maltrata los saltos de línea de la clave
 * privada; si alguien pega el JSON tal cual, también se entiende.
 */
@Configuration
@ConditionalOnExpression("'${application.push.credenciales:}' != ''")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    /** Nombre propio y no el de por defecto, para no chocar con nadie que inicialice otro. */
    static final String NOMBRE_APP = "nxtime";

    @Bean
    PushGateway pushGateway(@Value("${application.push.credenciales}") String credenciales) {
        try {
            FirebaseApp app = firebaseApp(credenciales);
            log.info("Push activado con el proyecto de Firebase '{}'.", app.getOptions().getProjectId());
            return new FirebasePushGateway(FirebaseMessaging.getInstance(app));
        } catch (IOException | RuntimeException | LinkageError e) {
            // LinkageError también: el 26/09/2026 el primer despliegue con
            // credencial se cayó con un NoClassDefFoundError (faltaba una
            // dependencia), que no es una Exception y se escapaba de aquí.
            // Sin el mensaje de la excepción en el log: podría citar un trozo
            // de la credencial.
            log.error("FIREBASE_CREDENTIALS_JSON no es una credencial válida ({}): se arranca SIN push.",
                    e.getClass().getSimpleName());
            return (tokens, datos) -> List.of();
        }
    }

    private static FirebaseApp firebaseApp(String credenciales) throws IOException {
        // Los tests levantan varios contextos en la misma JVM, y FirebaseApp es
        // un registro estático: inicializar dos veces el mismo nombre lanza.
        for (FirebaseApp existente : FirebaseApp.getApps()) {
            if (existente.getName().equals(NOMBRE_APP)) {
                return existente;
            }
        }
        GoogleCredentials credencial = GoogleCredentials.fromStream(new ByteArrayInputStream(json(credenciales)));
        FirebaseOptions.Builder opciones = FirebaseOptions.builder().setCredentials(credencial);
        // El proyecto, explícito: Firebase lo deduce de la credencial al enviar,
        // pero no lo copia a sus opciones, y el log del arranque diría 'null'.
        if (credencial instanceof ServiceAccountCredentials cuenta && cuenta.getProjectId() != null) {
            opciones.setProjectId(cuenta.getProjectId());
        }
        return FirebaseApp.initializeApp(opciones.build(), NOMBRE_APP);
    }

    /** El JSON de la cuenta de servicio, venga en base64 (lo esperado) o tal cual. */
    static byte[] json(String credenciales) {
        String limpio = credenciales.strip();
        if (limpio.startsWith("{")) {
            return limpio.getBytes(StandardCharsets.UTF_8);
        }
        // MIME y no básico: tolera los saltos de línea que mete "base64" en
        // Linux cada 76 caracteres si no se le pasa -w0.
        return Base64.getMimeDecoder().decode(limpio);
    }
}
