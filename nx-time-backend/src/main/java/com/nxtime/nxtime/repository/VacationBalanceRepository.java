package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.VacationBalance;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacationBalanceRepository extends JpaRepository<VacationBalance, Long> {

    Optional<VacationBalance> findByUsuarioAndAnio(User usuario, int anio);

    /**
     * Los saldos de un año para un grupo de usuarios, en una sola
     * consulta.
     *
     * Existe para el listado de empleados: pedir el saldo uno a uno con
     * {@link #findByUsuarioAndAnio} dispararía un SELECT por empleado
     * cada vez que se abre el panel de empresa.
     */
    List<VacationBalance> findByAnioAndUsuarioIn(int anio, Collection<User> usuarios);
}
