package com.nxtime.nxtime.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A quién se manda un aviso. Estas reglas estaban copiadas en cinco servicios;
 * ahora se prueban una vez aquí.
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
    @DisplayName("Se elige por authority, así que el ADMIN también recibe lo que puede aprobar")
    void porAuthority_noPorRol() {
        assertThat(Destinatarios.conAuthority(plantilla, "ausencia:aprobar"))
                .containsExactly(gestor, rrhh, admin);
    }

    @Test
    @DisplayName("Una cuenta de baja no recibe avisos, aunque tenga la authority")
    void lasCuentasDeBaja_noReciben() {
        assertThat(Destinatarios.conAuthority(plantilla, "ausencia:aprobar")).doesNotContain(gestorDeBaja);
        assertThat(Destinatarios.activosMenos(plantilla, empleada)).doesNotContain(gestorDeBaja);
    }

    @Test
    @DisplayName("Se puede excluir a quien hace la acción")
    void conAuthorityMenos_excluye() {
        assertThat(Destinatarios.conAuthorityMenos(plantilla, "correccion:aprobar", gestor))
                .containsExactly(rrhh, admin);
    }

    /*
     * La variante sin exclusión existe por el canal de denuncias: quitar al
     * denunciante de la lista de avisados revelaría quién ha denunciado.
     */
    @Test
    @DisplayName("conAuthority no excluye a nadie, ni siquiera a quien tiene la authority y actúa")
    void conAuthority_noExcluyeANadie() {
        assertThat(Destinatarios.conAuthority(plantilla, "denuncia:instruir")).containsExactly(admin);
    }

    @Test
    @DisplayName("La plantilla es todo activo menos el autor, sin mirar authorities")
    void activosMenos_laPlantilla() {
        assertThat(Destinatarios.activosMenos(plantilla, admin)).containsExactly(empleada, gestor, rrhh);
    }
}
