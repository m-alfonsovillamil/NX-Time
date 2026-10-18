package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByNombre(String nombre);

    long countByEstado(com.nxtime.nxtime.domain.CompanyStatus estado);

    /**
     * Las zonas horarias que usa alguna empresa (ADR 032). Las tareas que
     * recorren todas las empresas a la vez van zona por zona: "el mes pasado"
     * no empieza en el mismo instante en Madrid que en Canarias. Son muy
     * pocas, casi siempre una o dos.
     */
    @Query("SELECT DISTINCT c.zonaHoraria FROM empresas c")
    List<String> findZonasEnUso();

    /**
     * Quien deja de estar (baja o borrado) deja de ser Responsable del canal
     * de denuncias (ADR 048): con un Responsable de baja nadie podría
     * instruir, porque los ADMIN no lo hacen mientras haya uno designado.
     * Vuelven a instruir ellos hasta que designen a otro.
     */
    @Modifying
    @Query(value = "UPDATE empresas SET responsable_canal_id = NULL, responsable_canal_desde = NULL "
            + "WHERE responsable_canal_id = :usuarioId", nativeQuery = true)
    int quitarDelCanal(@Param("usuarioId") long usuarioId);
}
