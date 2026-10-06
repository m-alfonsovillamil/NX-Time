package com.nxtime.nxtime.security.sso;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.SsoProvider;
import com.nxtime.nxtime.security.sso.SsoState.Estado;
import com.nxtime.nxtime.security.sso.SsoState.Modo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Las piezas sueltas del SSO (ADR 036), en lo que {@code SsoIT} no alcanza
 * porque depende del reloj o de la configuración: que las cosas caduquen y que
 * un proveedor a medio configurar no exista.
 */
class SsoPiecesTest {

    private static final String CLAVE =
            "PXPGUXRppB7TpSN+W65Sl9qIKhKIOS6vxftZBZLCI4iIuCPt4CzTchUL4BFScNcgXWhlQess0tQaSOy8PABnRQ==";
    private static final String OTRA_CLAVE =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";

    private final SsoState estados = new SsoState(new ObjectMapper(), CLAVE);

    private static Estado estado(long expira) {
        return new Estado(SsoProvider.GOOGLE, Modo.APP, "el-state", "el-nonce", "el-verificador", "el-reto", 7L, expira);
    }

    private static long dentroDe(Duration cuanto) {
        return Instant.now().plus(cuanto).getEpochSecond();
    }

    // ------------------------------------------------------------------
    // La cookie de estado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Lo sellado se abre tal cual")
    void selladoYAbierto() {
        Estado original = estado(dentroDe(Duration.ofMinutes(5)));

        assertThat(estados.abrir(estados.sellar(original))).contains(original);
    }

    @Test
    @DisplayName("Un estado caducado no se abre, aunque la firma sea buena")
    void caducado() {
        assertThat(estados.abrir(estados.sellar(estado(dentroDe(Duration.ofSeconds(-1)))))).isEmpty();
    }

    @Test
    @DisplayName("Lo sellado con otra clave no se abre")
    void otraClave() {
        String sellado = new SsoState(new ObjectMapper(), OTRA_CLAVE).sellar(estado(dentroDe(Duration.ofMinutes(5))));

        assertThat(estados.abrir(sellado)).isEmpty();
    }

