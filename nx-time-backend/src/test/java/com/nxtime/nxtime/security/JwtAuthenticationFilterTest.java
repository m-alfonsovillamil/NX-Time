package com.nxtime.nxtime.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Qué responde el filtro del token cuando no puede comprobar una sesión (ADR 041).
 *
 * Hay dos motivos y no son el mismo: que el token no valga, y que no se haya
 * podido mirar. Hasta octubre de 2026 los dos acababan en 401. En la prueba de
 * carga, con las cinco conexiones cogidas, doce peticiones de gente con la
 * sesión en regla se llevaron un 401: para un cliente eso es «vuelve a
 * entrar», y entrar es justo lo más caro (docs/PRUEBA-DE-CARGA.md).
 */
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserDetailsService usuarios = mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filtro =
            new JwtAuthenticationFilter(jwtService, usuarios, new ObjectMapper());

    @BeforeEach
    void unTokenQueDiceSerDeAna() {
        when(jwtService.extractUsername(any())).thenReturn("ana@nxtime.test");
    }

    @AfterEach
    void sinSesion() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse pedir(MockFilterChain cadena) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/fichaje/activo");
        peticion.setServletPath("/api/v1/fichaje/activo");
        peticion.addHeader("Authorization", "Bearer un-token");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, cadena);
        return respuesta;
    }

    @Test
    @DisplayName("Sin conexiones libres en la base: 503 con Retry-After, y la petición no sigue")
    void sinConexiones_503() throws Exception {
        when(usuarios.loadUserByUsername("ana@nxtime.test")).thenThrow(new DataAccessResourceFailureException(
                "Unable to acquire JDBC Connection [HikariPool-1 - Connection is not available]"));
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse respuesta = pedir(cadena);

        assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(respuesta.getHeader("Retry-After")).isEqualTo("5");
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        assertThat(respuesta.getContentAsString()).contains("\"status\":503").contains("ocupado");
        // Si siguiera, Spring Security respondería 401 más abajo: es lo que se quita.
        assertThat(cadena.getRequest()).as("la cadena no continúa").isNull();
    }

    @Test
    @DisplayName("Si ni siquiera se puede abrir la transacción, también 503")
    void sinTransaccion_503() throws Exception {
        when(usuarios.loadUserByUsername("ana@nxtime.test"))
                .thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"));
        MockFilterChain cadena = new MockFilterChain();

        assertThat(pedir(cadena).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(cadena.getRequest()).isNull();
    }

    @Test
    @DisplayName("Un token de alguien que ya no existe sigue siendo cosa del token: pasa sin autenticar (401 más abajo)")
    void usuarioQueNoExiste_noEsUn503() throws Exception {
        when(usuarios.loadUserByUsername("ana@nxtime.test")).thenThrow(new UsernameNotFoundException("no está"));
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse respuesta = pedir(cadena);

        assertThat(respuesta.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(cadena.getRequest()).as("la cadena continúa, sin nadie autenticado").isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Un token corrupto, igual: no es un problema de la base")
    void tokenCorrupto_noEsUn503() throws Exception {
        when(jwtService.extractUsername(any())).thenThrow(new IllegalArgumentException("JWT mal formado"));
        MockFilterChain cadena = new MockFilterChain();

        assertThat(pedir(cadena).getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(cadena.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Con la base bien y el token bueno, la petición sigue con la persona autenticada")
    void todoBien_autentica() throws Exception {
        SecurityUser ana = new SecurityUser(
                User.builder().id(1L).email("ana@nxtime.test").rol(Role.EMPLEADO).activo(true).build());
        when(usuarios.loadUserByUsername("ana@nxtime.test")).thenReturn(ana);
        when(jwtService.isTokenValid("un-token", ana)).thenReturn(true);
        MockFilterChain cadena = new MockFilterChain();

        assertThat(pedir(cadena).getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(cadena.getRequest()).isNotNull();
    }
}
