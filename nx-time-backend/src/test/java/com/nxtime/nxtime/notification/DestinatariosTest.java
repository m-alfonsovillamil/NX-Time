package com.nxtime.nxtime.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A quién se manda un aviso.
 *
 * Desde la Fase A5 la selección se hace en SQL: se traducen las authorities a
 * roles con {@link RoleAuthorities#rolesCon} y la base devuelve solo a quien
 * hay que avisar, en vez de traer toda la plantilla de la empresa para
 * filtrarla en Java.
 *
 * Aquí se prueba la parte que no necesita base de datos, que es la que decide:
 * la traducción de authority a roles. Que la consulta la aplique bien lo
 * comprueba {@code UserRepositoryDestinatariosIT} contra PostgreSQL real.
 */
class DestinatariosTest {

    private static User usuario(long id, Role rol, boolean activo) {
        return User.builder().id(id).email(id + "@test").nombre("U" + id).rol(rol).activo(activo).build();
    }

    private final User empleada = usuario(1, Role.EMPLEADO, true);
    private final User gestor = usuario(2, Role.GESTOR, true);
    private final User rrhh = usuario(3, Role.RRHH, true);
    private final User admin = usuario(4, Role.ADMIN, true);
    private final User gestorDeBaja = usuario(5, Role.GESTOR, false);

    private final List<User> plantilla = List.of(empleada, gestor, rrhh, admin, gestorDeBaja);

    /*
     * Por authority y no por rol: "ausencia:aprobar" la tienen GESTOR, RRHH y
     * ADMIN. Filtrar por Role.GESTOR dejaría sin avisar a una empresa cuyo único
     * responsable es el ADMIN fundador, un error que ya se corrigió una vez.
     */
    @Test
    @DisplayName("Se elige por authority, así que el ADMIN también entra en lo que puede aprobar")
    void porAuthority_noPorRol() {
        assertThat(RoleAuthorities.rolesCon("ausencia:aprobar"))
                .containsExactly(Role.GESTOR, Role.RRHH, Role.ADMIN);
    }

    /*
     * La denuncia sobre un GESTOR no puede leerla ese GESTOR (Ley 2/2023, ver
     * RoleAuthorities): "denuncia:instruir" no baja de ADMIN.
     */
    @Test
    @DisplayName("Una authority que solo tiene ADMIN devuelve solo ADMIN")
    void authorityDeAdmin_soloAdmin() {
        assertThat(RoleAuthorities.rolesCon("denuncia:instruir")).containsExactly(Role.ADMIN);
    }

    @Test
    @DisplayName("Una authority de todo el mundo devuelve los cuatro roles")
    void authorityDeTodos_losCuatro() {
        assertThat(RoleAuthorities.rolesCon("fichaje:escribir")).hasSize(Role.values().length);
    }

    /**
     * El que ata las dos verdades.
     *
     * {@code rolesCon} es {@code forRole} del revés, y se calcula a partir de
     * los mismos conjuntos. Si algún día alguien lo sustituyera por una lista
     * escrita a mano --la tentación es escribirla, porque se lee mejor--, esto
     * se pondría rojo en cuanto las dos dejaran de coincidir. Importa porque
     * quedarse corto aquí no rompe nada visible: simplemente hay gente a la que
     * deja de llegarle un aviso, y nadie echa en falta un correo que no sabe
     * que existía.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("todasLasAuthorities")
    @DisplayName("rolesCon y forRole dicen lo mismo, para cada authority del sistema")
    void rolesCon_coincideConForRole(String authority) {
        Set<Role> esperados = java.util.Arrays.stream(Role.values())
                .filter(rol -> RoleAuthorities.forRole(rol).contains(authority))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(RoleAuthorities.rolesCon(authority)).isEqualTo(esperados);
    }

    /** Todas las authorities que existen, sacadas del propio catálogo. */
    static Set<String> todasLasAuthorities() {
        return java.util.Arrays.stream(Role.values())
                .flatMap(rol -> RoleAuthorities.forRole(rol).stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("Una authority que no existe no devuelve ningún rol, en vez de reventar")
    void authorityDesconocida_ningunRol() {
        // Destinatarios lo trata como "no hay a quién avisar" y ni siquiera
        // llega a consultar: un IN vacío falla en algunos dialectos.
        assertThat(RoleAuthorities.rolesCon("authority:que:no:existe")).isEmpty();
    }

    @Test
    @DisplayName("La plantilla es todo activo menos el autor, sin mirar authorities")
    void activosMenos_laPlantilla() {
        assertThat(Destinatarios.activosMenos(plantilla, admin)).containsExactly(empleada, gestor, rrhh);
    }

    @Test
    @DisplayName("Una cuenta de baja no recibe avisos, aunque quien consulte no los filtre")
    void lasCuentasDeBaja_noReciben() {
        // La consulta ya pide solo activos, pero la regla no puede depender de
        // cómo se consultara: esta lista los trae todos a propósito.
        assertThat(Destinatarios.activosMenos(plantilla, empleada)).doesNotContain(gestorDeBaja);
    }
}
