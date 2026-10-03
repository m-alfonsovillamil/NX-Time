package com.nxtime.nxtime.web.support;

import com.nxtime.nxtime.notification.NotificationEvents;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;

/**
 * Los códigos de acceso que se han mandado, para los tests que registran una
 * empresa por la API y tienen que confirmar el correo para entrar (V37, ADR
 * 034). Se recoge el evento y no el correo: el correo sale después del commit
 * y en otro hilo, y mirarlo sería una carrera. Mismo patrón que
 * {@code AccessCodeIT.CapturaDeCodigos}.
 *
 * <p>Se usa con {@code @Import(CodigosEnviados.class)}.
 */
@TestConfiguration
public class CodigosEnviados {

    private final List<NotificationEvents.AccessCodeRequested> eventos = new CopyOnWriteArrayList<>();

    @EventListener
    void capturar(NotificationEvents.AccessCodeRequested evento) {
        eventos.add(evento);
    }

    /** El último código mandado a ese correo. */
    public String ultimoPara(String email) {
        return eventos.stream()
                .filter(evento -> evento.email().equalsIgnoreCase(email))
                .reduce((primero, ultimo) -> ultimo)
                .map(evento -> (String) evento.variables().get("codigo"))
                .orElseThrow(() -> new AssertionError("No se ha mandado ningún código a " + email));
    }
}
