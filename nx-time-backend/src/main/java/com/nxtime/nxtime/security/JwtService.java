package com.nxtime.nxtime.security;

import org.springframework.security.core.userdetails.UserDetails;

/**
 * Define las 3 cosas principales que nuestro servicio de JWT debe saber hacer.
 */
public interface JwtService {

    String extractUsername(String token);

    String generateToken(UserDetails userDetails);

    boolean isTokenValid(String token, UserDetails userDetails);
}