    @Test
    @DisplayName("Un contenido escrito a mano con la firma de otro no se abre")
    void contenidoCambiado() {
        String bueno = estados.sellar(estado(dentroDe(Duration.ofMinutes(5))));
        String firma = bueno.substring(bueno.lastIndexOf('.') + 1);
        // El mismo estado, pero para el usuario 8.
        String cuerpo = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(bueno.substring(0, bueno.lastIndexOf('.'))))
                        .replace("\"usuarioId\":7", "\"usuarioId\":8").getBytes());

        assertThat(estados.abrir(cuerpo + "." + firma)).isEmpty();
    }

    @Test
    @DisplayName("Lo que no tiene forma de estado no rompe nada")
    void basura() {
        assertThat(estados.abrir(null)).isEmpty();
        assertThat(estados.abrir("")).isEmpty();
        assertThat(estados.abrir("sin-punto")).isEmpty();
        assertThat(estados.abrir(".solo-firma")).isEmpty();
        assertThat(estados.abrir("no-es-base64!!.firma")).isEmpty();
    }

    // ------------------------------------------------------------------
    // Los códigos de la app
    // ------------------------------------------------------------------

    /** Un reloj que el test mueve a mano. */
    private static Clock relojDe(AtomicReference<Instant> ahora) {
        return new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zona) {
                return this;
            }

            @Override
            public Instant instant() {
                return ahora.get();
            }
        };
    }

    @Test
    @DisplayName("Un código se canjea con su verificador, una sola vez")
    void codigoDeUnSoloUso() {
        AppExchangeCodes codigos = new AppExchangeCodes();
        String codigo = codigos.emitir(42, AppExchangeCodes.retoDe("mi-verificador"));

        assertThat(codigos.canjear(codigo, "mi-verificador")).contains(42L);
        assertThat(codigos.canjear(codigo, "mi-verificador")).isEmpty();
    }

    @Test
    @DisplayName("Pasado el minuto, el código ya no vale")
    void codigoCaducado() {
        AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-06T10:00:00Z"));
        AppExchangeCodes codigos = new AppExchangeCodes(relojDe(ahora));
        String aTiempo = codigos.emitir(42, AppExchangeCodes.retoDe("mi-verificador"));
        String tarde = codigos.emitir(42, AppExchangeCodes.retoDe("mi-verificador"));

        ahora.set(ahora.get().plus(AppExchangeCodes.VIDA).minusSeconds(1));
        assertThat(codigos.canjear(aTiempo, "mi-verificador")).contains(42L);

        ahora.set(ahora.get().plusSeconds(1));
        assertThat(codigos.canjear(tarde, "mi-verificador")).isEmpty();
    }

    @Test
    @DisplayName("Sin código o sin verificador no hay nada que canjear")
    void codigoSinDatos() {
        AppExchangeCodes codigos = new AppExchangeCodes();
        String codigo = codigos.emitir(42, AppExchangeCodes.retoDe("mi-verificador"));

        assertThat(codigos.canjear(null, "mi-verificador")).isEmpty();
        assertThat(codigos.canjear(codigo, null)).isEmpty();
        // Y no se ha gastado por preguntar mal.
        assertThat(codigos.canjear(codigo, "mi-verificador")).contains(42L);
    }

    @Test
    @DisplayName("El reto es el de PKCE: el ejemplo de la RFC 7636")
    void retoDePkce() {
        assertThat(AppExchangeCodes.retoDe("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    // ------------------------------------------------------------------
    // La configuración
    // ------------------------------------------------------------------

    private static MockEnvironment entornoCompleto() {
        return new MockEnvironment()
                .withProperty("application.security.sso.url-publica", "https://api.nxtime-web.com/")
                .withProperty("application.security.sso.url-web", "https://nxtime-web.com")
                .withProperty("application.security.sso.google.client-id", "id")
                .withProperty("application.security.sso.google.client-secret", "secreto");
    }

    @Test
    @DisplayName("Un proveedor existe con sus dos credenciales y las dos URL; el que no las tiene, no")
    void proveedorActivo() {
        SsoConfig config = new SsoConfig(entornoCompleto());

        assertThat(config.activos()).containsExactly(SsoProvider.GOOGLE);
        // Sin barra final, la escriban como la escriban.
        assertThat(config.urlDeVuelta(SsoProvider.GOOGLE)).isEqualTo("https://api.nxtime-web.com/auth/sso/google/vuelta");
        assertThat(config.urlDeInicio(SsoProvider.GOOGLE)).isEqualTo("https://api.nxtime-web.com/auth/sso/google/iniciar");
    }

    @Test
    @DisplayName("Si falta el secreto, o cualquiera de las dos URL, no hay proveedores")
    void proveedorAMedias() {
        assertThat(new SsoConfig(new MockEnvironment()).activos()).isEmpty();
        assertThat(new SsoConfig(entornoCompleto()
                .withProperty("application.security.sso.google.client-secret", "  ")).activos()).isEmpty();
        assertThat(new SsoConfig(entornoCompleto()
                .withProperty("application.security.sso.url-publica", "")).activos()).isEmpty();
        assertThat(new SsoConfig(entornoCompleto()
                .withProperty("application.security.sso.url-web", "")).activos()).isEmpty();
    }

    @Test
    @DisplayName("Los proveedores se reconocen por su id en minúsculas, y nada más")
    void proveedorPorId() {
        assertThat(SsoProvider.deId("google")).contains(SsoProvider.GOOGLE);
        assertThat(SsoProvider.deId("microsoft")).contains(SsoProvider.MICROSOFT);
        assertThat(SsoProvider.deId("GOOGLE")).isEmpty();
        assertThat(SsoProvider.deId("facebook")).isEmpty();
        assertThat(SsoProvider.deId(null)).isEmpty();
    }
}
