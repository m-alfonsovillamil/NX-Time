package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByNombre(String nombre);
}
