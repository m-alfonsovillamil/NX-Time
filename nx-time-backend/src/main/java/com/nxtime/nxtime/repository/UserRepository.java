package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByEmpresa(Company empresa);

    List<User> findByEmpresaAndRol(Company empresa, Role rol);

    /**
     * Cuánta gente activa hay con ese rol (Fase A6).
     *
     * El panel de empresa contaba con {@code findByEmpresaAndRol(...).stream()
     * .filter(User::isActivo).count()}: traía a toda la plantilla para devolver
     * un número.
     */
    long countByEmpresaAndRolAndActivoTrue(Company empresa, Role rol);

    /**
     * A quién hay que avisar de algo, resuelto en la base (Fase A5).
     *
     * Sustituye al patrón de traer {@code findByEmpresa(empresa)} entero y
     * filtrarlo en Java, que estaba en nueve sitios. Cada petición de ausencia,
     * corrección o denuncia cargaba **toda la plantilla de la empresa** --con su
     * empresa y su departamento, que son EAGER-- para quedarse con dos o tres
     * personas. Uno de esos sitios era el camino de fichar en día no laborable.
     *
     * Los roles los calcula {@link com.nxtime.nxtime.domain.RoleAuthorities#rolesCon}
     * a partir de la misma tabla que usan los {@code @PreAuthorize}: la
     * consulta filtra por rol porque es lo que la base sabe, pero quien decide
     * sigue siendo la authority.
     */
    // "usuarios" y no "User": las entidades de este proyecto se declaran con
    // @Entity(name = "<tabla>"), así que ese es su nombre en JPQL.
    @Query("select u from usuarios u where u.empresa = :empresa and u.activo = true and u.rol in :roles")
    List<User> findDestinatarios(@Param("empresa") Company empresa, @Param("roles") Collection<Role> roles);

    /**
     * Toda la plantilla activa de una empresa.
     *
     * Para los avisos que van a todo el mundo y no dependen de ninguna
     * authority, como publicar una oferta interna. Antes se traía también a los
     * inactivos para descartarlos después.
     */
    List<User> findByEmpresaAndActivoTrue(Company empresa);

    /**
     * Cuánta gente hay en un departamento (Fase B).
     *
     * Borrar un departamento con plantilla dentro tiene que fallar con
     * un mensaje que se entienda; sin esto, lo que salta es la
     * violación de clave ajena de {@code fk_usuarios_departamento}, que
     * llega al cliente como un 500 sin explicación.
     */
    long countByDepartamento_Id(long departamentoId);
}
