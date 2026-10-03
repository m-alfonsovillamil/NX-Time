package com.nxtime.nxtime.security.impl;

import com.nxtime.nxtime.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Se encarga de CREAR y VALIDAR los tokens JWT (de acceso -- los
 * refresh tokens son cadenas opacas gestionadas aparte, ver
 * {@link com.nxtime.nxtime.domain.RefreshToken}, no JWT).
 *
 * Desde la Fase 4 usa la API "moderna" de jjwt 0.12.x
 * (Jwts.parser().verifyWith(...)), no la antigua setClaims/parserBuilder
 * (ya deprecada) que traía la migración de la Fase 1.
 */
@Service
public class JwtServiceImpl implements JwtService {

    /** La familia del refresh con el que se emitió: la sesión (ADR 034). */
    static final String CLAIM_SESION = "sid";

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Override
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @Override
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    @Override
    public String generateToken(UserDetails userDetails, java.util.UUID sesion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_SESION, sesion.toString());
        return generateToken(claims, userDetails);
    }

    @Override
    public java.util.Optional<java.util.UUID> extractSesion(String token) {
        try {
            String sesion = extractClaim(token, claims -> claims.get(CLAIM_SESION, String.class));
            return java.util.Optional.ofNullable(sesion).map(java.util.UUID::fromString);
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Date now = new Date();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
