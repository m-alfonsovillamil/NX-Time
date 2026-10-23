package com.nxtime.nxtime.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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
     * cambiar la contraseña (ADR 034). Y con si esa sesión pasó por un segundo
     * factor (ADR 051), que es lo que mira {@code SegundoFactorObligatorioFilter}.
     */
    String generateToken(UserDetails userDetails, UUID sesion, boolean conSegundoFactor);

    /** La sesión del token, si la lleva. Los emitidos antes del ADR 034 no la llevan. */
    Optional<UUID> extractSesion(String token);

    /** Si la sesión del token pasó por un segundo factor. Los de antes del ADR 051, no. */
    boolean conSegundoFactor(String token);

    /**
     * Cuándo se emitió, al milisegundo: para cortar los emitidos antes de
     * «cerrar todas las sesiones» (ADR 051). El {@code iat} estándar va en
     * segundos, que no basta para separar un token de un cierre en el mismo
     * segundo; los tokens de antes solo lo llevan a él.
     */
    Instant emitidoEn(String token);

    boolean isTokenValid(String token, UserDetails userDetails);

    /**
     * El paso intermedio del login con segundo factor (ADR 051): la contraseña
     * ya se ha comprobado y falta el código. Vale cinco minutos y no abre
     * ninguna sesión: no sirve como access token (otro {@code aud}) ni al
     * revés.
     *
     * @param huella lo que el desafío tiene que seguir cumpliendo al canjearlo:
     *   si entretanto cambian la contraseña o el segundo factor, deja de valer
     */
    String generarDesafio(long usuarioId, String huella);

    /** El desafío, si es uno válido y no ha caducado. */
    Optional<Desafio> leerDesafio(String desafio);

    record Desafio(long usuarioId, String huella) {
    }
}
