package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByEmpresa(Company empresa);

    List<User> findByEmpresaAndRol(Company empresa, Role rol);

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
