package com.nxtime.nxtime.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * De dónde saca el limitador la IP, que resultó importar tanto como el límite.
 *
 * El defecto: se cogía el PRIMER valor de X-Forwarded-For. Esa cabecera se lee
 * "quien llamó, y luego cada proxy por el que pasó", así que el primer valor lo
 * escribe quien llama y se lo puede inventar. Bastaba con mandar una IP falsa
 * distinta en cada intento para estrenar contador cada vez.
 *
 * Comprobado contra producción antes de arreglarlo: sin cabecera, el 429 en el
 * intento 11; con una IP inventada por intento, quince intentos y ningún 429.
 */
class LoginRateLimitFilterTest {

    private static final int LIMITE = 10;
    private static final int ENTRADAS = 60;

    /** Lo que no es entrar: aquí, pedir un código para recuperar el acceso. */
    private static final String OTRA_RUTA = "/auth/recuperar";

    private MockHttpServletResponse intentar(LoginRateLimitFilter filtro, String cabecera) throws Exception {
        return intentar(filtro, OTRA_RUTA, cabecera);
    }

    private MockHttpServletResponse intentar(LoginRateLimitFilter filtro, String ruta, String cabecera)
            throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", ruta);
        peticion.setServletPath(ruta);
        peticion.setRemoteAddr("198.51.100.7");
        if (cabecera != null) {
            peticion.addHeader("X-Forwarded-For", cabecera);
        }
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        FilterChain cadena = new MockFilterChain();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta;
    }

    @Test
    @DisplayName("Con un proxy delante, la IP es la que puso el proxy: inventarse la primera no da contador nuevo")
    void conUnProxy_laIpEsLaDelUltimoSalto() throws Exception {
        LoginRateLimitFilter filtro = new LoginRateLimitFilter(new ObjectMapper(), 1);

        // Diez intentos, cada uno diciendo venir de una IP distinta. El
        // proxy (la última entrada) es siempre el mismo, así que todos caen
        // en el mismo contador.
        for (int i = 0; i < LIMITE; i++) {
            MockHttpServletResponse ok = intentar(filtro, "203.0.113." + i + ", 198.51.100.7");
            assertThat(ok.getStatus()).as("intento %d", i + 1).isEqualTo(HttpStatus.OK.value());
        }

        MockHttpServletResponse pasada = intentar(filtro, "203.0.113.99, 198.51.100.7");
        assertThat(pasada.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("Sin proxies de confianza se usa la IP de la conexión y la cabecera se ignora")
    void sinProxies_seIgnoraLaCabecera() throws Exception {
        LoginRateLimitFilter filtro = new LoginRateLimitFilter(new ObjectMapper(), 0);

        for (int i = 0; i < LIMITE; i++) {
            assertThat(intentar(filtro, "203.0.113." + i).getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        assertThat(intentar(filtro, "203.0.113.99").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("El límite se puede subir (solo lo hace el perfil dev), y sigue cortando al pasarlo")
    void limiteConfigurado_cortaAlPasarlo() throws Exception {
        LoginRateLimitFilter filtro = new LoginRateLimitFilter(new ObjectMapper(), 0, LIMITE + 5, ENTRADAS);

        for (int i = 0; i < LIMITE + 5; i++) {
            assertThat(intentar(filtro, "203.0.113.1").getStatus()).as("intento %d", i + 1).isEqualTo(HttpStatus.OK.value());
        }
        assertThat(intentar(filtro, "203.0.113.1").getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("Dos IPs distintas de verdad tienen cada una su contador")
    void distintasIpsReales_contadoresDistintos() throws Exception {
        LoginRateLimitFilter filtro = new LoginRateLimitFilter(new ObjectMapper(), 1);

        for (int i = 0; i < LIMITE; i++) {
            intentar(filtro, "203.0.113.1, 198.51.100.7");
        }
        assertThat(intentar(filtro, "203.0.113.1, 198.51.100.7").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        // Otro visitante, detrás del mismo proxy pero con otra salida real.
        assertThat(intentar(filtro, "203.0.113.1, 198.51.100.8").getStatus())
                .as("a quien no ha gastado su cupo no se le castiga")
                .isEqualTo(HttpStatus.OK.value());
    }

    /*
     * Entrar tiene su propio cupo (ADR 041). Con diez para todo, una oficina de
     * más de diez personas no podía entrar a la vez: salen todas por la misma
     * IP. En la prueba de carga, de cuarenta, treinta se llevaron un 429.
     */
    @Test
    @DisplayName("Una oficina entera entra desde la misma IP: sesenta entradas pasan, la siguiente no")
    void entrar_tieneSuPropioCupo() throws Exception {
        LoginRateLimitFilter filtro = new LoginRateLimitFilter(new ObjectMapper(), 1);

        for (int i = 0; i < ENTRADAS; i++) {
            assertThat(intentar(filtro, "/auth/login", "203.0.113.1, 198.51.100.7").getStatus())
                    .as("entrada %d", i + 1).isEqualTo(HttpStatus.OK.value());
        }
        assertThat(intentar(filtro, "/auth/login", "203.0.113.1, 198.51.100.7").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("Entrar no gasta el cupo de lo demás, ni al revés: registrar y pedir códigos siguen en diez")
    void losDosCuposNoSeGastanElUnoAlOtro() throws Exception {
        LoginRateLimitFilter filtro = new LoginRateLimitFilter(new ObjectMapper(), 1);
        String ip = "203.0.113.1, 198.51.100.7";

        // Veinte entradas no tocan el cupo de pedir códigos...
        for (int i = 0; i < 20; i++) {
            assertThat(intentar(filtro, "/auth/login", ip).getStatus()).isEqualTo(HttpStatus.OK.value());
        }
        // ...que sigue siendo de diez, y lo comparten todas las rutas que no son entrar.
        for (int i = 0; i < LIMITE / 2; i++) {
            assertThat(intentar(filtro, "/auth/recuperar", ip).getStatus()).isEqualTo(HttpStatus.OK.value());
            assertThat(intentar(filtro, "/auth/register-manager", ip).getStatus()).isEqualTo(HttpStatus.OK.value());
        }
        assertThat(intentar(filtro, "/auth/recuperar", ip).getStatus())
                .as("la undécima petición de lo que manda correos")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        // Y quien ha gastado ese cupo todavía puede entrar.
        assertThat(intentar(filtro, "/auth/login", ip).getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("Entrar con Google o con Microsoft cuenta en el mismo cupo que entrar con contraseña")
    void elSsoEsEntrar() throws Exception {
        LoginRateLimitFilter filtro = new LoginRateLimitFilter(new ObjectMapper(), 1, LIMITE, 3);
        String ip = "203.0.113.1, 198.51.100.7";

        assertThat(intentar(filtro, "/auth/login", ip).getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(intentar(filtro, "/auth/sso/google/vuelta", ip).getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(intentar(filtro, "/auth/sso/canjear", ip).getStatus()).isEqualTo(HttpStatus.OK.value());

        assertThat(intentar(filtro, "/auth/sso/microsoft/vuelta", ip).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("Las rutas que no son de autenticación no pasan por el límite")
    void otrasRutas_noSeLimitan() throws Exception {
        LoginRateLimitFilter filtro = new LoginRateLimitFilter(new ObjectMapper(), 1);

        for (int i = 0; i < LIMITE + 5; i++) {
            MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/fichaje/activo");
            peticion.setServletPath("/api/v1/fichaje/activo");
            peticion.setRemoteAddr("198.51.100.7");
            MockHttpServletResponse respuesta = new MockHttpServletResponse();
            filtro.doFilter(peticion, respuesta, new MockFilterChain());
            assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.OK.value());
        }
    }
}
