package com.nxtime.nxtime.security.sso;

import com.nxtime.nxtime.domain.Emails;
import com.nxtime.nxtime.domain.SsoProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Lo que se habla con Google y con Microsoft: mandar a la persona allí, canjear
 * el código con el que vuelve y comprobar el ID token (OpenID Connect, flujo de
 * código con PKCE).
 *
 * <b>La criptografía no es de esta casa.</b> La firma del ID token, sus claves
 * (JWKS) y sus fechas las valida {@link NimbusJwtDecoder}, el de Spring
 * Security. Aquí solo se le dice qué exigir además: que el token sea para esta
 * aplicación, que lo emita quien debe y que responda a esta ida.
 *
 * <h2>Cuándo se cree el correo</h2>
 *
 * Es la decisión de seguridad de todo esto: la primera vez, la cuenta de NX
 * Time se busca por el correo que dice el proveedor, así que un correo que el
 * proveedor no garantiza sería una forma de entrar en la cuenta de otro.
 *
 * <ul>
 *   <li><b>Google</b>: con {@code email_verified = true}.</li>
 *   <li><b>Microsoft</b>: su {@code email} <b>no</b> está verificado en una
 *       aplicación abierta a cualquier organización: el administrador de un
 *       inquilino puede ponerle a un usuario el correo que quiera (el fallo
 *       conocido como «nOAuth»). Se cree solo si viene {@code xms_edov = true}
 *       («el dueño del dominio del correo está verificado», un claim opcional
 *       que hay que pedir al registrar la aplicación) o si es una cuenta
 *       personal, que verifica el propio Microsoft.</li>
 * </ul>
 */
@Component
public class OidcClient {

    /** El inquilino de las cuentas personales de Microsoft (outlook.com, hotmail.com). */
    static final String INQUILINO_PERSONAL = "9188040d-6c67-4c5b-b112-36a304b66dad";

    private static final String MARCA_DE_INQUILINO = "{tenantid}";
    private static final Pattern GUID = Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

    private final SsoConfig config;
    private static final ParameterizedTypeReference<Map<String, Object>> RESPUESTA_DEL_TOKEN =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient http;
    /** Un decodificador por proveedor: guarda en caché las claves públicas que se descarga. */
    private final Map<SsoProvider, JwtDecoder> decodificadores = new ConcurrentHashMap<>();

