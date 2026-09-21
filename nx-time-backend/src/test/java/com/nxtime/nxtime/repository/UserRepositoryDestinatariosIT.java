package com.nxtime.nxtime.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * La consulta de destinatarios de avisos, contra PostgreSQL real (Fase A5).
 *
 * Lo que se comprueba aquí no es la regla --esa es {@link RoleAuthorities#rolesCon}
 * y se prueba sin base de datos en {@code DestinatariosTest}-- sino que la
 * consulta la aplica entera: filtra por empresa, descarta las cuentas de baja y
 * se queda solo con los roles pedidos.
 *
 * Con mocks no se probaría nada: el filtro lo hace el SQL.
 */
class UserRepositoryDestinatariosIT extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private Company empresa;
    private Company otraEmpresa;
    private User empleada;
    private User gestor;
    private User rrhh;
    private User admin;
    private User gestorDeBaja;
    private User gestorDeOtraEmpresa;

    private User alta(Company donde, String nombre, Role rol, boolean activo) {
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre + System.nanoTime() + "@test").contrasena("x")
                .rol(rol).empresa(donde).activo(activo).build());
    }

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("TechCorp " + System.nanoTime()).build());
        otraEmpresa = companyRepository.save(Company.builder().nombre("Otra " + System.nanoTime()).build());

        empleada = alta(empresa, "ana", Role.EMPLEADO, true);
        gestor = alta(empresa, "marta", Role.GESTOR, true);
        rrhh = alta(empresa, "elena", Role.RRHH, true);
        admin = alta(empresa, "javi", Role.ADMIN, true);
        gestorDeBaja = alta(empresa, "exgestor", Role.GESTOR, false);
        gestorDeOtraEmpresa = alta(otraEmpresa, "ajeno", Role.GESTOR, true);
    }

    @Test
    @DisplayName("Devuelve a quien tiene la authority, sin las bajas ni la gente de otra empresa")
    void destinatarios_filtraPorEmpresaActivoYRol() {
        List<User> destinatarios = userRepository.findDestinatarios(
                empresa, RoleAuthorities.rolesCon("ausencia:aprobar"));

        assertThat(destinatarios).containsExactlyInAnyOrder(gestor, rrhh, admin);
        // Las tres cosas que la consulta tiene que descartar, cada una por su
        // motivo: no tiene la authority, está de baja, y es de otra empresa.
        assertThat(destinatarios)
                .doesNotContain(empleada)
                .doesNotContain(gestorDeBaja)
                .doesNotContain(gestorDeOtraEmpresa);
    }

    /*
     * El caso que motivó que esto vaya por authority y no por rol: una empresa
     * cuyo único responsable es el ADMIN que la fundó. Filtrando por
     * Role.GESTOR se quedaría sin avisar a nadie, y nadie lo notaría.
     */
    @Test
    @DisplayName("En una empresa cuyo único responsable es el ADMIN, el aviso le llega a él")
    void destinatarios_empresaSoloConAdmin() {
        Company reciente = companyRepository.save(
                Company.builder().nombre("Recien fundada " + System.nanoTime()).build());
        User fundador = alta(reciente, "fundador", Role.ADMIN, true);
        alta(reciente, "primero", Role.EMPLEADO, true);

        assertThat(userRepository.findDestinatarios(reciente, RoleAuthorities.rolesCon("ausencia:aprobar")))
                .containsExactly(fundador);
    }

    @Test
    @DisplayName("Una authority que solo tiene ADMIN no arrastra a RRHH ni a GESTOR")
    void destinatarios_authorityDeAdmin() {
        assertThat(userRepository.findDestinatarios(empresa, RoleAuthorities.rolesCon("denuncia:instruir")))
                .containsExactly(admin);
    }

    @Test
    @DisplayName("La plantilla activa de una empresa excluye bajas y ajenos")
    void plantillaActiva() {
        // La usa el aviso de oferta interna, que va a todo el mundo.
        assertThat(userRepository.findByEmpresaAndActivoTrue(empresa))
                .containsExactlyInAnyOrder(empleada, gestor, rrhh, admin)
                .doesNotContain(gestorDeBaja, gestorDeOtraEmpresa);
    }
}
