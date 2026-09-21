package com.nxtime.nxtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * El reparto de authorities, que desde la Fase C3 <b>viaja al cliente</b>.
 *
 * Hasta septiembre de 2026 esta clase solo alimentaba los {@code @PreAuthorize}
 * del servidor, y cada cliente se la copiaba: {@code ui/util/Permisos.kt} era un
 * espejo a mano de estos conjuntos, y la web habría traído un tercero en
 * TypeScript. Ahora el servidor manda la lista resuelta en el login y en el
 * perfil, y los clientes pintan su menú con ella.
 *
 * Eso convierte este fichero en un contrato público, y le añade dos exigencias
 * que antes no tenía: que la lista salga <b>estable</b> y que no nombre
 * authorities que no existan en ninguna parte.
 */
class RoleAuthoritiesTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("enOrden dice exactamente lo mismo que forRole, solo que ordenado")
    void enOrden_mismosElementosQueForRole(Role rol) {
        assertThat(RoleAuthorities.enOrden(rol))
                .containsExactlyInAnyOrderElementsOf(RoleAuthorities.forRole(rol))
                .isSorted();
    }

    /**
     * Los {@code Set.of} no garantizan orden de iteración, y sin ordenar la
     * misma persona recibiría los mismos permisos en distinto orden entre dos
     * peticiones. No rompe a un cliente sensato, pero hace ilegible cualquier
     * diff del contrato y vuelve intermitente cualquier test que compare el
     * JSON entero.
     */
    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("La lista es la misma en dos llamadas seguidas")
    void enOrden_esEstable(Role rol) {
        assertThat(RoleAuthorities.enOrden(rol)).isEqualTo(RoleAuthorities.enOrden(rol));
    }

    @Test
    @DisplayName("La jerarquía se cumple: cada rol puede todo lo del anterior")
    void laJerarquiaSeCumple() {
        List<Role> jerarquia = List.of(Role.EMPLEADO, Role.GESTOR, Role.RRHH, Role.ADMIN);

        for (int i = 1; i < jerarquia.size(); i++) {
            assertThat(RoleAuthorities.forRole(jerarquia.get(i)))
                    .as("%s hereda de %s", jerarquia.get(i), jerarquia.get(i - 1))
                    .containsAll(RoleAuthorities.forRole(jerarquia.get(i - 1)));
        }
    }

    @Test
    @DisplayName("Un EMPLEADO no recibe permisos de gestión en su lista")
    void elEmpleadoNoRecibeLoQueNoPuede() {
        assertThat(RoleAuthorities.enOrden(Role.EMPLEADO))
                .contains("fichaje:escribir", "correccion:solicitar", "denuncia:crear")
                .doesNotContain("ausencia:aprobar", "gestor:crear", "denuncia:instruir");
    }

    /**
     * El test que impide una pantalla inalcanzable.
     *
     * Un {@code @PreAuthorize("hasAuthority('fichaje:corrgir')")} con una errata
     * compila, arranca y responde 403 a <b>todo el mundo, siempre</b>, porque
     * esa authority no se la concede este fichero a nadie. Nada lo detectaba:
     * el endpoint existe, el OpenAPI lo documenta y los tests de ese
     * controlador suelen simular el contexto de seguridad.
     *
     * Ahora además el cliente arma su menú con esta lista, así que la errata
     * sería doblemente invisible: ni el botón aparecería ni nadie sabría por
     * qué.
     *
     * Lee el fuente en vez de las anotaciones porque Spring evalúa esas
     * expresiones como SpEL en tiempo de ejecución: no hay forma de
     * preguntárselas sin arrancar el contexto y llamar a cada endpoint.
     */
    @Test
    @DisplayName("Toda authority exigida por un @PreAuthorize la tiene algún rol")
    void ningunaAuthorityExigidaEsInalcanzable() throws IOException {
        Set<String> concedidas = Stream.of(Role.values())
                .flatMap(rol -> RoleAuthorities.forRole(rol).stream())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        Set<String> exigidas = authoritiesExigidasEnElFuente();

        assertThat(exigidas)
                .as("si esto está vacío, el rastreo del fuente ha dejado de encontrar los "
                        + "@PreAuthorize y el test ya no comprueba nada")
                .hasSizeGreaterThan(20);
        assertThat(concedidas)
                .as("estas authorities se exigen en un endpoint y no las tiene ningún rol: "
                        + "ese endpoint responde 403 a todo el mundo")
                .containsAll(exigidas);
    }

    private static Set<String> authoritiesExigidasEnElFuente() throws IOException {
        Pattern patron = Pattern.compile("hasAuthority\\('([^']+)'\\)");
        Set<String> encontradas = new TreeSet<>();
        // El directorio de trabajo de los tests de Gradle es el del módulo.
        try (Stream<Path> ficheros = Files.walk(Path.of("src", "main", "java"))) {
            for (Path fichero : ficheros.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher encaja = patron.matcher(Files.readString(fichero));
                while (encaja.find()) {
                    encontradas.add(encaja.group(1));
                }
            }
        }
        return encontradas;
    }
}
