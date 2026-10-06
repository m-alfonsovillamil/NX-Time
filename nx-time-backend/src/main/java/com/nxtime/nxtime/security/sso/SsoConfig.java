package com.nxtime.nxtime.security.sso;

import com.nxtime.nxtime.domain.SsoProvider;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * La configuración del SSO (ADR 036), leída de {@code application.security.sso}.
 *
 * <b>Apagado por defecto.</b> Un proveedor solo existe si están sus dos
 * credenciales y las dos URL públicas; si falta algo, no sale en
 * {@code /auth/sso/proveedores}, la web y la app no pintan su botón y sus rutas
 * responden que no está disponible. Así desplegar esto antes de dar de alta la
 * aplicación en Google o en Microsoft no rompe nada.
 *
 * <b>Por qué hay una URL pública escrita a mano.</b> Detrás del proxy de Render
 * la petición llega a Tomcat por HTTP y con otro nombre de host, y el proyecto
 * no activa {@code server.forward-headers-strategy} a propósito (ver
 * {@code SecurityConfig}: reabriría el agujero del {@code X-Forwarded-For}). Así
 * que el backend no puede deducir su dirección de la petición, y la URL de
 * vuelta que se registra en el proveedor tiene que coincidir letra a letra.
 */
@Component
public class SsoConfig {

    private static final String PREFIJO = "application.security.sso.";

    private final Environment entorno;

    public SsoConfig(Environment entorno) {
        this.entorno = entorno;
    }

    /** Lo que hace falta saber de un proveedor para hablar con él. */
    public record Proveedor(
            String clientId,
            String clientSecret,
            String authorizationUri,
            String tokenUri,
            String jwkSetUri,
            /** El emisor esperado. Con {@code {tenantid}} si es uno por organización (Microsoft). */
            String issuer) {
    }

    public List<SsoProvider> activos() {
        return Arrays.stream(SsoProvider.values()).filter(this::activo).toList();
    }

    public boolean activo(SsoProvider proveedor) {
        return !urlPublica().isEmpty() && !urlWeb().isEmpty()
                && !valor(proveedor.id() + ".client-id").isEmpty()
                && !valor(proveedor.id() + ".client-secret").isEmpty();
    }

    public Proveedor de(SsoProvider proveedor) {
        String base = proveedor.id() + ".";
        return new Proveedor(
                valor(base + "client-id"),
                valor(base + "client-secret"),
                valor(base + "authorization-uri"),
                valor(base + "token-uri"),
                valor(base + "jwk-set-uri"),
                valor(base + "issuer"));
    }

    /** La dirección pública de esta API, sin barra final: {@code https://api.nxtime-web.com}. */
    public String urlPublica() {
        return sinBarraFinal(valor("url-publica"));
    }

    /** La de la web, adonde vuelve el navegador al terminar: {@code https://nxtime-web.com}. */
    public String urlWeb() {
        return sinBarraFinal(valor("url-web"));
    }

    /** La URL de vuelta que hay que registrar en el proveedor. */
    public String urlDeVuelta(SsoProvider proveedor) {
        return urlPublica() + "/auth/sso/" + proveedor.id() + "/vuelta";
    }

    public String urlDeInicio(SsoProvider proveedor) {
        return urlPublica() + "/auth/sso/" + proveedor.id() + "/iniciar";
    }

    private String valor(String clave) {
        String valor = entorno.getProperty(PREFIJO + clave);
        return valor == null ? "" : valor.trim();
    }

    private static String sinBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
