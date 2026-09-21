package com.nxtime.nxtime.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.UserRepository;
import java.util.Collection;
import java.util.List;

/**
 * Prepara un {@code UserRepository} simulado para las consultas de
 * destinatarios de avisos (Fase A5).
 *
 * <b>Por qué no basta un {@code thenReturn}.</b> Antes de A5, los servicios
 * pedían {@code findByEmpresa(empresa)} y filtraban en Java, así que a los
 * tests les bastaba con devolver la plantilla entera: el filtro ocurría en el
 * código bajo prueba. Ahora filtra la base, y un mock que devolviera la
 * plantilla entera haría pasar tests que en producción avisarían a quien no
 * debe --lo contrario de lo que sirve un test--.
 *
 * Esto simula lo que hace el SQL de verdad: quedarse con los activos de esa
 * empresa cuyo rol esté entre los pedidos. Así cada test sigue diciendo "la
 * empresa tiene esta gente" y comprobando a quién se avisa, que es lo que
 * quiere decir.
 *
 * Que el SQL de verdad haga exactamente esto lo comprueba
 * {@code UserRepositoryDestinatariosIT} contra PostgreSQL real; aquí se da por
 * bueno a propósito, igual que se da por buena cualquier otra consulta en un
 * test unitario.
 */
public final class MockDeDestinatarios {

    private MockDeDestinatarios() {
    }

    /**
     * Dice qué gente hay en una empresa, y deja el repositorio respondiendo a
     * las dos consultas de destinatarios como lo haría la base.
     *
     * Los stubs son {@code lenient} porque no todos los tests que preparan una
     * plantilla llegan a publicar un aviso: algunos comprueban justo que NO se
     * consulta a nadie.
     */
    public static void plantilla(UserRepository repositorio, Company empresa, User... personas) {
        List<User> gente = List.of(personas);

        lenient().when(repositorio.findDestinatarios(eq(empresa), any()))
                .thenAnswer(invocacion -> {
                    Collection<Role> roles = invocacion.getArgument(1);
                    return gente.stream()
                            .filter(User::isActivo)
                            .filter(persona -> roles.contains(persona.getRol()))
                            .toList();
                });

        lenient().when(repositorio.findByEmpresaAndActivoTrue(empresa))
                .thenAnswer(invocacion -> gente.stream().filter(User::isActivo).toList());
    }
}
