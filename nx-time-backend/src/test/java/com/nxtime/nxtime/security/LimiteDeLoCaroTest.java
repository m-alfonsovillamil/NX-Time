package com.nxtime.nxtime.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * El tope de peticiones por cuenta para lo que cuesta (ADR 039).
 *
 * Lo que tiene que sostener: que lo caro tenga tope y lo de todos los días no,
 * que el cupo sea de cada cuenta, y que a quien llega sin sesión no se le
 * responda aquí (para eso está el 401 de más adelante).
 */
class LimiteDeLoCaroTest {

    private static final int LIMITE = 3;

    private final LimiteDeLoCaro filtro = new LimiteDeLoCaro(new ObjectMapper(), LIMITE);

    @AfterEach
    void sinSesion() {
        SecurityContextHolder.clearContext();
    }

    private static void conSesionDe(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(new SimpleGrantedAuthority("fichaje:auditoria"))));
    }

    /** Pide esa ruta y dice si la petición llegó al controlador. */
    private MockHttpServletResponse pedir(String ruta, MockFilterChain cadena) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", ruta);
        peticion.setServletPath(ruta);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta;
    }

    private boolean pasa(String ruta) throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        pedir(ruta, cadena);
        return cadena.getRequest() != null;
    }

    @Test
    @DisplayName("Pasado el cupo responde 429 con Retry-After y la petición no llega al controlador")
    void pasadoElCupo_429() throws Exception {
        conSesionDe("ana@nxtime.test");
        for (int i = 0; i < LIMITE; i++) {
            assertThat(pasa("/api/v1/auditoria/integridad")).isTrue();
        }

        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse respuesta = pedir("/api/v1/auditoria/integridad", cadena);

        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(respuesta.getHeader("Retry-After")).isEqualTo("60");
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        assertThat(respuesta.getContentAsString()).contains("demasiadas veces");
        assertThat(cadena.getRequest()).as("no llega al controlador").isNull();
    }

    @Test
    @DisplayName("El cupo es uno para todo lo caro: no vale repartir las peticiones entre informes y analítica")
    void elCupoEsComunATodoLoCaro() throws Exception {
        conSesionDe("ana@nxtime.test");
        assertThat(pasa("/api/v1/informes/horas")).isTrue();
        assertThat(pasa("/api/v1/analitica/resumen")).isTrue();
        assertThat(pasa("/api/v1/perfil/mis-datos/pdf")).isTrue();

        assertThat(pasa("/api/v1/auditoria/integridad/ultima")).isFalse();
    }

    @Test
    @DisplayName("El panel de plataforma es de lo caro: sus cuatro rutas recorren todas las empresas")
    void elPanelDePlataformaEsCaro() {
        for (String ruta : List.of("/api/v1/plataforma/resumen", "/api/v1/plataforma/empresas",
                "/api/v1/plataforma/empresas/7", "/api/v1/plataforma/integridad")) {
            assertThat(LimiteDeLoCaro.esCara(ruta)).as(ruta).isTrue();
        }
    }

    @Test
    @DisplayName("El cupo es de cada cuenta: que una lo gaste no deja sin él a otra")
    void elCupoEsDeCadaCuenta() throws Exception {
        conSesionDe("ana@nxtime.test");
        for (int i = 0; i < LIMITE; i++) {
            pasa("/api/v1/informes/horas");
        }
        assertThat(pasa("/api/v1/informes/horas")).isFalse();

        conSesionDe("javi@nxtime.test");
        assertThat(pasa("/api/v1/informes/horas")).isTrue();
    }

    /** Fichar es lo que no puede quedar bloqueado nunca, y lo de todos los días tampoco. */
    @Test
    @DisplayName("Lo de todos los días no tiene tope: fichar, el panel, el perfil y la traza de un fichaje")
    void loDeTodosLosDiasNoTieneTope() throws Exception {
        conSesionDe("ana@nxtime.test");
        for (String ruta : List.of("/api/v1/fichaje", "/api/v1/fichaje/activo", "/api/v1/dashboard",
                "/api/v1/perfil", "/api/v1/perfil/identidades", "/api/v1/auditoria/fichaje/5", "/auth/login")) {
            assertThat(LimiteDeLoCaro.esCara(ruta)).as(ruta).isFalse();
            for (int i = 0; i < LIMITE * 3; i++) {
                assertThat(pasa(ruta)).as(ruta).isTrue();
            }
        }
    }

    @Test
    @DisplayName("Sin sesión no responde aquí: deja pasar, y el 401 lo da quien tiene que darlo")
    void sinSesion_dejaPasar() throws Exception {
        for (int i = 0; i < LIMITE * 3; i++) {
            assertThat(pasa("/api/v1/informes/horas")).isTrue();
        }

        // Y con la sesión anónima que pone Spring Security, igual.
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "clave", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        for (int i = 0; i < LIMITE * 3; i++) {
            assertThat(pasa("/api/v1/informes/horas")).isTrue();
        }
    }
}
