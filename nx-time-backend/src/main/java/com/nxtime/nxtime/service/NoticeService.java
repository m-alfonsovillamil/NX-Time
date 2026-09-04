package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CreateNoticeCommand;
import com.nxtime.nxtime.dto.NoticeResponse;
import java.util.List;

/**
 * Avisos dentro de la aplicación (Fase A): el hermano persistente del
 * correo. Ver {@link com.nxtime.nxtime.domain.Notice}.
 */
public interface NoticeService {

    /**
     * Escribe un aviso, en su PROPIA transacción.
     *
     * Lo llama {@link
     * com.nxtime.nxtime.notification.NotificationListener} desde un
     * método {@code @Async} + {@code AFTER_COMMIT}, donde no hay ni
     * transacción abierta ni sesión JPA. Por eso recibe un comando con
     * ids y no entidades.
     */
    void publicar(CreateNoticeCommand comando);

    /** Los avisos de una persona, del más reciente al más antiguo. */
    List<NoticeResponse> getMisAvisos(User destinatario);

    long contarNoLeidos(User destinatario);

    /**
     * Marca un aviso como leído. Idempotente: marcarlo dos veces no es
     * un error.
     *
     * @throws com.nxtime.nxtime.exception.ResourceNotFoundException si el aviso no existe
     * @throws com.nxtime.nxtime.exception.TenantAccessException si el aviso es de otra persona
     */
    void marcarLeido(long avisoId, User destinatario);

    /** @return cuántos avisos ha marcado (los que ya estaban leídos no cuentan). */
    int marcarTodosLeidos(User destinatario);
}
