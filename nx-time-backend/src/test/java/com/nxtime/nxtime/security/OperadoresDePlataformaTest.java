package com.nxtime.nxtime.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;

/**
 * Quién puede ver la instalación entera (ADR 040).
 *
 * Lo que importa de esta clase es a quién NO se lo da: es el único permiso que
 * cruza todas las empresas, y un descuido aquí enseña el contacto de los ADMIN
 * de todas a quien no toca.
 */
class OperadoresDePlataformaTest {

    private static final String OPERADORA = "miguel@nxtime.test";

    private static User cuenta(String email, Role rol) {
        return User.builder().id(1L).email(email).nombre("Alguien").rol(rol).activo(true).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ",", " , ,"})
    @DisplayName("Con la lista vacía no lo tiene nadie, ni un ADMIN")
    void listaVacia_nadie(String lista) {
        OperadoresDePlataforma operadores = new OperadoresDePlataforma(lista);
        User admin = cuenta(OPERADORA, Role.ADMIN);

        assertThat(operadores.es(admin)).isFalse();
        assertThat(operadores.authoritiesDe(admin)).isEqualTo(RoleAuthorities.enOrden(Role.ADMIN));
        assertThat(operadores.principalDe(admin).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .doesNotContain(RoleAuthorities.PLATAFORMA_VER);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Quien está en la lista lo tiene, sea del rol que sea, además de lo de su rol")
    void enLaLista_loTiene(Role rol) {
        OperadoresDePlataforma operadores = new OperadoresDePlataforma("otra@nxtime.test, " + OPERADORA);
        User cuenta = cuenta(OPERADORA, rol);

        assertThat(operadores.es(cuenta)).isTrue();
        assertThat(operadores.authoritiesDe(cuenta))
                .isSorted()
                .contains(RoleAuthorities.PLATAFORMA_VER)
                .containsAll(RoleAuthorities.forRole(rol))
                .hasSize(RoleAuthorities.forRole(rol).size() + 1);
        // Lo que autoriza el servidor y lo que se le enseña al cliente, lo mismo.
        assertThat(operadores.principalDe(cuenta).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrderElementsOf(operadores.authoritiesDe(cuenta));
    }

    @Test
    @DisplayName("Quien no está en la lista no lo tiene, aunque sea ADMIN de su empresa")
    void fueraDeLaLista_noLoTiene() {
        OperadoresDePlataforma operadores = new OperadoresDePlataforma(OPERADORA);
        User admin = cuenta("admin@otra-empresa.test", Role.ADMIN);

        assertThat(operadores.es(admin)).isFalse();
        assertThat(operadores.authoritiesDe(admin)).isEqualTo(RoleAuthorities.enOrden(Role.ADMIN));
        assertThat(operadores.principalDe(admin).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrderElementsOf(RoleAuthorities.forRole(Role.ADMIN));
    }

    @Test
    @DisplayName("Da igual cómo se escriba el correo en la variable: mayúsculas y espacios")
    void elCorreoSeNormaliza() {
        OperadoresDePlataforma operadores = new OperadoresDePlataforma("  Miguel@NXTime.Test  ,otra@nxtime.test");

        assertThat(operadores.es(cuenta(OPERADORA, Role.EMPLEADO))).isTrue();
        assertThat(operadores.es(cuenta("OTRA@nxtime.test", Role.EMPLEADO))).isTrue();
    }

    @Test
    @DisplayName("Un correo que solo se parece no vale: ni un prefijo ni un subdominio")
    void elCorreoTieneQueSerElMismo() {
        OperadoresDePlataforma operadores = new OperadoresDePlataforma(OPERADORA);

        assertThat(operadores.es(cuenta("miguel@nxtime.test.evil.example", Role.ADMIN))).isFalse();
        assertThat(operadores.es(cuenta("xmiguel@nxtime.test", Role.ADMIN))).isFalse();
        assertThat(operadores.es(cuenta("miguel+algo@nxtime.test", Role.ADMIN))).isFalse();
    }

    @Test
    @DisplayName("Una cuenta de baja o con el correo sin confirmar no lo tiene aunque esté en la lista")
    void deBajaOSinConfirmar_noLoTiene() {
        OperadoresDePlataforma operadores = new OperadoresDePlataforma(OPERADORA);

        User deBaja = cuenta(OPERADORA, Role.ADMIN);
        deBaja.setActivo(false);
        assertThat(operadores.es(deBaja)).isFalse();

        // Quien registra una empresa con el correo de un operador no ha
        // demostrado que el buzón sea suyo.
        User sinConfirmar = cuenta(OPERADORA, Role.ADMIN);
        sinConfirmar.setCorreoSinConfirmarDesde(Instant.parse("2026-10-09T10:00:00Z"));
        assertThat(operadores.es(sinConfirmar)).isFalse();
        assertThat(operadores.authoritiesDe(sinConfirmar)).doesNotContain(RoleAuthorities.PLATAFORMA_VER);
    }

    @Test
    @DisplayName("Sin cuenta o sin correo no revienta: no lo tiene")
    void sinCuenta() {
        OperadoresDePlataforma operadores = new OperadoresDePlataforma(OPERADORA);

        assertThat(operadores.es(null)).isFalse();
        assertThat(operadores.es(cuenta(null, Role.ADMIN))).isFalse();
    }
}
