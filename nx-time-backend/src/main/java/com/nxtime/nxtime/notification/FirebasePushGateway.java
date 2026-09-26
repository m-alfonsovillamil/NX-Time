package com.nxtime.nxtime.notification;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link PushGateway} con Firebase Cloud Messaging (Fase B5, ADR 028).
 *
 * <b>Mensajes solo de datos</b>, sin bloque {@code notification}: así el
 * mensaje llega siempre a la app ({@code onMessageReceived}), que decide si lo
 * enseña. Con un bloque {@code notification}, Android lo pintaría por su cuenta
 * con la app en segundo plano, y lo pintaría también en un móvil del que ya se
 * cerró la sesión. Prioridad alta, que es lo que hace que llegue aunque el
 * móvil esté en reposo, y un día de vida: un aviso de hace dos días ya está en
 * la app, y que el móvil lo pite al volver a encenderse no ayuda a nadie.
 *
 * No lo construye Spring solo: lo crea {@code FirebaseConfig}, y solo si hay
 * credenciales.
 */
public class FirebasePushGateway implements PushGateway {

    private static final Duration VIDA = Duration.ofDays(1);

    /**
     * Los errores que significan "este token no vuelve": borrarlo.
     *
     * {@code INVALID_ARGUMENT} NO está, aunque FCM también lo use para un token
     * malformado: sale igual si el MENSAJE está mal construido, que sería
     * culpa nuestra, y tratarlo como token muerto borraría los de todo el
     * mundo por un fallo del servidor. Un token de verdad malformado no puede
     * llegar aquí: lo registró la propia app con lo que le dio FCM.
     */
    private static final Set<MessagingErrorCode> TOKEN_MUERTO =
            Set.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.SENDER_ID_MISMATCH);

    private final FirebaseMessaging messaging;

    public FirebasePushGateway(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public List<String> enviar(List<String> tokens, Map<String, String> datos) {
        MulticastMessage mensaje = MulticastMessage.builder()
                .addAllTokens(tokens)
                .putAllData(datos)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(VIDA.toMillis())
                        .build())
                .build();
        BatchResponse respuesta;
        try {
            respuesta = messaging.sendEachForMulticast(mensaje);
        } catch (FirebaseMessagingException e) {
            // Falla el envío entero (credenciales, red): ningún token es culpable.
            throw new IllegalStateException("FCM rechazó el envío: " + e.getMessagingErrorCode(), e);
        }
        List<String> muertos = new ArrayList<>();
        List<SendResponse> resultados = respuesta.getResponses();
        for (int i = 0; i < resultados.size(); i++) {
            FirebaseMessagingException error = resultados.get(i).getException();
            if (error != null && TOKEN_MUERTO.contains(error.getMessagingErrorCode())) {
                muertos.add(tokens.get(i));
            }
        }
        return muertos;
    }
}
