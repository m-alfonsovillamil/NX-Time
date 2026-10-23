package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.SegundoFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SegundoFactorRepository extends JpaRepository<SegundoFactor, Long> {

    /**
     * Gasta el paso de un código: solo si es posterior al último usado.
     *
     * Devuelve 0 si otra petición se le ha adelantado con ese mismo código (o
     * con uno posterior). Es lo que impide que un código valga dos veces
     * aunque lleguen dos peticiones a la vez: comprobar en Java y guardar
     * después dejaría un hueco entre las dos cosas.
     */
    @Modifying
    @Query(value = "UPDATE segundo_factor SET ultimo_paso = :paso "
            + "WHERE usuario_id = :usuarioId AND (ultimo_paso IS NULL OR ultimo_paso < :paso)", nativeQuery = true)
    int usarPaso(@Param("usuarioId") long usuarioId, @Param("paso") long paso);

    /**
     * Empieza (o vuelve a empezar) una activación: un secreto nuevo, sin
     * confirmar y sin pasos usados. En una sola sentencia, para que dos
     * peticiones a la vez no choquen en la clave primaria (lo hacía la web con
     * el modo estricto de React; lo vio el e2e). Si ya está activo no lo toca:
     * devuelve 0.
     */
    @Modifying
    @Query(value = "INSERT INTO segundo_factor (usuario_id, secreto_cifrado, creado_en) "
            + "VALUES (:usuarioId, :secreto, :ahora) "
            + "ON CONFLICT (usuario_id) DO UPDATE SET secreto_cifrado = EXCLUDED.secreto_cifrado, "
            + "creado_en = EXCLUDED.creado_en, confirmado_en = NULL, ultimo_paso = NULL "
            + "WHERE segundo_factor.confirmado_en IS NULL", nativeQuery = true)
    int empezar(@Param("usuarioId") long usuarioId, @Param("secreto") String secreto,
            @Param("ahora") java.time.Instant ahora);
}
