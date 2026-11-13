package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.FichajeRecibido;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FichajeRecibidoRepository extends JpaRepository<FichajeRecibido, Long> {

    /** Si este fichaje sin conexión ya llegó antes (ADR 054). */
    Optional<FichajeRecibido> findByUsuarioIdAndIdCliente(long usuarioId, UUID idCliente);

    /**
     * De estas jornadas, las que tienen algún fichaje sin conexión aplicado.
     * No basta con {@code registros.origen}: solo dice cómo se ABRIÓ la
     * jornada, y la salida pudo llegar sin conexión a una jornada abierta con red.
     */
    @Query("SELECT DISTINCT f.registroId FROM fichajes_recibidos f WHERE f.registroId IN :ids")
    List<Long> conFichajeSinConexion(@Param("ids") Collection<Long> ids);
}
