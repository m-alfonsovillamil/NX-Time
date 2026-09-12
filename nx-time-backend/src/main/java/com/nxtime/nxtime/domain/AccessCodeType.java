package com.nxtime.nxtime.domain;

import java.time.Duration;

/** Para qué se emite un código de acceso (ver ADR 014). */
public enum AccessCodeType {

    /**
     * Al crear una cuenta: con él, su dueño elige su primera contraseña.
     *
     * Dura un día porque quien recibe el correo no tiene por qué leerlo al
     * momento. Si se le pasa, pide otro desde "He olvidado mi contraseña":
     * tener acceso a su correo es justo lo que demuestra quién es.
     */
    ALTA(Duration.ofHours(24)),

    /** "He olvidado mi contraseña". Corto: quien lo pide está esperándolo. */
    RECUPERACION(Duration.ofMinutes(15));

    private final Duration validez;

    AccessCodeType(Duration validez) {
        this.validez = validez;
    }

    public Duration getValidez() {
        return validez;
    }
}
