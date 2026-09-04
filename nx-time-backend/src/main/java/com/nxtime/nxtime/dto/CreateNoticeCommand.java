package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.NoticeType;

/**
 * Orden de publicar un aviso. Es un comando INTERNO: no entra ni sale
 * por HTTP, lo construye {@link
 * com.nxtime.nxtime.notification.NotificationListener} y lo consume
 * {@link com.nxtime.nxtime.service.NoticeService#publicar}.
 *
 * Lleva <b>ids y no entidades</b> a propósito. El listener corre
 * {@code @Async} y {@code AFTER_COMMIT}: en ese hilo no hay transacción
 * abierta ni sesión JPA viva, así que las entidades que trae el evento
 * están desprendidas. Con ids, el servicio resuelve las referencias
 * dentro de SU transacción y no hay que cruzar entidades desprendidas
 * entre dos transacciones distintas -- que es justo lo que revienta
 * cuando alguien añade un {@code cascade} o cambia un fetch.
 *
 * Es un record en vez de seis argumentos posicionales porque tres de
 * ellos son {@code String} consecutivos ({@code titulo}, {@code cuerpo},
 * {@code rutaDestino}) y confundirlos compilaría sin un solo aviso.
 *
 * @param rutaDestino destino LÓGICO ("ausencias-equipo/pendientes"), no
 *                    una ruta de navegación de ningún cliente. Puede ser
 *                    null: hay avisos que solo informan.
 */
public record CreateNoticeCommand(
        long empresaId,
        long destinatarioId,
        NoticeType tipo,
        String titulo,
        String cuerpo,
        String rutaDestino
) {
}
