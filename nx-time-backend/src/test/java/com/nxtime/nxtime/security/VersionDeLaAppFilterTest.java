package com.nxtime.nxtime.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Las versiones de la app que ya no se atienden (ADR 064). */
class VersionDeLaAppFilterTest {

    private static final String DESCARGA = "https://play.example/app";
    private final ObjectMapper json = new ObjectMapper();

    private final VersionDeLaAppFilter filtro =
            new VersionDeLaAppFilter(30, " com.viejo.app , otra.app ", DESCARGA, json);

    private MockHttpServletResponse pedir(VersionDeLaAppFilter conFiltro, String ruta, String paquete, String version)
            throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", ruta);
        peticion.setServletPath(ruta);
        if (paquete != null) {
            peticion.addHeader(VersionDeLaAppFilter.CABECERA_PAQUETE, paquete);
        }
        if (version != null) {
            peticion.addHeader(VersionDeLaAppFilter.CABECERA_VERSION, version);
        }
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        conFiltro.doFilter(peticion, respuesta, cadena);
        // Si ha pasado, la cadena ha recibido la petición; si no, se ha parado aquí.
        assertThat(cadena.getRequest() != null).isEqualTo(respuesta.getStatus() != 426);
        return respuesta;
    }

    /**
     * El cuerpo, con las propiedades del ProblemDetail arriba. El ObjectMapper
     * de Spring las aplana (lo comprueba VersionDeLaAppIT); uno hecho a mano,
     * como el de aquí, las deja en "properties".
     */
    private JsonNode cuerpo(MockHttpServletResponse respuesta) throws Exception {
        JsonNode todo = json.readTree(respuesta.getContentAsString());
        tools.jackson.databind.node.ObjectNode plano = ((tools.jackson.databind.node.ObjectNode) todo).deepCopy();
        if (todo.has("properties")) {
            todo.get("properties").properties().forEach(par -> plano.set(par.getKey(), par.getValue()));
        }
        return plano;
    }

    @Test
    @DisplayName("Una versión menor que la mínima recibe 426, con el motivo y dónde bajar la nueva")
    void versionVieja() throws Exception {
        MockHttpServletResponse respuesta = pedir(filtro, "/api/v1/fichaje/activo", "com.nxtime.app", "29");

        assertThat(respuesta.getStatus()).isEqualTo(426);
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        assertThat(cuerpo(respuesta).get("codigo").asText()).isEqualTo(VersionDeLaAppFilter.DESACTUALIZADA);
        assertThat(cuerpo(respuesta).get("urlDescarga").asText()).isEqualTo(DESCARGA);
        assertThat(cuerpo(respuesta).get("detail").asText()).contains("Actualízala");
    }

    @Test
    @DisplayName("La mínima y las de después pasan")
    void versionAlDia() throws Exception {
        assertThat(pedir(filtro, "/api/v1/fichaje/activo", "com.nxtime.app", "30").getStatus()).isEqualTo(200);
        assertThat(pedir(filtro, "/api/v1/fichaje/activo", "com.nxtime.app", "31").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Un paquete retirado recibe 426 aunque su versión sea nueva, también su variante de desarrollo")
    void paqueteRetirado() throws Exception {
        MockHttpServletResponse respuesta = pedir(filtro, "/api/v1/ausencias", "com.viejo.app", "99");
        assertThat(respuesta.getStatus()).isEqualTo(426);
        assertThat(cuerpo(respuesta).get("codigo").asText()).isEqualTo(VersionDeLaAppFilter.RETIRADA);
        assertThat(cuerpo(respuesta).get("detail").asText()).contains("ya no se usa");

        assertThat(pedir(filtro, "/api/v1/ausencias", "com.viejo.app.dev", "99").getStatus()).isEqualTo(426);
        assertThat(pedir(filtro, "/api/v1/ausencias", "otra.app", "99").getStatus()).isEqualTo(426);
    }

    @Test
    @DisplayName("Entrar, renovar la sesión y mandar los fichajes sin conexión siguen abiertos para una app vieja")
    void loAbierto() throws Exception {
        for (String ruta : new String[] {"/auth/login", "/auth/refresh", "/auth/logout", "/api/v1/fichaje/sin-conexion"}) {
            assertThat(pedir(filtro, ruta, "com.viejo.app", "1").getStatus()).as(ruta).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("Sin cabeceras (la web, el kiosco) o con una versión que no es un número, no hace nada")
    void sinCabeceras() throws Exception {
        assertThat(pedir(filtro, "/api/v1/fichaje/activo", null, null).getStatus()).isEqualTo(200);
        assertThat(pedir(filtro, "/api/v1/fichaje/activo", "com.nxtime.app", "1.26").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Con la mínima a 0 y sin paquetes retirados, todo pasa")
    void apagado() throws Exception {
        VersionDeLaAppFilter apagado = new VersionDeLaAppFilter(0, "", "", json);
        assertThat(pedir(apagado, "/api/v1/fichaje/activo", "com.viejo.app", "1").getStatus()).isEqualTo(200);
    }
}
