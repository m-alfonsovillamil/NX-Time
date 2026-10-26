package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.FichajeRecibido;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FichajeRecibidoRepository extends JpaRepository<FichajeRecibido, Long> {

    /** Si este fichaje sin conexión ya llegó antes (ADR 054). */
    Optional<FichajeRecibido> findByUsuarioIdAndIdCliente(long usuarioId, UUID idCliente);
}