    public OidcClient(SsoConfig config) {
        this.config = config;
        // Con plazos: es una llamada a un tercero en mitad de una redirección
        // del navegador, y sin ellos un proveedor colgado dejaría el hilo
        // esperando lo que quisiera.
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    /** Adónde hay que mandar el navegador. */
    public String urlDeAutorizacion(SsoProvider proveedor, String state, String nonce, String retoDePkce) {
        SsoConfig.Proveedor datos = config.de(proveedor);
        return UriComponentsBuilder.fromUriString(datos.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", datos.clientId())
                .queryParam("redirect_uri", config.urlDeVuelta(proveedor))
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", retoDePkce)
                .queryParam("code_challenge_method", "S256")
                // Que deje elegir cuenta: quien tiene la personal y la del
                // trabajo abiertas entraría, si no, con la que no es.
                .queryParam("prompt", "select_account")
                // Por la URL y no con un formulario: ver SsoState.
                .queryParam("response_mode", "query")
                .encode()
                .build()
                .toUriString();
    }

    /**
     * Canjea el código y devuelve quién es. Si algo no cuadra --el proveedor
     * rechaza el código, la firma no vale, el token es para otra aplicación--,
     * lanza {@link SsoException}.
     */
    public VerifiedIdentity canjear(SsoProvider proveedor, String codigo, String verificadorDePkce, String nonce) {
        SsoConfig.Proveedor datos = config.de(proveedor);

        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("grant_type", "authorization_code");
        formulario.add("code", codigo);
        formulario.add("redirect_uri", config.urlDeVuelta(proveedor));
        formulario.add("client_id", datos.clientId());
        formulario.add("client_secret", datos.clientSecret());
        formulario.add("code_verifier", verificadorDePkce);

        String idToken;
        try {
            // Un mapa, y no un JsonNode: el arbol es de UNA version de Jackson, y
            // con cual lee este cliente lo decide lo que haya en el classpath
            // (con Spring Boot 4, la 3). Un mapa lo entienden las dos.
            Map<String, Object> respuesta = http.post()
                    .uri(datos.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formulario)
                    .retrieve()
                    .body(RESPUESTA_DEL_TOKEN);
            idToken = respuesta != null && respuesta.get("id_token") instanceof String texto ? texto : null;
        } catch (Exception e) {
            // Sin el cuerpo de la respuesta en el mensaje: puede llevar el código.
            throw new SsoException(SsoException.Motivo.FALLO,
                    "El proveedor " + proveedor.id() + " no ha canjeado el código: " + e.getClass().getSimpleName(), e);
        }
        if (idToken == null || idToken.isBlank()) {
            throw new SsoException(SsoException.Motivo.FALLO, "El proveedor " + proveedor.id() + " no ha devuelto un ID token");
        }

        Jwt token;
        try {
            token = decodificadores.computeIfAbsent(proveedor, this::decodificadorDe).decode(idToken);
        } catch (Exception e) {
            throw new SsoException(SsoException.Motivo.FALLO,
                    "El ID token de " + proveedor.id() + " no es válido: " + e.getMessage(), e);
        }

        // El nonce no va con los demás validadores porque cambia en cada ida.
        String delToken = token.getClaimAsString("nonce");
        if (delToken == null || !MessageDigest.isEqual(
                delToken.getBytes(StandardCharsets.UTF_8), nonce.getBytes(StandardCharsets.UTF_8))) {
            throw new SsoException(SsoException.Motivo.FALLO, "El ID token de " + proveedor.id() + " no es de esta petición");
        }

        String sujeto = token.getSubject();
        if (sujeto == null || sujeto.isBlank()) {
            throw new SsoException(SsoException.Motivo.FALLO, "El ID token de " + proveedor.id() + " no trae sujeto");
        }
        String correo = token.getClaimAsString("email");
        correo = correo == null || correo.isBlank() ? null : Emails.normalizar(correo);
        return new VerifiedIdentity(proveedor, sujeto, correo, correo != null && correoGarantizado(proveedor, token));
    }

    /** Ver el Javadoc de la clase: es la regla que decide a quién se le cree el correo. */
    static boolean correoGarantizado(SsoProvider proveedor, Jwt token) {
        return switch (proveedor) {
            case GOOGLE -> Boolean.TRUE.equals(booleano(token, "email_verified"));
            case MICROSOFT -> Boolean.TRUE.equals(booleano(token, "xms_edov"))
                    || INQUILINO_PERSONAL.equalsIgnoreCase(token.getClaimAsString("tid"));
        };
    }

    /** Los proveedores mandan los booleanos a veces como booleano y a veces como texto. */
    private static Boolean booleano(Jwt token, String claim) {
        Object valor = token.getClaim(claim);
        if (valor instanceof Boolean b) {
            return b;
        }
        return valor instanceof String s ? Boolean.valueOf(s) : null;
    }

    private JwtDecoder decodificadorDe(SsoProvider proveedor) {
        SsoConfig.Proveedor datos = config.de(proveedor);
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withJwkSetUri(datos.jwkSetUri()).build();
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                paraEstaAplicacion(datos.clientId()),
                emitidoPor(datos.issuer())));
        return decodificador;
    }

    /** Un ID token de este proveedor pero pedido por OTRA aplicación no vale aquí. */
    static OAuth2TokenValidator<Jwt> paraEstaAplicacion(String clientId) {
        return token -> {
            List<String> audiencia = token.getAudience();
            return audiencia != null && audiencia.contains(clientId)
                    ? OAuth2TokenValidatorResult.success()
                    : rechazo("El token no es para esta aplicación");
        };
    }

    /**
     * El emisor. Google tiene uno fijo. Microsoft, uno por organización
     * ({@code …/{tenantid}/v2.0}): se compone con el {@code tid} del propio token,
     * exigiendo que sea un GUID --si no, cabría colar en él un trozo de URL--.
     * Componerlo con un dato del token no lo debilita: la firma ya ha
     * garantizado que el token lo emitió Microsoft, y esto comprueba que es
     * coherente consigo mismo.
     */
    static OAuth2TokenValidator<Jwt> emitidoPor(String esperado) {
        return token -> {
            String emisor = token.getClaimAsString("iss");
            if (emisor == null) {
                return rechazo("El token no dice quién lo emite");
            }
            String exigido = esperado;
            if (esperado.contains(MARCA_DE_INQUILINO)) {
                String inquilino = token.getClaimAsString("tid");
                if (inquilino == null || !GUID.matcher(inquilino).matches()) {
                    return rechazo("El token no trae un inquilino válido");
                }
                exigido = esperado.replace(MARCA_DE_INQUILINO, inquilino);
            }
            // Google lo manda con y sin «https://» según el caso; su documentación admite los dos.
            boolean coincide = emisor.equals(exigido) || ("https://" + emisor).equals(exigido);
            return coincide ? OAuth2TokenValidatorResult.success() : rechazo("El token lo emite otro");
        };
    }

    private static OAuth2TokenValidatorResult rechazo(String motivo) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", motivo, null));
    }
}
