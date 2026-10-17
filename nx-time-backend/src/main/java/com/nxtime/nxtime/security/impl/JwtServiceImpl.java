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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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

    /**
     * Quién emite el token y para quién es (revisión de seguridad del
     * 17/10/2026, plan J). Con la misma clave no se firma nada más hoy, pero
     * si un día se firmara otra cosa --un enlace, un token de otro servicio--,
     * no podría pasar por un access token.
     */
    static final String EMISOR = "nxtime";
    static final String DESTINO = "nxtime-api";

    /**
     * La clave de desarrollo que trae application.yml. Está en el repositorio,
     * así que la conoce cualquiera: con ella se fabrica un token de quien se
     * quiera. Producción no tiene valor por defecto, pero nada impedía poner
     * esta en JWT_SECRET; ahora el servidor no arranca.
     */
    static final String CLAVE_DE_DESARROLLO =
            "PXPGUXRppB7TpSN+W65Sl9qIKhKIOS6vxftZBZLCI4iIuCPt4CzTchUL4BFScNcgXWhlQess0tQaSOy8PABnRQ==";

    private final SecretKey clave;
    private final long jwtExpiration;

    @Autowired
    public JwtServiceImpl(
            @Value("${application.security.jwt.secret-key}") String secretKey,
            @Value("${application.security.jwt.expiration}") long jwtExpiration,
            Environment entorno) {
        this(secretKey, jwtExpiration, entorno.acceptsProfiles(Profiles.of("prod")));
    }

    JwtServiceImpl(String secretKey, long jwtExpiration, boolean produccion) {
        if (produccion && CLAVE_DE_DESARROLLO.equals(secretKey.strip())) {
            throw new IllegalStateException("JWT_SECRET es la clave de desarrollo, que está publicada en el "
                    + "repositorio: con ella cualquiera firma tokens. Genera otra (openssl rand -base64 64).");
        }
        this.clave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.jwtExpiration = jwtExpiration;
    }

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
                .issuer(EMISOR)
                .audience().add(DESTINO).and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtExpiration))
                .signWith(clave)
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
                .verifyWith(clave)
                .requireIssuer(EMISOR)
                .requireAudience(DESTINO)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
