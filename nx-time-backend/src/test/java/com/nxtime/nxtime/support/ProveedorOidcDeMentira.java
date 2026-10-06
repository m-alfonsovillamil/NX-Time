package com.nxtime.nxtime.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Un Google (o un Microsoft) de mentira, para los tests del SSO.
 *
 * Hace lo que el servidor le pide a un proveedor de verdad y nada más: publica
 * sus claves ({@code /jwks}) y canjea un código por un ID token firmado
 * ({@code /token}). La pantalla de «elige tu cuenta» no existe: el test hace de
 * navegador y salta de la ida a la vuelta.
 *
 * <b>El test decide qué dice el ID token</b> ({@link #proximoToken}), que es
 * justo lo que hay que poder torcer: otro destinatario, otro emisor, un nonce
 * que no es, una firma de otra clave. Con un proveedor de verdad nada de eso
 * se puede provocar.
 */
public class ProveedorOidcDeMentira implements AutoCloseable {

    private final HttpServer servidor;
    private final RSAKey clave;
    /** Otra clave que el servidor NO publica: para firmar tokens que no deben valer. */
    private final RSAKey claveAjena;

    private volatile Map<String, Object> claims = Map.of();
    private volatile boolean firmarConClaveAjena;
    private volatile int estadoDelToken = 200;
    private final List<Map<String, String>> canjes = new CopyOnWriteArrayList<>();

    public ProveedorOidcDeMentira() {
        try {
            clave = new RSAKeyGenerator(2048).keyID("clave-de-prueba").generate();
            claveAjena = new RSAKeyGenerator(2048).keyID("clave-de-prueba").generate();
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/jwks", peticion ->
                responder(peticion, 200, new JWKSet(clave.toPublicJWK()).toString()));
        servidor.createContext("/token", this::canjear);
        servidor.start();
    }

    public String url(String ruta) {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + ruta;
    }

    /** Lo que dirá el próximo ID token. {@code exp} e {@code iat} se ponen solos si no vienen. */
    public void proximoToken(Map<String, Object> claims) {
        this.claims = new LinkedHashMap<>(claims);
        this.firmarConClaveAjena = false;
        this.estadoDelToken = 200;
    }

    /** El próximo token irá firmado con una clave que este proveedor no publica. */
    public void firmarElProximoConOtraClave() {
        this.firmarConClaveAjena = true;
    }

    /** El próximo canje falla, como cuando el código ya se usó o no existe. */
    public void rechazarElProximoCanje() {
        this.estadoDelToken = 400;
    }

    /** Lo que el servidor ha mandado a {@code /token}, en orden. */
    public List<Map<String, String>> canjes() {
        return new ArrayList<>(canjes);
    }

    public void olvidarCanjes() {
        canjes.clear();
    }

    private void canjear(HttpExchange peticion) throws IOException {
        String cuerpo = new String(peticion.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> formulario = new LinkedHashMap<>();
        for (String par : cuerpo.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) {
                formulario.put(URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8),
                        URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
            }
        }
        canjes.add(formulario);
        if (estadoDelToken != 200) {
            responder(peticion, estadoDelToken, "{\"error\":\"invalid_grant\"}");
            return;
        }
        responder(peticion, 200, "{\"token_type\":\"Bearer\",\"id_token\":\"" + firmar() + "\"}");
    }

    private String firmar() {
        try {
            JWTClaimsSet.Builder cuerpo = new JWTClaimsSet.Builder();
            claims.forEach(cuerpo::claim);
            long ahora = System.currentTimeMillis();
            if (!claims.containsKey("iat")) {
                cuerpo.issueTime(new Date(ahora));
            }
            if (!claims.containsKey("exp")) {
                cuerpo.expirationTime(new Date(ahora + 300_000));
            }
            SignedJWT token = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(clave.getKeyID()).build(),
                    cuerpo.build());
            token.sign(new RSASSASigner(firmarConClaveAjena ? claveAjena : clave));
            return token.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void responder(HttpExchange peticion, int estado, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        peticion.getResponseHeaders().add("Content-Type", "application/json");
        peticion.sendResponseHeaders(estado, bytes.length);
        peticion.getResponseBody().write(bytes);
        peticion.close();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
