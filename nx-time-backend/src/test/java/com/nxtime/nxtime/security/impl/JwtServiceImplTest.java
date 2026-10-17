package com.nxtime.nxtime.security.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/** El access token (revisión de seguridad del 17/10/2026, plan J). */
class JwtServiceImplTest {

    private static final String CLAVE = JwtServiceImpl.CLAVE_DE_DESARROLLO;
    private static final UserDetails ANA = User.withUsername("ana@nxtime.test").password("x").authorities(List.of()).build();

    private final JwtServiceImpl jwt = new JwtServiceImpl(CLAVE, 900_000, false);

    @Test
    @DisplayName("Un token emitido aquí vale, y lleva emisor y destino")
    void elPropioVale() {
        String token = jwt.generateToken(ANA);

        assertThat(jwt.isTokenValid(token, ANA)).isTrue();
        assertThat(jwt.extractUsername(token)).isEqualTo("ana@nxtime.test");
    }

    @Test
    @DisplayName("Uno firmado con la misma clave pero sin el emisor y el destino de NX Time no vale")
    void sinEmisorNiDestinoNoVale() {
        String ajeno = Jwts.builder()
                .subject("ana@nxtime.test")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(CLAVE)))
                .compact();

        assertThatThrownBy(() -> jwt.extractUsername(ajeno)).isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("En producción no arranca con la clave de desarrollo, que está publicada")
    void enProduccionNoConLaClaveDeDesarrollo() {
        assertThatThrownBy(() -> new JwtServiceImpl(CLAVE, 900_000, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        // Con otra, sí.
        new JwtServiceImpl("dGhpcy1pcy1hbm90aGVyLWtleS1mb3ItdGVzdGluZy1vbmx5LTY0LWJ5dGVzLWxvbmctcGxlYXNlLW9rLW9r", 900_000, true);
    }
}
