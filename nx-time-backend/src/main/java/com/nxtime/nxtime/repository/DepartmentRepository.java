package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Department;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByEmpresaOrderByNombreAsc(Company empresa);

    /** Para no dejar que el UNIQUE de la base sea quien dé el error. */
    boolean existsByEmpresaAndNombreIgnoreCase(Company empresa, String nombre);
}
