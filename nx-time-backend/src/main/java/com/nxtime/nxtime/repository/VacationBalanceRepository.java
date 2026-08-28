package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.VacationBalance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacationBalanceRepository extends JpaRepository<VacationBalance, Long> {

    Optional<VacationBalance> findByUsuarioAndAnio(User usuario, int anio);
}
