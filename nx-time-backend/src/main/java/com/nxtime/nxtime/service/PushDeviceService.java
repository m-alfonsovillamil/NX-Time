package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.PushPlatform;
import com.nxtime.nxtime.domain.User;

/** Qué dispositivos reciben push de quién (Fase B5, ADR 028). */
public interface PushDeviceService {

    /**
     * Apunta este dispositivo a nombre de quien lo registra.
     *
     * Idempotente: la app lo llama cada vez que entra. Si el token ya era de
     * OTRA persona —un móvil compartido en el que ha entrado alguien más—,
     * pasa a ser de esta: a partir de ahora ese móvil recibe sus avisos y no
     * los de la anterior.
     */
    void registrar(User actor, String token, PushPlatform plataforma);

    /**
     * Borra el dispositivo, si es de quien lo pide. Si no existe o es de otra
     * persona no hace nada, y no lo dice: no hay por qué confirmar a nadie de
     * quién es un token.
     */
    void darDeBaja(User actor, String token);
}
