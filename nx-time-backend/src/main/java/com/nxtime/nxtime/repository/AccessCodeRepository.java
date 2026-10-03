package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.AccessCode;
import com.nxtime.nxtime.domain.AccessCodeType;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessCodeRepository extends JpaRepository<AccessCode, Long> {

    /** Los que siguen sin usar ni anular: se anulan al emitir otro, porque solo vale el último. */
    List<AccessCode> findByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNull(User usuario);

    /** El último emitido sin usar ni anular. Si ha caducado o se agotó, no vale. */
    Optional<AccessCode> findFirstByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNullOrderByCreadoEnDesc(User usuario);

    /** Para el límite de códigos por hora: cuentan también los anulados. */
    long countByUsuarioAndCreadoEnAfter(User usuario, Instant desde);

    /** Para el límite de altas por empresa y día (ADR 034): los códigos de ese tipo emitidos a gente de la empresa. */
    long countByUsuario_Empresa_IdAndTipoAndCreadoEnAfter(long empresaId, AccessCodeType tipo, Instant desde);
}
