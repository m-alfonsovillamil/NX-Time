package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByNombre(String nombre);

    /**
     * Las zonas horarias que usa alguna empresa (ADR 032). Las tareas que
     * recorren todas las empresas a la vez van zona por zona: "el mes pasado"
     * no empieza en el mismo instante en Madrid que en Canarias. Son muy
     * pocas, casi siempre una o dos.
     */
    @Query("SELECT DISTINCT c.zonaHoraria FROM empresas c")
    List<String> findZonasEnUso();
}
