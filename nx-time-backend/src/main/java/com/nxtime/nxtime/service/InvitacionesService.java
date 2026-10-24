package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.EstadoInvitacionesResponse;
import java.util.Collection;

/**
 * Los correos de alta que esperan cupo (ADR 052): al importar una plantilla,
 * las cuentas se crean todas a la vez, pero sus correos salen como mucho 50 al
 * día por empresa y sin pasar del tope del proveedor de correo.
 */
public interface InvitacionesService {

    /** Deja en cola las invitaciones de estas cuentas recién creadas. Dentro de la transacción de quien las crea. */
    void encolar(long empresaId, Collection<Long> usuarios);

    EstadoInvitacionesResponse estado(User actor);

    /**
     * Manda en segundo plano las pendientes de la empresa que quepan hoy. Si ya
     * se están mandando, no empieza otra tanda.
     */
    EstadoInvitacionesResponse enviarPendientes(User actor);
}
