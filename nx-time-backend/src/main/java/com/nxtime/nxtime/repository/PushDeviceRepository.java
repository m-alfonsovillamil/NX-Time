package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.PushDevice;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByToken(String token);

    /**
     * Los tokens a los que mandar un aviso: los de esa persona, si sigue
     * activa. A quien han dado de baja no se le manda nada aunque su móvil
     * siga registrado: no puede entrar a leerlo.
     */
    @Query("SELECT d.token FROM dispositivos_push d "
            + "WHERE d.usuario.id = :usuarioId AND d.usuario.activo = true")
    List<String> findTokensDeUsuarioActivo(@Param("usuarioId") long usuarioId);

    /** Para la exportación de datos personales. */
    List<PushDevice> findByUsuario_IdOrderByRegistradoEnDesc(long usuarioId);

    /**
     * Borra los tokens que FCM ha dicho que ya no valen.
     *
     * Transaccional por su cuenta: se llama DESPUÉS de enviar, fuera de
     * cualquier transacción, porque tener una conexión de la base abierta
     * mientras se espera a Google es justo lo que la Fase A9 sacó del correo.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM dispositivos_push d WHERE d.token IN :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);

    @Modifying
    @Transactional
    @Query("DELETE FROM dispositivos_push d WHERE d.token = :token AND d.usuario.id = :usuarioId")
    int deleteByTokenAndUsuario(@Param("token") String token, @Param("usuarioId") long usuarioId);
}
