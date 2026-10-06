package com.nxtime.nxtime.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Con qué cuentas de fuera se puede entrar (ADR 036).
 *
 * El nombre del valor es lo que se guarda en {@code identidades_externas}; el
 * {@link #id()} es lo que va en las URL ({@code /auth/sso/google/iniciar}).
 */
public enum SsoProvider {

    GOOGLE("Google"),
    MICROSOFT("Microsoft");

    private final String nombre;

    SsoProvider(String nombre) {
        this.nombre = nombre;
    }

    /** Cómo se llama de cara a la gente: va en el botón. */
    public String nombre() {
        return nombre;
    }

    /** En minúsculas, para las URL y para la configuración. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SsoProvider> deId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(proveedor -> proveedor.id().equals(id)).findFirst();
    }
}
