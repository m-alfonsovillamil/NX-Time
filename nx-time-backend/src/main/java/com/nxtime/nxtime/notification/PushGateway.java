package com.nxtime.nxtime.notification;

import java.util.List;
import java.util.Map;

/**
 * Por dónde sale un push (Fase B5). Hoy solo FCM ({@link FirebasePushGateway}).
 *
 * Es una interfaz para que la lógica de {@link PushSender} —qué tokens, qué
 * texto, qué hacer con los muertos— se pueda probar sin Google, y para que sin
 * credenciales simplemente no exista el bean.
 */
public interface PushGateway {

    /**
     * Manda el mismo mensaje de datos a varios tokens.
     *
     * @return los tokens que el servicio dice que <b>ya no valen</b> (la app se
     *     desinstaló, el token caducó): hay que borrarlos. Los fallos
     *     pasajeros no están aquí; esos simplemente se pierden.
     */
    List<String> enviar(List<String> tokens, Map<String, String> datos);
}
