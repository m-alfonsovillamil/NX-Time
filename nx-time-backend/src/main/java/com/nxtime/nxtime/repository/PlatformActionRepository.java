package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.PlatformAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformActionRepository extends JpaRepository<PlatformAction, Long> {

    /** Lo último que se ha hecho con una empresa, lo más reciente primero. */
    List<PlatformAction> findTop50ByEmpresaIdOrderByEnDescIdDesc(long empresaId);
}
