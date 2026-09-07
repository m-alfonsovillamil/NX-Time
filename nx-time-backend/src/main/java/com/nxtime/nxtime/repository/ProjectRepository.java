package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Los proyectos de una empresa, por código.
     *
     * Se ordena por código y no por nombre porque el código es lo
     * estable: un proyecto puede renombrarse a mitad y saltaría de sitio
     * en la lista, mientras que "NX-2026-04" sigue donde estaba.
     */
    List<Project> findByEmpresaOrderByCodigoAsc(Company empresa);

    /**
     * Se comprueba antes de insertar para dar un 409 con un mensaje que
     * se entienda; el UNIQUE de la base sigue siendo quien lo garantiza
     * si dos altas llegan a la vez (mismo criterio que
     * {@code DepartmentServiceImpl.crear}).
     */
    boolean existsByEmpresaAndCodigoIgnoreCase(Company empresa, String codigo);
}
