package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DeletionRequestDTO;
import com.nxtime.nxtime.dto.DeletionResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Derecho de supresión (RGPD, art. 17), a la manera en que un registro
 * horario lo permite. Ver ADR 016.
 *
 * La persona lo pide; RRHH o ADMIN lo ejecuta, después de comprobar que no
 * queda nada abierto. Ejecutar borra lo prescindible y desactiva la cuenta;
 * lo que la ley obliga a conservar se anonimiza cuatro años después del
 * último fichaje, en la tarea nocturna.
 */
public interface DataDeletionService {

    /** Pedir el borrado de los datos propios. 409 si ya hay una pendiente. */
    DeletionResponse solicitar(User persona, DeletionRequestDTO peticion);

    /** La última solicitud de la persona, en cualquier estado. */
    Optional<DeletionResponse> miUltimaSolicitud(User persona);

    /** Retirar la pendiente. 404 si no hay ninguna. */
    DeletionResponse cancelar(User persona);

    /** La bandeja: las pendientes de la empresa de quien pregunta, con lo que impide ejecutar cada una. */
    List<DeletionResponse> pendientes(User actor);

    int contarPendientes(User actor);

    /** Purga y desactiva. 409 con los bloqueos en el mensaje si queda algo abierto. */
    DeletionResponse ejecutar(long solicitudId, User actor);

    /** 400 sin comentario: quien lo pidió tiene que saber por qué no. */
    DeletionResponse rechazar(long solicitudId, String comentario, User actor);

    /** Anonimiza las ejecutadas cuyo plazo venció en o antes de {@code hoy}. Devuelve cuántas. */
    int anonimizarVencidas(LocalDate hoy);
}
