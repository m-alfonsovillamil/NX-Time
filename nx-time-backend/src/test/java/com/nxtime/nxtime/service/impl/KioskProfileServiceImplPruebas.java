package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Kiosk;

/**
 * Abre a los tests de otros paquetes las reglas del kiosco que son de paquete
 * (ADR 033): se prueban sin levantar nada, pero no forman parte de la API de
 * los servicios.
 */
public final class KioskProfileServiceImplPruebas {

    private KioskProfileServiceImplPruebas() {
    }

    public static boolean demasiadoFacil(String pin) {
        return KioskProfileServiceImpl.demasiadoFacil(pin);
    }

    public static String conKiosco(String motivo, Kiosk kiosco) {
        return TimeEntryServiceImpl.conKiosco(motivo, kiosco);
    }
}
