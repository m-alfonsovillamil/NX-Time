package com.nxtime.nxtime.security;

import com.nxtime.nxtime.domain.Kiosk;

/**
 * Quién llama cuando llama un kiosco (ADR 033): un dispositivo de una empresa,
 * no una persona. Lo pone {@link KioskAuthenticationFilter}, y con él solo se
 * llega a {@code /kiosco/**}.
 */
public record KioskPrincipal(Kiosk kiosco) {

    /** La única authority de un kiosco. No es de ningún rol: ver SecurityConfig. */
    public static final String AUTHORITY = "kiosco:fichar";

    public long empresaId() {
        return kiosco.getEmpresa().getId();
    }
}
