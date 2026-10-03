package com.nxtime.nxtime.security;

import org.springframework.security.core.userdetails.UserDetails;

/**
 * Define las 3 cosas principales que nuestro servicio de JWT debe saber hacer.
 */
public interface JwtService {

    String extractUsername(String token);

    String generateToken(UserDetails userDetails);

    /**
     * Con la sesión a la que pertenece (la familia de su refresh, claim
     * {@code sid}): es lo que permite cerrar las demás sesiones y no esta al
     * cambiar la contraseña (ADR 034).
     */
    String generateToken(UserDetails userDetails, java.util.UUID sesion);

    /** La sesión del token, si la lleva. Los emitidos antes del ADR 034 no la llevan. */
    java.util.Optional<java.util.UUID> extractSesion(String token);

    boolean isTokenValid(String token, UserDetails userDetails);
}
